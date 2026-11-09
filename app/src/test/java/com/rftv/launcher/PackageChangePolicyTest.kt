package com.rftv.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageChangePolicyTest {
    @Test
    fun refreshesCatalogForInstalledRemovedChangedAndReplacedApps() {
        val actions = listOf(
            "android.intent.action.PACKAGE_ADDED",
            "android.intent.action.PACKAGE_REMOVED",
            "android.intent.action.PACKAGE_CHANGED",
            "android.intent.action.PACKAGE_REPLACED",
            "android.intent.action.PACKAGE_FULLY_REMOVED"
        )
        actions.forEach { action ->
            assertTrue(PackageChangePolicy.shouldRefresh(action, "com.example.tv", "com.rftv.launcher"))
        }
    }

    @Test
    fun ignoresRemovalBroadcastWhenPackageIsBeingReplaced() {
        assertFalse(PackageChangePolicy.shouldRefresh(
            "android.intent.action.PACKAGE_REMOVED",
            "com.example.tv",
            "com.rftv.launcher",
            isReplacing = true
        ))
        assertTrue(PackageChangePolicy.shouldRefresh(
            "android.intent.action.PACKAGE_ADDED",
            "com.example.tv",
            "com.rftv.launcher",
            isReplacing = true
        ))
    }

    @Test
    fun ignoresOwnPackageAndUnrelatedOrMalformedBroadcasts() {
        assertFalse(PackageChangePolicy.shouldRefresh("android.intent.action.PACKAGE_ADDED", "com.rftv.launcher", "com.rftv.launcher"))
        assertFalse(PackageChangePolicy.shouldRefresh("android.intent.action.BOOT_COMPLETED", "com.example.tv", "com.rftv.launcher"))
        assertFalse(PackageChangePolicy.shouldRefresh("android.intent.action.PACKAGE_ADDED", null, "com.rftv.launcher"))
        assertFalse(PackageChangePolicy.shouldRefresh(null, "com.example.tv", "com.rftv.launcher"))
    }
}
