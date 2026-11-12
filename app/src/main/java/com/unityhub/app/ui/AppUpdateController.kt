package com.unityhub.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.BuildConfig
import com.unityhub.app.data.AppUpdateManager
import com.unityhub.app.data.AppUpdateRelease
import com.unityhub.app.data.AppUpdateRules
import com.unityhub.app.data.AppUpdateManifest
import com.unityhub.app.sync.AppUpdateWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppUpdateController() {
    val context = LocalContext.current
    val manager = remember(context) { AppUpdateManager(context) }
    val scope = rememberCoroutineScope()

    var updateRelease by remember { mutableStateOf<AppUpdateRelease?>(null) }
    var isRequired by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var newReleases by remember { mutableStateOf<List<AppUpdateRelease>>(emptyList()) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var canInstallDirectly by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var updateMessage by remember { mutableStateOf("") }

    val exportUpdateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AppUpdateManager.APK_MIME_TYPE)
    ) { destination ->
        val sourceFile = pendingExportFile
        pendingExportFile = null
        if (destination != null && sourceFile != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            sourceFile.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("The selected destination could not be opened.")
                    }
                }
                updateMessage = if (result.isSuccess) {
                    "The verified APK was saved. Sync your team data, uninstall the older UnityHub app, then open the saved APK to install it. Future updates can install in place."
                } else {
                    result.exceptionOrNull()?.message ?: "UnityHub could not save the update file."
                }
            }
        }
    }

    fun saveForReinstall(file: File, release: AppUpdateRelease) {
        pendingExportFile = file
        updateMessage = "This release uses a different signing key. Save the APK to Downloads for the one-time reinstall."
        exportUpdateLauncher.launch("UnityHub-${release.versionName}-update.apk")
    }

    fun download(release: AppUpdateRelease) {
        if (downloading) return
        scope.launch {
            downloading = true
            downloadProgress = 0f
            updateMessage = ""
            downloadedApk = null
            try {
                val verifiedFile = manager.downloadRelease(release) { progress ->
                    downloadProgress = progress
                }
                downloadedApk = verifiedFile
                canInstallDirectly = manager.hasMatchingInstalledSigner(verifiedFile, release)
                updateMessage = if (canInstallDirectly) {
                    "The update is downloaded and verified. Android will ask you to approve installation."
                } else {
                    "The update is verified, but its signing key differs from this installation. Save it for the one-time reinstall."
                }
                showUpdateDialog = true
            } catch (exception: Exception) {
                updateMessage = exception.message ?: "UnityHub could not download the update."
            } finally {
                downloading = false
            }
        }
    }

    fun install(file: File, release: AppUpdateRelease) {
        if (!manager.hasMatchingInstalledSigner(file, release)) {
            saveForReinstall(file, release)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            updateMessage = "Allow UnityHub to install updates in Android settings, then return here and tap Install update."
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }.onFailure {
                updateMessage = "Open Android Settings and allow UnityHub to install unknown apps."
            }
            return
        }

        runCatching {
            context.startActivity(manager.createInstallIntent(file, release))
        }.onFailure {
            updateMessage = it.message ?: "Android could not open the installer."
        }
    }

    LaunchedEffect(manager) {
        AppUpdateWorkScheduler.setEnabled(context, manager.isAutoDownloadEnabled())
        val loadedManifest = runCatching { manager.fetchManifest() }.getOrNull()
            ?: return@LaunchedEffect
        val latest = AppUpdateRules.latestRelease(loadedManifest)
            ?: return@LaunchedEffect

        if (AppUpdateRules.hasUpdate(BuildConfig.VERSION_CODE, latest.versionCode)) {
            updateRelease = latest
            isRequired = AppUpdateRules.isRequiredUpdate(
                BuildConfig.VERSION_CODE,
                loadedManifest.minimumSupportedVersionCode
            )
            downloadedApk = manager.findVerifiedDownload(latest)
            canInstallDirectly = downloadedApk?.let {
                manager.hasMatchingInstalledSigner(it, latest)
            } == true
            showUpdateDialog = isRequired ||
                !manager.isUpdateDismissed(latest.versionCode) ||
                downloadedApk != null

            if (manager.isAutoDownloadEnabled() &&
                downloadedApk == null &&
                AppUpdateRules.hasValidDownloadMetadata(latest)
            ) {
                download(latest)
            }
        } else {
            newReleases = AppUpdateRules.unseenReleases(
                loadedManifest,
                manager.lastSeenVersionCode(),
                BuildConfig.VERSION_CODE
            )
            showWhatsNew = newReleases.isNotEmpty()
        }
    }

    val release = updateRelease
    if (showUpdateDialog && release != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isRequired && !downloading) {
                    manager.dismissUpdate(release.versionCode)
                    showUpdateDialog = false
                }
            },
            title = {
                Text(
                    if (isRequired) "UnityHub update required" else "UnityHub update available",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${release.versionName} is ready for your device.")
                    if (release.summary.isNotBlank()) {
                        Text(release.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    release.changes.forEach { change ->
                        Text("• $change", style = MaterialTheme.typography.bodySmall)
                    }
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Downloading update… ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (updateMessage.isNotBlank()) {
                        Text(
                            updateMessage,
                            color = if (downloadedApk != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Updates are downloaded from UnityHub's update service. Android will ask you to confirm installation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = downloadedApk
                        if (file == null) {
                            download(release)
                        } else if (canInstallDirectly) {
                            install(file, release)
                        } else {
                            saveForReinstall(file, release)
                        }
                    },
                    enabled = !downloading
                ) {
                    if (downloading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            when {
                                downloadedApk == null -> "Download update"
                                canInstallDirectly -> "Install update"
                                else -> "Save APK for reinstall"
                            }
                        )
                    }
                }
            },
            dismissButton = {
                if (!isRequired) {
                    TextButton(
                        onClick = {
                            manager.dismissUpdate(release.versionCode)
                            showUpdateDialog = false
                        },
                        enabled = !downloading
                    ) {
                        Text("Later")
                    }
                }
            }
        )
    }

    if (showWhatsNew && newReleases.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                manager.markReleaseNotesSeen(BuildConfig.VERSION_CODE)
                showWhatsNew = false
            },
            title = { Text("What's new in UnityHub", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(newReleases, key = { it.versionCode }) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Version ${item.versionName}", fontWeight = FontWeight.SemiBold)
                            if (item.summary.isNotBlank()) {
                                Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                            }
                            item.changes.forEach { change ->
                                Text("• $change", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        manager.markReleaseNotesSeen(BuildConfig.VERSION_CODE)
                        showWhatsNew = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}
