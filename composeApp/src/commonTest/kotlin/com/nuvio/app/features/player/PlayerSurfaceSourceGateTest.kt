package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerSurfaceSourceGateTest {
    @Test
    fun sourceWaitsUntilSurfaceIsAttached() {
        val gate = PlayerSurfaceSourceGate<String>()

        assertNull(gate.offer("episode-1"))
        assertEquals("episode-1", gate.onSurfaceAttached())
    }

    @Test
    fun newestPendingSourceWinsBeforeSurfaceAttachment() {
        val gate = PlayerSurfaceSourceGate<String>()

        assertNull(gate.offer("episode-1"))
        assertNull(gate.offer("episode-2"))
        assertEquals("episode-2", gate.onSurfaceAttached())
    }

    @Test
    fun attachedSurfaceLoadsImmediatelyAndDetachRestoresGate() {
        val gate = PlayerSurfaceSourceGate<String>()

        assertNull(gate.onSurfaceAttached())
        assertEquals("episode-1", gate.offer("episode-1"))
        gate.onSurfaceDetached()
        assertNull(gate.offer("episode-2"))
        assertEquals("episode-2", gate.onSurfaceAttached())
    }
}
