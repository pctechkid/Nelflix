package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences

actual object AccountStatisticsStorage {
    private const val preferencesName = "nelflix_account_statistics"
    private const val payloadKey = "account_statistics"
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(userId: String, profileId: Int): String? =
        preferences?.getString(key(userId, profileId), null)

    actual fun savePayload(userId: String, profileId: Int, payload: String) {
        preferences?.edit()?.putString(key(userId, profileId), payload)?.apply()
    }

    private fun key(userId: String, profileId: Int): String =
        "${payloadKey}_${userId.trim()}_$profileId"
}
