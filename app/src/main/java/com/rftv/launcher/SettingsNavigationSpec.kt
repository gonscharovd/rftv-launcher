package com.rftv.launcher

/** Shared settings navigation model; prevents section/icon index drift. */
internal object SettingsNavigationSpec {
    val SECTION_GLYPHS = listOf("⌂", "◉", "▣", "◷", "▦", "▤", "ⓘ", "⚙")

    fun glyphFor(sectionIndex: Int): String = SECTION_GLYPHS.getOrElse(sectionIndex) { "•" }
}
