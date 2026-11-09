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
        assertTrue(report.limitation.contains("не подтверждают"))
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
