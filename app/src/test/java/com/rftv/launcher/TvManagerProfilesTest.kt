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
    fun profileRequiresExactBoardDeviceAndSdkTogether() {
        val profile = TvManagerProfiles.all.first { it.id == "sber-pb766" }
        assertTrue(profile.matchesFingerprint(
            board = "TPD.MT9216.PB766",
            hardware = "mt9256",
            device = "MD_CVTE_OVERSEA_MT9256_11_0_1GB_STARKOS",
            product = "pb766",
            sdk = 30
        ))
        assertFalse(profile.matchesFingerprint(
            board = "TPD.MT9216.PB766",
            hardware = "mt9256",
            device = "MD_CVTE_OVERSEA_MT9256_11_0_1GB_STARKOS",
            product = "pb766",
            sdk = 28
        ))
        assertFalse(profile.matchesFingerprint(
            board = "MT9632",
            hardware = "MT9632",
            device = "SDX-50U4123B",
            product = "SDX-50U4123B",
            sdk = 30
        ))
    }
}
