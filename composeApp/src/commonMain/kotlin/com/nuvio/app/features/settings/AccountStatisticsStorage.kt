package com.nuvio.app.features.settings

internal expect object AccountStatisticsStorage {
    fun loadPayload(userId: String, profileId: Int): String?
    fun savePayload(userId: String, profileId: Int, payload: String)
}
