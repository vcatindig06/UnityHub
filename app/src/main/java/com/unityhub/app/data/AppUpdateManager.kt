package com.unityhub.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.unityhub.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val releasedAt: String,
    val summary: String,
    val changes: List<String>,
    val downloadPath: String,
    val sha256: String,
    val sizeBytes: Long
)

data class AppUpdateManifest(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minimumSupportedVersionCode: Int,
    val releases: List<AppUpdateRelease>
)

internal object AppUpdateRules {
    fun latestRelease(manifest: AppUpdateManifest): AppUpdateRelease? =
        manifest.releases.maxByOrNull(AppUpdateRelease::versionCode)

    fun hasUpdate(installedVersionCode: Int, latestVersionCode: Int): Boolean =
        latestVersionCode > installedVersionCode

    fun isRequiredUpdate(installedVersionCode: Int, minimumSupportedVersionCode: Int): Boolean =
        minimumSupportedVersionCode > installedVersionCode

    fun unseenReleases(
        manifest: AppUpdateManifest,
        lastSeenVersionCode: Int,
        installedVersionCode: Int
    ): List<AppUpdateRelease> = manifest.releases
        .filter { it.versionCode > lastSeenVersionCode && it.versionCode <= installedVersionCode }
        .sortedBy(AppUpdateRelease::versionCode)

