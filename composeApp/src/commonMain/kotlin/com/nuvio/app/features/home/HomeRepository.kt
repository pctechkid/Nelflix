package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.absoluteValue
import kotlin.random.Random

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val aiometadataVisualsJson = Json { ignoreUnknownKeys = true }
    private val aiometadataVisualsCacheMutex = Mutex()
    private val aiometadataVisualsFetchSemaphore = Semaphore(AIOMETADATA_VISUAL_FETCH_CONCURRENCY)
    private val aiometadataVisualsCache = LinkedHashMap<String, AiometadataVisuals?>()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null
    private var lastRequestKey: String? = null
    private var currentDefinitions: List<HomeCatalogDefinition> = emptyList()
    private var cachedSections: Map<String, HomeCatalogSection> = emptyMap()
    private var lastErrorMessage: String? = null

    fun refresh(addons: List<ManagedAddon>, force: Boolean = false) {
        val requests = buildHomeCatalogDefinitions(addons)
        currentDefinitions = requests
        val requestKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::key)
        cachedSections = cachedSections.filterKeys(requestKeys::contains)
        val requestKey = requests.joinToString(separator = "|") { request ->
            "${request.manifestUrl}:${request.type}:${request.catalogId}"
        }

        if (!force && activeRequestKey == requestKey && _uiState.value.isLoading) return

        if (!force && requestKey == lastRequestKey && requestKeys.all(cachedSections::containsKey)) {
            if (_uiState.value.sections.isEmpty() || _uiState.value.heroItems.isEmpty()) {
                applyCurrentSettings()
            }
            return
        }
        lastRequestKey = requestKey
        activeRequestKey = requestKey

        if (requests.isEmpty()) {
            activeJob?.cancel()
            activeJob = null
            activeRequestKey = null
            cachedSections = emptyMap()
            lastErrorMessage = null
            _uiState.value = HomeUiState(
                isLoading = false,
                sections = emptyList(),
                errorMessage = null,
            )
            return
        }

        activeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        activeJob = scope.launch {
            val prioritizedRequests = prioritizeDefinitions(
                definitions = requests,
                snapshot = HomeCatalogSettingsRepository.snapshot(),
            )
            val pendingRequests = prioritizedRequests.filter { definition ->
                force || cachedSections[definition.key] == null
            }
            if (pendingRequests.isEmpty()) {
                publishCurrentState(
                    isLoading = false,
                    requestKey = requestKey,
                )
                return@launch
            }
            val loadedSections = linkedMapOf<String, HomeCatalogSection>().apply {
                putAll(cachedSections)
            }
            var firstErrorMessage: String? = null
            var batchIndex = 0

            pendingRequests.chunked(HOME_CATALOG_FETCH_BATCH_SIZE).forEach { batch ->
                if (activeRequestKey != requestKey) return@launch
                val results = batch.map { request ->
                    async { runCatching { request.toSection() } }
                }.awaitAll()

                if (activeRequestKey != requestKey) return@launch

                results.mapNotNull { it.getOrNull() }.forEach { section ->
                    loadedSections[section.key] = section
                }
                if (firstErrorMessage == null) {
                    firstErrorMessage = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                }
                cachedSections = loadedSections.toMap()
                lastErrorMessage = firstErrorMessage
                if (batchIndex == 0 || (batchIndex + 1) % HOME_CATALOG_PUBLISH_INTERVAL == 0) {
                    publishCurrentState(
                        isLoading = true,
                        requestKey = requestKey,
                    )
                }
                batchIndex++
            }

            if (activeRequestKey != requestKey) return@launch

            cachedSections = loadedSections.toMap()
            lastErrorMessage = firstErrorMessage
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
        }
    }

    fun recoverIfEmpty(addons: List<ManagedAddon>): Boolean {
        val state = _uiState.value
        if (state.isLoading || state.sections.isNotEmpty() || state.heroItems.isNotEmpty()) {
            return false
        }

        val requests = buildHomeCatalogDefinitions(addons)
        if (requests.isEmpty()) return false

        val requestKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::key)
        if (currentDefinitions.isNotEmpty() && requestKeys.all(cachedSections::containsKey)) {
            applyCurrentSettings()
            val recoveredState = _uiState.value
            if (recoveredState.sections.isNotEmpty() || recoveredState.heroItems.isNotEmpty()) {
                return true
            }
        }

        refresh(addons, force = true)
        return true
    }

    fun applyCurrentSettings() {
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = activeRequestKey ?: lastRequestKey,
        )
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        lastRequestKey = null
        currentDefinitions = emptyList()
        cachedSections = emptyMap()
        lastErrorMessage = null
        _uiState.value = HomeUiState()
    }

    private fun publishCurrentState(
        isLoading: Boolean,
        requestKey: String?,
    ) {
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        val preferences = snapshot.preferences
        val todayIsoDate = if (snapshot.hideUnreleasedContent) CurrentDateProvider.todayIsoDate() else null
        fun HomeCatalogSection.withReleaseFilter(): HomeCatalogSection =
            if (todayIsoDate == null) this else filterReleasedItems(todayIsoDate)

        val sections = currentDefinitions
            .sortedBy { definition -> preferences[definition.key]?.order ?: Int.MAX_VALUE }
            .mapNotNull { definition ->
                val preference = preferences[definition.key]
                if (preference?.enabled == false) return@mapNotNull null

                val section = cachedSections[definition.key]?.withReleaseFilter() ?: return@mapNotNull null
                if (section.items.isEmpty()) return@mapNotNull null
                val customTitle = preference?.customTitle.orEmpty()
                val defaultTitle = if (snapshot.showCatalogTypeLabels) {
                    "${definition.defaultTitle} - ${definition.typeLabel}"
                } else {
                    definition.defaultTitle
                }
                section.copy(
                    title = customTitle.ifBlank { defaultTitle },
                )
            }

        val heroItems = if (snapshot.heroEnabled) {
            val heroRandom = Random((requestKey?.hashCode() ?: 0).absoluteValue + 1)
            currentDefinitions
                .filter { definition -> preferences[definition.key]?.heroSourceEnabled != false }
                .mapNotNull { definition -> cachedSections[definition.key] }
                .map { section -> section.withReleaseFilter() }
                .flatMap { section -> section.items }
                .distinctBy { item -> "${item.type}:${item.id}" }
                .shuffled(heroRandom)
                .take(HOME_HERO_ITEM_LIMIT)
        } else {
            emptyList()
        }

        _uiState.value = HomeUiState(
            isLoading = isLoading,
            heroItems = heroItems,
            sections = sections,
            errorMessage = if (sections.isEmpty()) lastErrorMessage else null,
        )
    }

    private suspend fun HomeCatalogDefinition.toSection(): HomeCatalogSection {
        val page = fetchCatalogPage(
            manifestUrl = manifestUrl,
            type = type,
            catalogId = catalogId,
            maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
        )
        val items = page.items.withAiometadataVisuals()
        if (items.isEmpty()) {
            return HomeCatalogSection(
                key = key,
                title = defaultTitle,
                subtitle = addonName,
                addonName = addonName,
                type = type,
                manifestUrl = manifestUrl,
                catalogId = catalogId,
                items = emptyList(),
                availableItemCount = 0,
                supportsPagination = supportsPagination,
            )
        }

        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            type = type,
            manifestUrl = manifestUrl,
            catalogId = catalogId,
            items = items,
            availableItemCount = page.rawItemCount,
            supportsPagination = supportsPagination,
        )
    }

    private suspend fun List<MetaPreview>.withAiometadataVisuals(): List<MetaPreview> = coroutineScope {
        map { item ->
            async { item.withAiometadataVisuals() }
        }.awaitAll()
    }

    private suspend fun MetaPreview.withAiometadataVisuals(): MetaPreview {
        val normalizedType = type.trim().lowercase()
        if (normalizedType != "movie" && normalizedType != "series") return this
        if (id.isBlank()) return this

        val cacheKey = "$normalizedType:$id"
        readAiometadataVisualsCache(cacheKey)?.let { cached ->
            return applyAiometadataVisuals(cached.visuals)
        }

        val visuals = aiometadataVisualsFetchSemaphore.withPermit {
            readAiometadataVisualsCache(cacheKey)?.let { cached ->
                return@withPermit cached.visuals
            }
            val fetched = withTimeoutOrNull(AIOMETADATA_VISUAL_FETCH_TIMEOUT_MS) {
                fetchAiometadataVisuals(type = normalizedType, id = id)
            }?.takeIf {
                it.logo != null ||
                    it.background != null ||
                    it.releaseInfo != null ||
                    it.released != null ||
                    it.genres.isNotEmpty()
            }
            writeAiometadataVisualsCache(cacheKey, fetched)
            fetched
        }

        return applyAiometadataVisuals(visuals)
    }

    private fun MetaPreview.applyAiometadataVisuals(visuals: AiometadataVisuals?): MetaPreview =
        if (visuals != null) {
            copy(
                banner = visuals.background ?: banner,
                logo = visuals.logo ?: logo,
                releaseInfo = visuals.releaseInfo ?: releaseInfo,
                rawReleaseDate = visuals.released ?: rawReleaseDate,
                genres = visuals.genres.ifEmpty { genres },
            )
        } else {
            this
        }

    private suspend fun readAiometadataVisualsCache(cacheKey: String): AiometadataVisualsCacheHit? =
        aiometadataVisualsCacheMutex.withLock {
            if (!aiometadataVisualsCache.containsKey(cacheKey)) return@withLock null
            val visuals = aiometadataVisualsCache.remove(cacheKey)
            aiometadataVisualsCache[cacheKey] = visuals
            AiometadataVisualsCacheHit(visuals)
        }

    private suspend fun writeAiometadataVisualsCache(
        cacheKey: String,
        visuals: AiometadataVisuals?,
    ) {
        aiometadataVisualsCacheMutex.withLock {
            aiometadataVisualsCache.remove(cacheKey)
            aiometadataVisualsCache[cacheKey] = visuals
            while (aiometadataVisualsCache.size > AIOMETADATA_VISUAL_CACHE_LIMIT) {
                val oldestKey = aiometadataVisualsCache.keys.firstOrNull() ?: break
                aiometadataVisualsCache.remove(oldestKey)
            }
        }
    }

    private suspend fun fetchAiometadataVisuals(type: String, id: String): AiometadataVisuals? {
        val url = buildAddonResourceUrl(
            manifestUrl = AIOMETADATA_MANIFEST_URL,
            resource = "meta",
            type = type,
            id = id,
        )
        return try {
            val payload = httpGetText(url)
            val root = aiometadataVisualsJson.parseToJsonElement(payload).jsonObject
            val meta = root["meta"] as? JsonObject
                ?: (root["data"] as? JsonObject)?.get("meta") as? JsonObject
                ?: root
            AiometadataVisuals(
                logo = meta.string("logo"),
                background = meta.string("background") ?: meta.string("banner"),
                releaseInfo = meta.string("releaseInfo"),
                released = meta.string("released"),
                genres = meta.stringList("genres"),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }
    }
}

