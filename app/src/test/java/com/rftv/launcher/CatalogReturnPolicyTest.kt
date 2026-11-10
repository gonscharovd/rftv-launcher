package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogReturnPolicyTest {
    @Test
    fun returnsHomeOnlyForPendingCatalogLaunch() {
        assertTrue(
            CatalogReturnPolicy.shouldReturnHomeAfterAppLaunch(
                launchPending = true,
                page = CatalogReturnPolicy.CATALOG_PAGE,
                activePanelTitle = CatalogReturnPolicy.CATALOG_TITLE
            )
        )
        assertFalse(
            CatalogReturnPolicy.shouldReturnHomeAfterAppLaunch(
                launchPending = false,
                page = CatalogReturnPolicy.CATALOG_PAGE,
                activePanelTitle = CatalogReturnPolicy.CATALOG_TITLE
            )
        )
        assertFalse(
            CatalogReturnPolicy.shouldReturnHomeAfterAppLaunch(
                launchPending = true,
                page = "home",
                activePanelTitle = CatalogReturnPolicy.CATALOG_TITLE
            )
        )
        assertFalse(
            CatalogReturnPolicy.shouldReturnHomeAfterAppLaunch(
                launchPending = true,
                page = CatalogReturnPolicy.CATALOG_PAGE,
                activePanelTitle = "Настройки"
            )
        )
    }

    @Test
    fun allAppsDockTagIsStableAndNonBlank() {
        assertEquals("rftv-all-apps-trigger", CatalogReturnPolicy.ALL_APPS_DOCK_TAG)
        assertTrue(CatalogReturnPolicy.ALL_APPS_DOCK_TAG.isNotBlank())
    }
}
