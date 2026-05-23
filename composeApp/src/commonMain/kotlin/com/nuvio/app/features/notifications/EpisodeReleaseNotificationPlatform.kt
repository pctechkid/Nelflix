package com.nuvio.app.features.notifications

internal expect object EpisodeReleaseNotificationPlatform {
    suspend fun notificationsAuthorized(): Boolean
    suspend fun requestAuthorization(): Boolean
    fun availableTimezoneIds(): List<String>
    fun exactAlarmsAllowed(): Boolean
    fun openExactAlarmSettings(): Boolean
    fun resolveReleaseTriggerEpochMs(rawReleaseValue: String?, timezoneId: String): Long?
    fun formatReleaseTriggerLabel(epochMs: Long, timezoneId: String): String
    suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>)
    suspend fun clearScheduledEpisodeReleaseNotifications()
}
