package com.rftv.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageOperationPolicyTest {
    private val safeCandidate = PackageOperationPolicy.Candidate(
        packageName = "com.vendor.optional.demo",
        isOwnPackage = false,
        isProtected = false,
        exactDeviceProfileMatch = true,
        explicitlyVerifiedOptional = true,
        confidence = PackageOperationPolicy.Confidence.HIGH,
        dependenciesChecked = true,
        hasUnresolvedDependencies = false,
        currentlyEnabled = true,
        originalState = PackageOperationPolicy.OriginalState(
            PackageOperationPolicy.EnabledSetting.DEFAULT,
            applicationEnabled = true
        )
    )

    private val adbAccess = PackageOperationPolicy.Access(
        mode = PackageOperationPolicy.AccessMode.SHIZUKU_ADB,
        packageStateChange = true,
        hidePackage = false,
        verified = true
    )

    @Test
    fun allowsOnlyFullyVerifiedOptionalPackageWithPrivilegedAccess() {
        val result = PackageOperationPolicy.canDisable(safeCandidate, adbAccess)
        assertTrue(result.allowed)
    }

    @Test
    fun deniesWhenAccessIsOnlyOrdinaryApplication() {
        val access = PackageOperationPolicy.Access(
            PackageOperationPolicy.AccessMode.NONE,
            packageStateChange = false,
            hidePackage = false,
            verified = false
        )
        assertFalse(PackageOperationPolicy.canDisable(safeCandidate, access).allowed)
    }

    @Test
    fun deniesCriticalUnknownLowConfidenceAndUnmatchedPackages() {
        val critical = safeCandidate.copy(isProtected = true)
        val unknown = safeCandidate.copy(explicitlyVerifiedOptional = false)
        val lowConfidence = safeCandidate.copy(confidence = PackageOperationPolicy.Confidence.LOW)
        val unmatched = safeCandidate.copy(exactDeviceProfileMatch = false)

        assertFalse(PackageOperationPolicy.canDisable(critical, adbAccess).allowed)
        assertFalse(PackageOperationPolicy.canDisable(unknown, adbAccess).allowed)
        assertFalse(PackageOperationPolicy.canDisable(lowConfidence, adbAccess).allowed)
        assertFalse(PackageOperationPolicy.canDisable(unmatched, adbAccess).allowed)
    }

    @Test
    fun deniesUnresolvedDependenciesAlreadyDisabledAndMissingSnapshot() {
        assertFalse(PackageOperationPolicy.canDisable(
            safeCandidate.copy(hasUnresolvedDependencies = true), adbAccess
        ).allowed)
        assertFalse(PackageOperationPolicy.canDisable(
            safeCandidate.copy(currentlyEnabled = false), adbAccess
        ).allowed)
        assertFalse(PackageOperationPolicy.canDisable(
            safeCandidate.copy(originalState = null), adbAccess
        ).allowed)
    }

    @Test
    fun restoreRequiresRftvOwnershipAndOriginalSnapshot() {
        val original = safeCandidate.originalState
        assertTrue(PackageOperationPolicy.canRestore(
            safeCandidate.packageName, false, false, original, true, adbAccess
        ).allowed)
        assertFalse(PackageOperationPolicy.canRestore(
            safeCandidate.packageName, false, false, original, false, adbAccess
        ).allowed)
        assertFalse(PackageOperationPolicy.canRestore(
            safeCandidate.packageName, false, false, null, true, adbAccess
        ).allowed)
    }

    @Test
    fun manualModeAllowsUserChoiceForUnknownPackagesButKeepsHardProtection() {
        val unknown = safeCandidate.copy(
            exactDeviceProfileMatch = false,
            explicitlyVerifiedOptional = false,
            confidence = PackageOperationPolicy.Confidence.LOW,
            dependenciesChecked = false,
            hasUnresolvedDependencies = true
        )
        val manual = PackageOperationPolicy.canDisable(
            unknown, adbAccess, PackageOperationPolicy.Mode.MANUAL
        )
        assertTrue(manual.allowed)
        assertTrue(manual.requiresExplicitWarning)

        val automatic = PackageOperationPolicy.canDisable(
            unknown, adbAccess, PackageOperationPolicy.Mode.AUTOMATIC
        )
        assertFalse(automatic.allowed)

        val protected = PackageOperationPolicy.canDisable(
            unknown.copy(isProtected = true), adbAccess, PackageOperationPolicy.Mode.MANUAL
        )
        assertFalse(protected.allowed)
    }

    @Test
    fun batchFailureRequiresRollbackOnlyAfterAtLeastOneAppliedChange() {
        assertFalse(PackageOperationPolicy.shouldRollbackBatch(0, true))
        assertFalse(PackageOperationPolicy.shouldRollbackBatch(3, false))
        assertTrue(PackageOperationPolicy.shouldRollbackBatch(3, true))
    }

    @Test
    fun enabledSettingRoundTripsKnownAndroidStates() {
        PackageOperationPolicy.EnabledSetting.entries.forEach {
            assertTrue(PackageOperationPolicy.EnabledSetting.from(it.value) == it)
        }
        assertTrue(PackageOperationPolicy.EnabledSetting.from(99) == null)
    }
}
