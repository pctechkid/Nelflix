package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.watching.domain.WatchingContentRef
import kotlinx.serialization.Serializable

internal const val WatchProgressCompletionPercentThreshold = 90f
internal const val WatchProgressTraktPlaybackNextUpSeedPercentThreshold = 95f
internal const val WatchProgressSourceLocal = "local"
internal const val WatchProgressSourceTraktPlayback = "trakt_playback"
internal const val WatchProgressSourceTraktHistory = "trakt_history"
internal const val WatchProgressSourceTraktShowProgress = "trakt_show_progress"

@Serializable
enum class ContinueWatchingSectionStyle {
    Wide,
    Poster,
}

@Serializable
enum class ContinueWatchingSortMode {
    DEFAULT,
    STREAMING_STYLE,
}

@Serializable
data class WatchProgressEntry(
    val contentType: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val videoId: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastUpdatedEpochMs: Long,
    val providerName: String? = null,
    val providerAddonId: String? = null,
    val lastStreamTitle: String? = null,
    val lastStreamSubtitle: String? = null,
    val pauseDescription: String? = null,
    val lastSourceUrl: String? = null,
    val isCompleted: Boolean = false,
    val progressPercent: Float? = null,
    val source: String = WatchProgressSourceLocal,
    val metadataCheckedAtEpochMs: Long = 0L,
) {
    val normalizedProgressPercent: Float?
        get() = progressPercent?.coerceIn(0f, 100f)

    val isEffectivelyCompleted: Boolean
        get() = isCompleted ||
            (normalizedProgressPercent?.let { it >= WatchProgressCompletionPercentThreshold } == true) ||
            (durationMs > 0L && isWatchProgressComplete(lastPositionMs, durationMs, false))

    val progressFraction: Float
        get() {
            normalizedProgressPercent?.let { explicitPercent ->
                return (explicitPercent / 100f).coerceIn(0f, 1f)
            }
            return if (durationMs > 0L) {
                (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    val isEpisode: Boolean
        get() = seasonNumber != null && episodeNumber != null

    val isResumable: Boolean
        get() = !isEffectivelyCompleted

    fun normalizedCompletion(): WatchProgressEntry {
        val completed = isEffectivelyCompleted
        val normalizedPositionMs = when {
            completed && durationMs > 0L -> durationMs
            else -> lastPositionMs.coerceAtLeast(0L)
        }
        val normalizedPercent = when {
            normalizedProgressPercent != null -> normalizedProgressPercent
            completed && durationMs <= 0L -> 100f
            else -> null
        }

        return if (
            completed == isCompleted &&
            normalizedPositionMs == lastPositionMs &&
            normalizedPercent == progressPercent
        ) {
            this
        } else {
            copy(
                lastPositionMs = normalizedPositionMs,
                isCompleted = completed,
                progressPercent = normalizedPercent,
            )
        }
    }

    fun resolveResumePosition(actualDurationMs: Long): Long {
        if (actualDurationMs <= 0L) return lastPositionMs.coerceAtLeast(0L)
        if (durationMs > 0L && lastPositionMs > 0L) {
            return lastPositionMs.coerceIn(0L, actualDurationMs)
        }
        normalizedProgressPercent?.let { percent ->
            val fraction = (percent / 100f).coerceIn(0f, 1f)
            return (actualDurationMs * fraction).toLong()
        }
        return lastPositionMs.coerceAtLeast(0L)
    }
}

data class WatchProgressUiState(
    val entries: List<WatchProgressEntry> = emptyList(),
) {
    val byVideoId: Map<String, WatchProgressEntry>
        get() = entries.associateBy { it.videoId }

    val continueWatchingEntries: List<WatchProgressEntry>
        get() = entries.continueWatchingEntries(limit = ContinueWatchingLimit)
}

data class WatchProgressPlaybackSession(
    val contentType: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val videoId: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val providerName: String? = null,
    val providerAddonId: String? = null,
    val lastStreamTitle: String? = null,
    val lastStreamSubtitle: String? = null,
    val pauseDescription: String? = null,
    val lastSourceUrl: String? = null,
)

internal fun WatchProgressPlaybackSession.withPreservedCheckedMetadata(
    previousEntry: WatchProgressEntry?,
): WatchProgressPlaybackSession {
    if (previousEntry == null || previousEntry.metadataCheckedAtEpochMs <= 0L) return this

    val previousEpisodeTitle = previousEntry.episodeTitle?.trim()?.takeIf(String::isNotBlank)
    val sessionEpisodeTitle = episodeTitle?.trim()?.takeIf(String::isNotBlank)
    val mergedEpisodeTitle = when {
        previousEpisodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber) &&
            !sessionEpisodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber) -> sessionEpisodeTitle
        previousEpisodeTitle != null -> previousEpisodeTitle
        else -> sessionEpisodeTitle
    }

    return copy(
        title = previousEntry.title.trim().takeIf(String::isNotBlank) ?: title,
        logo = previousEntry.logo.preferredDisplayValue(logo),
        poster = previousEntry.poster.preferredDisplayValue(poster),
        background = previousEntry.background.preferredDisplayValue(background),
        episodeTitle = mergedEpisodeTitle,
        episodeThumbnail = previousEntry.episodeThumbnail.preferredDisplayValue(episodeThumbnail),
        pauseDescription = previousEntry.pauseDescription.preferredDisplayValue(pauseDescription),
    )
}

private fun String?.preferredDisplayValue(fallback: String?): String? =
    this?.trim()?.takeIf(String::isNotBlank)
        ?: fallback?.trim()?.takeIf(String::isNotBlank)

data class ContinueWatchingItem(
    val parentMetaId: String,
    val parentMetaType: String,
    val videoId: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val metadataCheckedAtEpochMs: Long = 0L,
    val released: String? = null,
    val releaseEpochMs: Long? = null,
    val isNextUp: Boolean = false,
    val nextUpSeedSeasonNumber: Int? = null,
    val nextUpSeedEpisodeNumber: Int? = null,
    val nextUpSeedLastUpdatedEpochMs: Long? = null,
    val resumePositionMs: Long,
    val resumeProgressFraction: Float? = null,
    val durationMs: Long,
    val progressFraction: Float,
    val isReleaseAlert: Boolean = false,
    val isNewSeasonRelease: Boolean = false,
)

internal fun ContinueWatchingItem.canonicalIdentity(): String =
    continueWatchingCanonicalIdentity(
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        videoId = videoId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

internal fun continueWatchingCanonicalIdentity(
    parentMetaId: String,
    parentMetaType: String,
    videoId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String {
    val normalizedType = when (parentMetaType.trim().lowercase()) {
        "series", "show", "tv", "tvshow" -> "series"
        "movie", "film" -> "movie"
        else -> parentMetaType.trim().lowercase().ifBlank { "unknown" }
    }
    val normalizedVideoId = videoId.trim().lowercase()
    if (normalizedType != "series" || seasonNumber == null || episodeNumber == null) {
        return "$normalizedType|${normalizedVideoId.ifBlank { parentMetaId.trim().lowercase() }}"
    }

    val identityInput = "$parentMetaId|$videoId"
    val canonicalSeriesId = ContinueWatchingImdbIdRegex.find(identityInput)
        ?.value
        ?.lowercase()
        ?.let { "imdb:$it" }
        ?: ContinueWatchingTmdbIdRegex.find(identityInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { "tmdb:$it" }
        ?: parentMetaId.trim().lowercase().ifBlank { normalizedVideoId }
    return "$normalizedType|$canonicalSeriesId|s${seasonNumber}:e${episodeNumber}"
}

internal fun ContinueWatchingItem.metadataQualityScore(): Int {
    var score = 0
    if (title.isNotBlank()) score += 1
    if (!episodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber)) score += 4
    if (!episodeThumbnail.isNullOrBlank()) score += 3
    if (!pauseDescription.isNullOrBlank()) score += 2
    if (!logo.isNullOrBlank()) score += 1
    if (!poster.isNullOrBlank()) score += 1
    if (!background.isNullOrBlank()) score += 1
    return score
}

private val ContinueWatchingImdbIdRegex = Regex("tt[0-9]+", RegexOption.IGNORE_CASE)
private val ContinueWatchingTmdbIdRegex = Regex(
    "(?:^|[|:/])tmdb(?::|/)([0-9]+)",
    RegexOption.IGNORE_CASE,
)

data class ContinueWatchingPreferencesUiState(
    val isVisible: Boolean = true,
    val style: ContinueWatchingSectionStyle = ContinueWatchingSectionStyle.Wide,
    val upNextFromFurthestEpisode: Boolean = true,
    val useEpisodeThumbnails: Boolean = true,
    val useClearlogo: Boolean = true,
    val showUnairedNextUp: Boolean = false,
    val blurNextUp: Boolean = false,
    val dismissedNextUpKeys: Set<String> = emptySet(),
    val showResumePromptOnLaunch: Boolean = true,
    val sortMode: ContinueWatchingSortMode = ContinueWatchingSortMode.STREAMING_STYLE,
)

internal fun nextUpDismissKey(
    contentId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String = buildString {
    append(contentId.trim())
    append("|")
    append(seasonNumber ?: -1)
    append("|")
    append(episodeNumber ?: -1)
}

internal fun WatchProgressEntry.toContinueWatchingItem(): ContinueWatchingItem {
    val normalizedEntry = normalizedCompletion()
    val explicitResumeProgressFraction = normalizedEntry.normalizedProgressPercent
        ?.takeIf { durationMs <= 0L && it > 0f }
        ?.let { explicitPercent -> (explicitPercent / 100f).coerceIn(0f, 1f) }

    return ContinueWatchingItem(
        parentMetaId = normalizedEntry.parentMetaId,
        parentMetaType = normalizedEntry.parentMetaType,
        videoId = normalizedEntry.videoId,
        title = normalizedEntry.title,
        subtitle = buildContinueWatchingEpisodeSubtitle(
            seasonNumber = normalizedEntry.seasonNumber,
            episodeNumber = normalizedEntry.episodeNumber,
            episodeTitle = normalizedEntry.episodeTitle,
        ),
        imageUrl = normalizedEntry.episodeThumbnail ?: normalizedEntry.background ?: normalizedEntry.poster,
        logo = normalizedEntry.logo,
        poster = normalizedEntry.poster,
        background = normalizedEntry.background,
        seasonNumber = normalizedEntry.seasonNumber,
        episodeNumber = normalizedEntry.episodeNumber,
        episodeTitle = normalizedEntry.episodeTitle,
        episodeThumbnail = normalizedEntry.episodeThumbnail,
        pauseDescription = normalizedEntry.pauseDescription,
        metadataCheckedAtEpochMs = normalizedEntry.metadataCheckedAtEpochMs,
        released = null,
        releaseEpochMs = null,
        isNextUp = false,
        nextUpSeedSeasonNumber = null,
        nextUpSeedEpisodeNumber = null,
        nextUpSeedLastUpdatedEpochMs = null,
        resumePositionMs = if (explicitResumeProgressFraction != null) 0L else normalizedEntry.lastPositionMs,
        resumeProgressFraction = explicitResumeProgressFraction,
        durationMs = normalizedEntry.durationMs,
        progressFraction = normalizedEntry.progressFraction,
        isReleaseAlert = false,
        isNewSeasonRelease = false,
    )
}

internal fun WatchProgressEntry.toUpNextContinueWatchingItem(
    nextEpisode: MetaVideo,
    releaseEpochMs: Long?,
): ContinueWatchingItem {
    val alertState = calculateReleaseAlertState(
        seedLastUpdatedEpochMs = lastUpdatedEpochMs,
        seedSeasonNumber = seasonNumber,
        nextSeasonNumber = nextEpisode.season,
        releaseEpochMs = releaseEpochMs,
    )
    return ContinueWatchingItem(
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        videoId = nextEpisode.id.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
            parentMetaId = parentMetaId,
            seasonNumber = nextEpisode.season,
            episodeNumber = nextEpisode.episode,
            fallbackVideoId = nextEpisode.id,
        ),
        title = title,
        subtitle = buildContinueWatchingEpisodeSubtitle(
            seasonNumber = nextEpisode.season,
            episodeNumber = nextEpisode.episode,
            episodeTitle = nextEpisode.title,
        ),
        imageUrl = nextEpisode.thumbnail ?: episodeThumbnail ?: background ?: poster,
        logo = logo,
        poster = poster,
        background = background,
        seasonNumber = nextEpisode.season,
        episodeNumber = nextEpisode.episode,
        episodeTitle = nextEpisode.title,
        episodeThumbnail = nextEpisode.thumbnail,
        pauseDescription = nextEpisode.overview,
        released = nextEpisode.released,
        releaseEpochMs = releaseEpochMs,
        isNextUp = true,
        nextUpSeedSeasonNumber = seasonNumber,
        nextUpSeedEpisodeNumber = episodeNumber,
        nextUpSeedLastUpdatedEpochMs = lastUpdatedEpochMs,
        resumePositionMs = 0L,
        resumeProgressFraction = null,
        durationMs = 0L,
        progressFraction = 0f,
        isReleaseAlert = alertState.isReleaseAlert,
        isNewSeasonRelease = alertState.isNewSeasonRelease,
    )
}

internal fun ContinueWatchingItem.withReleaseAlertState(
    nowEpochMs: Long,
): ContinueWatchingItem {
    if (!isNextUp) return this
    val seedTimestamp = nextUpSeedLastUpdatedEpochMs
        ?: return copy(isReleaseAlert = false, isNewSeasonRelease = false)
    val alertState = calculateReleaseAlertState(
        seedLastUpdatedEpochMs = seedTimestamp,
        seedSeasonNumber = nextUpSeedSeasonNumber,
        nextSeasonNumber = seasonNumber,
        releaseEpochMs = releaseEpochMs,
        nowEpochMs = nowEpochMs,
    )
    return copy(
        isReleaseAlert = alertState.isReleaseAlert,
        isNewSeasonRelease = alertState.isNewSeasonRelease,
    )
}

internal fun buildContinueWatchingEpisodeSubtitle(
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
): String {
    val episodeCode = continueWatchingEpisodeCode(seasonNumber, episodeNumber)
    val title = episodeTitle.orEmpty()
    return listOfNotNull(episodeCode, title.takeIf { it.isNotBlank() }).joinToString(" • ")
}

internal fun continueWatchingEpisodeCode(
    seasonNumber: Int?,
    episodeNumber: Int?,
): String? = when {
    seasonNumber != null && episodeNumber != null -> "S${seasonNumber}:E${episodeNumber}"
    episodeNumber != null -> "E${episodeNumber}"
    else -> null
}

internal const val IncompleteContinueWatchingMetadataTtlMs = 30L * 60L * 1000L
internal const val CompleteContinueWatchingMetadataTtlMs = 6L * 60L * 60L * 1000L

internal fun WatchProgressEntry.needsContinueWatchingMetadataRefresh(
    nowEpochMs: Long,
    force: Boolean = false,
): Boolean {
    if (isEffectivelyCompleted) return false
    if (force || metadataCheckedAtEpochMs <= 0L) return true
    val ttlMs = if (hasIncompleteContinueWatchingMetadata()) {
        IncompleteContinueWatchingMetadataTtlMs
    } else {
        CompleteContinueWatchingMetadataTtlMs
    }
    return nowEpochMs - metadataCheckedAtEpochMs >= ttlMs
}

internal fun WatchProgressEntry.withRefreshedContinueWatchingMetadata(
    meta: MetaDetails?,
    checkedAtEpochMs: Long,
): WatchProgressEntry {
    val episode = meta?.videos?.firstOrNull { video ->
        seasonNumber != null &&
            episodeNumber != null &&
            video.season == seasonNumber &&
            video.episode == episodeNumber
    }
    return copy(
        title = preferFreshText(title, meta?.name) ?: title,
        logo = preferFreshText(logo, meta?.logo),
        poster = preferFreshText(poster, meta?.poster),
        background = preferFreshText(background, meta?.background),
        episodeTitle = preferFreshEpisodeTitle(
            current = episodeTitle,
            incoming = episode?.title,
            episodeNumber = episodeNumber,
        ),
        episodeThumbnail = preferFreshText(episodeThumbnail, episode?.thumbnail),
        pauseDescription = preferFreshText(
            current = pauseDescription,
            incoming = episode?.overview ?: meta?.description,
        ),
        metadataCheckedAtEpochMs = checkedAtEpochMs.coerceAtLeast(metadataCheckedAtEpochMs),
    )
}

private fun WatchProgressEntry.hasIncompleteContinueWatchingMetadata(): Boolean {
    if (poster.isNullOrBlank() || background.isNullOrBlank()) return true
    if (!isEpisode) return false
    return episodeThumbnail.isNullOrBlank() ||
        pauseDescription.isNullOrBlank() ||
        episodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber)
}

private fun preferFreshText(current: String?, incoming: String?): String? =
    incoming?.trim()?.takeIf(String::isNotBlank)
        ?: current?.trim()?.takeIf(String::isNotBlank)

private fun preferFreshEpisodeTitle(
    current: String?,
    incoming: String?,
    episodeNumber: Int?,
): String? {
    val currentValue = current?.trim()?.takeIf(String::isNotBlank)
    val incomingValue = incoming?.trim()?.takeIf(String::isNotBlank) ?: return currentValue
    if (
        incomingValue.isGenericContinueWatchingEpisodeTitle(episodeNumber) &&
        currentValue != null &&
        !currentValue.isGenericContinueWatchingEpisodeTitle(episodeNumber)
    ) {
        return currentValue
    }
    return incomingValue
}

internal fun String?.isGenericContinueWatchingEpisodeTitle(episodeNumber: Int?): Boolean {
    val value = this?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return true
    if (value == "episode" || value == "tba" || value == "untitled") return true
    val number = episodeNumber ?: return false
    return value.matches(Regex("""episode\s*0*$number""")) ||
        value.matches(Regex("""ep\.?\s*0*$number""")) ||
        value.matches(Regex("""e0*$number"""))
}

fun buildPlaybackVideoId(
    parentMetaId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    fallbackVideoId: String? = null,
): String = com.nuvio.app.features.watching.domain.buildPlaybackVideoId(
    content = WatchingContentRef(type = "", id = parentMetaId),
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    fallbackVideoId = fallbackVideoId,
)
