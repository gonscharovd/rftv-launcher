package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageAnalysisPolicyTest {
    @Test
    fun internetPermissionAloneIsNotPrivacyRisk() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.example.catalog",
                label = "Catalog",
                isSystemApp = false,
                enabled = true,
                requestedPermissions = listOf("android.permission.INTERNET")
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.INFORMATIONAL, report.severity)
        assertTrue(report.evidence.any { it.contains("не доказывает") })
    }

    @Test
    fun sensitivePermissionsAndInternetRequireReviewButDoNotClaimSpying() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.example.voice",
                label = "Voice",
                isSystemApp = true,
                enabled = true,
                requestedPermissions = listOf(
                    "android.permission.INTERNET",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION"
                )
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.REVIEW, report.severity)
        assertEquals(PackageAnalysisPolicy.Confidence.LOW, report.confidence)
        assertTrue(report.limitation.contains("не доказывает"))
        assertTrue(report.evidence.any { it.contains("RECORD_AUDIO") })
    }

    @Test
    fun protectedPackageIdentityIsCritical() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.android.systemui",
                label = "System UI",
                isSystemApp = true,
                enabled = true
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.CRITICAL, report.severity)
        assertEquals(PackageAnalysisPolicy.Confidence.MEDIUM, report.confidence)
    }

    @Test
    fun dynamicallyProtectedHomeRoleIsReportedAsCritical() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.example.home",
                label = "OEM Home",
                isSystemApp = true,
                enabled = true,
                isProtected = true
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.CRITICAL, report.severity)
        assertTrue(report.evidence.any { it.contains("динамически защищён") })
    }

    @Test
    fun ordinaryAppNameContainingSettingsIsNotMarkedCritical() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.example.settings.helper",
                label = "Settings helper",
                isSystemApp = false,
                enabled = true
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.INFORMATIONAL, report.severity)
    }

    @Test
    fun mediatekTvMiddlewareIsProtected() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.mediatek.tis",
                label = "TV Input Service",
                isSystemApp = true,
                enabled = true
            )
        )
        assertEquals(PackageAnalysisPolicy.Severity.CRITICAL, report.severity)
    }

    @Test
    fun sberInputAndKeyboardComponentsAreProtected() {
        listOf("ru.sberdevices.app.starkeyboard", "ru.sberdevices.tvinputs", "com.mediatek.tvinput")
            .forEach { packageId ->
                val report = PackageAnalysisPolicy.analyze(
                    PackageAnalysisPolicy.Signals(
                        packageName = packageId,
                        label = packageId,
                        isSystemApp = true,
                        enabled = true
                    )
                )
                assertEquals(PackageAnalysisPolicy.Severity.CRITICAL, report.severity)
            }
    }

    @Test
    fun hardProtectedIdentityAndPlatformPrefixesAreDeniedForOperations() {
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.tv",
            "com.android.tv.settings",
            "com.mediatek.tv.service",
            "com.cvte.platform.service",
            "ru.sberdevices.starlauncher",
            "ru.sberdevices.services.remote",
            "ru.sberdevices.tvinputs.service"
        ).forEach { assertTrue("Expected protected package: $it", PackageAnalysisPolicy.isProtectedPackage(it)) }
        assertTrue(!PackageAnalysisPolicy.isProtectedPackage("com.example.settings.helper"))
    }

    @Test
    fun exactMatchedProfileProvidesHighConfidenceButStillHasLimitation() {
        val report = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = "com.example.tv",
                label = "TV",
                isSystemApp = true,
                enabled = true,
                profilePurpose = "Проверенное приложение контента",
                profileRisk = "Средний"
            )
        )
        assertEquals(PackageAnalysisPolicy.Confidence.HIGH, report.confidence)
        assertTrue(report.limitation.contains("другой версии прошивки"))
    }
}
