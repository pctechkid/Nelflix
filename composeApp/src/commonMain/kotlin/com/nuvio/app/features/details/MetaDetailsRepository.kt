package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.core.cache.InFlightRequestCoalescer
import com.nuvio.app.core.cache.withBoundedEntry
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile

object MetaDetailsRepository {
    private const val aiometadataManifestUrl =
        "https://aiometadata.home.kg/stremio/02253c19-8905-4cee-a5db-8c894551a50a/manifest.json"

    private data class CachedMetaEntry(
        val baseMeta: MetaDetails,
        val metaScreenMeta: MetaDetails? = null,
        val metaScreenSettingsFingerprint: String? = null,
    )

    private val log = Logger.withTag("MetaDetailsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val continueWatchingMetaRequests =
        InFlightRequestCoalescer<String, MetaDetails?>()
    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()
    private var activeRequestKey: String? = null
    @Volatile
    private var cachedMetaByRequestKey: Map<String, CachedMetaEntry> = emptyMap()

    fun load(type: String, id: String) {
        log.d { "load() called — type=$type id=$id" }
        val requestKey = "$type:$id"
        val currentState = _uiState.value
        val mdbListSettings = MdbListSettingsRepository.snapshot()
        val metaScreenSettingsFingerprint = buildMetaScreenSettingsFingerprint(mdbListSettings)

        cachedMetaByRequestKey[requestKey]?.let { cachedEntry ->
            cachedEntry.metaScreenMeta
                ?.takeIf { cachedEntry.metaScreenSettingsFingerprint == metaScreenSettingsFingerprint }
                ?.let { cachedMeta ->
                    _uiState.value = MetaDetailsUiState(meta = cachedMeta.withUnreleasedFilter())
                    activeRequestKey = requestKey
                    return
                }

            val cachedBaseMeta = cachedEntry.baseMeta
            if (!shouldFetchMdbListOnMetaScreen(cachedBaseMeta, id, mdbListSettings)) {
                _uiState.value = MetaDetailsUiState(meta = cachedBaseMeta.withUnreleasedFilter())
                activeRequestKey = requestKey
                return
            }

            if (currentState.isLoading && activeRequestKey == requestKey) {
                log.d { "Meta screen enrichment already in flight — type=$type id=$id" }
                return
            }

            activeRequestKey = requestKey
            _uiState.value = MetaDetailsUiState(
                isLoading = true,
                meta = cachedBaseMeta,
            )

            scope.launch {
                val enrichedMeta = withContext(Dispatchers.Default) {
                    enrichForMetaScreen(
                        requestKey = requestKey,
                        meta = cachedBaseMeta,
                        fallbackItemId = id,
                        settings = mdbListSettings,
                        settingsFingerprint = metaScreenSettingsFingerprint,
                    )
                }
                _uiState.value = MetaDetailsUiState(meta = enrichedMeta.withUnreleasedFilter())
                activeRequestKey = requestKey
            }
            return
        }

        if (currentState.meta?.type == type && currentState.meta.id == id && !currentState.isLoading) {
            log.d { "Skipping reload for cached meta — type=$type id=$id" }
            activeRequestKey = requestKey
            return
        }

        if (currentState.isLoading && activeRequestKey == requestKey) {
            log.d { "Request already in flight — type=$type id=$id" }
            return
        }

        activeRequestKey = requestKey
        _uiState.value = MetaDetailsUiState(isLoading = true)

        scope.launch {
            val metaLookupId = withContext(Dispatchers.Default) {
                resolveMetaLookupId(itemId = id, itemType = type)
            }
            val manifests = findMetaManifests(type = type, id = metaLookupId)

            if (manifests.isEmpty()) {
                val tmdbMeta = withContext(Dispatchers.Default) {
                    tryFetchTmdbFallbackMeta(type = type, id = id)
                }
                if (tmdbMeta != null) {
                    publishLoadedMeta(
                        requestKey = requestKey,
                        meta = tmdbMeta,
                        fallbackItemId = id,
                        mdbListSettings = mdbListSettings,
                        metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
                    )
                    return@launch
                }

                log.w { "No addon provides meta for type=$type id=$id" }
                _uiState.value = MetaDetailsUiState(
                    errorMessage = getString(Res.string.details_no_addon_meta),
                )
                activeRequestKey = null
                return@launch
            }

            for (manifest in manifests) {
                val result = withContext(Dispatchers.Default) {
                    tryFetchMeta(manifest, type, metaLookupId, includeMdbList = false)
                }
                if (result != null) {
                    publishLoadedMeta(
                        requestKey = requestKey,
                        meta = result,
                        fallbackItemId = metaLookupId,
                        mdbListSettings = mdbListSettings,
                        metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
                    )
                    return@launch
                }
            }

            val tmdbMeta = withContext(Dispatchers.Default) {
                tryFetchTmdbFallbackMeta(type = type, id = id)
            }
            if (tmdbMeta != null) {
                publishLoadedMeta(
                    requestKey = requestKey,
                    meta = tmdbMeta,
                    fallbackItemId = id,
                    mdbListSettings = mdbListSettings,
                    metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
                )
                return@launch
            }

            _uiState.value = MetaDetailsUiState(
                errorMessage = getString(Res.string.details_load_failed_all_addons),
            )
            activeRequestKey = null
        }
    }

    fun peek(type: String, id: String): MetaDetails? {
        val requestKey = "$type:$id"
        val currentMeta = _uiState.value.meta?.takeIf { it.type == type && it.id == id }
        if (currentMeta != null) return currentMeta

        val metaScreenSettingsFingerprint = buildMetaScreenSettingsFingerprint(MdbListSettingsRepository.snapshot())
        val cachedEntry = cachedMetaByRequestKey[requestKey] ?: return null
        return cachedEntry.metaScreenMeta
            ?.takeIf { cachedEntry.metaScreenSettingsFingerprint == metaScreenSettingsFingerprint }
            ?: cachedEntry.baseMeta
    }

    fun clear() {
        activeRequestKey = null
        cachedMetaByRequestKey = emptyMap()
        _uiState.value = MetaDetailsUiState()
    }

    suspend fun fetch(
        type: String,
        id: String,
        refreshIncompleteMaturityMetadata: Boolean = false,
    ): MetaDetails? = withContext(Dispatchers.Default) {
        fetchInternal(
            type = type,
            id = id,
            refreshIncompleteMaturityMetadata = refreshIncompleteMaturityMetadata,
        )
    }

    private suspend fun fetchInternal(
        type: String,
        id: String,
        refreshIncompleteMaturityMetadata: Boolean,
    ): MetaDetails? {
        val requestKey = "$type:$id"
        val cachedMeta = cachedMetaByRequestKey[requestKey]?.baseMeta
        if (
            cachedMeta != null &&
            (!refreshIncompleteMaturityMetadata || cachedMeta.hasCompleteMaturityMetadata())
        ) {
            return cachedMeta
        }

        val metaLookupId = resolveMetaLookupId(itemId = id, itemType = type)
        val manifests = findMetaManifests(type = type, id = metaLookupId)
        var bestMaturityResult = cachedMeta

        for (manifest in manifests) {
            val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                tryFetchMeta(manifest, type, metaLookupId, includeMdbList = false)
            }
            if (result != null) {
                if (!refreshIncompleteMaturityMetadata) {
                    cacheMetaEntry(requestKey, CachedMetaEntry(baseMeta = result))
                    return result
                }
                bestMaturityResult = bestMaturityResult
                    ?.withMaturityFallback(result)
                    ?: result
                if (bestMaturityResult.hasCompleteMaturityMetadata()) {
                    cacheMetaEntry(requestKey, CachedMetaEntry(baseMeta = bestMaturityResult))
                    return bestMaturityResult
                }
            }
        }

        val tmdbFallback = tryFetchTmdbFallbackMeta(type = type, id = id)
        val result = when {
            bestMaturityResult != null && tmdbFallback != null ->
                bestMaturityResult.withMaturityFallback(tmdbFallback)
            bestMaturityResult != null -> bestMaturityResult
            else -> tmdbFallback
        }
        result?.let { cacheMetaEntry(requestKey, CachedMetaEntry(baseMeta = it)) }
        return result
    }

