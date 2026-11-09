package com.rftv.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvManagerProfilesTest {
    @Test
    fun sberProfilesDoNotMatchRftvTargetOnAndroidNineMt9632() {
        val profiles = TvManagerProfiles.all
        assertTrue(profiles.isNotEmpty())
        assertTrue(profiles.none {
            it.matchesFingerprint(
                board = "MT9632",
                hardware = "MT9632",
                device = "SDX-50U4123B",
                product = "SDX-50U4123B",
                sdk = 28
            )
        })
    }

    @Test
    fun donorProfileEntriesAreNotAutomaticallyTrustedForDebloating() {
        assertTrue(TvManagerProfiles.all.flatMap { it.packages }.none {
            it.verifiedOptional && it.confidenceHigh && it.dependenciesVerified
        })
    }

    @Test
    fun profileRequiresExactHardwareManufacturerModelFirmwareAbiAndSdk() {
        val reference = TvManagerProfiles.all.first { it.id == "sber-pb766" }
        assertFalse(reference.matchesFingerprint(
            board = "TPD.MT9216.PB766",
            hardware = "mt9256",
            device = "MD_CVTE_OVERSEA_MT9256_11_0_1GB_STARKOS",
            product = "pb766",
            sdk = 30,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/123:user/release-keys",
            supportedAbis = listOf("armeabi-v7a")
        ))

        val profile = reference.copy(
            hardwareTokens = listOf("mt9256"),
            productTokens = listOf("pb766"),
            manufacturerTokens = listOf("Sber"),
            modelTokens = listOf("Sber TV 32"),
            buildFingerprintTokens = listOf("vendor/sber/pb766:11/BUILD/123:user/release-keys"),
            abiTokens = listOf("armeabi-v7a")
        )
        val exact = mapOf(
            "board" to "TPD.MT9216.PB766",
            "hardware" to "mt9256",
            "device" to "MD_CVTE_OVERSEA_MT9256_11_0_1GB_STARKOS",
            "product" to "pb766",
            "sdk" to "30"
        )
        assertTrue(profile.matchesFingerprint(
            board = exact.getValue("board"),
            hardware = exact.getValue("hardware"),
            device = exact.getValue("device"),
            product = exact.getValue("product"),
            sdk = 30,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/123:user/release-keys",
            supportedAbis = listOf("armeabi-v7a")
        ))
        assertFalse(profile.matchesFingerprint(
            board = exact.getValue("board"),
            hardware = exact.getValue("hardware"),
            device = exact.getValue("device"),
            product = exact.getValue("product"),
            sdk = 30,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/DIFFERENT:user/release-keys",
            supportedAbis = listOf("armeabi-v7a")
        ))
        assertFalse(profile.matchesFingerprint(
            board = exact.getValue("board"),
            hardware = exact.getValue("hardware"),
            device = exact.getValue("device"),
            product = exact.getValue("product"),
            sdk = 30,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/123:user/release-keys",
            supportedAbis = listOf("arm64-v8a")
        ))
        assertFalse(profile.matchesFingerprint(
            board = exact.getValue("board"),
            hardware = exact.getValue("hardware"),
            device = exact.getValue("device"),
            product = "different-product",
            sdk = 30,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/123:user/release-keys",
            supportedAbis = listOf("armeabi-v7a")
        ))
        assertFalse(profile.matchesFingerprint(
            board = exact.getValue("board"),
            hardware = exact.getValue("hardware"),
            device = exact.getValue("device"),
            product = exact.getValue("product"),
            sdk = 28,
            manufacturer = "Sber",
            model = "Sber TV 32",
            buildFingerprint = "vendor/sber/pb766:11/BUILD/123:user/release-keys",
            supportedAbis = listOf("armeabi-v7a")
        ))
    }
}
