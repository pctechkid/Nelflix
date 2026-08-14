package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.trakt.WatchProgressSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedNextUpItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val released: String? = null,
    /** Identifies whether releaseEpochMs already includes the current business delay. */
    val releaseTimingRuleVersion: Int = 0,
    val releaseEpochMs: Long? = null,
    val hasAired: Boolean = true,
    val lastWatched: Long,
    val sortTimestamp: Long,
    val seedSeason: Int? = null,
    val seedEpisode: Int? = null,
    val seedLastUpdatedEpochMs: Long? = null,
    val isReleaseAlert: Boolean = false,
    val isNewSeasonRelease: Boolean = false,
    val metadataCheckedAtEpochMs: Long = 0L,
)

@Serializable
data class CachedInProgressItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val progressPercent: Float? = null,
    val metadataCheckedAtEpochMs: Long = 0L,
)

@Serializable
private data class CachedEnrichmentPayload(
    val nextUp: List<CachedNextUpItem> = emptyList(),
    val inProgress: List<CachedInProgressItem> = emptyList(),
    val savedAtEpochMs: Long = 0L,
)

internal data class ContinueWatchingEnrichmentSnapshot(
    val profileId: Int = -1,
    val source: WatchProgressSource? = null,
    val generation: Long = 0L,
    val nextUp: List<CachedNextUpItem> = emptyList(),
    val inProgress: List<CachedInProgressItem> = emptyList(),
    val savedAtEpochMs: Long = 0L,
)

internal object ContinueWatchingEnrichmentCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val storageKey = "cw_enrichment_cache"
    private val _snapshots = MutableStateFlow(ContinueWatchingEnrichmentSnapshot())
    val snapshots: StateFlow<ContinueWatchingEnrichmentSnapshot> = _snapshots.asStateFlow()
    private val _generation = MutableStateFlow(0L)
    val generation: StateFlow<Long> = _generation.asStateFlow()

    fun loadProfile(profileId: Int, source: WatchProgressSource) {
        _snapshots.value = loadPayload(profileId, source).toSnapshot(
            profileId = profileId,
            source = source,
            generation = _generation.value,
        )
    }

    fun saveSnapshots(
        profileId: Int,
        source: WatchProgressSource,
        generation: Long,
        nextUp: List<CachedNextUpItem>,
        inProgress: List<CachedInProgressItem>,
        savedAtEpochMs: Long,
    ): Boolean {
        if (!isCurrent(generation)) return false
        val payload = CachedEnrichmentPayload(
            nextUp = normalizeNextUpSnapshotForCache(nextUp),
            inProgress = normalizeInProgressSnapshotForCache(inProgress),
            savedAtEpochMs = savedAtEpochMs,
        )
        return savePayload(profileId, source, generation, payload)
    }

    fun saveNextUpSnapshot(
        profileId: Int,
        source: WatchProgressSource,
        generation: Long,
        nextUp: List<CachedNextUpItem>,
        savedAtEpochMs: Long,
    ): Boolean {
        val current = payloadForProfile(profileId, source)
        return savePayload(
            profileId = profileId,
            source = source,
            generation = generation,
            payload = current.copy(
                nextUp = normalizeNextUpSnapshotForCache(nextUp),
                savedAtEpochMs = maxOf(current.savedAtEpochMs, savedAtEpochMs),
            ),
        )
    }

    fun saveInProgressSnapshot(
        profileId: Int,
        source: WatchProgressSource,
        generation: Long,
        inProgress: List<CachedInProgressItem>,
        savedAtEpochMs: Long,
    ): Boolean {
        val current = payloadForProfile(profileId, source)
        return savePayload(
            profileId = profileId,
            source = source,
            generation = generation,
            payload = current.copy(
                inProgress = normalizeInProgressSnapshotForCache(inProgress),
                savedAtEpochMs = maxOf(current.savedAtEpochMs, savedAtEpochMs),
            ),
        )
    }

    fun invalidate(profileId: Int, source: WatchProgressSource) {
        advanceGeneration()
        ContinueWatchingEnrichmentStorage.removePayload(storageKey(profileId, source))
        if (source == WatchProgressSource.NUVIO_SYNC) {
            ContinueWatchingEnrichmentStorage.removePayload(legacyStorageKey(profileId))
        }
        val current = _snapshots.value
        if (current.profileId == profileId && current.source == source) {
            _snapshots.value = ContinueWatchingEnrichmentSnapshot(
                profileId = profileId,
                source = source,
                generation = _generation.value,
            )
        }
    }

    fun invalidateContent(
        profileId: Int,
        source: WatchProgressSource,
        contentIds: Collection<String>,
    ) {
        val normalizedContentIds = contentIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (normalizedContentIds.isEmpty()) return

        val current = payloadForProfile(profileId, source)
        val retained = current.copy(
            nextUp = current.nextUp.filterNot { item -> item.contentId in normalizedContentIds },
            inProgress = current.inProgress.filterNot { item -> item.contentId in normalizedContentIds },
        )
        advanceGeneration()
        savePayload(
            profileId = profileId,
            source = source,
            generation = _generation.value,
            payload = retained,
        )
    }

    fun upsertNextUp(
        profileId: Int,
        source: WatchProgressSource,
        item: CachedNextUpItem,
        savedAtEpochMs: Long,
    ): Boolean {
        val current = payloadForProfile(profileId, source)
        val updated = current.copy(
            nextUp = normalizeNextUpSnapshotForCache(
                listOf(item) + current.nextUp.filterNot { cached ->
                    cached.contentId == item.contentId
                },
            ),
            inProgress = current.inProgress.filterNot { cached ->
                cached.contentId == item.contentId
            },
            savedAtEpochMs = maxOf(current.savedAtEpochMs, savedAtEpochMs),
        )
        advanceGeneration()
        return savePayload(
            profileId = profileId,
            source = source,
            generation = _generation.value,
            payload = updated,
        )
    }

    fun clearAll(profileId: Int) {
        advanceGeneration()
        WatchProgressSource.entries.forEach { source ->
            ContinueWatchingEnrichmentStorage.removePayload(storageKey(profileId, source))
        }
        ContinueWatchingEnrichmentStorage.removePayload(legacyStorageKey(profileId))
        if (_snapshots.value.profileId == profileId) {
            _snapshots.value = ContinueWatchingEnrichmentSnapshot(generation = _generation.value)
        }
    }

    fun onProfileChanged() {
        advanceGeneration()
        _snapshots.value = ContinueWatchingEnrichmentSnapshot(generation = _generation.value)
    }

    fun clearLocalState() {
        advanceGeneration()
        _snapshots.value = ContinueWatchingEnrichmentSnapshot(generation = _generation.value)
    }

    fun isCurrent(expectedGeneration: Long): Boolean = expectedGeneration == _generation.value

    private fun payloadForProfile(
        profileId: Int,
        source: WatchProgressSource,
    ): CachedEnrichmentPayload {
        val current = _snapshots.value
        return if (current.profileId == profileId && current.source == source) {
            CachedEnrichmentPayload(
                nextUp = current.nextUp,
                inProgress = current.inProgress,
                savedAtEpochMs = current.savedAtEpochMs,
            )
        } else {
            loadPayload(profileId, source) ?: CachedEnrichmentPayload()
        }
    }

    private fun savePayload(
        profileId: Int,
        source: WatchProgressSource,
        generation: Long,
        payload: CachedEnrichmentPayload,
    ): Boolean {
        if (!isCurrent(generation)) return false
        val encoded = runCatching { json.encodeToString(payload) }.getOrNull() ?: return false
        val key = storageKey(profileId, source)
        ContinueWatchingEnrichmentStorage.savePayload(key, encoded)
        if (!isCurrent(generation)) {
            ContinueWatchingEnrichmentStorage.removePayload(key)
            return false
        }
        if (source == WatchProgressSource.NUVIO_SYNC) {
            ContinueWatchingEnrichmentStorage.removePayload(legacyStorageKey(profileId))
        }
        _snapshots.value = payload.toSnapshot(profileId, source, generation)
        return true
    }

    private fun loadPayload(
        profileId: Int,
        source: WatchProgressSource,
    ): CachedEnrichmentPayload? {
        val scopedKey = storageKey(profileId, source)
        var raw = ContinueWatchingEnrichmentStorage.loadPayload(scopedKey)
        if (raw == null && source == WatchProgressSource.NUVIO_SYNC) {
            val legacyKey = legacyStorageKey(profileId)
            raw = ContinueWatchingEnrichmentStorage.loadPayload(legacyKey)
            if (raw != null) {
                ContinueWatchingEnrichmentStorage.savePayload(scopedKey, raw)
                ContinueWatchingEnrichmentStorage.removePayload(legacyKey)
            }
        }
        raw ?: return null
        return runCatching {
            json.decodeFromString<CachedEnrichmentPayload>(raw)
        }.getOrNull().also { decoded ->
            if (decoded == null) ContinueWatchingEnrichmentStorage.removePayload(scopedKey)
        }
    }

    private fun storageKey(profileId: Int, source: WatchProgressSource): String =
        "${storageKey}_${source.name.lowercase()}_$profileId"

    private fun legacyStorageKey(profileId: Int): String = "${storageKey}_$profileId"

    private fun CachedEnrichmentPayload?.toSnapshot(
        profileId: Int,
        source: WatchProgressSource,
        generation: Long,
    ): ContinueWatchingEnrichmentSnapshot =
        ContinueWatchingEnrichmentSnapshot(
            profileId = profileId,
            source = source,
            generation = generation,
            nextUp = this?.nextUp.orEmpty(),
            inProgress = this?.inProgress.orEmpty(),
            savedAtEpochMs = this?.savedAtEpochMs ?: 0L,
        )

    private fun advanceGeneration() {
        _generation.value += 1L
    }
}

