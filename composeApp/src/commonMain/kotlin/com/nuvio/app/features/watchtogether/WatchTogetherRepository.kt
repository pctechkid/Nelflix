package com.nuvio.app.features.watchtogether

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

private const val WatchTogetherDriftCorrectionThresholdMs = 3_500L

data class WatchTogetherRoomState(
    val roomId: String,
    val roomCode: String,
    val isHost: Boolean,
    val title: String,
    val contentMetadata: WatchTogetherContentMetadata,
    val sourceUrl: String,
    val sourceHeaders: Map<String, String>,
    val streamTitle: String,
    val providerName: String,
    val playbackState: WatchTogetherPlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val updatedAtMs: Long,
    val serverNowMs: Long,
    val roomClosed: Boolean,
    val receivedAtMs: Long,
    val memberNames: List<String>,
    val memberCount: Int,
) {
    val expectedPositionMs: Long
        get() {
            if (playbackState != WatchTogetherPlaybackState.Playing) return positionMs
            val localElapsed = WatchProgressClock.nowEpochMs() - receivedAtMs
            val elapsed = (serverNowMs - updatedAtMs + localElapsed).coerceAtLeast(0L)
            val advanced = positionMs + (elapsed * playbackSpeed.coerceAtLeast(0f)).toLong()
            return if (durationMs > 0L) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
        }
}

enum class WatchTogetherPlaybackState(val wireValue: String) {
    Playing("playing"),
    Paused("paused"),
    Loading("loading"),
    Ended("ended"),
}

data class WatchTogetherPlaybackPayload(
    val title: String,
    val contentMetadata: WatchTogetherContentMetadata,
    val sourceUrl: String,
    val sourceHeaders: Map<String, String>,
    val streamTitle: String,
    val providerName: String,
    val playbackState: WatchTogetherPlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
)

@Serializable
data class WatchTogetherContentMetadata(
    @SerialName("content_type") val contentType: String,
    @SerialName("parent_meta_id") val parentMetaId: String,
    @SerialName("parent_meta_type") val parentMetaType: String,
    @SerialName("video_id") val videoId: String? = null,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("episode_thumbnail") val episodeThumbnail: String? = null,
    @SerialName("pause_description") val pauseDescription: String? = null,
)

object WatchTogetherRepository {
    private val log = Logger.withTag("WatchTogether")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val driftCorrectionThresholdMs: Long = WatchTogetherDriftCorrectionThresholdMs

    suspend fun createRoom(
        profileId: Int,
        payload: WatchTogetherPlaybackPayload,
    ): Result<WatchTogetherRoomState> {
        cleanupOldRooms()
        return callRoomRpc("watch_together_create") {
            put("p_profile_id", profileId)
            put("p_payload", json.encodeToJsonElement(payload.toWirePayload()))
        }
    }

    suspend fun joinRoom(
        roomCode: String,
        profileId: Int,
        displayName: String,
    ): Result<WatchTogetherRoomState> {
        cleanupOldRooms()
        return callRoomRpc("watch_together_join") {
            put("p_room_code", roomCode.trim().uppercase())
            put("p_profile_id", profileId)
            put("p_display_name", displayName.trim().take(80))
        }
    }

    suspend fun refreshRoom(roomId: String): Result<WatchTogetherRoomState> =
        callRoomRpc("watch_together_get") {
            put("p_room_id", roomId)
        }

