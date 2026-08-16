package com.nuvio.app.features.player

internal object PlayerHardwareDecodingPolicy {
    fun shouldDisableForDevice(manufacturer: String?, model: String?): Boolean {
        if (!manufacturer.equals("xiaomi", ignoreCase = true)) return false
        return model?.trim()?.startsWith("25097RP43", ignoreCase = true) == true
    }
}