    suspend fun fetchNotificationReleaseMeta(type: String, id: String): MetaDetails? =
        continueWatchingMetaRequests.runCoalesced("notifications:$type:$id") {
            withContext(Dispatchers.Default) {
                fetchNotificationReleaseMetaUncoalesced(type = type, id = id)
            }
        }

    suspend fun fetchFreshContinueWatchingMeta(type: String, id: String): MetaDetails? =
        continueWatchingMetaRequests.runCoalesced("continue-watching:$type:$id") {
            withContext(Dispatchers.Default) {
                fetchFreshContinueWatchingMetaUncoalesced(type = type, id = id)
            }
        }

    private suspend fun fetchFreshContinueWatchingMetaUncoalesced(
        type: String,
        id: String,
    ): MetaDetails? {
        val requestKey = "$type:$id"
        val cached = peek(type = type, id = id)
        val releaseMeta = fetchNotificationReleaseMetaUncoalesced(type = type, id = id)
        val metaLookupId = resolveMetaLookupId(itemId = id, itemType = type)
        for (manifest in findMetaManifests(type = type, id = metaLookupId)) {
            val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                tryFetchMeta(
                    manifest = manifest,
                    type = type,
                    id = metaLookupId,
                    includeMdbList = false,
                )
            }
            if (result != null) {
                return cacheContinueWatchingMeta(
                    requestKey = requestKey,
                    fresh = result.withContinueWatchingReleaseTimingFallback(releaseMeta),
                    fallback = cached,
                )
            }
        }
        val tmdbFallback = tryFetchTmdbFallbackMeta(type = type, id = id)
        return if (tmdbFallback != null) {
            cacheContinueWatchingMeta(
                requestKey = requestKey,
                fresh = tmdbFallback.withContinueWatchingReleaseTimingFallback(releaseMeta),
                fallback = cached,
            )
        } else {
            releaseMeta?.let { releaseOnlyMeta ->
                cacheContinueWatchingMeta(
                    requestKey = requestKey,
                    fresh = releaseOnlyMeta,
                    fallback = cached,
                )
            } ?: cached
        }
    }

    private suspend fun fetchNotificationReleaseMetaUncoalesced(
        type: String,
        id: String,
    ): MetaDetails? {
        val metaLookupId = resolveMetaLookupId(itemId = id, itemType = type)
        val aiometadataManifest = AddonRepository.uiState.value.addons
            .mapNotNull { it.manifest }
            .firstOrNull { manifest ->
                manifest.transportUrl.substringBefore("?")
                    .equals(aiometadataManifestUrl, ignoreCase = true)
            }
        val manifests = buildList {
            aiometadataManifest?.let(::add)
            addAll(
                findMetaManifests(type = type, id = metaLookupId)
                    .filterNot { manifest ->
                        manifest.transportUrl.substringBefore("?")
                            .equals(aiometadataManifestUrl, ignoreCase = true)
                    },
            )
        }

        for (manifest in manifests) {
            val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                tryFetchMeta(
                    manifest = manifest,
                    type = type,
                    id = metaLookupId,
                    includeMdbList = false,
                    enrichWithTmdb = false,
                )
            }
            if (result != null) return result
        }

        return null
    }

    private fun cacheContinueWatchingMeta(
        requestKey: String,
        fresh: MetaDetails,
        fallback: MetaDetails?,
    ): MetaDetails {
        val merged = fresh.withContinueWatchingMetadataFallback(fallback)
        updateCachedMetaEntry(requestKey) { cachedEntry ->
            val mergedScreenMeta = cachedEntry?.metaScreenMeta
                ?.withContinueWatchingMetadataFallback(merged)
            CachedMetaEntry(
                baseMeta = merged,
                metaScreenMeta = mergedScreenMeta,
                metaScreenSettingsFingerprint = cachedEntry?.metaScreenSettingsFingerprint,
            )
        }
        return merged
    }

    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val TMDB_ENRICH_TIMEOUT_MS = 5_000L
    private const val MDBLIST_ENRICH_TIMEOUT_MS = 5_000L

    private suspend fun tryFetchMeta(
        manifest: AddonManifest,
        type: String,
        id: String,
        includeMdbList: Boolean,
        enrichWithTmdb: Boolean = true,
    ): MetaDetails? {
        val url = buildAddonResourceUrl(
            manifestUrl = manifest.transportUrl,
            resource = "meta",
            type = type,
            id = id,
        )

        return try {
            TmdbSettingsRepository.ensureLoaded()
            log.d { "Fetching meta from: $url" }
            val payload = httpGetText(url)
            log.d { "Raw payload length=${payload.length}, first 500 chars: ${payload.take(500)}" }
            val result = MetaDetailsParser.parse(payload)
            val tmdbEnriched = if (enrichWithTmdb) {
                withTimeoutOrNull(TMDB_ENRICH_TIMEOUT_MS) {
                    TmdbMetadataService.enrichMeta(
                        meta = result,
                        fallbackItemId = id,
                        settings = TmdbSettingsRepository.snapshot(),
                    )
                } ?: result
            } else {
                result
            }
            val enriched = if (includeMdbList) {
                MdbListSettingsRepository.ensureLoaded()
                withTimeoutOrNull(MDBLIST_ENRICH_TIMEOUT_MS) {
                    MdbListMetadataService.enrichMeta(
                        meta = tmdbEnriched,
                        fallbackItemId = id,
                        settings = MdbListSettingsRepository.snapshot(),
                    )
                } ?: tmdbEnriched
            } else {
                tmdbEnriched
            }
            val logoPreferred = preferAiometadataLogo(
                meta = enriched,
                fallbackItemId = id,
                manifest = manifest,
            )
            log.d { "Parsed meta: type=${logoPreferred.type}, name=${logoPreferred.name}, videos=${logoPreferred.videos.size}" }
            if (logoPreferred.videos.isNotEmpty()) {
                val first = logoPreferred.videos.first()
                log.d { "First video: id=${first.id} title=${first.title} s=${first.season} e=${first.episode} embeddedStreams=${first.streams.size}" }
            }
            logoPreferred
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e(e) { "Failed to fetch/parse meta from $url (manifest=${manifest.transportUrl})" }
            null
        }
    }

    private fun findMetaManifests(type: String, id: String): List<AddonManifest> =
        AddonRepository.uiState.value.addons
            .mapNotNull { it.manifest }
            .filter { manifest ->
                manifest.resources.any { resource ->
                    resource.name == "meta" &&
                        resource.types.contains(type) &&
                        (resource.idPrefixes.isEmpty() || resource.idPrefixes.any { id.startsWith(it) })
                }
            }

    private suspend fun resolveMetaLookupId(itemId: String, itemType: String): String {
        val tmdbId = itemId
            .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return itemId

        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            TmdbService.tmdbToImdb(tmdbId = tmdbId, mediaType = itemType)
        }
            ?.takeIf { it.isNotBlank() }
            ?: itemId
    }

    private suspend fun tryFetchTmdbFallbackMeta(type: String, id: String): MetaDetails? =
        withTimeoutOrNull(TMDB_ENRICH_TIMEOUT_MS) {
            TmdbMetadataService.fetchStandaloneMeta(
                type = type,
                id = id,
                settings = TmdbSettingsRepository.snapshot(),
            )
        }?.let { meta ->
            preferAiometadataLogo(meta = meta, fallbackItemId = id, manifest = null)
        }

    private suspend fun preferAiometadataLogo(
        meta: MetaDetails,
        fallbackItemId: String,
        manifest: AddonManifest?,
    ): MetaDetails {
        if (manifest?.transportUrl?.substringBefore("?")?.equals(aiometadataManifestUrl, ignoreCase = true) == true) {
            return meta
        }
        val normalizedType = meta.type.trim().lowercase()
        if (normalizedType != "movie" && normalizedType != "series") return meta
        val lookupId = meta.id.takeIf { it.isNotBlank() } ?: fallbackItemId
        val aiometadataMeta = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            tryFetchAiometadataMeta(type = normalizedType, id = lookupId)
        }
        val logo = aiometadataMeta?.logo?.trim()?.takeIf(String::isNotBlank)
        return if (logo != null) meta.copy(logo = logo) else meta
    }

    private suspend fun tryFetchAiometadataMeta(type: String, id: String): MetaDetails? {
        val url = buildAddonResourceUrl(
            manifestUrl = aiometadataManifestUrl,
            resource = "meta",
            type = type,
            id = id,
        )
        return try {
            MetaDetailsParser.parse(httpGetText(url))
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun publishLoadedMeta(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        mdbListSettings: com.nuvio.app.features.mdblist.MdbListSettings,
        metaScreenSettingsFingerprint: String,
    ) {
        val cachedEntry = CachedMetaEntry(baseMeta = meta)
        cacheMetaEntry(requestKey, cachedEntry)

        if (!shouldFetchMdbListOnMetaScreen(meta, fallbackItemId, mdbListSettings)) {
            _uiState.value = MetaDetailsUiState(meta = meta.withUnreleasedFilter())
            activeRequestKey = requestKey
            return
        }

        _uiState.value = MetaDetailsUiState(
            isLoading = true,
            meta = meta,
        )
        val enrichedMeta = withContext(Dispatchers.Default) {
            enrichForMetaScreen(
                requestKey = requestKey,
                meta = meta,
                fallbackItemId = fallbackItemId,
                settings = mdbListSettings,
                settingsFingerprint = metaScreenSettingsFingerprint,
            )
        }
        cacheMetaEntry(
            requestKey = requestKey,
            entry = cachedEntry.copy(
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
            ),
        )
        _uiState.value = MetaDetailsUiState(meta = enrichedMeta.withUnreleasedFilter())
        activeRequestKey = requestKey
    }

    private suspend fun enrichForMetaScreen(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
        settingsFingerprint: String,
    ): MetaDetails {
        val enrichedMeta = withTimeoutOrNull(MDBLIST_ENRICH_TIMEOUT_MS) {
            MdbListMetadataService.enrichMeta(
                meta = meta,
                fallbackItemId = fallbackItemId,
                settings = settings,
            )
        } ?: meta

        updateCachedMetaEntry(requestKey) { cachedEntry ->
            cachedEntry?.copy(
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = settingsFingerprint,
            )
            ?: CachedMetaEntry(
                baseMeta = meta,
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = settingsFingerprint,
            )
        }

        return enrichedMeta
    }

    private fun cacheMetaEntry(requestKey: String, entry: CachedMetaEntry) {
        cachedMetaByRequestKey = cachedMetaByRequestKey.withBoundedEntry(
            key = requestKey,
            value = entry,
            maxEntries = MAX_CACHED_META_ENTRIES,
        )
    }

    private inline fun updateCachedMetaEntry(
        requestKey: String,
        transform: (CachedMetaEntry?) -> CachedMetaEntry,
    ) {
        val snapshot = cachedMetaByRequestKey
        cachedMetaByRequestKey = snapshot.withBoundedEntry(
            key = requestKey,
            value = transform(snapshot[requestKey]),
            maxEntries = MAX_CACHED_META_ENTRIES,
        )
    }

    private fun shouldFetchMdbListOnMetaScreen(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): Boolean = MdbListMetadataService.shouldFetchForMeta(
        meta = meta,
        fallbackItemId = fallbackItemId,
        settings = settings,
    )

    private fun buildMetaScreenSettingsFingerprint(
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): String {
        val providers = settings.enabledProvidersInPriorityOrder().joinToString(",")
        return "${settings.enabled}:${settings.apiKey.trim()}:$providers"
    }

    private fun MetaDetails.withUnreleasedFilter(): MetaDetails {
        if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return this
        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        return copy(
            moreLikeThis = moreLikeThis.filterReleasedItems(todayIsoDate),
            collectionItems = collectionItems.filterReleasedItems(todayIsoDate),
        )
    }

    private const val MAX_CACHED_META_ENTRIES = 16

    fun findEmbeddedStreams(videoId: String): List<com.nuvio.app.features.streams.StreamItem> {
        val meta = _uiState.value.meta ?: return emptyList()
        val videosWithStreams = meta.videos.filter { it.streams.isNotEmpty() }
        if (videosWithStreams.isEmpty()) return emptyList()

        val directMatch = videosWithStreams.firstOrNull { it.id == videoId }
        if (directMatch != null) return directMatch.streams

        val parts = videoId.split(":")
        if (parts.size >= 3) {
            val season = parts[parts.size - 2].toIntOrNull()
            val episode = parts[parts.size - 1].toIntOrNull()
            if (season != null && episode != null) {
                val episodeMatch = videosWithStreams.firstOrNull { it.season == season && it.episode == episode }
                if (episodeMatch != null) return episodeMatch.streams
            }
        }

        val prefixMatch = videosWithStreams.firstOrNull { it.id.startsWith("$videoId:") }
        if (prefixMatch != null) return prefixMatch.streams

        if (videoId == meta.id && videosWithStreams.size == 1) {
            return videosWithStreams.first().streams
        }

        if (videoId == meta.id && videosWithStreams.isNotEmpty()) {
            return videosWithStreams.flatMap { it.streams }
        }

        return emptyList()
    }
}

internal fun MetaDetails.hasCompleteMaturityMetadata(): Boolean =
    !ageRating.isNullOrBlank() && genres.any { it.isNotBlank() }

internal fun MetaDetails.withMaturityFallback(fallback: MetaDetails): MetaDetails {
    val fallbackVideosByKey = fallback.videos.associateBy(MetaVideo::maturityMatchKey)
    return copy(
        ageRating = ageRating?.trim()?.takeIf(String::isNotBlank)
            ?: fallback.ageRating?.trim()?.takeIf(String::isNotBlank),
        genres = genres.filter(String::isNotBlank)
            .ifEmpty { fallback.genres.filter(String::isNotBlank) },
        videos = videos.map { video ->
            val fallbackVideo = fallbackVideosByKey[video.maturityMatchKey()] ?: return@map video
            video.copy(
                ageRating = video.ageRating?.trim()?.takeIf(String::isNotBlank)
                    ?: fallbackVideo.ageRating?.trim()?.takeIf(String::isNotBlank),
                genres = video.genres.filter(String::isNotBlank)
                    .ifEmpty { fallbackVideo.genres.filter(String::isNotBlank) },
            )
        },
    )
}

private fun MetaVideo.maturityMatchKey(): String =
    if (season != null && episode != null) {
        "episode:$season:$episode"
    } else {
        "id:$id"
    }

internal fun MetaDetails.withContinueWatchingMetadataFallback(
    fallback: MetaDetails?,
): MetaDetails {
    if (fallback == null) return this
    val fallbackVideos = fallback.videos.associateBy(MetaVideo::continueWatchingMetadataKey)
    val freshKeys = videos.mapTo(mutableSetOf(), MetaVideo::continueWatchingMetadataKey)
    return copy(
        name = name.displayValue() ?: fallback.name,
        poster = poster.displayValue() ?: fallback.poster.displayValue(),
        background = background.displayValue() ?: fallback.background.displayValue(),
        logo = logo.displayValue() ?: fallback.logo.displayValue(),
        description = description.displayValue() ?: fallback.description.displayValue(),
        videos = videos.map { freshVideo ->
            freshVideo.withContinueWatchingMetadataFallback(
                fallbackVideos[freshVideo.continueWatchingMetadataKey()],
            )
        } + fallback.videos.filterNot { video -> video.continueWatchingMetadataKey() in freshKeys },
    )
}

private fun MetaVideo.withContinueWatchingMetadataFallback(fallback: MetaVideo?): MetaVideo {
    if (fallback == null) return this
    val mergedTitle = when {
        !title.isGenericEpisodeTitle(episode) -> title.trim()
        !fallback.title.isGenericEpisodeTitle(episode) -> fallback.title.trim()
        else -> title.displayValue() ?: fallback.title
    }
    return copy(
        title = mergedTitle,
        released = released.displayValue() ?: fallback.released.displayValue(),
        thumbnail = thumbnail.displayValue() ?: fallback.thumbnail.displayValue(),
        seasonPoster = seasonPoster.displayValue() ?: fallback.seasonPoster.displayValue(),
        overview = overview.displayValue() ?: fallback.overview.displayValue(),
        runtime = runtime ?: fallback.runtime,
        imdbRating = imdbRating.displayValue() ?: fallback.imdbRating.displayValue(),
        ageRating = ageRating.displayValue() ?: fallback.ageRating.displayValue(),
        genres = genres.filter(String::isNotBlank).ifEmpty { fallback.genres.filter(String::isNotBlank) },
        streams = streams.ifEmpty { fallback.streams },
    )
}

private fun MetaVideo.continueWatchingMetadataKey(): String =
    if (season != null && episode != null) "episode:$season:$episode" else "id:${id.trim()}"

internal fun MetaDetails.withContinueWatchingReleaseTimingFallback(releaseMeta: MetaDetails?): MetaDetails {
    if (releaseMeta == null) return this
    val releaseVideosByKey = releaseMeta.videos.associateBy(MetaVideo::continueWatchingMetadataKey)
    val richVideosByKey = videos.associateBy(MetaVideo::continueWatchingMetadataKey)
    val mergedVideos = videos.map { richVideo ->
        val releaseVideo = releaseVideosByKey[richVideo.continueWatchingMetadataKey()] ?: return@map richVideo
        richVideo.copy(
            released = releaseVideo.released.displayValue() ?: richVideo.released.displayValue(),
        )
    } + releaseMeta.videos.filterNot { releaseVideo ->
        releaseVideo.continueWatchingMetadataKey() in richVideosByKey
    }
    return copy(
        released = releaseMeta.released.displayValue() ?: released.displayValue(),
        videos = mergedVideos,
    )
}

private fun String?.displayValue(): String? = this?.trim()?.takeIf(String::isNotBlank)

private fun String?.isGenericEpisodeTitle(episodeNumber: Int?): Boolean {
    val value = displayValue()?.lowercase() ?: return true
    if (value == "episode" || value == "tba" || value == "untitled") return true
    val number = episodeNumber ?: return false
    return value.matches(Regex("""episode\s*0*$number""")) ||
        value.matches(Regex("""ep\.?\s*0*$number""")) ||
        value.matches(Regex("""e0*$number"""))
}
