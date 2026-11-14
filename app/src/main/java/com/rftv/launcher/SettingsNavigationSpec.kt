package com.rftv.launcher

/** Shared settings navigation model; prevents section/icon index drift. */
internal object SettingsNavigationSpec {
    val SECTION_GLYPHS = listOf("⌂", "◉", "▣", "◷", "▦", "▤", "ⓘ", "⚙")

    const val RETURN_APP_CATALOG = "settings:return:app-catalog"
    const val RETURN_HIDDEN_APPS = "settings:return:hidden-apps"
    const val RETURN_TV_DEVICE_INFO = "settings:return:tv-device-info"
    const val RETURN_TV_CLEANUP = "settings:return:tv-cleanup"
    const val RETURN_TV_DISABLED = "settings:return:tv-disabled"
    const val RETURN_TV_LOG = "settings:return:tv-log"
    const val RETURN_DIAGNOSTICS = "settings:return:diagnostics"
    const val RETURN_ABOUT = "settings:return:about"

    val SUBPAGE_RETURN_TAGS = listOf(
        RETURN_APP_CATALOG,
        RETURN_HIDDEN_APPS,
        RETURN_TV_DEVICE_INFO,
        RETURN_TV_CLEANUP,
        RETURN_TV_DISABLED,
        RETURN_TV_LOG,
        RETURN_DIAGNOSTICS,
        RETURN_ABOUT
    )

    fun glyphFor(sectionIndex: Int): String = SECTION_GLYPHS.getOrElse(sectionIndex) { "•" }
}
