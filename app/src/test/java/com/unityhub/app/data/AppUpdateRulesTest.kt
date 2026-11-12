package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRulesTest {
    private fun release(
        versionCode: Int,
        path: String = "releases/$versionCode/UnityHub.apk",
        hash: String = "a".repeat(64),
        size: Long = 1024L
    ) = AppUpdateRelease(
        versionCode = versionCode,
        versionName = "0.$versionCode.0",
        releasedAt = "2026-11-12",
        summary = "Test release",
        changes = listOf("A tested change"),
        downloadPath = path,
        sha256 = hash,
        sizeBytes = size
    )

    @Test
    fun latestReleaseUsesHighestVersionCode() {
        val manifest = AppUpdateManifest(31, "0.31.0", 30, listOf(release(29), release(31), release(30)))
        assertEquals(31, AppUpdateRules.latestRelease(manifest)?.versionCode)
    }

    @Test
    fun detectsOutdatedAndRequiredVersionsSeparately() {
        assertTrue(AppUpdateRules.hasUpdate(30, 31))
        assertFalse(AppUpdateRules.hasUpdate(31, 31))
        assertTrue(AppUpdateRules.isRequiredUpdate(29, 30))
        assertFalse(AppUpdateRules.isRequiredUpdate(30, 30))
    }

    @Test
    fun releaseNotesOnlyIncludeVersionsSinceLastViewedAndNotNewerThanInstalled() {
        val manifest = AppUpdateManifest(
            32,
            "0.32.0",
            30,
            listOf(release(29), release(30), release(31), release(32))
        )
        assertEquals(
            listOf(31),
            AppUpdateRules.unseenReleases(manifest, lastSeenVersionCode = 30, installedVersionCode = 31)
                .map(AppUpdateRelease::versionCode)
        )
    }

    @Test
    fun acceptsOnlySafeReleasePathsAndValidHashesAndSizes() {
        assertTrue(AppUpdateRules.hasValidDownloadMetadata(release(31)))
        assertFalse(AppUpdateRules.hasValidDownloadMetadata(release(31, path = "../../outside.apk")))
        assertFalse(AppUpdateRules.hasValidDownloadMetadata(release(31, hash = "bad")))
        assertFalse(AppUpdateRules.hasValidDownloadMetadata(release(31, size = AppUpdateManager.MAX_APK_SIZE_BYTES + 1)))
    }
}
