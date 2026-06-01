package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.isAnonymous
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
private data class StoredProfilePayload(
    val userId: String,
    val activeProfileIndex: Int = 1,
    val profiles: List<NuvioProfile> = emptyList(),
)

@Serializable
private data class DirectProfilePinRow(
    @SerialName("pin_enabled") val pinEnabled: Boolean = false,
    @SerialName("pin_hash") val pinHash: String? = null,
)

@Serializable
private data class DirectProfilePinPayload(
    @SerialName("pin_enabled") val pinEnabled: Boolean,
    @SerialName("pin_hash") val pinHash: String? = null,
    @SerialName("pin_updated_at") val pinUpdatedAt: String? = null,
    @SerialName("failed_pin_attempts") val failedPinAttempts: Int = 0,
    @SerialName("pin_locked_until") val pinLockedUntil: String? = null,
)

object ProfileRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ProfileRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var activeProfileIndex: Int = 1
    private var loadedCacheForUserId: String? = null

    val activeProfileId: Int get() = activeProfileIndex

    fun loadCachedProfiles(): Boolean {
        val stored = decodeStoredPayload() ?: return false
        loadedCacheForUserId = stored.userId
        applyStoredPayload(stored)
        ThemeSettingsRepository.onProfileChanged()
        return _state.value.profiles.isNotEmpty()
    }

    fun ensureLoaded(userId: String) {
        if (loadedCacheForUserId == userId && _state.value.isLoaded) return

        val stored = decodeStoredPayload()
        loadedCacheForUserId = userId
        if (stored == null) {
            _state.value = ProfileState()
            activeProfileIndex = 1
            return
        }

        if (stored.userId != userId) {
            _state.value = ProfileState()
            activeProfileIndex = 1
            return
        }

        applyStoredPayload(stored)
    }

    fun clearInMemory() {
        loadedCacheForUserId = null
        activeProfileIndex = 1
        _state.value = ProfileState()
    }

    suspend fun pullProfiles() {
        if (AuthRepository.state.value.isAnonymous) {
            if (!_state.value.isLoaded) {
                _state.value = _state.value.copy(isLoaded = true)
            }
            return
        }
        runCatching {
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_profiles")
            val profiles = result.decodeList<NuvioProfile>()
            _state.value = _state.value.copy(
                profiles = profiles.sortedBy { it.profileIndex },
                isLoaded = true,
                activeProfile = profiles.find { it.profileIndex == activeProfileIndex }
                    ?: profiles.firstOrNull(),
            )
            if (_state.value.activeProfile != null) {
                activeProfileIndex = _state.value.activeProfile!!.profileIndex
            }
            persist()
        }.onFailure { e ->
            log.e(e) { "Failed to pull profiles" }
            if (!_state.value.isLoaded) {
                _state.value = _state.value.copy(isLoaded = true)
            }
        }
    }

    fun selectProfile(profileIndex: Int) {
        activeProfileIndex = profileIndex
        _state.value = _state.value.copy(
            activeProfile = _state.value.profiles.find { it.profileIndex == profileIndex },
        )
        persist()
        WatchedRepository.onProfileChanged(profileIndex)
        TraktSettingsRepository.onProfileChanged()
        LibraryRepository.onProfileChanged(profileIndex)
        WatchProgressRepository.onProfileChanged(profileIndex)
        AddonRepository.onProfileChanged(profileIndex)
        if (com.nuvio.app.core.build.AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.onProfileChanged(profileIndex)
        }
        ThemeSettingsRepository.onProfileChanged()
        PosterCardStyleRepository.onProfileChanged()
        PlayerSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.onProfileChanged()
        HomeRepository.clear()
        MetaScreenSettingsRepository.onProfileChanged()
        ContinueWatchingPreferencesRepository.onProfileChanged()
        EpisodeReleaseNotificationsRepository.onProfileChanged()
        TmdbSettingsRepository.onProfileChanged()
        MdbListSettingsRepository.onProfileChanged()
        TraktAuthRepository.onProfileChanged()
        SearchHistoryRepository.onProfileChanged()
        CollectionRepository.onProfileChanged()
        CollectionMobileSettingsRepository.onProfileChanged()
        DownloadsRepository.onProfileChanged()
    }

    suspend fun pushProfiles(profiles: List<ProfilePushPayload>) {
        if (AuthRepository.state.value.isAnonymous) {
            applyPayloadsLocally(profiles)
            return
        }
        runCatching {
            val params = buildJsonObject {
                put("p_profiles", json.encodeToJsonElement(profiles))
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_profiles", params)
            pullProfiles()
        }.onFailure { e ->
            log.e(e) { "Failed to push profiles" }
        }
    }

    fun isPinEnabled(profileIndex: Int): Boolean =
        _state.value.profiles.firstOrNull { it.profileIndex == profileIndex }?.pinEnabled == true

    suspend fun verifyPin(profileIndex: Int, pin: String): PinVerifyResult {
        if (AuthRepository.state.value.isAnonymous) {
            return PinVerifyResult(unlocked = true)
        }

        return runCatching {
            val params = buildJsonObject {
                put("p_profile_index", profileIndex)
                put("p_pin", pin)
            }
            SupabaseProvider.client.postgrest
                .rpc("profile_pin_verify_v2", params)
                .decodeSingle<PinVerifyResult>()
        }.onFailure { e ->
            log.w(e) { "Failed to verify PIN with v2 RPC for profile $profileIndex; trying legacy RPC" }
        }.getOrElse {
            verifyPinLegacy(profileIndex, pin)
        }
    }

    suspend fun setPin(profileIndex: Int, pin: String): PinVerifyResult {
        if (AuthRepository.state.value.isAnonymous) {
            updatePinStateLocally(profileIndex, enabled = true)
            return PinVerifyResult(unlocked = true)
        }

        return runCatching {
            val params = buildJsonObject {
                put("p_profile_index", profileIndex)
                put("p_pin", pin)
            }
            val result = SupabaseProvider.client.postgrest
                .rpc("profile_pin_set_v2", params)
                .decodeSingle<PinVerifyResult>()
            pullProfiles()
            result
        }.onFailure { e ->
            log.w(e) { "Failed to set PIN with v2 RPC for profile $profileIndex; trying legacy RPC" }
        }.getOrElse {
            setPinLegacy(profileIndex, pin)
        }
    }

    suspend fun clearPin(profileIndex: Int): PinVerifyResult {
        if (AuthRepository.state.value.isAnonymous) {
            updatePinStateLocally(profileIndex, enabled = false)
            return PinVerifyResult(unlocked = true)
        }

        return runCatching {
            val params = buildJsonObject {
                put("p_profile_index", profileIndex)
            }
            val result = SupabaseProvider.client.postgrest
                .rpc("profile_pin_clear_v2", params)
                .decodeSingle<PinVerifyResult>()
            pullProfiles()
            result
        }.onFailure { e ->
            log.w(e) { "Failed to clear PIN with v2 RPC for profile $profileIndex; trying legacy RPC" }
        }.getOrElse {
            clearPinLegacy(profileIndex)
        }
    }

    private suspend fun verifyPinLegacy(profileIndex: Int, pin: String): PinVerifyResult =
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                put("p_pin", pin)
            }
            SupabaseProvider.client.postgrest
                .rpc("verify_profile_pin", params)
                .decodeSingle<PinVerifyResult>()
        }.onFailure { e ->
            log.e(e) { "Failed to verify PIN for profile $profileIndex" }
        }.getOrElse {
            verifyPinDirect(profileIndex, pin)
        }

    private suspend fun setPinLegacy(profileIndex: Int, pin: String): PinVerifyResult =
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                put("p_pin", pin)
                put("p_current_pin", JsonNull)
            }
            SupabaseProvider.client.postgrest.rpc("set_profile_pin", params)
            pullProfiles()
            PinVerifyResult(unlocked = true)
        }.onFailure { e ->
            log.e(e) { "Failed to set PIN for profile $profileIndex" }
        }.getOrElse {
            setPinDirect(profileIndex, pin)
        }

    private suspend fun clearPinLegacy(profileIndex: Int): PinVerifyResult =
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                put("p_current_pin", JsonNull)
            }
            SupabaseProvider.client.postgrest.rpc("clear_profile_pin", params)
            pullProfiles()
            PinVerifyResult(unlocked = true)
        }.onFailure { e ->
            log.e(e) { "Failed to clear PIN for profile $profileIndex" }
        }.getOrElse {
            clearPinDirect(profileIndex)
        }

    private suspend fun verifyPinDirect(profileIndex: Int, pin: String): PinVerifyResult =
        runCatching {
            val row = SupabaseProvider.client.postgrest
                .from("profiles")
                .select {
                    filter { eq("profile_index", profileIndex) }
                    limit(1)
                }
                .decodeSingle<DirectProfilePinRow>()

            when {
                !row.pinEnabled -> PinVerifyResult(unlocked = true)
                row.pinHash == directPinValue(pin) || row.pinHash == pin -> PinVerifyResult(unlocked = true)
                else -> PinVerifyResult(
                    unlocked = false,
                    message = "Incorrect PIN.",
                )
            }
        }.onFailure { e ->
            log.e(e) { "Failed to verify PIN directly for profile $profileIndex" }
        }.getOrElse {
            PinVerifyResult(
                unlocked = false,
                message = "Couldn't verify PIN. Check your connection and try again.",
            )
        }

    private suspend fun setPinDirect(profileIndex: Int, pin: String): PinVerifyResult =
        runCatching {
            SupabaseProvider.client.postgrest
                .from("profiles")
                .update(
                    DirectProfilePinPayload(
                        pinEnabled = true,
                        pinHash = directPinValue(pin),
                    ),
                ) {
                    filter { eq("profile_index", profileIndex) }
                }
            pullProfiles()
            updatePinStateLocally(profileIndex, enabled = true)
            PinVerifyResult(unlocked = true)
        }.onFailure { e ->
            log.e(e) { "Failed to set PIN directly for profile $profileIndex" }
        }.getOrElse {
            PinVerifyResult(
                unlocked = false,
                message = "Couldn't set PIN. Try again.",
            )
        }

    private suspend fun clearPinDirect(profileIndex: Int): PinVerifyResult =
        runCatching {
            SupabaseProvider.client.postgrest
                .from("profiles")
                .update(
                    DirectProfilePinPayload(
                        pinEnabled = false,
                        pinHash = null,
                    ),
                ) {
                    filter { eq("profile_index", profileIndex) }
                }
            pullProfiles()
            updatePinStateLocally(profileIndex, enabled = false)
            PinVerifyResult(unlocked = true)
        }.onFailure { e ->
            log.e(e) { "Failed to clear PIN directly for profile $profileIndex" }
        }.getOrElse {
            PinVerifyResult(
                unlocked = false,
                message = "Couldn't remove PIN lock. Try again.",
            )
        }

    private fun directPinValue(pin: String): String = "plain:$pin"

    suspend fun createProfile(
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        avatarUrl: String? = null,
        usesPrimaryAddons: Boolean = false,
    ) {
        val existing = _state.value.profiles
        val nextIndex = ((1..4).toSet() - existing.map { it.profileIndex }.toSet()).minOrNull() ?: return

        val allPayloads = existing.map { profile ->
            ProfilePushPayload(
                profileIndex = profile.profileIndex,
                name = profile.name,
                avatarColorHex = profile.avatarColorHex,
                usesPrimaryAddons = profile.usesPrimaryAddons,
                usesPrimaryPlugins = profile.usesPrimaryPlugins,
                avatarId = profile.avatarId,
                avatarUrl = profile.avatarUrl,
            )
        } + ProfilePushPayload(
            profileIndex = nextIndex,
            name = name,
            avatarColorHex = avatarColorHex,
            usesPrimaryAddons = usesPrimaryAddons,
            avatarId = avatarId,
            avatarUrl = avatarUrl,
        )

        pushProfiles(allPayloads)
    }

    suspend fun updateProfile(
        profileIndex: Int,
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        avatarUrl: String? = null,
        usesPrimaryAddons: Boolean = false,
    ) {
        val allPayloads = _state.value.profiles.map { profile ->
            if (profile.profileIndex == profileIndex) {
                ProfilePushPayload(
                    profileIndex = profileIndex,
                    name = name,
                    avatarColorHex = avatarColorHex,
                    usesPrimaryAddons = usesPrimaryAddons,
                    avatarId = avatarId,
                    avatarUrl = avatarUrl,
                )
            } else {
                ProfilePushPayload(
                    profileIndex = profile.profileIndex,
                    name = profile.name,
                    avatarColorHex = profile.avatarColorHex,
                    usesPrimaryAddons = profile.usesPrimaryAddons,
                    usesPrimaryPlugins = profile.usesPrimaryPlugins,
                    avatarId = profile.avatarId,
                    avatarUrl = profile.avatarUrl,
                )
            }
        }

        pushProfiles(allPayloads)
    }

    suspend fun deleteProfile(profileIndex: Int) {
        if (AuthRepository.state.value.isAnonymous) {
            val remaining = _state.value.profiles.filter { it.profileIndex != profileIndex }
            _state.value = _state.value.copy(
                profiles = remaining,
                activeProfile = if (_state.value.activeProfile?.profileIndex == profileIndex) remaining.firstOrNull() else _state.value.activeProfile,
            )
            if (_state.value.activeProfile != null) {
                activeProfileIndex = _state.value.activeProfile!!.profileIndex
            }
            persist()
            return
        }
        runCatching {
            val params = buildJsonObject { put("p_profile_id", profileIndex) }
            SupabaseProvider.client.postgrest.rpc("sync_delete_profile_data", params)
            pullProfiles()
        }.onFailure { e ->
            log.e(e) { "Failed to delete profile $profileIndex" }
        }
    }

    private fun applyPayloadsLocally(payloads: List<ProfilePushPayload>) {
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        val profiles = payloads.map { p ->
            NuvioProfile(
                id = "",
                userId = authState.userId,
                profileIndex = p.profileIndex,
                name = p.name,
                avatarColorHex = p.avatarColorHex,
                avatarId = p.avatarId,
                avatarUrl = p.avatarUrl,
                usesPrimaryAddons = p.usesPrimaryAddons,
                usesPrimaryPlugins = p.usesPrimaryPlugins,
            )
        }.sortedBy { it.profileIndex }
        _state.value = _state.value.copy(
            profiles = profiles,
            isLoaded = true,
            activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(),
        )
        if (_state.value.activeProfile != null) {
            activeProfileIndex = _state.value.activeProfile!!.profileIndex
        }
        persist()
    }

    private fun updatePinStateLocally(profileIndex: Int, enabled: Boolean) {
        val profiles = _state.value.profiles.map { profile ->
            if (profile.profileIndex == profileIndex) {
                profile.copy(pinEnabled = enabled, pinLockedUntil = null)
            } else {
                profile
            }
        }
        _state.value = _state.value.copy(
            profiles = profiles,
            activeProfile = profiles.find { it.profileIndex == activeProfileIndex },
        )
        persist()
    }

    private fun decodeStoredPayload(): StoredProfilePayload? {
        val payload = ProfileStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return null

        return runCatching {
            json.decodeFromString<StoredProfilePayload>(payload)
        }.getOrNull()
    }

    private fun applyStoredPayload(stored: StoredProfilePayload) {
        val profiles = stored.profiles.sortedBy { it.profileIndex }
        activeProfileIndex = stored.activeProfileIndex
        _state.value = ProfileState(
            profiles = profiles,
            activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(),
            isLoaded = profiles.isNotEmpty(),
        )
        _state.value.activeProfile?.let { activeProfileIndex = it.profileIndex }
    }

    private fun persist() {
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        ProfileStorage.savePayload(
            json.encodeToString(
                StoredProfilePayload(
                    userId = authState.userId,
                    activeProfileIndex = activeProfileIndex,
                    profiles = _state.value.profiles,
                ),
            ),
        )
    }
}
