package com.nuvio.app.features.settings

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cache.InFlightRequestCoalescer
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AccountStatistics(
    val progress: Long = 0L,
    val library: Long = 0L,
    val watched: Long = 0L,
)

data class AccountStatisticsUiState(
    val userId: String? = null,
    val profileId: Int? = null,
    val statistics: AccountStatistics? = null,
    val isRefreshing: Boolean = false,
    val isServerBacked: Boolean = false,
    val errorMessage: String? = null,
)

private data class AccountStatisticsRequestKey(
    val userId: String,
    val profileId: Int,
) {
    override fun toString(): String = "$userId:$profileId"
}

private data class AccountStatisticsRequestToken(
    val key: AccountStatisticsRequestKey,
    val generation: Long,
)

private class AccountStatisticsRequestGate {
    private var generation = 0L
    private var activeKey: AccountStatisticsRequestKey? = null

    fun begin(key: AccountStatisticsRequestKey): AccountStatisticsRequestToken {
        generation += 1L
        activeKey = key
        return AccountStatisticsRequestToken(key = key, generation = generation)
    }

    fun isCurrent(token: AccountStatisticsRequestToken): Boolean =
        token.generation == generation && token.key == activeKey

    fun invalidate() {
        generation += 1L
        activeKey = null
    }
}

object AccountStatisticsRepository {
    private val log = Logger.withTag("AccountStatistics")
    private val requests = InFlightRequestCoalescer<String, AccountStatistics>()
    private val requestGate = AccountStatisticsRequestGate()
    private val _uiState = MutableStateFlow(AccountStatisticsUiState())
    val uiState: StateFlow<AccountStatisticsUiState> = _uiState.asStateFlow()

    suspend fun refresh(
        userId: String,
        profileId: Int,
        fallback: AccountStatistics,
    ) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank() || profileId !in 1..4) return
        val key = AccountStatisticsRequestKey(normalizedUserId, profileId)
        val token = requestGate.begin(key)
        val current = _uiState.value
        val cached = AccountStatisticsCodec.decode(
            AccountStatisticsStorage.loadPayload(normalizedUserId, profileId),
        )
        val retained = current.statistics.takeIf {
            current.userId == normalizedUserId && current.profileId == profileId
        } ?: cached ?: fallback
        _uiState.value = AccountStatisticsUiState(
            userId = normalizedUserId,
            profileId = profileId,
            statistics = retained,
            isRefreshing = true,
            isServerBacked = cached != null || (
                current.userId == normalizedUserId &&
                    current.profileId == profileId &&
                    current.isServerBacked
                ),
        )

        runCatching {
            requests.runCoalesced(key.toString()) {
                fetchServerStatistics(profileId)
            }
        }.onSuccess { statistics ->
            if (!shouldAccept(token)) return@onSuccess
            AccountStatisticsStorage.savePayload(
                userId = normalizedUserId,
                profileId = profileId,
                payload = AccountStatisticsCodec.encode(statistics),
            )
            _uiState.value = AccountStatisticsUiState(
                userId = normalizedUserId,
                profileId = profileId,
                statistics = statistics,
                isServerBacked = true,
            )
        }.onFailure { error ->
            if (!requestGate.isCurrent(token)) return@onFailure
            log.w { "Unable to refresh account statistics: ${error.message}" }
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                errorMessage = error.message,
            )
        }
    }

    fun clear() {
        requestGate.invalidate()
        _uiState.value = AccountStatisticsUiState()
    }

    private fun shouldAccept(token: AccountStatisticsRequestToken): Boolean {
        if (!requestGate.isCurrent(token)) return false
        val auth = AuthRepository.state.value as? AuthState.Authenticated ?: return false
        return accountStatisticsResponseMatches(
            activeUserId = auth.userId,
            activeProfileId = ProfileRepository.activeProfileId,
            isAnonymous = auth.isAnonymous,
            responseUserId = token.key.userId,
            responseProfileId = token.key.profileId,
        )
    }

    private suspend fun fetchServerStatistics(profileId: Int): AccountStatistics {
        val params = buildJsonObject {
            put("p_profile_id", profileId)
        }
        val row = SupabaseProvider.client.postgrest
            .rpc("sync_account_statistics", params)
            .decodeList<AccountStatisticsRpcRow>()
            .firstOrNull()
            ?: error("Account statistics response was empty")
        return AccountStatistics(
            progress = row.progressCount.coerceAtLeast(0L),
            library = row.libraryCount.coerceAtLeast(0L),
            watched = row.watchedCount.coerceAtLeast(0L),
        )
    }
}

@Serializable
private data class AccountStatisticsRpcRow(
    @SerialName("progress_count") val progressCount: Long = 0L,
    @SerialName("library_count") val libraryCount: Long = 0L,
    @SerialName("watched_count") val watchedCount: Long = 0L,
)

@Serializable
private data class StoredAccountStatistics(
    val progress: Long = 0L,
    val library: Long = 0L,
    val watched: Long = 0L,
)

internal object AccountStatisticsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(statistics: AccountStatistics): String = json.encodeToString(
        StoredAccountStatistics(
            progress = statistics.progress.coerceAtLeast(0L),
            library = statistics.library.coerceAtLeast(0L),
            watched = statistics.watched.coerceAtLeast(0L),
        ),
    )

    fun decode(payload: String?): AccountStatistics? {
        val value = payload?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching { json.decodeFromString<StoredAccountStatistics>(value) }
            .getOrNull()
            ?.let { stored ->
                AccountStatistics(
                    progress = stored.progress.coerceAtLeast(0L),
                    library = stored.library.coerceAtLeast(0L),
                    watched = stored.watched.coerceAtLeast(0L),
                )
            }
    }
}

internal fun accountStatisticsForDisplay(
    serverOrCached: AccountStatistics?,
    fallback: AccountStatistics,
): AccountStatistics = serverOrCached ?: fallback

internal fun accountStatisticsResponseMatches(
    activeUserId: String,
    activeProfileId: Int,
    isAnonymous: Boolean,
    responseUserId: String,
    responseProfileId: Int,
): Boolean =
    !isAnonymous &&
        activeUserId == responseUserId &&
        activeProfileId == responseProfileId
