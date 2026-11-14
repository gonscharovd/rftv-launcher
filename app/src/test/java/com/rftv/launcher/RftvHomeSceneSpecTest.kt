package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RftvHomeSceneSpecTest {
    @Test
    fun sceneCatalogContainsFiveUniqueScenesAndFourTransparentForegrounds() {
        assertEquals(5, RftvHomeSceneSpec.ALL.size)
        assertEquals(
            RftvHomeSceneSpec.ALL.size,
            RftvHomeSceneSpec.ALL.map { it.id }.toSet().size
        )
        assertEquals(4, RftvHomeSceneSpec.ALL.count { it.foregroundAsset != null })
        assertTrue(RftvHomeSceneSpec.ALL.all { it.backgroundAsset.startsWith("scenes/") })
        assertTrue(RftvHomeSceneSpec.ALL.all { it.glassAsset.startsWith("scenes/") })
        assertFalse(RftvHomeSceneSpec.ALL.any { it.id.isBlank() || it.title.isBlank() })
    }

    @Test
    fun donorAssetsArePinnedToAnImmutableUpstreamRevision() {
        assertEquals("463fa6c2a9b7322c4407f1d00db935ef09d20f4f", RftvHomeSceneSpec.SOURCE_REVISION)
        assertTrue(RftvHomeSceneSpec.SOURCE_BASE_URL.endsWith("/${RftvHomeSceneSpec.SOURCE_REVISION}/assets/backgrounds/"))
    }

    @Test
    fun automaticSceneSelectionFollowsTheme() {
        assertEquals("night", RftvHomeSceneSpec.automaticSceneId(true))
        assertEquals("golden", RftvHomeSceneSpec.automaticSceneId(false))
        assertNotNull(RftvHomeSceneSpec.find("fog"))
    }
}