    suspend fun pushState(
        roomId: String,
        payload: WatchTogetherPlaybackPayload,
    ): Result<WatchTogetherRoomState> = callRoomRpc("watch_together_push") {
        put("p_room_id", roomId)
        put("p_payload", json.encodeToJsonElement(payload.toWirePayload()))
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> = runCatching {
        val params = buildJsonObject { put("p_room_id", roomId) }
        SupabaseProvider.client.postgrest.rpc("watch_together_leave", params)
        Unit
    }.onFailure { error ->
        log.w(error) { "Leave room failed" }
    }

    fun canUseWatchTogether(): Boolean {
        val authState = AuthRepository.state.value
        return authState is AuthState.Authenticated && !authState.isAnonymous
    }

    suspend fun cleanupOldRooms(): Result<Unit> = runCatching {
        if (!canUseWatchTogether()) return@runCatching
        SupabaseProvider.client.postgrest.rpc("watch_together_cleanup")
        Unit
    }.onFailure { error ->
        log.w(error) { "watch_together_cleanup failed" }
    }

    private suspend fun callRoomRpc(
        name: String,
        paramsBuilder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Result<WatchTogetherRoomState> = runCatching {
        check(canUseWatchTogether()) { "Sign in to use Watch Together." }
        val params = buildJsonObject(paramsBuilder)
        SupabaseProvider.client.postgrest.rpc(name, params)
            .decodeSingle<WatchTogetherRoomStateDto>()
            .toDomain()
    }.onFailure { error ->
        log.w(error) { "$name failed" }
    }
}

private fun WatchTogetherPlaybackPayload.toWirePayload(): WatchTogetherPayloadDto =
    WatchTogetherPayloadDto(
        title = title,
        contentMetadata = contentMetadata,
        sourceUrl = sourceUrl,
        sourceHeaders = sourceHeaders,
        streamTitle = streamTitle,
        providerName = providerName,
        playbackState = playbackState.wireValue,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        playbackSpeed = playbackSpeed.coerceIn(0.25f, 4f),
    )

@Serializable
private data class WatchTogetherPayloadDto(
    val title: String,
    @SerialName("content_metadata") val contentMetadata: WatchTogetherContentMetadata,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("source_headers") val sourceHeaders: Map<String, String> = emptyMap(),
    @SerialName("stream_title") val streamTitle: String,
    @SerialName("provider_name") val providerName: String,
    @SerialName("playback_state") val playbackState: String,
    @SerialName("position_ms") val positionMs: Long,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("playback_speed") val playbackSpeed: Float,
)

@Serializable
private data class WatchTogetherRoomStateDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("room_code") val roomCode: String,
    @SerialName("is_host") val isHost: Boolean = false,
    val title: String = "",
    @SerialName("content_metadata") val contentMetadata: WatchTogetherContentMetadata? = null,
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("source_headers") val sourceHeaders: Map<String, String> = emptyMap(),
    @SerialName("stream_title") val streamTitle: String = "",
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("playback_state") val playbackState: String = "paused",
    @SerialName("position_ms") val positionMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("playback_speed") val playbackSpeed: Float = 1f,
    @SerialName("updated_at_ms") val updatedAtMs: Long = 0,
    @SerialName("server_now_ms") val serverNowMs: Long = 0,
    @SerialName("room_closed") val roomClosed: Boolean = false,
    @SerialName("member_names") val memberNames: List<String> = emptyList(),
    @SerialName("member_count") val memberCount: Int = 1,
) {
    fun toDomain(): WatchTogetherRoomState =
        WatchTogetherRoomState(
            roomId = roomId,
            roomCode = roomCode,
            isHost = isHost,
            title = title,
            contentMetadata = contentMetadata ?: WatchTogetherContentMetadata(
                contentType = "",
                parentMetaId = "",
                parentMetaType = "",
                title = title,
            ),
            sourceUrl = sourceUrl,
            sourceHeaders = sourceHeaders,
            streamTitle = streamTitle,
            providerName = providerName,
            playbackState = when (playbackState.lowercase()) {
                WatchTogetherPlaybackState.Playing.wireValue -> WatchTogetherPlaybackState.Playing
                WatchTogetherPlaybackState.Loading.wireValue -> WatchTogetherPlaybackState.Loading
                WatchTogetherPlaybackState.Ended.wireValue -> WatchTogetherPlaybackState.Ended
                else -> WatchTogetherPlaybackState.Paused
            },
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            updatedAtMs = updatedAtMs,
            serverNowMs = serverNowMs,
            roomClosed = roomClosed,
            receivedAtMs = WatchProgressClock.nowEpochMs(),
            memberNames = memberNames,
            memberCount = memberCount,
        )
}