    fun hasValidDownloadMetadata(release: AppUpdateRelease): Boolean =
        release.downloadPath.matches(
            Regex("releases/${release.versionCode}/[A-Za-z0-9._-]+\\.apk")
        ) &&
            release.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            release.sizeBytes in 1..AppUpdateManager.MAX_APK_SIZE_BYTES
}

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAutoDownloadEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_DOWNLOAD, false)

    fun setAutoDownloadEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_DOWNLOAD, enabled).apply()
    }

    fun isUpdateDismissed(versionCode: Int): Boolean =
        preferences.getInt(KEY_DISMISSED_VERSION, -1) == versionCode

    fun dismissUpdate(versionCode: Int) {
        preferences.edit().putInt(KEY_DISMISSED_VERSION, versionCode).apply()
    }

    fun lastSeenVersionCode(): Int =
        preferences.getInt(KEY_LAST_SEEN_VERSION, 0)

    fun markReleaseNotesSeen(versionCode: Int) {
        preferences.edit().putInt(KEY_LAST_SEEN_VERSION, versionCode).apply()
    }

    suspend fun findVerifiedDownload(release: AppUpdateRelease): File? = withContext(Dispatchers.IO) {
        if (!AppUpdateRules.hasValidDownloadMetadata(release)) return@withContext null
        val candidate = File(
            File(appContext.filesDir, "updates"),
            "UnityHub-${release.versionCode}.apk"
        )
        if (!candidate.isFile || candidate.length() != release.sizeBytes) {
            return@withContext null
        }
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            candidate.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            require(actualHash.equals(release.sha256, ignoreCase = true))
            validateDownloadedPackage(candidate, release)
            candidate
        }.getOrNull()
    }

    suspend fun fetchManifest(): AppUpdateManifest = withContext(Dispatchers.IO) {
        val connection = openStorageConnection("manifest.json")
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            require(status in 200..299) {
                "The update service returned HTTP $status."
            }
            requireTrustedStorageHost(connection)
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_MANIFEST_SIZE_BYTES) {
                        "The update information is larger than expected."
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            parseManifest(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadRelease(
        release: AppUpdateRelease,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(AppUpdateRules.hasValidDownloadMetadata(release)) {
            "The release does not contain valid download information."
        }

        val updatesDirectory = File(appContext.filesDir, "updates")
        require(updatesDirectory.exists() || updatesDirectory.mkdirs()) {
            "UnityHub could not prepare the update folder."
        }
        val destination = File(updatesDirectory, "UnityHub-${release.versionCode}.apk")
        val temporary = File(updatesDirectory, "UnityHub-${release.versionCode}.apk.part")
        val connection = openStorageConnection(release.downloadPath)
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", APK_MIME_TYPE)
            val status = connection.responseCode
            require(status in 200..299) {
                "The update download returned HTTP $status."
            }
            requireTrustedStorageHost(connection)

            val expectedHash = release.sha256.lowercase()
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0L) {
                require(declaredLength == release.sizeBytes) {
                    "The update file size does not match its release information."
                }
            }

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_APK_SIZE_BYTES &&
                                totalBytes <= release.sizeBytes) {
                                "The downloaded update is larger than expected."
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress((totalBytes.toFloat() / release.sizeBytes).coerceIn(0f, 1f))
                        }
                        output.fd.sync()
                    }
                }

                require(totalBytes == release.sizeBytes) {
                    "The downloaded update is incomplete. Check your connection and try again."
                }
                val actualHash = digest.digest().joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
                require(actualHash.equals(expectedHash, ignoreCase = true)) {
                    "UnityHub could not verify the downloaded update. The file was discarded."
                }
                validateDownloadedPackage(temporary, release)

                if (destination.exists()) {
                    require(destination.delete()) {
                        "UnityHub could not replace an older downloaded update."
                    }
                }
                require(temporary.renameTo(destination)) {
                    "UnityHub could not finish saving the downloaded update."
                }
                onProgress(1f)
                destination
            } catch (exception: Exception) {
                temporary.delete()
                throw exception
            }
        } finally {
            connection.disconnect()
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        require(apkFile.isFile && apkFile.parentFile == File(appContext.filesDir, "updates")) {
            "The verified UnityHub update file is no longer available."
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun parseManifest(source: String): AppUpdateManifest {
        val root = JSONObject(source)
        require(root.optInt("schemaVersion") == 1) {
            "This version of UnityHub cannot read the update information."
        }
        val releaseArray = root.optJSONArray("releases")
            ?: throw IllegalArgumentException("The update history is missing.")
        val releases = buildList {
            for (index in 0 until releaseArray.length()) {
                val item = releaseArray.optJSONObject(index) ?: continue
                val changesArray = item.optJSONArray("changes")
                val changes = buildList {
                    if (changesArray != null) {
                        for (changeIndex in 0 until changesArray.length()) {
                            changesArray.optString(changeIndex)
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }
                val versionCode = item.optInt("versionCode", -1)
                val versionName = item.optString("versionName")
                if (versionCode > 0 && versionName.isNotBlank()) {
                    add(
                        AppUpdateRelease(
                            versionCode = versionCode,
                            versionName = versionName,
                            releasedAt = item.optString("releasedAt"),
                            summary = item.optString("summary"),
                            changes = changes,
                            downloadPath = item.optString("downloadPath"),
                            sha256 = item.optString("sha256"),
                            sizeBytes = item.optLong("sizeBytes", 0L)
                        )
                    )
                }
            }
        }.distinctBy(AppUpdateRelease::versionCode).sortedBy(AppUpdateRelease::versionCode)

        require(releases.isNotEmpty()) { "No UnityHub releases are listed yet." }
        val latestVersionCode = root.optInt(
            "latestVersionCode",
            releases.last().versionCode
        )
        require(releases.last().versionCode == latestVersionCode) {
            "The latest UnityHub version does not match the release history."
        }
        val minimumSupportedVersionCode = root.optInt("minimumSupportedVersionCode", 1)
        require(minimumSupportedVersionCode in 1..latestVersionCode) {
            "The minimum supported UnityHub version is invalid."
        }

        return AppUpdateManifest(
            latestVersionCode = latestVersionCode,
            latestVersionName = root.optString(
                "latestVersionName",
                releases.last { it.versionCode == latestVersionCode }.versionName
            ),
            minimumSupportedVersionCode = minimumSupportedVersionCode,
            releases = releases
        )
    }

    private fun openStorageConnection(relativePath: String): HttpURLConnection {
        require(BuildConfig.SUPABASE_URL.startsWith("https://")) {
            "The UnityHub update service is not configured."
        }
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val encodedPath = relativePath.split('/').joinToString("/") { Uri.encode(it) }
        val url = URL(
            "$baseUrl/storage/v1/object/public/$UPDATE_BUCKET/$encodedPath"
        )
        require(url.protocol == "https") { "UnityHub updates must use a secure connection." }
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    private fun requireTrustedStorageHost(connection: HttpURLConnection) {
        val configuredHost = URL(BuildConfig.SUPABASE_URL).host
        require(connection.url.protocol == "https" &&
            connection.url.host.equals(configuredHost, ignoreCase = true)
        ) {
            "The update service redirected to an untrusted host."
        }
    }

    private fun validateDownloadedPackage(file: File, release: AppUpdateRelease) {
        val packageManager = appContext.packageManager
        val signingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlag)
            ?: throw IllegalArgumentException("The downloaded file is not a valid Android app.")
        require(archive.packageName == appContext.packageName) {
            "This update is not a UnityHub installation package."
        }
        require(archive.versionCode == release.versionCode &&
            archive.versionName == release.versionName) {
            "The downloaded app version does not match the update information."
        }

        val installed = packageManager.getPackageInfo(appContext.packageName, signingFlag)
        val installedSigners = signerDigests(installed)
        val updateSigners = signerDigests(archive)
        require(installedSigners.isNotEmpty() && installedSigners == updateSigners) {
            "This update uses a different UnityHub signing key. Back up and sync your data, then reinstall UnityHub using the official update setup before using in-app updates."
        }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
        }.toSet()
    }

    companion object {
        const val MAX_APK_SIZE_BYTES = 100L * 1024L * 1024L
        private const val MAX_MANIFEST_SIZE_BYTES = 1024 * 1024
        private const val NETWORK_TIMEOUT_MILLIS = 20_000
        private const val UPDATE_BUCKET = "unityhub-updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PREFERENCES_NAME = "unityhub_app_updates"
        private const val KEY_AUTO_DOWNLOAD = "auto_download_enabled"
        private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"
    }
}
