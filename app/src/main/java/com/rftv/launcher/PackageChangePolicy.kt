package com.rftv.launcher

/**
 * Accept only package-scoped lifecycle broadcasts that can change the launchable
 * application inventory. The launcher's own package is handled by process restart.
 */
internal object PackageChangePolicy {
    private val relevantActions = setOf(
        "android.intent.action.PACKAGE_ADDED",
        "android.intent.action.PACKAGE_REMOVED",
        "android.intent.action.PACKAGE_CHANGED",
        "android.intent.action.PACKAGE_REPLACED",
        "android.intent.action.PACKAGE_FULLY_REMOVED"
    )

    fun shouldRefresh(
        action: String?,
        changedPackage: String?,
        ownPackage: String,
        isReplacing: Boolean = false
    ): Boolean =
        action in relevantActions &&
            !(action == "android.intent.action.PACKAGE_REMOVED" && isReplacing) &&
            !changedPackage.isNullOrBlank() &&
            changedPackage != ownPackage
}