internal const val MaxCachedNextUpItems = 60
internal const val MaxCachedInProgressItems = 20
internal const val CurrentEpisodeReleaseTimingRuleVersion = 3

internal fun normalizeNextUpSnapshotForCache(items: List<CachedNextUpItem>): List<CachedNextUpItem> =
    items
        .asSequence()
        .filter { item -> item.contentId.isNotBlank() && item.videoId.isNotBlank() }
        .groupBy { item ->
            continueWatchingCanonicalIdentity(
                parentMetaId = item.contentId,
                parentMetaType = item.contentType,
                videoId = item.videoId,
                seasonNumber = item.season,
                episodeNumber = item.episode,
            )
        }
        .values
        .mapNotNull { duplicates ->
            duplicates.maxWithOrNull(
                compareBy<CachedNextUpItem> { item -> item.metadataCheckedAtEpochMs }
                    .thenBy { item -> item.cachedMetadataQualityScore() }
                    .thenBy { item -> maxOf(item.sortTimestamp, item.lastWatched) },
            )
        }
        .sortedByDescending { item -> maxOf(item.sortTimestamp, item.lastWatched) }
        .take(MaxCachedNextUpItems)
        .toList()

private fun CachedNextUpItem.cachedMetadataQualityScore(): Int {
    var score = 0
    if (!episodeTitle.isGenericContinueWatchingEpisodeTitle(episode)) score += 4
    if (!episodeThumbnail.isNullOrBlank()) score += 3
    if (!pauseDescription.isNullOrBlank()) score += 2
    if (!logo.isNullOrBlank()) score += 1
    if (!poster.isNullOrBlank()) score += 1
    if (!backdrop.isNullOrBlank()) score += 1
    return score
}

internal fun normalizeInProgressSnapshotForCache(items: List<CachedInProgressItem>): List<CachedInProgressItem> =
    items
        .asSequence()
        .filter { item -> item.contentId.isNotBlank() && item.videoId.isNotBlank() }
        .sortedByDescending { item -> item.lastWatched }
        .distinctBy { item ->
            continueWatchingCanonicalIdentity(
                parentMetaId = item.contentId,
                parentMetaType = item.contentType,
                videoId = item.videoId,
                seasonNumber = item.season,
                episodeNumber = item.episode,
            )
        }
        .take(MaxCachedInProgressItems)
        .toList()

internal fun CachedNextUpItem.needsContinueWatchingMetadataRefresh(nowEpochMs: Long): Boolean {
    if (metadataCheckedAtEpochMs <= 0L) return true
    val isIncomplete = episodeTitle.isGenericContinueWatchingEpisodeTitle(episode) ||
        episodeThumbnail.isNullOrBlank() ||
        pauseDescription.isNullOrBlank()
    val ttlMs = if (isIncomplete) {
        IncompleteContinueWatchingMetadataTtlMs
    } else {
        CompleteContinueWatchingMetadataTtlMs
    }
    return nowEpochMs - metadataCheckedAtEpochMs >= ttlMs
}

internal fun matchesNextUpCompletionSeed(
    seedSeason: Int?,
    seedEpisode: Int?,
    seedTimestamp: Long?,
    completedSeason: Int,
    completedEpisode: Int,
    completedTimestamp: Long,
): Boolean =
    seedSeason == completedSeason &&
        seedEpisode == completedEpisode &&
        (seedTimestamp == null || seedTimestamp == completedTimestamp)
