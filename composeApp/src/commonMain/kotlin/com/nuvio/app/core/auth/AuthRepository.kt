package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.storage.LocalAccountDataCleaner
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AuthRepository")

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        val savedAnonId = AuthStorage.loadAnonymousUserId()
        if (savedAnonId != null) {
            _state.value = AuthState.Authenticated(
                userId = savedAnonId,
                email = null,
                isAnonymous = true,
            )
        }

        scope.launch {
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                if (AuthStorage.loadAnonymousUserId() != null) return@collect
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        _state.value = AuthState.Authenticated(
                            userId = user?.id ?: "",
                            email = user?.email,
                            isAnonymous = false,
                        )
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _state.value = AuthState.Unauthenticated
                    }
                    is SessionStatus.Initializing -> {
                        if (savedAnonId == null) _state.value = AuthState.Loading
                    }
                    is SessionStatus.RefreshFailure -> {
                        _state.value = AuthState.Unauthenticated
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun signInAnonymously() {
        _error.value = null
        val userId = Uuid.random().toString()
        AuthStorage.saveAnonymousUserId(userId)
        _state.value = AuthState.Authenticated(
            userId = userId,
            email = null,
            isAnonymous = true,
        )
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        SupabaseProvider.client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }.onFailure { e ->
        log.e(e) { "Email sign-up failed" }
        _error.value = e.toFriendlyAuthError(getString(Res.string.auth_sign_up_failed))
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        SupabaseProvider.client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }.onFailure { e ->
        log.e(e) { "Email sign-in failed" }
        _error.value = e.toFriendlyAuthError(getString(Res.string.auth_sign_in_failed))
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        _error.value = null
        val wasAnonymous = AuthStorage.loadAnonymousUserId() != null
        if (!wasAnonymous) {
            SupabaseProvider.client.auth.signOut()
        }
    }.onFailure { e ->
        log.e(e) { "Sign-out failed" }
        _error.value = e.toFriendlyAuthError(getString(Res.string.auth_sign_out_failed))
    }.also {
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Unauthenticated
        LocalAccountDataCleaner.wipe()
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        _error.value = null
        SupabaseProvider.client.functions.invoke("delete-account")
        SupabaseProvider.client.auth.signOut()
        LocalAccountDataCleaner.wipe()
    }.onFailure { e ->
        log.e(e) { "Account deletion failed" }
        _error.value = e.toFriendlyAuthError(getString(Res.string.auth_account_deletion_failed))
    }

    fun clearError() {
        _error.value = null
    }
}

private fun Throwable.toFriendlyAuthError(fallback: String): String {
    val text = buildString {
        message?.let(::append)
        cause?.message?.let {
            append(' ')
            append(it)
        }
    }.trim()
    val lower = text.lowercase()
    return when {
        lower.contains("invalid_credentials") || lower.contains("invalid login credentials") ->
            "Invalid email or password."
        lower.contains("email not confirmed") ->
            "Please confirm your email before signing in."
        lower.contains("network") || lower.contains("failed to connect") || lower.contains("unable to resolve host") ||
            lower.contains("timeout") || lower.contains("connection") ->
            "Unable to connect. Check your internet connection and try again."
        lower.contains("already registered") || lower.contains("user already registered") ->
            "An account with this email already exists."
        lower.contains("password") && lower.contains("short") ->
            "Password is too short."
        else -> text
            .substringBefore(" URL:")
            .substringBefore(" url:")
            .substringBefore('\n')
            .take(120)
            .takeIf { it.isNotBlank() }
            ?: fallback
    }
}
