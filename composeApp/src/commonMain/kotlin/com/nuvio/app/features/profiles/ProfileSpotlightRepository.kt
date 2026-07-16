package com.nuvio.app.features.profiles

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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

data class ProfileSpotlightItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
)

data class ProfileSpotlightState(
    val items: List<ProfileSpotlightItem> = emptyList(),
    val isLoading: Boolean = false,
)

object ProfileSpotlightRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val enrichmentSemaphore = Semaphore(ProfileSpotlightEnrichmentConcurrency)
    private val _state = MutableStateFlow(ProfileSpotlightState())

    val state: StateFlow<ProfileSpotlightState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var hasLoaded = false

    fun load(force: Boolean = false) {
        if (!force && (hasLoaded || loadJob?.isActive == true)) return
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val catalogItems = runCatching { fetchCatalogItems() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
                .distinctBy { item -> "${item.normalizedType}:${item.id}" }
                .shuffled(Random.Default)
                .take(ProfileSpotlightItemLimit)

            val enriched = coroutineScope {
                catalogItems.map { item ->
                    async {
                        enrichmentSemaphore.withPermit {
                            withTimeoutOrNull(ProfileSpotlightEnrichmentTimeoutMs) {
                                item.enrichWithAiometadata()
                            } ?: item.toSpotlightItem()
                        }
                    }
                }.awaitAll()
            }.filter { item ->
                item.banner.isProfileSpotlightTmdbBackground()
            }.shuffled(Random.Default)

            if (enriched.isNotEmpty()) {
                _state.value = ProfileSpotlightState(
                    items = enriched,
                    isLoading = false,
                )
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
            hasLoaded = true
        }
    }

    private suspend fun fetchCatalogItems(): List<ProfileSpotlightCatalogItem> = coroutineScope {
        ProfileSpotlightCatalogUrls.map { url ->
            async {
                runCatching {
                    json.decodeFromString<ProfileSpotlightCatalogResponse>(httpGetText(url)).metas
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
            }
        }.awaitAll()
            .flatten()
            .filter { item ->
                item.id.isNotBlank() &&
                    item.name.isNotBlank() &&
                    item.normalizedType != null
            }
    }

    private suspend fun ProfileSpotlightCatalogItem.enrichWithAiometadata(): ProfileSpotlightItem {
        val normalizedType = normalizedType ?: return toSpotlightItem()
        val url = buildAddonResourceUrl(
            manifestUrl = ProfileSpotlightAiometadataManifestUrl,
            resource = "meta",
            type = normalizedType,
            id = id,
        )

        return try {
            val payload = httpGetText(url)
            val root = json.parseToJsonElement(payload).jsonObject
            val nestedMeta = (root["data"] as? JsonObject)?.get("meta") as? JsonObject
            val meta = root["meta"] as? JsonObject ?: nestedMeta ?: root

            ProfileSpotlightItem(
                id = id,
                type = normalizedType,
                name = meta.string("name") ?: name,
                poster = meta.string("poster") ?: poster?.trim()?.takeIf(String::isNotBlank),
                banner = (meta.string("background") ?: meta.string("banner"))
                    .cleanProfileSpotlightBackgroundUrl(),
                logo = meta.string("logo").cleanProfileSpotlightLogoUrl(),
                genres = meta.stringList("genres").take(3).ifEmpty { genres.take(3) },
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            toSpotlightItem()
        }
    }
}

private fun ProfileSpotlightCatalogItem.toSpotlightItem(): ProfileSpotlightItem =
    ProfileSpotlightItem(
        id = id,
        type = normalizedType ?: "movie",
        name = name,
        poster = poster?.trim()?.takeIf(String::isNotBlank),
        banner = background.cleanProfileSpotlightBackgroundUrl(),
        logo = logo.cleanProfileSpotlightLogoUrl(),
        genres = genres.take(3),
    )

private val ProfileSpotlightCatalogItem.normalizedType: String?
    get() = when (type.trim().lowercase()) {
        "movie" -> "movie"
        "series", "tv" -> "series"
        else -> null
    }

private fun String?.cleanProfileSpotlightBackgroundUrl(): String? {
    val clean = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    return clean.takeIf { it.startsWith(ProfileSpotlightTmdbImageBase, ignoreCase = true) }
}

private fun String?.cleanProfileSpotlightLogoUrl(): String? {
    val clean = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    return clean.takeIf { url ->
        (url.startsWith(ProfileSpotlightTmdbImageBase, ignoreCase = true) ||
            url.startsWith("https://images.metahub.space/logo/", ignoreCase = true)) &&
            !url.contains("/poster/", ignoreCase = true)
    }
}

private fun String?.isProfileSpotlightTmdbBackground(): Boolean =
    this?.startsWith(ProfileSpotlightTmdbImageBase, ignoreCase = true) == true

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun JsonObject.stringList(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { element ->
            (element as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
        .orEmpty()

@Serializable
private data class ProfileSpotlightCatalogResponse(
    val metas: List<ProfileSpotlightCatalogItem> = emptyList(),
)

@Serializable
private data class ProfileSpotlightCatalogItem(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
)

private val ProfileSpotlightCatalogUrls = listOf(
    "$ProfileSpotlightCatalogBase/catalog/movie/tmdb-today.json",
    "$ProfileSpotlightCatalogBase/catalog/series/tmdb-today-shows.json",
)

private const val ProfileSpotlightAiometadataManifestUrl =
    "https://aiometadata.home.kg/stremio/02253c19-8905-4cee-a5db-8c894551a50a/manifest.json"

private const val ProfileSpotlightTmdbImageBase = "https://image.tmdb.org/t/p/"

private const val ProfileSpotlightCatalogBase =
    "https://btttr.cc/nZpNb-M2EIb_isDLblELQdFTfUtbdAukaYNdt5dFD7TF2IQlUSWpbN0g_70QJUqi-DHDvSnkq4dDixLnHeaVnKimtTgrsv9MaqqZ0mVDdvZSkR3RoqI30zheLW2U7MhJNLw9m-7pUi2Xg0BL1lZWMv8xiDrR9TWVpsNej_DOSOlVl11_3KtW0GtZUi6HO83Qof4purIRSpczW7xwptBydRFfwmrToMplNnHwVpqAToorr1SSOOloyxuGF0YH7jv72Ayq1BdGNZNBrV0JWjLapGdupbFh7friLVdMpiEt0881_xcezQqhUWlD_xNt2cn0TxhSg-iuq1mpXxBYq4SQFVctu8HASQfhOippI_pWw8RFCkEvR1E2FPGIrBAE9nWPoA0qCHWS9AXxmEcZBFOXvqpY6mNiH-9Jc9GideC6anlDNasQQKvEIYfBoW9J5AbwhxcNqxALd9KB74GkDUW8BkYGwsSpb1irqcS8WSsxuHCFlAKxPiYdhLsiZ33FTVsyWnMNz0KKhrYnxJqwQvC9OfHymSM2jFEH4rpbqS-S1zWTJVWKKsURL1v4NnCwvmPywqSAB5jheGU8JRAdYutbq5Io5KbnSdNQeLtzZEkYuNGtREkQZotzdUkcvLmtVWlUelubJVEIMqPFZbJWAO5VGyEMhHcrT4qEYvar2B3gEOCOtRHCQOBtc3UgDrdrhdQj-u8daZimZP8KeKr9K2lpw8ie3Jvm4mCtFtmTI3nL9FwLznDeqeJRKF08jaricZ5KHn2cFAD_ZH_UODti6FbgQfFOFYdJgYo47P0SUEykAZe4Jk6kB14pXIwhNxkA3g_9X0H05-wA4RlH7enC_HOSTOEV7w-T5pskOWZmF_BvRlF8sgrM_B3f66PA-fq-2IM8Op4ZIm0yCI_2-9ifMTc324gC0XMNJSYe9d6IiieJXIdx755G46PeZD4-dhAUh7--zQnXzZMSTHScblLlEX823TkxOglYlIeOz8vVPOSTVeREuU3tUlR0rJtE0GP--uMfxSPNeZfcpDEKxEe4yi99Wl_3ObEtiWgYhY7KqcB4rJ-G3oy41oWaCAwd2aacE_hgm_6cV9jJpf032HTn82IfhBGH_2ptEnMfOAlyInQz-DgyM0ov04-QuWjv0PlJspQFDoBf8Y6R8Fep6c5Z82u_EcPh94R1Tcn_hA-9ORvCqvIUgeEj8w2Oj1w0OVF6ZigJxn9xnXqf_6E03Tlf3XVdMIZDR3dNP-qH3Gd9TT7sh8yn7VYjPdzHsTuD51YtfeDYnzFft7wZBeL3G6cM6m83J17-wnN8xrpcGsPho0tVVX16d7s7TOq7-0mdE3uiGIscDD-zbQnXH8AqMmawrfZ6UBvx1zAjP8SMRFQL_ALyulbS5Xg_v8wcRuGiSlu-gZfr9yJl6wQUGWnU5hlgjscLFMJjNFxsMWs3sDJ8nVdVD5NwMSXs3ADL83KhGn2Uh4svauEGWoZ_8yv-YRQyqqBtMxykZ9ucHAQgcCTpym1uxTZZqc2s0IYPKhacJeHdVfBEIwrMiDBqsGZkhsOKHJIkoLmRJkyWy85yWekDGHgI_CxiRsuS8U4reLQTBWZEGN7kZh52hwsdFcVw-OiShstCMx1X_Pwpjd5E_bYjTXWsuRr-btnx-9sLq354vtX8-uWkjkd567_revMPg0ozaQ61REf2hPZaDM1SnCVTiuy17Nlu_IfDAz3PDf_0xl-MTc-0VmxHhjg_sFYyKxoaPlLN27NtoWdmG-xNQg4xCsnPvKU1eXv7Hw"

private const val ProfileSpotlightItemLimit = 24
private const val ProfileSpotlightEnrichmentConcurrency = 4
private const val ProfileSpotlightEnrichmentTimeoutMs = 2_500L
