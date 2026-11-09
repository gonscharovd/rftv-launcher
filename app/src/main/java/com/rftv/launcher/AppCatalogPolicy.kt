package com.rftv.launcher

internal object AppCatalogPolicy {
    /** Seed default favorites only on first initialization, never after a deliberate empty choice. */
    fun shouldSeedDefaultFavorites(favoritesEmpty: Boolean, initialized: Boolean): Boolean =
        favoritesEmpty && !initialized
}
