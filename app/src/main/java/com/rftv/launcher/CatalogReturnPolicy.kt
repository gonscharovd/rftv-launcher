package com.rftv.launcher

/**
 * Pure policy for restoring the launcher after leaving the app catalog.
 * Kept separate from Activity lifecycle code so the return conditions are regression-tested.
 */
internal object CatalogReturnPolicy {
    const val ALL_APPS_DOCK_TAG = "rftv-all-apps-trigger"
    const val CATALOG_PAGE = "panel"
    const val CATALOG_TITLE = "Каталог приложений"

    fun shouldReturnHomeAfterAppLaunch(
        launchPending: Boolean,
        page: String,
        activePanelTitle: String
    ): Boolean = launchPending && page == CATALOG_PAGE && activePanelTitle == CATALOG_TITLE
}
