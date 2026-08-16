package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PlayerSettingsDefaultsTest {

    @Test
    fun defaultMpvConfigPreservesAssPlacementWithOriginalOpaqueBox() {
        assertContains(DefaultMpvConf.lineSequence().toList(), "vo=gpu")
        assertContains(DefaultMpvConf.lineSequence().toList(), "sub-ass-override=force")
        assertContains(DefaultMpvConf.lineSequence().toList(), "sub-border-style=opaque-box")
        assertContains(DefaultMpvConf.lineSequence().toList(), "sub-border-size=0")
        assertContains(DefaultMpvConf.lineSequence().toList(), "demuxer-max-bytes=1024MiB")
        assertContains(DefaultMpvConf.lineSequence().toList(), "demuxer-max-back-bytes=128MiB")
        assertFalse(DefaultMpvConf.lineSequence().any { it == "vo=gpu-next" })
        assertFalse(DefaultMpvConf.lineSequence().any { it == "sub-ass-override=strip" })
        assertFalse(DefaultMpvConf.lineSequence().any { it == "sub-border-style=background-box" })
        assertFalse(DefaultMpvConf.lineSequence().any { it.startsWith("sub-outline-color=") })
    }
}