private data class AiometadataVisuals(
    val logo: String?,
    val background: String?,
    val releaseInfo: String?,
    val released: String?,
    val genres: List<String>,
)

private data class AiometadataVisualsCacheHit(
    val visuals: AiometadataVisuals?,
)

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

private fun JsonObject.stringList(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        }
        .orEmpty()

private const val HOME_HERO_ITEM_LIMIT = 8
private const val HOME_CATALOG_FETCH_BATCH_SIZE = 4
private const val HOME_CATALOG_PREVIEW_FETCH_LIMIT = 18
private const val HOME_CATALOG_PUBLISH_INTERVAL = 2
private const val AIOMETADATA_VISUAL_FETCH_CONCURRENCY = 4
private const val AIOMETADATA_VISUAL_CACHE_LIMIT = 512
private const val AIOMETADATA_VISUAL_FETCH_TIMEOUT_MS = 2_500L
private const val AIOMETADATA_MANIFEST_URL =
    "https://aiometadata.home.kg/stremio/02253c19-8905-4cee-a5db-8c894551a50a/manifest.json"

private fun prioritizeDefinitions(
    definitions: List<HomeCatalogDefinition>,
    snapshot: HomeCatalogSettingsSnapshot,
): List<HomeCatalogDefinition> {
    val orderedDefinitions = definitions.sortedBy { definition ->
        snapshot.preferences[definition.key]?.order ?: Int.MAX_VALUE
    }
    val (priority, remainder) = orderedDefinitions.partition { definition ->
        val preference = snapshot.preferences[definition.key]
        if (preference == null) {
            true
        } else {
            preference.enabled || (snapshot.heroEnabled && preference.heroSourceEnabled)
        }
    }
    return priority + remainder
}
