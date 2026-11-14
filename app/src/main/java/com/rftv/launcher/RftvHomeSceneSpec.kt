package com.rftv.launcher

/**
 * Five layered photographic scenes adapted from the tvOS Launcher scene asset collection.
 * Background and glass textures are JPEG; foreground cut-outs retain their WebP alpha channel.
 */
internal data class SceneAsset(
    val id: String,
    val title: String,
    val backgroundFile: String,
    val foregroundFile: String?,
    val glassFile: String,
    val darkClock: Boolean
) {
    val backgroundAsset: String get() = "scenes/$id-bg.jpg"
    val foregroundAsset: String? get() = foregroundFile?.let { "scenes/$id-fg.webp" }
    val glassAsset: String get() = "scenes/$id-glass.jpg"
}

internal object RftvHomeSceneSpec {
    const val SOURCE_REVISION = "463fa6c2a9b7322c4407f1d00db935ef09d20f4f"
    const val SOURCE_BASE_URL =
        "https://raw.githubusercontent.com/techgenius1/tvOS-Launcher/$SOURCE_REVISION/assets/backgrounds/"

    val ALL = listOf(
        SceneAsset("night", "Ночь", "scene-night.jpg", "scene-night-fg.webp", "scene-night-glass.jpg", false),
        SceneAsset("golden", "Золотой час", "scene-golden.jpg", "scene-golden-fg.webp", "scene-golden-glass.jpg", false),
        SceneAsset("fog", "Туман", "scene-fog.jpg", "scene-fog-fg.webp", "scene-fog-glass.jpg", true),
        SceneAsset("dawn", "Рассвет", "scene-dawn.jpg", "scene-dawn-fg.webp", "scene-dawn-glass.jpg", false),
        SceneAsset("water", "Водная гладь", "scene-water.jpg", null, "scene-water-glass.jpg", false)
    )

    fun find(id: String): SceneAsset? = ALL.firstOrNull { it.id == id }

    fun automaticSceneId(isDark: Boolean): String = if (isDark) "night" else "golden"
}
