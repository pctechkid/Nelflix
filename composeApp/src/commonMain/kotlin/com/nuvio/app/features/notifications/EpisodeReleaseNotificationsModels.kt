package com.nuvio.app.features.notifications

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_episode_code_episode_only
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.notifications_episode_release_body_code
import nuvio.composeapp.generated.resources.notifications_episode_release_body_code_title
import nuvio.composeapp.generated.resources.notifications_episode_release_body_generic
import nuvio.composeapp.generated.resources.notifications_episode_release_body_title
import org.jetbrains.compose.resources.getString
import kotlin.math.abs

data class EpisodeReleaseNotificationsUiState(
    val isEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val scheduledCount: Int = 0,
    val timezoneId: String = DefaultEpisodeReleaseTimezoneId,
    val expectedAlerts: List<EpisodeReleaseAlertPreview> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

data class EpisodeReleaseAlertPreview(
    val requestId: String,
    val title: String,
    val body: String,
    val triggerTimeLabel: String,
    val imageUrl: String? = null,
    val deepLinkUrl: String? = null,
)

@Serializable
internal data class StoredEpisodeReleaseNotificationsPayload(
    val enabled: Boolean = false,
    val followedShows: List<TrackedFollowedShow> = emptyList(),
    val timezoneId: String = DefaultEpisodeReleaseTimezoneId,
)

@Serializable
internal data class TrackedFollowedShow(
    val contentId: String,
    val contentType: String,
    val followedOnIsoDate: String,
)

internal data class EpisodeReleaseNotificationRequest(
    val requestId: String,
    val notificationTitle: String,
    val notificationBody: String,
    val releaseDateIso: String,
    val triggerAtEpochMs: Long? = null,
    val triggerTimeLabel: String? = null,
    val deepLinkUrl: String,
    val timezoneId: String = "",
    val backdropUrl: String? = null,
)

internal const val EpisodeReleaseNotificationHour = 9
internal const val EpisodeReleaseNotificationMinute = 0
internal const val EpisodeReleaseNotificationDelayHours = 13L
internal const val EpisodeReleaseNotificationScheduleGraceMs = 15L * 60L * 1000L
internal const val DefaultEpisodeReleaseTimezoneId = "UTC"
internal const val MinReasonableSavedAtEpochMs = 946684800000L

internal fun buildTrackedShowKey(
    type: String,
    id: String,
): String = "${normalizeSeriesType(type)}:${id.trim()}"

internal fun normalizeSeriesType(type: String): String = when (type.trim().lowercase()) {
    "tv", "show", "series", "tvshow" -> "series"
    "film", "movie", "movies" -> "movie"
    else -> type.trim().lowercase()
}

internal fun isSeriesLibraryType(type: String): Boolean = normalizeSeriesType(type) == "series"
internal fun isMovieLibraryType(type: String): Boolean = normalizeSeriesType(type) == "movie"
internal fun isReleaseAlertLibraryType(type: String): Boolean =
    isSeriesLibraryType(type) || isMovieLibraryType(type)

internal fun releaseDateIso(rawValue: String?): String? {
    val value = rawValue
        ?.substringBefore('T')
        ?.trim()
        .orEmpty()
    return value.takeIf { it.length == 10 }
}

internal fun buildEpisodeReleaseNotificationId(
    profileId: Int,
    contentType: String,
    contentId: String,
    episodeId: String,
    releaseDateIso: String,
): String {
    val contentHash = abs(buildTrackedShowKey(contentType, contentId).hashCode())
    val episodeHash = abs(episodeId.trim().ifBlank { releaseDateIso }.hashCode())
    return "episode-release-$profileId-$contentHash-$episodeHash-$releaseDateIso"
}

internal fun buildEpisodeReleaseNotificationBody(
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
): String = runBlocking {
    val code = when {
        seasonNumber != null && episodeNumber != null ->
            getString(Res.string.compose_player_episode_code_full, seasonNumber, episodeNumber)
        episodeNumber != null ->
            getString(Res.string.compose_player_episode_code_episode_only, episodeNumber)
        else -> ""
    }
    val title = episodeTitle?.trim().takeUnless { it.isNullOrBlank() }

    when {
        code.isNotBlank() && title != null ->
            getString(Res.string.notifications_episode_release_body_code_title, code, title)
        code.isNotBlank() ->
            getString(Res.string.notifications_episode_release_body_code, code)
        title != null ->
            getString(Res.string.notifications_episode_release_body_title, title)
        else ->
            getString(Res.string.notifications_episode_release_body_generic)
    }
}
