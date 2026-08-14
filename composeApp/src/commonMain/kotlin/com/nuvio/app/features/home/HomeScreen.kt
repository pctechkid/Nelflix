package com.nuvio.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberHeroStretchState
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.nextReleasedEpisodeAfter
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeContinueWatchingSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.HomeFeaturedProductionsSection
import com.nuvio.app.features.home.components.HomeHeroReservedSpace
import com.nuvio.app.features.home.components.HomeHeroSection
import com.nuvio.app.features.home.components.HomeSkeletonHero
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TRAKT_CONTINUE_WATCHING_DAYS_CAP_ALL
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.trakt.WatchProgressSource
import com.nuvio.app.features.trakt.normalizeTraktContinueWatchingDaysCap
import com.nuvio.app.features.trakt.shouldUseTraktProgress
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.CachedInProgressItem
import com.nuvio.app.features.watchprogress.CachedNextUpItem
import com.nuvio.app.features.watchprogress.ContinueWatchingClockChangeMonitor
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentSnapshot
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingSortMode
import com.nuvio.app.features.watchprogress.CurrentEpisodeReleaseTimingRuleVersion
import com.nuvio.app.features.watchprogress.isSeriesTypeForContinueWatching
import com.nuvio.app.features.watchprogress.matchesNextUpCompletionSeed
import com.nuvio.app.features.watchprogress.nextUpDismissKey
import com.nuvio.app.features.watchprogress.needsContinueWatchingMetadataRefresh
import com.nuvio.app.core.time.parseEpisodeReleaseEpochMs
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildContinueWatchingEpisodeSubtitle
import com.nuvio.app.features.watchprogress.canonicalIdentity
import com.nuvio.app.features.watchprogress.isGenericContinueWatchingEpisodeTitle
import com.nuvio.app.features.watchprogress.metadataQualityScore
import com.nuvio.app.features.watchprogress.toContinueWatchingItem
import com.nuvio.app.features.watchprogress.toUpNextContinueWatchingItem
import com.nuvio.app.features.watchprogress.withReleaseAlertState
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watching.domain.WatchingContentRef
import com.nuvio.app.features.watching.domain.isReleasedBy
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.ProfileSpotlightRepository
import com.nuvio.app.features.home.components.HomeCollectionRowSection
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationDelayHours
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationHour
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationMinute
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.nuvio.app.features.home.components.ContinueWatchingLayout
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.rememberContinueWatchingLayout
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    animateCollectionGifs: Boolean = true,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)? = null,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    onFeaturedProductionClick: ((FeaturedProductionEntity) -> Unit)? = null,
    onFeaturedProductionsViewAllClick: (() -> Unit)? = null,
    onFirstCatalogRendered: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        CollectionRepository.initialize()
        ContinueWatchingPreferencesRepository.ensureLoaded()
        EpisodeReleaseNotificationsRepository.ensureLoaded()
        WatchedRepository.ensureLoaded()
        WatchProgressRepository.ensureLoaded()
        ProfileSpotlightRepository.load()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val homeUiState by HomeRepository.uiState.collectAsStateWithLifecycle()
    val profileSpotlightState by ProfileSpotlightRepository.state.collectAsStateWithLifecycle()
    val homeHeroItems = remember(profileSpotlightState.items) {
        profileSpotlightState.items
            .map { item ->
                MetaPreview(
                    id = item.id,
                    type = item.type,
                    name = item.name,
                    poster = item.poster,
                    banner = item.banner,
                    logo = item.logo,
                    description = item.description,
                    releaseInfo = item.releaseInfo,
                    rawReleaseDate = item.rawReleaseDate,
                    imdbRating = item.imdbRating,
                    genres = item.genres,
                )
            }
            .take(HOME_SPOTLIGHT_ITEM_LIMIT)
    }
    val homeSettingsUiState by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    val homeListState = rememberLazyListState()
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
    val continueWatchingPreferences by ContinueWatchingPreferencesRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val watchProgressUiState by WatchProgressRepository.uiState.collectAsStateWithLifecycle()
    val releaseNotificationsUiState by EpisodeReleaseNotificationsRepository.uiState.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    val traktSettingsUiState by remember {
        TraktSettingsRepository.ensureLoaded()
        TraktSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val isTraktAuthenticated by remember {
        TraktAuthRepository.ensureLoaded()
        TraktAuthRepository.isAuthenticated
    }.collectAsStateWithLifecycle()
    var observedOfflineState by remember { mutableStateOf(false) }
    var continueWatchingNowEpochMs by remember { mutableLongStateOf(WatchProgressClock.nowEpochMs()) }
    var continueWatchingRefreshGeneration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect {
            homeListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        ContinueWatchingClockChangeMonitor.events.collect {
            continueWatchingNowEpochMs = WatchProgressClock.nowEpochMs()
        }
    }

    LaunchedEffect(networkStatusUiState.condition) {
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (observedOfflineState) {
                    observedOfflineState = false
                    continueWatchingRefreshGeneration += 1L
                    WatchProgressRepository.requestMetadataRefresh(force = true)
                    HomeRepository.refresh(addonsUiState.addons, force = true)
                    ProfileSpotlightRepository.load(force = true)
                }
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    val isTraktProgressActive = remember(
        isTraktAuthenticated,
        traktSettingsUiState.watchProgressSource,
    ) {
        shouldUseTraktProgress(
            isAuthenticated = isTraktAuthenticated,
            source = traktSettingsUiState.watchProgressSource,
        )
    }
    val effectiveWatchProgressSource = if (isTraktProgressActive) {
        WatchProgressSource.TRAKT
    } else {
        WatchProgressSource.NUVIO_SYNC
    }

    val effectiveWatchProgressEntries = remember(
        watchProgressUiState.entries,
        isTraktProgressActive,
        traktSettingsUiState.continueWatchingDaysCap,
    ) {
        filterEntriesForTraktContinueWatchingWindow(
            entries = watchProgressUiState.entries,
            isTraktProgressActive = isTraktProgressActive,
            daysCap = traktSettingsUiState.continueWatchingDaysCap,
            nowEpochMs = WatchProgressClock.nowEpochMs(),
        )
    }

    val effectiveWatchedItems = remember(watchedUiState.items, isTraktProgressActive) {
        if (isTraktProgressActive) emptyList() else watchedUiState.items
    }

    val latestCompletedBySeries = remember(effectiveWatchProgressEntries, effectiveWatchedItems, continueWatchingPreferences.upNextFromFurthestEpisode) {
        WatchingState.latestCompletedBySeries(
            progressEntries = effectiveWatchProgressEntries,
            watchedItems = effectiveWatchedItems,
            preferFurthestEpisode = continueWatchingPreferences.upNextFromFurthestEpisode,
        )
    }
    val completedSeriesCandidates = remember(latestCompletedBySeries) {
        latestCompletedBySeries.map { (content, completed) ->
            CompletedSeriesCandidate(
                content = content,
                seasonNumber = completed.seasonNumber,
                episodeNumber = completed.episodeNumber,
                markedAtEpochMs = completed.markedAtEpochMs,
            )
        }
    }
    val completedSeriesContentIds = remember(completedSeriesCandidates) {
        completedSeriesCandidates.mapTo(mutableSetOf()) { candidate -> candidate.content.id }
    }
    val completedSeriesCandidatesById = remember(completedSeriesCandidates) {
        completedSeriesCandidates.associateBy { candidate -> candidate.content.id }
    }
    val visibleContinueWatchingEntries = remember(
        effectiveWatchProgressEntries,
        latestCompletedBySeries,
    ) {
        WatchingState.visibleContinueWatchingEntries(
            progressEntries = effectiveWatchProgressEntries,
            latestCompletedBySeries = latestCompletedBySeries,
        )
    }
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val activeProfileId = profileState.activeProfile?.profileIndex ?: 1

    var nextUpItemsBySeries by remember(activeProfileId, effectiveWatchProgressSource) {
        mutableStateOf<Map<String, Pair<Long, ContinueWatchingItem>>>(emptyMap())
    }

    val todayIsoDateForContinueWatching = CurrentDateProvider.todayIsoDate()
    val cacheSnapshot by ContinueWatchingEnrichmentCache.snapshots.collectAsStateWithLifecycle()
    val cacheGeneration by ContinueWatchingEnrichmentCache.generation.collectAsStateWithLifecycle()
    val latestCacheSnapshot by rememberUpdatedState(cacheSnapshot)
    LaunchedEffect(activeProfileId, effectiveWatchProgressSource) {
        ContinueWatchingEnrichmentCache.loadProfile(activeProfileId, effectiveWatchProgressSource)
        continueWatchingRefreshGeneration += 1L
        WatchProgressRepository.requestMetadataRefresh()
    }
    LaunchedEffect(activeProfileId, effectiveWatchProgressSource, networkStatusUiState.condition) {
        if (networkStatusUiState.condition != NetworkCondition.Online) return@LaunchedEffect
        while (true) {
            delay(CONTINUE_WATCHING_REVALIDATION_TICK_MS)
            continueWatchingRefreshGeneration += 1L
            WatchProgressRepository.requestMetadataRefresh()
        }
    }
    val cachedSnapshots = if (
        cacheSnapshot.profileId == activeProfileId &&
        cacheSnapshot.source == effectiveWatchProgressSource &&
        cacheSnapshot.generation == cacheGeneration
    ) {
        cacheSnapshot
    } else {
        ContinueWatchingEnrichmentSnapshot()
    }
    val cachedNextUpItems = remember(
        cachedSnapshots.nextUp,
        continueWatchingPreferences.dismissedNextUpKeys,
        completedSeriesContentIds,
        completedSeriesCandidatesById,
        isTraktProgressActive,
        continueWatchingPreferences.showUnairedNextUp,
        watchedUiState.isLoaded,
        todayIsoDateForContinueWatching,
        releaseNotificationsUiState.timezoneId,
        continueWatchingNowEpochMs,
    ) {
        cachedSnapshots.nextUp.mapNotNull { cached ->
            if (
                !isTraktProgressActive &&
                watchedUiState.isLoaded &&
                cached.contentId !in completedSeriesContentIds
            ) {
                return@mapNotNull null
            }
            val completedSeed = completedSeriesCandidatesById[cached.contentId]
            if (completedSeed != null && !cached.matchesCompletedSeed(completedSeed)) {
                return@mapNotNull null
            }
            if (nextUpDismissKey(cached.contentId, cached.seedSeason, cached.seedEpisode) in continueWatchingPreferences.dismissedNextUpKeys) {
                return@mapNotNull null
            }
            val cachedIsAvailable = cached.released?.let { released ->
                isReleasedByContinueWatchingNotificationDay(
                    todayIsoDate = todayIsoDateForContinueWatching,
                    releasedDate = released,
                )
            } ?: cached.hasAired
            if (!cachedIsAvailable && !continueWatchingPreferences.showUnairedNextUp) {
                return@mapNotNull null
            }
            val item = cached.toContinueWatchingItem(
                timezoneId = releaseNotificationsUiState.timezoneId,
                nowEpochMs = continueWatchingNowEpochMs,
            ) ?: return@mapNotNull null
            val sortTimestamp = if (item.isReleaseAlert) {
                item.releaseEpochMs ?: parseEpisodeReleaseEpochMs(item.released) ?: cached.sortTimestamp
            } else {
                cached.sortTimestamp
            }
            cached.contentId to (sortTimestamp to item)
        }.toMap()
    }
    val cachedInProgressItems = remember(cachedSnapshots.inProgress) {
        cachedSnapshots.inProgress.associate { cached ->
            cached.videoId to cached.toContinueWatchingItem()
        }
    }

    val effectivNextUpItems = remember(
        nextUpItemsBySeries,
        cachedNextUpItems,
        continueWatchingPreferences.dismissedNextUpKeys,
    ) {
        val liveNextUpItems = nextUpItemsBySeries.filterValues { (_, item) ->
            val completedSeed = completedSeriesCandidatesById[item.parentMetaId]
            val matchesCurrentCompletion = completedSeed == null || item.matchesCompletedSeed(completedSeed)
            matchesCurrentCompletion && nextUpDismissKey(
                item.parentMetaId,
                item.nextUpSeedSeasonNumber,
                item.nextUpSeedEpisodeNumber,
            ) !in continueWatchingPreferences.dismissedNextUpKeys
        }
        cachedNextUpItems.toMutableMap().apply {
            liveNextUpItems.forEach { (contentId, pair) ->
                val cachedItem = cachedNextUpItems[contentId]?.second
                this[contentId] = pair.first to pair.second.withFallbackMetadata(cachedItem)
            }
        }
    }

    val continueWatchingItems = remember(
        visibleContinueWatchingEntries,
        cachedInProgressItems,
        effectivNextUpItems,
        continueWatchingPreferences.sortMode,
        continueWatchingNowEpochMs,
    ) {
        buildHomeContinueWatchingItems(
            visibleEntries = visibleContinueWatchingEntries,
            cachedInProgressByVideoId = cachedInProgressItems,
            nextUpItemsBySeries = effectivNextUpItems,
            sortMode = continueWatchingPreferences.sortMode,
            todayIsoDate = todayIsoDateForContinueWatching,
            nowEpochMs = continueWatchingNowEpochMs,
        )
    }

    LaunchedEffect(effectivNextUpItems, continueWatchingNowEpochMs) {
        val nextBoundary = effectivNextUpItems.values
            .asSequence()
            .mapNotNull { (_, item) -> item.releaseEpochMs }
            .filter { releaseEpochMs -> releaseEpochMs > continueWatchingNowEpochMs }
            .minOrNull()
            ?: return@LaunchedEffect
        delay(nextReleaseBoundaryDelayMs(continueWatchingNowEpochMs, nextBoundary))
        continueWatchingNowEpochMs = maxOf(WatchProgressClock.nowEpochMs(), nextBoundary)
    }
    val availableManifests = remember(addonsUiState.addons) {
        addonsUiState.addons.mapNotNull { addon -> addon.manifest }
    }

    val metaProviderKey = remember(availableManifests) {
        availableManifests
            .filter { manifest -> manifest.resources.any { resource -> resource.name == "meta" } }
            .map { manifest -> manifest.transportUrl }
            .sorted()
    }

    val catalogRefreshKey = remember(availableManifests) {
        availableManifests
            .map { manifest ->
                buildString {
                    append(manifest.transportUrl)
                    append(':')
                    append(manifest.catalogs.joinToString(separator = ",") { catalog ->
                        "${catalog.type}:${catalog.id}:${catalog.extra.count { it.isRequired }}"
                    })
                }
            }
            .sorted()
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(catalogRefreshKey) {
        if (catalogRefreshKey.isEmpty()) return@LaunchedEffect
        HomeCatalogSettingsRepository.syncCatalogs(addonsUiState.addons)
        HomeRepository.refresh(addonsUiState.addons)
    }

    DisposableEffect(
        lifecycleOwner,
        catalogRefreshKey,
        addonsUiState.addons,
        homeUiState.isLoading,
        homeUiState.sections.size,
        homeHeroItems.size,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            continueWatchingNowEpochMs = WatchProgressClock.nowEpochMs()
            continueWatchingRefreshGeneration += 1L
            WatchProgressRepository.requestMetadataRefresh()
            if (catalogRefreshKey.isEmpty()) return@LifecycleEventObserver
            if (homeUiState.isLoading || homeUiState.sections.isNotEmpty()) {
                return@LifecycleEventObserver
            }

            HomeCatalogSettingsRepository.syncCatalogs(addonsUiState.addons)
            HomeRepository.recoverIfEmpty(addonsUiState.addons)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(collections) {
        HomeCatalogSettingsRepository.syncCollections(collections)
    }

    LaunchedEffect(
        completedSeriesCandidates,
        metaProviderKey,
        continueWatchingPreferences.showUnairedNextUp,
        releaseNotificationsUiState.timezoneId,
        continueWatchingRefreshGeneration,
        activeProfileId,
        effectiveWatchProgressSource,
        cacheGeneration,
    ) {
        val requestedProfileId = activeProfileId
        val requestedSource = effectiveWatchProgressSource
        val requestedCacheGeneration = cacheGeneration
        if (completedSeriesCandidates.isEmpty()) {
            if (ContinueWatchingEnrichmentCache.isCurrent(requestedCacheGeneration)) {
                nextUpItemsBySeries = emptyMap()
            }
            return@LaunchedEffect
        }

        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val nowEpochMs = WatchProgressClock.nowEpochMs()
        val currentCache = latestCacheSnapshot.takeIf {
            it.profileId == requestedProfileId &&
                it.source == requestedSource &&
                it.generation == requestedCacheGeneration
        }

        fun resolveFromMeta(
            completedEntry: CompletedSeriesCandidate,
            meta: com.nuvio.app.features.details.MetaDetails,
            metadataCheckedAtEpochMs: Long,
        ): NextUpEnrichmentResult? {
            val nextEpisode = meta.nextReleasedEpisodeAfter(
                seasonNumber = completedEntry.seasonNumber,
                episodeNumber = completedEntry.episodeNumber,
                todayIsoDate = todayIsoDate,
                showUnairedNextUp = continueWatchingPreferences.showUnairedNextUp,
            ) ?: return null
            if (!continueWatchingPreferences.showUnairedNextUp &&
                !isReleasedByContinueWatchingNotificationDay(
                    todayIsoDate = todayIsoDate,
                    releasedDate = nextEpisode.released,
                )
            ) {
                return null
            }
            val releaseEpochMs = resolvePreciseContinueWatchingReleaseEpochMs(
                rawReleaseValue = nextEpisode.released,
            ) { rawReleaseValue ->
                EpisodeReleaseNotificationPlatform.resolveReleaseTriggerEpochMs(
                    rawReleaseValue = rawReleaseValue,
                    timezoneId = releaseNotificationsUiState.timezoneId,
                )
            }
            val item = completedEntry.toContinueWatchingSeed(meta)
                .toUpNextContinueWatchingItem(
                    nextEpisode = nextEpisode,
                    releaseEpochMs = releaseEpochMs,
                )
                .copy(metadataCheckedAtEpochMs = metadataCheckedAtEpochMs)
            if (
                nextUpDismissKey(
                    item.parentMetaId,
                    item.nextUpSeedSeasonNumber,
                    item.nextUpSeedEpisodeNumber,
                ) in continueWatchingPreferences.dismissedNextUpKeys
            ) {
                return null
            }
            return NextUpEnrichmentResult(
                contentId = completedEntry.content.id,
                sortTimestamp = completedEntry.markedAtEpochMs,
                item = item,
                metadataCheckedAtEpochMs = metadataCheckedAtEpochMs,
            )
        }

        val orderedCandidates = completedSeriesCandidates
            .sortedByDescending(CompletedSeriesCandidate::markedAtEpochMs)
        val optimisticResults = orderedCandidates.mapNotNull { completedEntry ->
            val cached = currentCache?.nextUp?.firstOrNull { item ->
                item.contentId == completedEntry.content.id &&
                    item.matchesCompletedSeed(completedEntry)
            }
            cached?.toContinueWatchingItem(
                timezoneId = releaseNotificationsUiState.timezoneId,
                nowEpochMs = nowEpochMs,
            )?.let { cachedItem ->
                NextUpEnrichmentResult(
                    contentId = completedEntry.content.id,
                    sortTimestamp = completedEntry.markedAtEpochMs,
                    item = cachedItem,
                    metadataCheckedAtEpochMs = cached.metadataCheckedAtEpochMs,
                )
            } ?: MetaDetailsRepository.peek(
                type = completedEntry.content.type,
                id = completedEntry.content.id,
            )?.let { cachedMeta ->
                resolveFromMeta(
                    completedEntry = completedEntry,
                    meta = cachedMeta,
                    metadataCheckedAtEpochMs = cached?.metadataCheckedAtEpochMs ?: 0L,
                )
            }
        }
        if (ContinueWatchingEnrichmentCache.isCurrent(requestedCacheGeneration)) {
            val currentCandidates = orderedCandidates.associateBy { candidate -> candidate.content.id }
            val retainedResults = nextUpItemsBySeries.mapNotNull { (contentId, pair) ->
                val candidate = currentCandidates[contentId] ?: return@mapNotNull null
                if (!pair.second.matchesCompletedSeed(candidate)) return@mapNotNull null
                NextUpEnrichmentResult(
                    contentId = contentId,
                    sortTimestamp = pair.first,
                    item = pair.second,
                    metadataCheckedAtEpochMs = pair.second.metadataCheckedAtEpochMs,
                )
            }
            nextUpItemsBySeries = mergeNextUpEnrichmentResults(
                current = retainedResults,
                incoming = optimisticResults,
            ).associate { result ->
                result.contentId to (result.sortTimestamp to result.item)
            }
        }

        if (metaProviderKey.isEmpty()) return@LaunchedEffect

        val semaphore = Semaphore(4)
        val pendingResults = orderedCandidates.map { completedEntry ->
            async {
                completedEntry.content.id to semaphore.withPermit {
                    val cached = currentCache?.nextUp?.firstOrNull { item ->
                        item.contentId == completedEntry.content.id &&
                            item.matchesCompletedSeed(completedEntry)
                    }
                    if (
                        cached != null &&
                        !cached.needsContinueWatchingMetadataRefresh(nowEpochMs)
                    ) {
                        val cachedItem = cached.toContinueWatchingItem(
                            timezoneId = releaseNotificationsUiState.timezoneId,
                            nowEpochMs = nowEpochMs,
                        ) ?: return@withPermit null
                        return@withPermit NextUpEnrichmentResult(
                            contentId = completedEntry.content.id,
                            sortTimestamp = completedEntry.markedAtEpochMs,
                            item = cachedItem,
                            metadataCheckedAtEpochMs = cached.metadataCheckedAtEpochMs,
                        )
                    }
                    val meta = MetaDetailsRepository.fetchFreshContinueWatchingMeta(
                        type = completedEntry.content.type,
                        id = completedEntry.content.id,
                    ) ?: return@withPermit cached?.toContinueWatchingItem(
                        timezoneId = releaseNotificationsUiState.timezoneId,
                        nowEpochMs = nowEpochMs,
                    )?.let { cachedItem ->
                        NextUpEnrichmentResult(
                            contentId = completedEntry.content.id,
                            sortTimestamp = completedEntry.markedAtEpochMs,
                            item = cachedItem,
                            metadataCheckedAtEpochMs = cached.metadataCheckedAtEpochMs,
                        )
                    }
                    resolveFromMeta(
                        completedEntry = completedEntry,
                        meta = meta,
                        metadataCheckedAtEpochMs = nowEpochMs,
                    )
                }
            }
        }
        val retainedResults = nextUpItemsBySeries.map { (contentId, pair) ->
            NextUpEnrichmentResult(
                contentId = contentId,
                sortTimestamp = pair.first,
                item = pair.second,
                metadataCheckedAtEpochMs = pair.second.metadataCheckedAtEpochMs,
            )
        }
        val resolvedResultsByContentId = mergeNextUpEnrichmentResults(
            current = retainedResults,
            incoming = optimisticResults,
        ).associateByTo(mutableMapOf(), NextUpEnrichmentResult::contentId)
        pendingResults.forEach { pending ->
            val (contentId, result) = pending.await()
            if (!ContinueWatchingEnrichmentCache.isCurrent(requestedCacheGeneration)) {
                return@LaunchedEffect
            }
            if (result == null) {
                resolvedResultsByContentId.remove(contentId)
            } else {
                resolvedResultsByContentId[contentId] = mergeNextUpEnrichmentResult(
                    current = resolvedResultsByContentId[contentId],
                    incoming = result,
                )
            }
            nextUpItemsBySeries = resolvedResultsByContentId.values.associate { resolved ->
                resolved.contentId to (resolved.sortTimestamp to resolved.item)
            }
        }
        val results = resolvedResultsByContentId.values.toList()
        val nextUpCache = results.map { result ->
            val item = result.item
            CachedNextUpItem(
                contentId = result.contentId,
                contentType = item.parentMetaType,
                name = item.title,
                poster = item.poster,
                backdrop = item.background,
                logo = item.logo,
                videoId = item.videoId,
                season = item.seasonNumber,
                episode = item.episodeNumber,
                episodeTitle = item.episodeTitle,
                episodeThumbnail = item.episodeThumbnail,
                pauseDescription = item.pauseDescription,
                released = item.released,
                releaseTimingRuleVersion = CurrentEpisodeReleaseTimingRuleVersion,
                releaseEpochMs = item.releaseEpochMs,
                hasAired = item.released?.let { released ->
                    isReleasedByContinueWatchingNotificationDay(
                        todayIsoDate = todayIsoDate,
                        releasedDate = released,
                    )
                } ?: true,
                lastWatched = result.sortTimestamp,
                sortTimestamp = result.sortTimestamp,
                seedSeason = item.nextUpSeedSeasonNumber,
                seedEpisode = item.nextUpSeedEpisodeNumber,
                seedLastUpdatedEpochMs = item.nextUpSeedLastUpdatedEpochMs,
                isReleaseAlert = item.isReleaseAlert,
                isNewSeasonRelease = item.isNewSeasonRelease,
                metadataCheckedAtEpochMs = result.metadataCheckedAtEpochMs,
            )
        }
        val inProgressCache = visibleContinueWatchingEntries.map { entry ->
            CachedInProgressItem(
                contentId = entry.parentMetaId,
                contentType = entry.contentType,
                name = entry.title,
                poster = entry.poster,
                backdrop = entry.background,
                logo = entry.logo,
                videoId = entry.videoId,
                season = entry.seasonNumber,
                episode = entry.episodeNumber,
                episodeTitle = entry.episodeTitle,
                episodeThumbnail = entry.episodeThumbnail,
                pauseDescription = entry.pauseDescription,
                position = entry.lastPositionMs,
                duration = entry.durationMs,
                lastWatched = entry.lastUpdatedEpochMs,
                progressPercent = entry.progressPercent,
                metadataCheckedAtEpochMs = entry.metadataCheckedAtEpochMs,
            )
        }
        val saved = ContinueWatchingEnrichmentCache.saveSnapshots(
            profileId = requestedProfileId,
            source = requestedSource,
            generation = requestedCacheGeneration,
            nextUp = nextUpCache,
            inProgress = inProgressCache,
            savedAtEpochMs = nowEpochMs,
        )
        if (saved) {
            nextUpItemsBySeries = results.associate { result ->
                result.contentId to (result.sortTimestamp to result.item)
            }
        }
    }

    val showHeroSlot = homeSettingsUiState.heroEnabled
    val isResolvingHeroSources = profileSpotlightState.isLoading
    val showHeroSkeleton = showHeroSlot &&
        homeHeroItems.isEmpty() &&
        isResolvingHeroSources
    val homeBackgroundColor = MaterialTheme.colorScheme.background
    var firstCatalogReported by remember { mutableStateOf(false) }

    LaunchedEffect(homeUiState.sections.firstOrNull()?.key, onFirstCatalogRendered) {
        if (firstCatalogReported || homeUiState.sections.isEmpty()) return@LaunchedEffect
        firstCatalogReported = true
        onFirstCatalogRendered?.invoke()
    }

    val visibleCollections = remember(collections) {
        collections.filter { it.folders.isNotEmpty() }
    }
    val collectionsMap = remember(visibleCollections) {
        visibleCollections.associateBy { "collection_${it.id}" }
    }
    val sectionsMap = remember(homeUiState.sections) {
        homeUiState.sections.associateBy(HomeCatalogSection::key)
    }
    val enabledHomeItems = remember(homeSettingsUiState.items) {
        homeSettingsUiState.items.filter { it.enabled }
    }
    val firstAddonCatalogGroupLastKey = remember(enabledHomeItems, sectionsMap) {
        val renderableAddonItems = enabledHomeItems.filter { item ->
            !item.isCollection && sectionsMap[item.key]?.items?.isNotEmpty() == true
        }
        val firstAddonName = renderableAddonItems.firstOrNull()?.addonName
        renderableAddonItems
            .takeWhile { item -> item.addonName == firstAddonName }
            .lastOrNull()
            ?.key
    }
    val hasRenderableCollectionRows = remember(enabledHomeItems, collectionsMap) {
        enabledHomeItems.any { item ->
            item.isCollection && collectionsMap[item.key] != null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(homeBackgroundColor),
    ) {
        val homeSectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value)
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidth.value)
        val nativeBottomNavigationOverlayHeight =
            if (LocalNuvioBottomNavigationOverlayPadding.current > 0.dp) {
                nuvioSafeBottomPadding()
            } else {
                0.dp
            }
        val mobileHeroBelowSectionHeightHint = remember(
            maxWidth.value,
            continueWatchingPreferences.isVisible,
            continueWatchingPreferences.style,
            continueWatchingItems.isNotEmpty(),
            continueWatchingLayout,
            nativeBottomNavigationOverlayHeight,
        ) {
            heroMobileBelowSectionHeightHint(
                maxWidthDp = maxWidth.value,
                continueWatchingVisible = continueWatchingPreferences.isVisible,
                hasContinueWatchingItems = continueWatchingItems.isNotEmpty(),
                continueWatchingStyle = continueWatchingPreferences.style,
                continueWatchingLayout = continueWatchingLayout,
                bottomNavigationOverlayHeight = nativeBottomNavigationOverlayHeight,
            )
        }

        val heroStretchState = rememberHeroStretchState(homeListState)
        val heroStretchModifier = if (showHeroSlot) {
            Modifier.nestedScroll(heroStretchState.nestedScrollConnection)
        } else {
            Modifier
        }

        NuvioScreen(
            modifier = Modifier
                .fillMaxSize()
                .then(heroStretchModifier),
            horizontalPadding = 0.dp,
            topPadding = if (showHeroSlot) 0.dp else null,
            backgroundColor = homeBackgroundColor,
            listState = homeListState,
        ) {
            if (showHeroSlot) {
                item {
                    when {
                        showHeroSkeleton -> HomeSkeletonHero(
                            modifier = Modifier,
                            viewportHeight = maxHeight,
                            mobileBelowSectionHeightHint = mobileHeroBelowSectionHeightHint,
                        )

                        homeHeroItems.isNotEmpty() -> HomeHeroSection(
                            items = homeHeroItems,
                            modifier = Modifier,
                            viewportHeight = maxHeight,
                            mobileBelowSectionHeightHint = mobileHeroBelowSectionHeightHint,
                            listState = homeListState,
                            backgroundColor = homeBackgroundColor,
                            stretchPx = { heroStretchState.stretchPx },
                            onItemClick = onPosterClick,
                        )

                        else -> HomeHeroReservedSpace(
                            modifier = Modifier,
                            viewportHeight = maxHeight,
                            mobileBelowSectionHeightHint = mobileHeroBelowSectionHeightHint,
                        )
                    }
                }
            }

            when {
                addonsUiState.addons.none { it.manifest != null } && !hasRenderableCollectionRows -> {
                    if (continueWatchingPreferences.isVisible && continueWatchingItems.isNotEmpty()) {
                        item {
                            HomeContinueWatchingSection(
                                items = continueWatchingItems,
                                style = continueWatchingPreferences.style,
                                useEpisodeThumbnails = continueWatchingPreferences.useEpisodeThumbnails,
                                useClearlogo = continueWatchingPreferences.useClearlogo,
                                blurNextUp = continueWatchingPreferences.blurNextUp,
                                modifier = Modifier.padding(bottom = 12.dp),
                                sectionPadding = homeSectionPadding,
                                layout = continueWatchingLayout,
                                onItemClick = onContinueWatchingClick,
                                onItemLongPress = onContinueWatchingLongPress,
                            )
                        }
                    }
                    item {
                        HomeEmptyStateCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            title = stringResource(Res.string.compose_search_empty_no_active_addons_title),
                            message = stringResource(Res.string.home_empty_no_active_addons_message),
                        )
                    }
                }

                homeUiState.isLoading && homeUiState.sections.isEmpty() && !hasRenderableCollectionRows -> {
                    if (continueWatchingPreferences.isVisible && continueWatchingItems.isNotEmpty()) {
                        item {
                            HomeContinueWatchingSection(
                                items = continueWatchingItems,
                                style = continueWatchingPreferences.style,
                                useEpisodeThumbnails = continueWatchingPreferences.useEpisodeThumbnails,
                                useClearlogo = continueWatchingPreferences.useClearlogo,
                                blurNextUp = continueWatchingPreferences.blurNextUp,
                                modifier = Modifier.padding(bottom = 12.dp),
                                sectionPadding = homeSectionPadding,
                                layout = continueWatchingLayout,
                                onItemClick = onContinueWatchingClick,
                                onItemLongPress = onContinueWatchingLongPress,
                            )
                        }
                    }
                    items(3) {
                        HomeSkeletonRow(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                homeUiState.sections.isEmpty() && homeHeroItems.isEmpty() &&
                    (!continueWatchingPreferences.isVisible || continueWatchingItems.isEmpty()) &&
                    !hasRenderableCollectionRows -> {
                    item {
                        if (networkStatusUiState.isOfflineLike) {
                            NuvioNetworkOfflineCard(
                                condition = networkStatusUiState.condition,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onRetry = {
                                    NetworkStatusRepository.requestRefresh(force = true)
                                    HomeRepository.refresh(addonsUiState.addons, force = true)
                                    ProfileSpotlightRepository.load(force = true)
                                },
                            )
                        } else {
                            HomeEmptyStateCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                title = stringResource(Res.string.home_empty_no_rows_title),
                                message = homeUiState.errorMessage
                                    ?: stringResource(Res.string.home_empty_no_rows_message),
                            )
                        }
                    }
                }

                else -> {
                    if (continueWatchingPreferences.isVisible && continueWatchingItems.isNotEmpty()) {
                        item {
                            HomeContinueWatchingSection(
                                items = continueWatchingItems,
                                style = continueWatchingPreferences.style,
                                useEpisodeThumbnails = continueWatchingPreferences.useEpisodeThumbnails,
                                useClearlogo = continueWatchingPreferences.useClearlogo,
                                blurNextUp = continueWatchingPreferences.blurNextUp,
                                modifier = Modifier.padding(bottom = 12.dp),
                                sectionPadding = homeSectionPadding,
                                layout = continueWatchingLayout,
                                onItemClick = onContinueWatchingClick,
                                onItemLongPress = onContinueWatchingLongPress,
                            )
                        }
                    }

                    var featuredProductionsInserted = false
                    enabledHomeItems.forEach { settingsItem ->
                        if (settingsItem.isCollection) {
                            val collection = collectionsMap[settingsItem.key]
                            if (collection != null) {
                                item(key = settingsItem.key) {
                                    HomeCollectionRowSection(
                                        collection = collection,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        sectionPadding = homeSectionPadding,
                                        animateGifs = animateCollectionGifs,
                                        onFolderClick = onFolderClick,
                                    )
                                }
                            }
                        } else {
                            val section = sectionsMap[settingsItem.key]
                            if (section != null && section.items.isNotEmpty()) {
                                item(key = settingsItem.key) {
                                    HomeCatalogRowSection(
                                        section = section,
                                        entries = section.items.take(HOME_CATALOG_PREVIEW_LIMIT),
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        sectionPadding = homeSectionPadding,
                                        onViewAllClick = if (section.canOpenCatalog(HOME_CATALOG_PREVIEW_LIMIT)) {
                                            onCatalogClick?.let { { it(section) } }
                                        } else {
                                            null
                                        },
                                        watchedKeys = watchedUiState.watchedKeys,
                                        onPosterClick = onPosterClick,
                                        onPosterLongClick = onPosterLongClick,
                                    )
                                }
                                if (!featuredProductionsInserted &&
                                    settingsItem.key == firstAddonCatalogGroupLastKey &&
                                    onFeaturedProductionClick != null &&
                                    onFeaturedProductionsViewAllClick != null
                                ) {
                                    item(key = "featured-productions-home-rail") {
                                        HomeFeaturedProductionsSection(
                                            entries = featuredProductionEntities.take(HOME_FEATURED_PRODUCTIONS_LIMIT),
                                            modifier = Modifier.padding(bottom = 12.dp),
                                            sectionPadding = homeSectionPadding,
                                            onEntityClick = onFeaturedProductionClick,
                                            onViewAllClick = onFeaturedProductionsViewAllClick,
                                        )
                                    }
                                    featuredProductionsInserted = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val HOME_CATALOG_PREVIEW_LIMIT = 18
private const val HOME_FEATURED_PRODUCTIONS_LIMIT = 20
private const val HOME_SPOTLIGHT_ITEM_LIMIT = 8
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val CONTINUE_WATCHING_REVALIDATION_TICK_MS = 15L * 60L * 1000L

internal fun filterEntriesForTraktContinueWatchingWindow(
    entries: List<WatchProgressEntry>,
    isTraktProgressActive: Boolean,
    daysCap: Int,
    nowEpochMs: Long,
): List<WatchProgressEntry> {
    if (!isTraktProgressActive) return entries
    val normalizedDaysCap = normalizeTraktContinueWatchingDaysCap(daysCap)
    if (normalizedDaysCap == TRAKT_CONTINUE_WATCHING_DAYS_CAP_ALL) return entries

    val cutoffMs = nowEpochMs - (normalizedDaysCap.toLong() * MILLIS_PER_DAY)
    return entries.filter { entry -> entry.lastUpdatedEpochMs >= cutoffMs }
}

private fun heroMobileBelowSectionHeightHint(
    maxWidthDp: Float,
    continueWatchingVisible: Boolean,
    hasContinueWatchingItems: Boolean,
    continueWatchingStyle: ContinueWatchingSectionStyle,
    continueWatchingLayout: ContinueWatchingLayout,
    bottomNavigationOverlayHeight: Dp,
): Dp? {
    if (maxWidthDp >= 600f || !continueWatchingVisible || !hasContinueWatchingItems) return null

    val sectionHeight = when (continueWatchingStyle) {
        ContinueWatchingSectionStyle.Wide -> continueWatchingLayout.wideCardHeight + 56.dp
        ContinueWatchingSectionStyle.Poster ->
            continueWatchingLayout.posterCardHeight + continueWatchingLayout.posterTitleBlockHeight + 70.dp
    }
    return sectionHeight + bottomNavigationOverlayHeight
}

internal fun buildHomeContinueWatchingItems(
    visibleEntries: List<WatchProgressEntry>,
    cachedInProgressByVideoId: Map<String, ContinueWatchingItem> = emptyMap(),
    nextUpItemsBySeries: Map<String, Pair<Long, ContinueWatchingItem>>,
    sortMode: ContinueWatchingSortMode = ContinueWatchingSortMode.DEFAULT,
    todayIsoDate: String = "",
    nowEpochMs: Long = WatchProgressClock.nowEpochMs(),
): List<ContinueWatchingItem> {
    val inProgressSeriesIds = visibleEntries
        .asSequence()
        .filter { entry -> entry.parentMetaType.isSeriesTypeForContinueWatching() }
        .map { entry -> entry.parentMetaId }
        .filter(String::isNotBlank)
        .toSet()

    val candidates = buildList {
        addAll(
            visibleEntries.map { entry ->
                val liveItem = entry.toContinueWatchingItem()
                HomeContinueWatchingCandidate(
                    lastUpdatedEpochMs = entry.lastUpdatedEpochMs,
                    item = liveItem.withFallbackMetadata(
                        cachedInProgressByVideoId[entry.videoId]
                            ?: cachedInProgressByVideoId.values.firstOrNull { cached ->
                                cached.canonicalIdentity() == liveItem.canonicalIdentity()
                            },
                    ),
                    isProgressEntry = true,
                )
            },
        )
        addAll(
            nextUpItemsBySeries.values.mapNotNull { (lastUpdatedEpochMs, item) ->
                if (item.parentMetaId in inProgressSeriesIds) return@mapNotNull null
                val currentItem = item.withReleaseAlertState(nowEpochMs)
                val sortTimestamp = if (currentItem.isReleaseAlert) {
                    currentItem.releaseEpochMs ?: parseEpisodeReleaseEpochMs(currentItem.released) ?: lastUpdatedEpochMs
                } else {
                    lastUpdatedEpochMs
                }
                HomeContinueWatchingCandidate(
                    lastUpdatedEpochMs = sortTimestamp,
                    item = currentItem,
                    isProgressEntry = false,
                )
            },
        )
    }

    val deduplicated = candidates
        .filter { candidate -> candidate.item.shouldDisplayInContinueWatching() }
        .groupBy { candidate -> candidate.item.canonicalIdentity() }
        .values
        .mapNotNull { duplicates ->
            duplicates.maxWithOrNull(
                compareBy<HomeContinueWatchingCandidate> { candidate -> candidate.isProgressEntry }
                    .thenBy { candidate -> candidate.item.metadataQualityScore() }
                    .thenBy { candidate -> candidate.lastUpdatedEpochMs },
            )
        }
        .sortedByDescending { candidate -> candidate.lastUpdatedEpochMs }

    return when (sortMode) {
        ContinueWatchingSortMode.DEFAULT -> deduplicated.map(HomeContinueWatchingCandidate::item)
        ContinueWatchingSortMode.STREAMING_STYLE -> applyStreamingStyleSort(
            candidates = deduplicated,
            todayIsoDate = todayIsoDate,
        )
    }
}

private fun applyStreamingStyleSort(
    candidates: List<HomeContinueWatchingCandidate>,
    todayIsoDate: String,
): List<ContinueWatchingItem> {
    val (released, unreleased) = candidates.partition { candidate ->
        val item = candidate.item
        if (!item.isNextUp) {
            true // in-progress items are always "released"
        } else {
            val itemReleased = item.released
            if (itemReleased.isNullOrBlank() || todayIsoDate.isBlank()) {
                true // no date info → treat as released
            } else {
                isReleasedByContinueWatchingNotificationDay(
                    todayIsoDate = todayIsoDate,
                    releasedDate = itemReleased,
                )
            }
        }
    }

    // Released: most recently watched first (already sorted by dedup pass)
    val sortedReleased = released.map(HomeContinueWatchingCandidate::item)

    // Unaired: soonest air date first; unknown dates go to the end
    val sortedUnreleased = unreleased
        .sortedWith { a, b ->
            val dateA = continueWatchingNotificationReleaseDayIso(a.item.released)
            val dateB = continueWatchingNotificationReleaseDayIso(b.item.released)
            when {
                dateA == null && dateB == null -> 0
                dateA == null -> 1
                dateB == null -> -1
                else -> dateA.compareTo(dateB)
            }
        }
        .map(HomeContinueWatchingCandidate::item)

    return sortedReleased + sortedUnreleased
}

private fun isReleasedByContinueWatchingNotificationDay(
    todayIsoDate: String,
    releasedDate: String?,
): Boolean {
    if (todayIsoDate.isBlank()) return true
    val releaseDay = continueWatchingNotificationReleaseDayIso(releasedDate)
        ?: return isReleasedBy(todayIsoDate = todayIsoDate, releasedDate = releasedDate)
    return releaseDay <= todayIsoDate
}

internal fun continueWatchingNotificationReleaseDayIso(releasedDate: String?): String? {
    val rawRelease = releasedDate?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val notificationBase = normalizedContinueWatchingLocalDateTime(rawRelease)
        ?: notificationBaseLocalDateTimeForDateOnly(rawRelease)
        ?: return rawRelease.substringBefore('T').substringBefore(' ').takeIf { it.length == 10 }
    return addHoursToContinueWatchingLocalDateTime(
        localDateTime = notificationBase,
        hours = EpisodeReleaseNotificationDelayHours.toInt(),
    )?.take(10)
}

private fun notificationBaseLocalDateTimeForDateOnly(value: String): String? {
    val normalizedDate = value.trim().substringBefore('T').substringBefore(' ')
    if (normalizedDate.length != 10) return null
    val hour = EpisodeReleaseNotificationHour.toString().padStart(2, '0')
    val minute = EpisodeReleaseNotificationMinute.toString().padStart(2, '0')
    val candidate = "${normalizedDate}T$hour:$minute:00"
    return candidate.takeIf(::isValidContinueWatchingLocalDateTime)
}

private fun normalizedContinueWatchingLocalDateTime(value: String): String? {
    if (!value.contains('T') && !value.contains(' ')) return null
    val normalized = value.trim().replace(' ', 'T')
    val withoutZone = stripContinueWatchingZoneSuffix(normalized)
    val dateTime = when {
        withoutZone.length >= 19 -> withoutZone.take(19)
        withoutZone.length == 16 -> "$withoutZone:00"
        else -> return null
    }
    return dateTime.takeIf(::isValidContinueWatchingLocalDateTime)
}

private fun stripContinueWatchingZoneSuffix(value: String): String {
    val withoutZulu = if (value.endsWith("Z", ignoreCase = true)) {
        value.dropLast(1)
    } else {
        value
    }
    val plusIndex = withoutZulu.indexOf('+', startIndex = 10)
    val minusIndex = withoutZulu.indexOf('-', startIndex = 10)
    val zoneIndex = listOf(plusIndex, minusIndex)
        .filter { it >= 0 }
        .minOrNull()
        ?: return withoutZulu
    return withoutZulu.substring(0, zoneIndex)
}

private fun isValidContinueWatchingLocalDateTime(value: String): Boolean {
    if (value.length != 19) return false
    if (value[4] != '-' || value[7] != '-' || value[10] != 'T' || value[13] != ':' || value[16] != ':') return false
    val year = value.substring(0, 4).toIntOrNull() ?: return false
    val month = value.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return false
    val day = value.substring(8, 10).toIntOrNull() ?: return false
    value.substring(11, 13).toIntOrNull()?.takeIf { it in 0..23 } ?: return false
    value.substring(14, 16).toIntOrNull()?.takeIf { it in 0..59 } ?: return false
    value.substring(17, 19).toIntOrNull()?.takeIf { it in 0..59 } ?: return false
    return day in 1..daysInMonthForContinueWatching(year, month)
}

private fun addHoursToContinueWatchingLocalDateTime(
    localDateTime: String,
    hours: Int,
): String? {
    if (!isValidContinueWatchingLocalDateTime(localDateTime)) return null

    var year = localDateTime.substring(0, 4).toIntOrNull() ?: return null
    var month = localDateTime.substring(5, 7).toIntOrNull() ?: return null
    var day = localDateTime.substring(8, 10).toIntOrNull() ?: return null
    var hour = localDateTime.substring(11, 13).toIntOrNull() ?: return null
    val rest = localDateTime.substring(13)

    hour += hours
    while (hour >= 24) {
        hour -= 24
        day += 1
        val maxDay = daysInMonthForContinueWatching(year, month)
        if (day > maxDay) {
            day = 1
            month += 1
            if (month > 12) {
                month = 1
                year += 1
            }
        }
    }

    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}$rest"
}

private fun daysInMonthForContinueWatching(year: Int, month: Int): Int =
    when (month) {
        2 -> if (isLeapYearForContinueWatching(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun isLeapYearForContinueWatching(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private data class CompletedSeriesCandidate(
    val content: WatchingContentRef,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val markedAtEpochMs: Long,
)

private data class HomeContinueWatchingCandidate(
    val lastUpdatedEpochMs: Long,
    val item: ContinueWatchingItem,
    val isProgressEntry: Boolean,
)

private data class NextUpEnrichmentResult(
    val contentId: String,
    val sortTimestamp: Long,
    val item: ContinueWatchingItem,
    val metadataCheckedAtEpochMs: Long,
)

private fun CachedNextUpItem.matchesCompletedSeed(candidate: CompletedSeriesCandidate): Boolean =
    matchesNextUpCompletionSeed(
        seedSeason = seedSeason,
        seedEpisode = seedEpisode,
        seedTimestamp = seedLastUpdatedEpochMs,
        completedSeason = candidate.seasonNumber,
        completedEpisode = candidate.episodeNumber,
        completedTimestamp = candidate.markedAtEpochMs,
    )

private fun ContinueWatchingItem.matchesCompletedSeed(candidate: CompletedSeriesCandidate): Boolean =
    matchesNextUpCompletionSeed(
        seedSeason = nextUpSeedSeasonNumber,
        seedEpisode = nextUpSeedEpisodeNumber,
        seedTimestamp = nextUpSeedLastUpdatedEpochMs,
        completedSeason = candidate.seasonNumber,
        completedEpisode = candidate.episodeNumber,
        completedTimestamp = candidate.markedAtEpochMs,
    )

private fun CompletedSeriesCandidate.toContinueWatchingSeed(meta: com.nuvio.app.features.details.MetaDetails) =
    WatchProgressEntry(
        contentType = content.type,
        parentMetaId = content.id,
        parentMetaType = content.type,
        videoId = "${content.id}:${seasonNumber}:${episodeNumber}",
        title = meta.name,
        logo = meta.logo,
        poster = meta.poster,
        background = meta.background,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        lastPositionMs = 0L,
        durationMs = 0L,
        lastUpdatedEpochMs = markedAtEpochMs,
        isCompleted = true,
    )

private fun ContinueWatchingItem.shouldDisplayInContinueWatching(): Boolean =
    isNextUp || progressFraction < 0.995f

private fun CachedNextUpItem.toContinueWatchingItem(
    timezoneId: String,
    nowEpochMs: Long,
): ContinueWatchingItem? {
    val resolvedReleaseEpochMs = resolveCachedAdjustedReleaseEpochMs(
        cachedReleaseEpochMs = releaseEpochMs,
        cachedTimingRuleVersion = releaseTimingRuleVersion,
    ) {
        resolvePreciseContinueWatchingReleaseEpochMs(
            rawReleaseValue = released,
        ) { rawReleaseValue ->
            EpisodeReleaseNotificationPlatform.resolveReleaseTriggerEpochMs(
                rawReleaseValue = rawReleaseValue,
                timezoneId = timezoneId,
            )
        }
    }
    val seedTimestamp = seedLastUpdatedEpochMs ?: lastWatched
    val alertState = com.nuvio.app.features.watchprogress.calculateReleaseAlertState(
        seedLastUpdatedEpochMs = seedTimestamp,
        seedSeasonNumber = seedSeason,
        nextSeasonNumber = season,
        releaseEpochMs = resolvedReleaseEpochMs,
        nowEpochMs = nowEpochMs,
    )
    return ContinueWatchingItem(
        parentMetaId = contentId,
        parentMetaType = contentType,
        videoId = videoId,
        title = name,
        subtitle = buildContinueWatchingEpisodeSubtitle(
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
        ),
        imageUrl = episodeThumbnail ?: backdrop ?: poster,
        logo = logo,
        poster = poster,
        background = backdrop,
        seasonNumber = season,
        episodeNumber = episode,
        episodeTitle = episodeTitle,
        episodeThumbnail = episodeThumbnail,
        pauseDescription = pauseDescription,
        metadataCheckedAtEpochMs = metadataCheckedAtEpochMs,
        released = released,
        releaseEpochMs = resolvedReleaseEpochMs,
        isNextUp = true,
        nextUpSeedSeasonNumber = seedSeason,
        nextUpSeedEpisodeNumber = seedEpisode,
        nextUpSeedLastUpdatedEpochMs = seedTimestamp,
        resumePositionMs = 0L,
        resumeProgressFraction = null,
        durationMs = 0L,
        progressFraction = 0f,
        isReleaseAlert = alertState.isReleaseAlert,
        isNewSeasonRelease = alertState.isNewSeasonRelease,
    )
}

private fun CachedInProgressItem.toContinueWatchingItem(): ContinueWatchingItem {
    val explicitResumeProgressFraction = progressPercent
        ?.takeIf { duration <= 0L && it > 0f }
        ?.let { (it / 100f).coerceIn(0f, 1f) }
    val normalizedProgressFraction = progressPercent
        ?.let { (it / 100f).coerceIn(0f, 1f) }
        ?: if (duration > 0L) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    return ContinueWatchingItem(
        parentMetaId = contentId,
        parentMetaType = contentType,
        videoId = videoId,
        title = name,
        subtitle = buildContinueWatchingEpisodeSubtitle(
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
        ),
        imageUrl = episodeThumbnail ?: backdrop ?: poster,
        logo = logo,
        poster = poster,
        background = backdrop,
        seasonNumber = season,
        episodeNumber = episode,
        episodeTitle = episodeTitle,
        episodeThumbnail = episodeThumbnail,
        pauseDescription = pauseDescription,
        metadataCheckedAtEpochMs = metadataCheckedAtEpochMs,
        isNextUp = false,
        nextUpSeedSeasonNumber = null,
        nextUpSeedEpisodeNumber = null,
        resumePositionMs = if (explicitResumeProgressFraction != null) 0L else position,
        resumeProgressFraction = explicitResumeProgressFraction,
        durationMs = duration,
        progressFraction = normalizedProgressFraction,
    )
}

internal fun ContinueWatchingItem.withFallbackMetadata(
    fallback: ContinueWatchingItem?,
): ContinueWatchingItem {
    if (fallback == null) return this
    if (canonicalIdentity() != fallback.canonicalIdentity()) return this

    val preferred = when {
        fallback.metadataCheckedAtEpochMs > metadataCheckedAtEpochMs -> fallback
        fallback.metadataCheckedAtEpochMs < metadataCheckedAtEpochMs -> this
        fallback.metadataQualityScore() > metadataQualityScore() -> fallback
        else -> this
    }
    val secondary = if (preferred === this) fallback else this

    val mergedEpisodeTitle = when {
        !preferred.episodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber) ->
            preferred.episodeTitle?.trim()
        !secondary.episodeTitle.isGenericContinueWatchingEpisodeTitle(episodeNumber) ->
            secondary.episodeTitle?.trim()
        else -> preferred.episodeTitle.displayTextOrNull() ?: secondary.episodeTitle.displayTextOrNull()
    }
    val mergedEpisodeThumbnail = preferred.episodeThumbnail.displayTextOrNull()
        ?: secondary.episodeThumbnail.displayTextOrNull()
    val mergedBackground = preferred.background.displayTextOrNull()
        ?: secondary.background.displayTextOrNull()
    val mergedPoster = preferred.poster.displayTextOrNull()
        ?: secondary.poster.displayTextOrNull()
    val mergedPauseDescription = preferred.pauseDescription.displayTextOrNull()
        ?: secondary.pauseDescription.displayTextOrNull()

    return copy(
        title = preferred.title.ifBlank { secondary.title },
        subtitle = buildContinueWatchingEpisodeSubtitle(
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = mergedEpisodeTitle,
        ),
        imageUrl = mergedEpisodeThumbnail
            ?: mergedBackground
            ?: mergedPoster
            ?: imageUrl.displayTextOrNull()
            ?: fallback.imageUrl.displayTextOrNull(),
        logo = preferred.logo.displayTextOrNull() ?: secondary.logo.displayTextOrNull(),
        poster = mergedPoster,
        background = mergedBackground,
        episodeTitle = mergedEpisodeTitle,
        episodeThumbnail = mergedEpisodeThumbnail,
        pauseDescription = mergedPauseDescription,
        metadataCheckedAtEpochMs = maxOf(metadataCheckedAtEpochMs, fallback.metadataCheckedAtEpochMs),
        released = released ?: fallback.released,
        releaseEpochMs = releaseEpochMs ?: fallback.releaseEpochMs,
        nextUpSeedLastUpdatedEpochMs =
            nextUpSeedLastUpdatedEpochMs ?: fallback.nextUpSeedLastUpdatedEpochMs,
    )
}

private fun mergeNextUpEnrichmentResult(
    current: NextUpEnrichmentResult?,
    incoming: NextUpEnrichmentResult,
): NextUpEnrichmentResult {
    if (current == null || current.item.canonicalIdentity() != incoming.item.canonicalIdentity()) {
        return incoming
    }
    val mergedItem = incoming.item.withFallbackMetadata(current.item)
    return incoming.copy(
        item = mergedItem,
        metadataCheckedAtEpochMs = maxOf(
            current.metadataCheckedAtEpochMs,
            incoming.metadataCheckedAtEpochMs,
            mergedItem.metadataCheckedAtEpochMs,
        ),
    )
}

private fun mergeNextUpEnrichmentResults(
    current: Collection<NextUpEnrichmentResult>,
    incoming: Collection<NextUpEnrichmentResult>,
): List<NextUpEnrichmentResult> {
    val merged = current.associateByTo(mutableMapOf(), NextUpEnrichmentResult::contentId)
    incoming.forEach { result ->
        merged[result.contentId] = mergeNextUpEnrichmentResult(
            current = merged[result.contentId],
            incoming = result,
        )
    }
    return merged.values.toList()
}

private fun String?.displayTextOrNull(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

internal fun nextReleaseBoundaryDelayMs(
    nowEpochMs: Long,
    releaseEpochMs: Long,
): Long = (releaseEpochMs - nowEpochMs).coerceAtLeast(0L)

internal inline fun resolvePreciseContinueWatchingReleaseEpochMs(
    rawReleaseValue: String?,
    resolve: (String) -> Long?,
): Long? {
    val value = rawReleaseValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!PreciseContinueWatchingReleaseRegex.matches(value)) return null
    return resolve(value)
}

internal inline fun resolveCachedAdjustedReleaseEpochMs(
    cachedReleaseEpochMs: Long?,
    cachedTimingRuleVersion: Int,
    resolveRawRelease: () -> Long?,
): Long? {
    val freshlyResolvedEpochMs = resolveRawRelease()
    if (freshlyResolvedEpochMs != null) return freshlyResolvedEpochMs
    return cachedReleaseEpochMs.takeIf {
        cachedTimingRuleVersion >= CurrentEpisodeReleaseTimingRuleVersion
    }
}

private val PreciseContinueWatchingReleaseRegex = Regex(
    """^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})?$""",
)
