package com.nuvio.app.features.watched

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.trakt.WatchProgressSource
import com.nuvio.app.features.trakt.shouldUseTraktProgress
import com.nuvio.app.features.watching.sync.SupabaseWatchedSyncAdapter
import com.nuvio.app.features.watching.sync.TraktWatchedSyncAdapter
import com.nuvio.app.features.watching.sync.WatchedSyncAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredWatchedPayload(
    val items: List<WatchedItem> = emptyList(),
    val pendingMutations: List<PendingWatchedMutation> = emptyList(),
)

@Serializable
internal data class PendingWatchedMutation(
    val item: WatchedItem,
    val isWatched: Boolean,
)

object WatchedRepository {
    private const val watchedItemsPageSize = 500

    private var syncScopeJob: Job = SupervisorJob()
    private var syncScope = CoroutineScope(syncScopeJob + Dispatchers.Default)
    private var pendingSyncJob: Job? = null
    private var pendingSyncProfileId: Int? = null
    private val mutationSyncMutex = Mutex()
    private val log = Logger.withTag("WatchedRepository")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(WatchedUiState())
    val uiState: StateFlow<WatchedUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var currentProfileId: Int = 1
    private var itemsByKey: MutableMap<String, WatchedItem> = mutableMapOf()
    private var pendingMutationsByKey: MutableMap<String, PendingWatchedMutation> = mutableMapOf()
    private var lastSyncErrorMessage: String? = null
    internal var syncAdapter: WatchedSyncAdapter = SupabaseWatchedSyncAdapter

