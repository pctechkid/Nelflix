package com.nuvio.app.features.watchprogress

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.nextReleasedEpisodeAfter
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktProgressRepository
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.trakt.WatchProgressSource
import com.nuvio.app.features.trakt.shouldUseTraktProgress as shouldUseTraktProgressSource
import com.nuvio.app.features.watching.application.WatchingActions
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watching.domain.WatchingCompletedEpisode
import com.nuvio.app.features.watching.domain.WatchingContentRef
import com.nuvio.app.features.watching.domain.isReleasedBy
import com.nuvio.app.features.watching.sync.ProgressSyncAdapter
import com.nuvio.app.features.watching.sync.SupabaseProgressSyncAdapter
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object WatchProgressRepository {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("WatchProgressRepository")

    private val _uiState = MutableStateFlow(WatchProgressUiState())
    val uiState: StateFlow<WatchProgressUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var currentProfileId: Int = 1
    private var entriesByVideoId: MutableMap<String, WatchProgressEntry> = mutableMapOf()
    private var metadataResolutionJob: Job? = null
    private var metadataRefreshPending = false
    private var metadataRefreshPendingForce = false
    internal var syncAdapter: ProgressSyncAdapter = SupabaseProgressSyncAdapter

    init {
        syncScope.launch {
            TraktAuthRepository.isAuthenticated.collectLatest { authenticated ->
                if (shouldUseTraktProgressSource(
                        isAuthenticated = authenticated,
                        source = TraktSettingsRepository.uiState.value.watchProgressSource,
                    )
                ) {
                    runCatching { TraktProgressRepository.refreshNow() }
                        .onFailure { error -> log.w { "Failed to refresh Trakt progress after auth: ${error.message}" } }
                }
                publish()
            }
        }

        syncScope.launch {
            TraktSettingsRepository.uiState.collectLatest { settings ->
                if (shouldUseTraktProgressSource(
                        isAuthenticated = TraktAuthRepository.isAuthenticated.value,
                        source = settings.watchProgressSource,
                    )
                ) {
                    runCatching { TraktProgressRepository.refreshNow() }
                        .onFailure { error -> log.w { "Failed to refresh Trakt progress after source change: ${error.message}" } }
                }
                publish()
            }
        }

        syncScope.launch {
            TraktProgressRepository.uiState.collectLatest {
                if (shouldUseTraktProgress()) {
                    publish()
                }
            }
        }
    }

    fun ensureLoaded() {
        TraktAuthRepository.ensureLoaded()
        TraktSettingsRepository.ensureLoaded()
        TraktProgressRepository.ensureLoaded()
        if (hasLoaded) return
        loadFromDisk(ProfileRepository.activeProfileId)
        if (shouldUseTraktProgress()) {
            TraktProgressRepository.refreshAsync()
        }
    }

    fun onProfileChanged(profileId: Int) {
        if (profileId == currentProfileId && hasLoaded) return
        ContinueWatchingEnrichmentCache.onProfileChanged()
        TraktSettingsRepository.onProfileChanged()
        loadFromDisk(profileId)
        TraktProgressRepository.onProfileChanged()
        if (shouldUseTraktProgress()) {
            TraktProgressRepository.refreshAsync()
        }
    }

    fun clearLocalState() {
        metadataResolutionJob?.cancel()
        metadataResolutionJob = null
        metadataRefreshPending = false
        metadataRefreshPendingForce = false
        hasLoaded = false
        currentProfileId = 1
        entriesByVideoId.clear()
        ContinueWatchingEnrichmentCache.clearLocalState()
        TraktProgressRepository.clearLocalState()
        TraktSettingsRepository.clearLocalState()
        _uiState.value = WatchProgressUiState()
    }

    private fun loadFromDisk(profileId: Int) {
        currentProfileId = profileId
        hasLoaded = true
        entriesByVideoId.clear()

        val payload = WatchProgressStorage.loadPayload(profileId).orEmpty().trim()
        if (payload.isNotEmpty()) {
            entriesByVideoId = WatchProgressCodec.decodeEntries(payload)
                .associateBy { it.videoId }
                .toMutableMap()
        }
        publish()
        requestMetadataRefresh(force = true)
    }

    suspend fun pullFromServer(profileId: Int) {
        TraktAuthRepository.ensureLoaded()
        TraktSettingsRepository.ensureLoaded()
        TraktProgressRepository.ensureLoaded()
        currentProfileId = profileId

        val useTraktProgress = shouldUseTraktProgress()

        if (useTraktProgress) {
            runCatching { TraktProgressRepository.refreshNow() }
                .onFailure { e -> log.e(e) { "Failed to pull Trakt progress" } }
            publish()
            return
        }

        runCatching {
            val pullStartedAtEpochMs = WatchProgressClock.nowEpochMs()
            val serverEntries = syncAdapter.pull(profileId = profileId)

            val oldLocal = entriesByVideoId.toMap()
            val newMap = mutableMapOf<String, WatchProgressEntry>()
            val localEntriesToRepush = mutableListOf<WatchProgressEntry>()

            serverEntries.forEach { entry ->
                val videoId = entry.videoId
                val cached = oldLocal[videoId] ?: oldLocal.values.firstOrNull { local ->
                    local.parentMetaId == entry.contentId &&
                        local.seasonNumber == entry.season &&
                        local.episodeNumber == entry.episode
                }
                val displayMetadata = entry.displayMetadata
                val remoteEntry = WatchProgressEntry(
                    contentType = entry.contentType,
                    parentMetaId = entry.contentId,
                    parentMetaType = cached?.parentMetaType ?: entry.contentType,
                    videoId = videoId,
                    title = cached?.title.displayTextOrNull()
                        ?: displayMetadata?.title.displayTextOrNull()
                        ?: entry.contentId,
                    logo = cached?.logo.displayTextOrNull() ?: displayMetadata?.logo.displayTextOrNull(),
                    poster = cached?.poster.displayTextOrNull() ?: displayMetadata?.poster.displayTextOrNull(),
                    background = cached?.background.displayTextOrNull() ?: displayMetadata?.background.displayTextOrNull(),
                    seasonNumber = entry.season,
                    episodeNumber = entry.episode,
                    episodeTitle = cached?.episodeTitle.displayTextOrNull()
                        ?: displayMetadata?.episodeTitle.displayTextOrNull(),
                    episodeThumbnail = cached?.episodeThumbnail.displayTextOrNull()
                        ?: displayMetadata?.episodeThumbnail.displayTextOrNull(),
                    lastPositionMs = entry.position,
                    durationMs = entry.duration,
                    lastUpdatedEpochMs = entry.lastWatched,
                    providerName = cached?.providerName,
                    providerAddonId = cached?.providerAddonId,
                    lastStreamTitle = cached?.lastStreamTitle,
                    lastStreamSubtitle = cached?.lastStreamSubtitle,
                    pauseDescription = cached?.pauseDescription.displayTextOrNull()
                        ?: displayMetadata?.pauseDescription.displayTextOrNull(),
                    lastSourceUrl = cached?.lastSourceUrl,
                    isCompleted = isWatchProgressComplete(entry.position, entry.duration, false),
                    metadataCheckedAtEpochMs = cached?.metadataCheckedAtEpochMs ?: 0L,
                )
                val selected = if (
                    shouldPreferLocalProgressAfterPull(
                        local = cached,
                        remote = remoteEntry,
                        pullStartedAtEpochMs = pullStartedAtEpochMs,
                    )
                ) {
                    cached!!.also(localEntriesToRepush::add)
                } else {
                    remoteEntry
                }
                newMap[selected.videoId] = selected
            }

            oldLocal.values.forEach { local ->
                val representedRemotely = serverEntries.any { remote ->
                    remote.videoId == local.videoId ||
                        (
                            remote.contentId == local.parentMetaId &&
                                remote.season == local.seasonNumber &&
                                remote.episode == local.episodeNumber
                            )
                }
                if (
                    !representedRemotely &&
                    shouldPreferLocalProgressAfterPull(
                        local = local,
                        remote = null,
                        pullStartedAtEpochMs = pullStartedAtEpochMs,
                    )
                ) {
                    newMap[local.videoId] = local
                    localEntriesToRepush += local
                }
            }

            entriesByVideoId = newMap
            hasLoaded = true
            publish()
            persist()

            requestMetadataRefresh(force = true)
            if (localEntriesToRepush.isNotEmpty()) {
                runCatching {
                    syncAdapter.push(
                        profileId = profileId,
                        entries = localEntriesToRepush.distinctBy { entry ->
                            Triple(
                                entry.parentMetaId,
                                entry.seasonNumber,
                                entry.episodeNumber,
                            )
                        },
                    )
                }.onFailure { error ->
                    log.e(error) { "Failed to reconcile newer local watch progress after pull" }
                }
            }
        }.onFailure { e ->
            log.e(e) { "Failed to pull watch progress from server" }
        }
    }

    fun requestMetadataRefresh(force: Boolean = false) {
        ensureLoaded()

        if (metadataResolutionJob?.isActive == true) {
            metadataRefreshPending = true
            metadataRefreshPendingForce = metadataRefreshPendingForce || force
            return
        }

        val nowEpochMs = WatchProgressClock.nowEpochMs()
        val requestedSource = activeProgressSource()
        val needsResolution = currentEntries()
            .filter { entry ->
                entry.needsContinueWatchingMetadataRefresh(
                    nowEpochMs = nowEpochMs,
                    force = force,
                )
            }
            .groupBy { it.parentMetaId to it.parentMetaType }

        if (needsResolution.isEmpty()) return

        val requestedProfileId = currentProfileId
        metadataResolutionJob = syncScope.launch {
            try {
                withTimeoutOrNull(30_000L) {
                    AddonRepository.awaitManifestsLoaded()
                } ?: run {
                    log.w { "Timed out waiting for addon manifests" }
                    return@launch
                }

                var changed = false
                for ((key, entries) in needsResolution) {
                    if (
                        currentProfileId != requestedProfileId ||
                        activeProgressSource() != requestedSource
                    ) {
                        return@launch
                    }
                    val (metaId, metaType) = key
                    val meta = runCatching {
                        MetaDetailsRepository.fetchFreshContinueWatchingMeta(metaType, metaId)
                    }.onFailure { error ->
                        log.w { "Failed to refresh Continue Watching metadata for $metaType:$metaId: ${error.message}" }
                    }.getOrNull()
                    val checkedAtEpochMs = WatchProgressClock.nowEpochMs()

                    for (requestedEntry in entries) {
                        val currentEntry = currentEntries().firstOrNull { candidate ->
                            candidate.videoId == requestedEntry.videoId ||
                                (
                                    candidate.parentMetaId == requestedEntry.parentMetaId &&
                                        candidate.seasonNumber == requestedEntry.seasonNumber &&
                                        candidate.episodeNumber == requestedEntry.episodeNumber
                                    )
                        } ?: continue
                        if (
                            currentEntry.parentMetaId != requestedEntry.parentMetaId ||
                            currentEntry.seasonNumber != requestedEntry.seasonNumber ||
                            currentEntry.episodeNumber != requestedEntry.episodeNumber
                        ) {
                            continue
                        }
                        val refreshed = currentEntry.withRefreshedContinueWatchingMetadata(
                            meta = meta,
                            checkedAtEpochMs = checkedAtEpochMs,
                        )
                        if (refreshed != currentEntry) {
                            changed = if (requestedSource == WatchProgressSource.TRAKT) {
                                TraktProgressRepository.applyMetadataRefresh(refreshed) || changed
                            } else {
                                entriesByVideoId[currentEntry.videoId] = refreshed
                                true
                            }
                        }
                    }
                }
                if (
                    changed &&
                    currentProfileId == requestedProfileId &&
                    activeProgressSource() == requestedSource
                ) {
                    publish()
                    if (requestedSource == WatchProgressSource.NUVIO_SYNC) persist()
                }
            } finally {
                metadataResolutionJob = null
                if (metadataRefreshPending && currentProfileId == requestedProfileId) {
                    val rerunForce = metadataRefreshPendingForce
                    metadataRefreshPending = false
                    metadataRefreshPendingForce = false
                    requestMetadataRefresh(force = rerunForce)
                } else {
                    metadataRefreshPending = false
                    metadataRefreshPendingForce = false
                }
            }
        }
    }

    fun upsertPlaybackProgress(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
    ) {
        ensureLoaded()
        upsert(session = session, snapshot = snapshot, persist = true)
    }

    fun flushPlaybackProgress(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
    ) {
        ensureLoaded()
        upsert(session = session, snapshot = snapshot, persist = true)
    }

    fun clearProgress(videoId: String) {
        clearProgress(listOf(videoId))
    }

    fun clearProgress(videoIds: Collection<String>) {
        ensureLoaded()
        if (videoIds.isEmpty()) return

        val entriesToRemove = currentEntries().filter { entry -> entry.videoId in videoIds }

        if (shouldUseTraktProgress()) {
            videoIds.forEach(TraktProgressRepository::applyOptimisticRemoval)
            invalidateContinueWatchingProjection(entriesToRemove.map(WatchProgressEntry::parentMetaId))
            publish()
            return
        }

        val removedEntries = videoIds.mapNotNull { videoId ->
            entriesByVideoId.remove(videoId)
        }
        if (removedEntries.isNotEmpty()) {
            invalidateContinueWatchingProjection(removedEntries.map(WatchProgressEntry::parentMetaId))
            publish()
            persist()
            pushDeleteToServer(removedEntries)
        }
    }

    fun removeProgress(
        contentId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        ensureLoaded()
        val normalizedContentId = contentId.trim()
        if (normalizedContentId.isBlank()) return

        val entriesToRemove = currentEntries().filter { entry ->
            if (entry.parentMetaId != normalizedContentId) {
                false
            } else if (seasonNumber != null && episodeNumber != null) {
                entry.seasonNumber == seasonNumber && entry.episodeNumber == episodeNumber
            } else {
                true
            }
        }
        if (entriesToRemove.isEmpty()) return

        if (shouldUseTraktProgress()) {
            TraktProgressRepository.applyOptimisticRemoval(
                contentId = normalizedContentId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
            invalidateContinueWatchingProjection(listOf(normalizedContentId))
            publish()
            syncScope.launch {
                runCatching {
                    TraktProgressRepository.removeProgress(
                        contentId = normalizedContentId,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                    )
                }.onFailure { error ->
                    log.e(error) { "Failed to remove Trakt watch progress" }
                }
            }
            return
        }

        entriesToRemove.forEach { entry ->
            entriesByVideoId.remove(entry.videoId)
        }
        invalidateContinueWatchingProjection(entriesToRemove.map(WatchProgressEntry::parentMetaId))
        publish()
        persist()
        pushDeleteToServer(entriesToRemove)
    }

    fun progressForVideo(videoId: String): WatchProgressEntry? {
        ensureLoaded()
        return if (shouldUseTraktProgress()) {
            TraktProgressRepository.uiState.value.entries
        } else {
            entriesByVideoId.values.toList()
        }.firstOrNull { it.videoId == videoId }
    }

    fun resumeEntryForSeries(metaId: String): WatchProgressEntry? {
        ensureLoaded()
        return currentEntries().resumeEntryForSeries(metaId)
    }

    fun continueWatching(): List<WatchProgressEntry> {
        ensureLoaded()
        return currentEntries().continueWatchingEntries()
    }

    private fun upsert(
        session: WatchProgressPlaybackSession,
        snapshot: PlayerPlaybackSnapshot,
        persist: Boolean,
    ) {
        val positionMs = snapshot.positionMs.coerceAtLeast(0L)
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val isCompleted = isWatchProgressComplete(
            positionMs = positionMs,
            durationMs = durationMs,
            isEnded = snapshot.isEnded,
        )
        if (!isCompleted && !shouldStoreWatchProgress(positionMs = positionMs, durationMs = durationMs)) {
            return
        }

        val useTraktProgress = shouldUseTraktProgress()
        val previousEntry = currentEntries().firstOrNull { entry ->
            entry.videoId == session.videoId ||
                (
                    entry.parentMetaId == session.parentMetaId &&
                        entry.seasonNumber == session.seasonNumber &&
                        entry.episodeNumber == session.episodeNumber
                    )
        }
        if (
            shouldIgnoreTerminalProgressRegression(
                previousEntry = previousEntry,
                snapshot = snapshot,
                incomingIsCompleted = isCompleted,
            )
        ) {
            return
        }
        val metadataSession = session.withPreservedCheckedMetadata(previousEntry)
        val entry = WatchProgressEntry(
            contentType = metadataSession.contentType,
            parentMetaId = metadataSession.parentMetaId,
            parentMetaType = metadataSession.parentMetaType,
            videoId = metadataSession.videoId,
            title = metadataSession.title,
            logo = metadataSession.logo,
            poster = metadataSession.poster,
            background = metadataSession.background,
            seasonNumber = metadataSession.seasonNumber,
            episodeNumber = metadataSession.episodeNumber,
            episodeTitle = metadataSession.episodeTitle,
            episodeThumbnail = metadataSession.episodeThumbnail,
            lastPositionMs = if (isCompleted && durationMs > 0L) durationMs else positionMs,
            durationMs = durationMs,
            lastUpdatedEpochMs = WatchProgressClock.nowEpochMs(),
            providerName = metadataSession.providerName,
            providerAddonId = metadataSession.providerAddonId,
            lastStreamTitle = metadataSession.lastStreamTitle,
            lastStreamSubtitle = metadataSession.lastStreamSubtitle,
            pauseDescription = metadataSession.pauseDescription,
            lastSourceUrl = metadataSession.lastSourceUrl,
            isCompleted = isCompleted,
            metadataCheckedAtEpochMs = previousEntry?.metadataCheckedAtEpochMs ?: 0L,
        ).normalizedCompletion()

        if (entry.parentMetaType.equals("series", ignoreCase = true)) {
            ContinueWatchingPreferencesRepository.removeDismissedNextUpKeysForContent(entry.parentMetaId)
        }

        val becameCompleted = entry.isEffectivelyCompleted && previousEntry?.isEffectivelyCompleted != true

        entriesByVideoId[session.videoId] = entry
        if (useTraktProgress) {
            TraktProgressRepository.applyOptimisticProgress(entry)
        }
        if (becameCompleted) {
            invalidateContinueWatchingProjection(listOf(entry.parentMetaId))
        }
        publish()
        if (persist) persist()
        if (entry.needsContinueWatchingMetadataRefresh(WatchProgressClock.nowEpochMs())) {
            requestMetadataRefresh()
        }
        pushScrobbleToServer(entry)
        if (shouldCascadeCompletedProgressToWatchedHistory(entry, useTraktProgress)) {
            WatchingActions.onProgressEntryUpdated(entry)
        }
    }

    private fun pushScrobbleToServer(entry: WatchProgressEntry) {
        syncScope.launch {
            runCatching {
                val profileId = ProfileRepository.activeProfileId
                syncAdapter.push(profileId = profileId, entries = listOf(entry))
            }.onFailure { e ->
                log.e(e) { "Failed to push watch progress scrobble" }
            }
        }
    }

    private fun pushDeleteToServer(entries: Collection<WatchProgressEntry>) {
        if (shouldUseTraktProgress()) return
        syncScope.launch {
            runCatching {
                if (entries.isEmpty()) return@runCatching
                val profileId = ProfileRepository.activeProfileId
                syncAdapter.delete(profileId = profileId, entries = entries)
            }.onFailure { e ->
                log.e(e) { "Failed to push watch progress delete" }
            }
        }
    }

    private fun publish() {
        val entries = currentEntries()
        val sortedEntries = entries.sortedByDescending { it.lastUpdatedEpochMs }
        _uiState.value = WatchProgressUiState(
            entries = sortedEntries,
        )
    }

    private fun persist() {
        WatchProgressStorage.savePayload(
            currentProfileId,
            WatchProgressCodec.encodeEntries(entriesByVideoId.values),
        )
    }

    private fun shouldUseTraktProgress(): Boolean =
        shouldUseTraktProgressSource(
            isAuthenticated = TraktAuthRepository.isAuthenticated.value,
            source = TraktSettingsRepository.uiState.value.watchProgressSource,
        )

    private fun activeProgressSource(): WatchProgressSource =
        if (shouldUseTraktProgress()) WatchProgressSource.TRAKT else WatchProgressSource.NUVIO_SYNC

    fun invalidateContinueWatchingProjection(contentIds: Collection<String>) {
        ContinueWatchingEnrichmentCache.invalidateContent(
            profileId = currentProfileId,
            source = activeProgressSource(),
            contentIds = contentIds,
        )
    }

    fun primeContinueWatchingProjection(meta: MetaDetails) {
        ensureLoaded()
        ContinueWatchingPreferencesRepository.ensureLoaded()
        WatchedRepository.ensureLoaded()

        val source = activeProgressSource()
        val watchedItems = if (source == WatchProgressSource.TRAKT) {
            emptyList()
        } else {
            WatchedRepository.uiState.value.items
        }
        val completed = WatchingState.latestCompletedBySeries(
            progressEntries = currentEntries(),
            watchedItems = watchedItems,
            preferFurthestEpisode = ContinueWatchingPreferencesRepository.uiState.value.upNextFromFurthestEpisode,
        )[WatchingContentRef(type = meta.type, id = meta.id)]
        val nowEpochMs = WatchProgressClock.nowEpochMs()
        val nextUp = completed?.let { completedEpisode ->
            buildImmediateNextUpCacheItem(
                meta = meta,
                completed = completedEpisode,
                todayIsoDate = CurrentDateProvider.todayIsoDate(),
                showUnairedNextUp = ContinueWatchingPreferencesRepository.uiState.value.showUnairedNextUp,
                metadataCheckedAtEpochMs = nowEpochMs,
            )
        }

        if (nextUp == null) {
            invalidateContinueWatchingProjection(listOf(meta.id))
            return
        }
        ContinueWatchingEnrichmentCache.upsertNextUp(
            profileId = currentProfileId,
            source = source,
            item = nextUp,
            savedAtEpochMs = nowEpochMs,
        )
    }

    private fun currentEntries(): List<WatchProgressEntry> {
        return if (shouldUseTraktProgress()) {
            TraktProgressRepository.uiState.value.entries
        } else {
            entriesByVideoId.values.toList()
        }
    }

}

internal fun buildImmediateNextUpCacheItem(
    meta: MetaDetails,
    completed: WatchingCompletedEpisode,
    todayIsoDate: String,
    showUnairedNextUp: Boolean,
    metadataCheckedAtEpochMs: Long,
): CachedNextUpItem? {
    val nextEpisode = meta.nextReleasedEpisodeAfter(
        seasonNumber = completed.seasonNumber,
        episodeNumber = completed.episodeNumber,
        todayIsoDate = todayIsoDate,
        showUnairedNextUp = showUnairedNextUp,
    ) ?: return null
    val seed = WatchProgressEntry(
        contentType = meta.type,
        parentMetaId = meta.id,
        parentMetaType = meta.type,
        videoId = "${meta.id}:${completed.seasonNumber}:${completed.episodeNumber}",
        title = meta.name,
        logo = meta.logo,
        poster = meta.poster,
        background = meta.background,
        seasonNumber = completed.seasonNumber,
        episodeNumber = completed.episodeNumber,
        lastPositionMs = 0L,
        durationMs = 0L,
        lastUpdatedEpochMs = completed.markedAtEpochMs,
        isCompleted = true,
    )
    val item = seed.toUpNextContinueWatchingItem(
        nextEpisode = nextEpisode,
        releaseEpochMs = null,
    )
    return CachedNextUpItem(
        contentId = meta.id,
        contentType = meta.type,
        name = meta.name,
        poster = meta.poster,
        backdrop = meta.background,
        logo = meta.logo,
        videoId = item.videoId,
        season = item.seasonNumber,
        episode = item.episodeNumber,
        episodeTitle = item.episodeTitle,
        episodeThumbnail = item.episodeThumbnail,
        pauseDescription = item.pauseDescription,
        released = item.released,
        releaseTimingRuleVersion = CurrentEpisodeReleaseTimingRuleVersion,
        releaseEpochMs = null,
        hasAired = isReleasedBy(todayIsoDate = todayIsoDate, releasedDate = item.released),
        lastWatched = completed.markedAtEpochMs,
        sortTimestamp = completed.markedAtEpochMs,
        seedSeason = completed.seasonNumber,
        seedEpisode = completed.episodeNumber,
        seedLastUpdatedEpochMs = completed.markedAtEpochMs,
        isReleaseAlert = false,
        isNewSeasonRelease = false,
        metadataCheckedAtEpochMs = metadataCheckedAtEpochMs,
    )
}

internal fun shouldIgnoreTerminalProgressRegression(
    previousEntry: WatchProgressEntry?,
    snapshot: PlayerPlaybackSnapshot,
    incomingIsCompleted: Boolean,
): Boolean {
    if (previousEntry?.isEffectivelyCompleted != true) return false
    if (incomingIsCompleted) return true
    return !snapshot.isPlaying
}

internal fun shouldPreferLocalProgressAfterPull(
    local: WatchProgressEntry?,
    remote: WatchProgressEntry?,
    pullStartedAtEpochMs: Long,
): Boolean {
    local ?: return false
    if (remote == null) {
        return local.lastUpdatedEpochMs >= pullStartedAtEpochMs - LOCAL_PROGRESS_PULL_GRACE_MS
    }
    if (local.lastUpdatedEpochMs != remote.lastUpdatedEpochMs) {
        return local.lastUpdatedEpochMs > remote.lastUpdatedEpochMs
    }
    return local.isEffectivelyCompleted && !remote.isEffectivelyCompleted
}

private const val LOCAL_PROGRESS_PULL_GRACE_MS = 2L * 60L * 1000L

private fun String?.displayTextOrNull(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
