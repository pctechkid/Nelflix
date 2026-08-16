package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerHardwareDecodingPolicyTest {
    @Test
    fun disablesMediaCodecForReportedXiaomiPad8Models() {
        assertTrue(PlayerHardwareDecodingPolicy.shouldDisableForDevice("Xiaomi", "25097RP43G"))
        assertTrue(PlayerHardwareDecodingPolicy.shouldDisableForDevice("xiaomi", "25097RP43C"))
    }

    @Test
    fun keepsHardwareDecodingForOtherDevices() {
        assertFalse(PlayerHardwareDecodingPolicy.shouldDisableForDevice("Xiaomi", "2410CRP4CG"))
        assertFalse(PlayerHardwareDecodingPolicy.shouldDisableForDevice("Samsung", "25097RP43G"))
        assertFalse(PlayerHardwareDecodingPolicy.shouldDisableForDevice(null, null))
    }
}
