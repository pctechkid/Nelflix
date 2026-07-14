package com.nuvio.app.features.player

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.httpGetText
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_subtitles_found
import org.jetbrains.compose.resources.getString

object SubtitleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _addonSubtitles = MutableStateFlow<List<AddonSubtitle>>(emptyList())
    val addonSubtitles: StateFlow<List<AddonSubtitle>> = _addonSubtitles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeFetchJob: Job? = null

    fun fetchAddonSubtitles(type: String, videoId: String) {
        activeFetchJob?.cancel()
        activeFetchJob = scope.launch {
            val requestType = canonicalSubtitleType(type)
            _isLoading.value = true
            _error.value = null
            _addonSubtitles.value = emptyList()

            val addons = AddonRepository.uiState.value.addons
            val compatibleAddons = addons.mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                val subtitleResource = manifest.resources.find { it.name.isSubtitleResourceName() } ?: return@mapNotNull null
                if (!subtitleResource.supportsSubtitleType(requestType, videoId)) return@mapNotNull null
                SubtitleAddonRequest(addon = addon, requestType = requestType, videoId = videoId)
            }
            val allSubs = compatibleAddons.fetchConcurrently()

            _addonSubtitles.value = allSubs
            if (allSubs.isEmpty() && addons.any { it.manifest?.resources?.any { r -> r.name == "subtitles" } == true }) {
                _error.value = getString(Res.string.compose_player_no_subtitles_found)
            }
            _isLoading.value = false
        }
    }

    fun clear() {
        activeFetchJob?.cancel()
        _addonSubtitles.value = emptyList()
        _isLoading.value = false
        _error.value = null
    }
}

private data class SubtitleAddonRequest(
    val addon: ManagedAddon,
    val requestType: String,
    val videoId: String,
)

private suspend fun List<SubtitleAddonRequest>.fetchConcurrently(): List<AddonSubtitle> =
    coroutineScope {
        map { request ->
            async {
                request.fetchAddonSubtitles()
            }
        }.awaitAll()
            .flatten()
    }

private suspend fun SubtitleAddonRequest.fetchAddonSubtitles(): List<AddonSubtitle> {
    val manifest = addon.manifest ?: return emptyList()
    val subtitleUrl = buildAddonResourceUrl(
        manifestUrl = manifest.transportUrl,
        resource = "subtitles",
        type = requestType,
        id = videoId,
    )

    return try {
        val response = withTimeoutOrNull(SUBTITLE_ADDON_FETCH_TIMEOUT_MS) {
            withContext(Dispatchers.Default) {
                httpGetText(subtitleUrl)
            }
        } ?: return emptyList()
        val parsed = SubtitleRepositoryJson.parseToJsonElement(response).jsonObject
        val subtitlesArray = parsed["subtitles"]?.jsonArray ?: return emptyList()

        subtitlesArray.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val id = obj.stringValue("id")
                ?: "${manifest.id}_$index"
            val url = obj.stringValue("url") ?: return@mapIndexedNotNull null
            val rawLang = obj.subtitleLanguage() ?: "unknown"
            val normalizedLang = normalizeLanguageCode(rawLang) ?: rawLang

            AddonSubtitle(
                id = id,
                url = url,
                language = normalizedLang,
                display = "${getLanguageLabelForCode(rawLang)} (${addon.displayTitle})",
            )
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        emptyList()
    }
}

private val SubtitleRepositoryJson = Json { ignoreUnknownKeys = true }

private const val SUBTITLE_ADDON_FETCH_TIMEOUT_MS = 12_000L

private fun canonicalSubtitleType(type: String): String =
    if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()

private fun String.isSubtitleResourceName(): Boolean =
    equals("subtitles", ignoreCase = true) || equals("subtitle", ignoreCase = true)

private fun AddonResource.supportsSubtitleType(type: String, videoId: String): Boolean {
    val typeMatches = types.isEmpty() || types.any { it.equals(type, ignoreCase = true) }
    if (!typeMatches) return false
    return idPrefixes.isEmpty() || idPrefixes.any { prefix -> videoId.startsWith(prefix) }
}

private fun JsonObject.subtitleLanguage(): String? =
    stringValue("lang")
        ?: stringValue("language")
        ?: stringValue("languageCode")
        ?: stringValue("locale")
        ?: stringValue("label")

private fun JsonObject.stringValue(name: String): String? =
    this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotBlank() }