    private fun activePullSyncAdapter(): WatchedSyncAdapter =
        if (shouldUseTraktWatchedSync()) TraktWatchedSyncAdapter else syncAdapter

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk(ProfileRepository.activeProfileId)
    }

    fun onProfileChanged(profileId: Int) {
        if (profileId == currentProfileId && hasLoaded) return
        loadFromDisk(profileId)
    }

    fun clearLocalState() {
        syncScopeJob.cancel()
        syncScopeJob = SupervisorJob()
        syncScope = CoroutineScope(syncScopeJob + Dispatchers.Default)
        pendingSyncJob = null
        pendingSyncProfileId = null
        hasLoaded = false
        currentProfileId = 1
        itemsByKey.clear()
        pendingMutationsByKey.clear()
        lastSyncErrorMessage = null
        _uiState.value = WatchedUiState()
    }

    private fun loadFromDisk(profileId: Int) {
        pendingSyncJob?.cancel()
        pendingSyncJob = null
        pendingSyncProfileId = null
        currentProfileId = profileId
        hasLoaded = true
        itemsByKey.clear()
        pendingMutationsByKey.clear()
        lastSyncErrorMessage = null

        val payload = WatchedStorage.loadPayload(profileId).orEmpty().trim()
        if (payload.isNotEmpty()) {
            val stored = runCatching {
                json.decodeFromString<StoredWatchedPayload>(payload)
            }.getOrDefault(StoredWatchedPayload())
            itemsByKey = stored.items
                .map(WatchedItem::normalizedMarkedAt)
                .associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
                .toMutableMap()
            pendingMutationsByKey = stored.pendingMutations
                .associateBy(PendingWatchedMutation::key)
                .toMutableMap()
            applyPendingWatchedMutations(
                targetItems = itemsByKey,
                pendingMutations = pendingMutationsByKey.values,
            )
        }

        publish()
    }

    suspend fun pullFromServer(profileId: Int) {
        TraktAuthRepository.ensureLoaded()
        TraktSettingsRepository.ensureLoaded()
        if (!hasLoaded || currentProfileId != profileId) {
            loadFromDisk(profileId)
        }
        currentProfileId = profileId
        runCatching {
            flushPendingMutations(profileId)
            val serverItems = activePullSyncAdapter().pull(
                profileId = profileId,
                pageSize = watchedItemsPageSize,
            )

            val merged = mergeWatchedPull(
                serverItems = serverItems,
                pendingMutations = pendingMutationsByKey.values,
            )

            itemsByKey = merged.items.toMutableMap()
            hasLoaded = true
            publish()
            persist()
            if (pendingMutationsByKey.isNotEmpty()) {
                requestPendingMutationSync(profileId)
            }
        }.onFailure { e ->
            log.e(e) { "Failed to pull watched items from server" }
        }
    }

    fun toggleWatched(item: WatchedItem) {
        ensureLoaded()
        val key = watchedItemKey(item.type, item.id, item.season, item.episode)
        if (itemsByKey.containsKey(key)) {
            unmarkWatched(item)
        } else {
            markWatched(item)
        }
    }

    fun markWatched(item: WatchedItem) {
        markWatched(listOf(item))
    }

    fun markWatched(items: Collection<WatchedItem>) {
        ensureLoaded()
        if (items.isEmpty()) return
        val markedAt = WatchedClock.nowEpochMs()
        val timestampedItems = items.map { watchedItem ->
            watchedItem.copy(markedAtEpochMs = markedAt)
        }
        timestampedItems.forEach { watchedItem ->
            val key = watchedItemKey(watchedItem.type, watchedItem.id, watchedItem.season, watchedItem.episode)
            itemsByKey[key] = watchedItem
            pendingMutationsByKey[key] = PendingWatchedMutation(
                item = watchedItem,
                isWatched = true,
            )
        }
        publish()
        persist()
        requestPendingMutationSync(currentProfileId)
    }

    fun unmarkWatched(item: WatchedItem) {
        unmarkWatched(listOf(item))
    }

    fun unmarkWatched(
        id: String,
        type: String,
        season: Int? = null,
        episode: Int? = null,
    ) {
        unmarkWatched(
            listOf(
                WatchedItem(
                    id = id,
                    type = type,
                    name = "",
                    season = season,
                    episode = episode,
                    markedAtEpochMs = 0L,
                ),
            ),
        )
    }

    fun unmarkWatched(items: Collection<WatchedItem>) {
        ensureLoaded()
        if (items.isEmpty()) return
        items.forEach { watchedItem ->
            val key = watchedItem.key()
            val removedItem = itemsByKey.remove(key)
            pendingMutationsByKey[key] = PendingWatchedMutation(
                item = removedItem ?: watchedItem,
                isWatched = false,
            )
        }
        publish()
        persist()
        requestPendingMutationSync(currentProfileId)
    }

    fun isWatched(
        id: String,
        type: String,
        season: Int? = null,
        episode: Int? = null,
    ): Boolean {
        ensureLoaded()
        return itemsByKey.containsKey(watchedItemKey(type, id, season, episode))
    }

    fun reconcileSeriesWatchedState(
        meta: MetaDetails,
        todayIsoDate: String,
        isEpisodeCompleted: (com.nuvio.app.features.details.MetaVideo) -> Boolean = { false },
    ) {
        ensureLoaded()
        val shouldMarkSeriesWatched = meta.hasWatchedAllMainSeasonEpisodes(todayIsoDate) { episode ->
            isWatched(
                id = meta.id,
                type = meta.type,
                season = episode.season,
                episode = episode.episode,
            ) || isEpisodeCompleted(episode)
        }
        val seriesWatchedItem = meta.toSeriesWatchedItem()
        if (shouldMarkSeriesWatched) {
            if (!isWatched(id = meta.id, type = meta.type)) {
                markWatched(seriesWatchedItem)
            }
        } else if (isWatched(id = meta.id, type = meta.type)) {
            unmarkWatched(seriesWatchedItem)
        }
    }

    private fun requestPendingMutationSync(profileId: Int) {
        if (pendingSyncJob?.isActive == true && pendingSyncProfileId == profileId) return
        pendingSyncJob?.cancel()
        pendingSyncProfileId = profileId
        pendingSyncJob = syncScope.launch {
            var retryDelayMs = INITIAL_SYNC_RETRY_DELAY_MS
            while (profileId == currentProfileId && pendingMutationsByKey.isNotEmpty()) {
                if (flushPendingMutations(profileId)) return@launch
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2L).coerceAtMost(MAX_SYNC_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun flushPendingMutations(profileId: Int): Boolean = mutationSyncMutex.withLock {
        if (profileId != currentProfileId) return@withLock false

        TraktAuthRepository.ensureLoaded()
        TraktSettingsRepository.ensureLoaded()
        val authState = AuthRepository.state.value
        val hasSupabaseAccount = authState is AuthState.Authenticated && !authState.isAnonymous
        val hasTraktAccount = TraktAuthRepository.isAuthenticated.value
        if (!hasSupabaseAccount && !hasTraktAccount) return@withLock false

        while (profileId == currentProfileId && pendingMutationsByKey.isNotEmpty()) {
            val snapshot = pendingMutationsByKey.toMap()
            val marks = snapshot.values.filter(PendingWatchedMutation::isWatched).map(PendingWatchedMutation::item)
            val deletes = snapshot.values.filterNot(PendingWatchedMutation::isWatched).map(PendingWatchedMutation::item)

            val result = runCatching {
                if (hasSupabaseAccount) {
                    if (marks.isNotEmpty()) syncAdapter.push(profileId = profileId, items = marks)
                    if (deletes.isNotEmpty()) syncAdapter.delete(profileId = profileId, items = deletes)
                }
                if (hasTraktAccount) {
                    if (marks.isNotEmpty()) TraktWatchedSyncAdapter.push(profileId = profileId, items = marks)
                    if (deletes.isNotEmpty()) TraktWatchedSyncAdapter.delete(profileId = profileId, items = deletes)
                }
            }

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                    ?: IllegalStateException("Watched-state synchronization failed")
                lastSyncErrorMessage = error.message ?: "Watched-state synchronization failed"
                publish()
                log.e(error) { "Failed to synchronize ${snapshot.size} watched mutations" }
                return@withLock false
            }

            if (profileId != currentProfileId) return@withLock false
            pendingMutationsByKey = acknowledgePendingWatchedMutations(
                current = pendingMutationsByKey,
                sent = snapshot,
            ).toMutableMap()
            lastSyncErrorMessage = null
            persist()
            publish()
        }
        true
    }

    private fun publish() {
        val items = itemsByKey.values
            .map(WatchedItem::normalizedMarkedAt)
            .sortedByDescending { it.markedAtEpochMs }
        _uiState.value = WatchedUiState(
            items = items,
            watchedKeys = items.mapTo(linkedSetOf()) {
                watchedItemKey(it.type, it.id, it.season, it.episode)
            },
            isLoaded = true,
            syncErrorMessage = lastSyncErrorMessage,
        )
    }

    private fun persist() {
        WatchedStorage.savePayload(
            currentProfileId,
            json.encodeToString(
                StoredWatchedPayload(
                    items = itemsByKey.values
                        .map(WatchedItem::normalizedMarkedAt)
                        .sortedByDescending { it.markedAtEpochMs },
                    pendingMutations = pendingMutationsByKey.values.toList(),
                ),
            ),
        )
    }

    private fun shouldUseTraktWatchedSync(): Boolean =
        shouldUseTraktWatchedSync(
            isAuthenticated = TraktAuthRepository.isAuthenticated.value,
            source = TraktSettingsRepository.uiState.value.watchProgressSource,
        )

    private const val INITIAL_SYNC_RETRY_DELAY_MS = 2_000L
    private const val MAX_SYNC_RETRY_DELAY_MS = 60_000L
}

internal data class WatchedPullMerge(
    val items: Map<String, WatchedItem>,
)

internal fun mergeWatchedPull(
    serverItems: Collection<WatchedItem>,
    pendingMutations: Collection<PendingWatchedMutation>,
): WatchedPullMerge {
    val mergedByKey = serverItems
        .map(WatchedItem::normalizedMarkedAt)
        .associateBy(WatchedItem::key)
        .toMutableMap()

    applyPendingWatchedMutations(
        targetItems = mergedByKey,
        pendingMutations = pendingMutations,
    )
    return WatchedPullMerge(
        items = mergedByKey,
    )
}

internal fun applyPendingWatchedMutations(
    targetItems: MutableMap<String, WatchedItem>,
    pendingMutations: Collection<PendingWatchedMutation>,
) {
    pendingMutations.forEach { mutation ->
        val key = mutation.key()
        if (mutation.isWatched) {
            targetItems[key] = mutation.item.normalizedMarkedAt()
        } else {
            targetItems.remove(key)
        }
    }
}

internal fun acknowledgePendingWatchedMutations(
    current: Map<String, PendingWatchedMutation>,
    sent: Map<String, PendingWatchedMutation>,
): Map<String, PendingWatchedMutation> = current.filter { (key, mutation) ->
    sent[key] != mutation
}

private fun PendingWatchedMutation.key(): String = item.key()

private fun WatchedItem.key(): String = watchedItemKey(type, id, season, episode)

internal fun shouldUseTraktWatchedSync(
    isAuthenticated: Boolean,
    source: WatchProgressSource,
): Boolean = shouldUseTraktProgress(
    isAuthenticated = isAuthenticated,
    source = source,
)
