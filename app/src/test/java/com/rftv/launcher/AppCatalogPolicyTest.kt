package com.rftv.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogPolicyTest {
    @Test
    fun seedsDefaultsOnlyForAnUninitializedEmptyFavoritesList() {
        assertTrue(AppCatalogPolicy.shouldSeedDefaultFavorites(favoritesEmpty = true, initialized = false))
        assertFalse(AppCatalogPolicy.shouldSeedDefaultFavorites(favoritesEmpty = true, initialized = true))
        assertFalse(AppCatalogPolicy.shouldSeedDefaultFavorites(favoritesEmpty = false, initialized = false))
        assertFalse(AppCatalogPolicy.shouldSeedDefaultFavorites(favoritesEmpty = false, initialized = true))
    }
}
