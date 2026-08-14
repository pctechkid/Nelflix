package com.nuvio.app.features.settings

import platform.Foundation.NSUserDefaults

actual object AccountStatisticsStorage {
    private const val payloadKey = "account_statistics"

    actual fun loadPayload(userId: String, profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(key(userId, profileId))

    actual fun savePayload(userId: String, profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = key(userId, profileId))
    }

    private fun key(userId: String, profileId: Int): String =
        "${payloadKey}_${userId.trim()}_$profileId"
}
