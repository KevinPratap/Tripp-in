package com.trippin.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.common.DataResult
import com.trippin.core.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInStage { ENTER_EMAIL, ENTER_CODE }

data class SignInUiState(
    val email: String = "",
    val stage: SignInStage = SignInStage.ENTER_EMAIL,
    val loading: Boolean = false,
    val token: String = "",
    /** Honest delivery note from the server: this build prints the link to its log, not email. */
    val deliveryNote: String? = null,
    val error: String? = null,
    val signedIn: Boolean = false
) {
    val emailValid: Boolean get() = email.contains("@") && email.substringAfter("@").contains(".")
    val canRequest: Boolean get() = emailValid && !loading
    val canVerify: Boolean get() = token.isNotBlank() && !loading
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onTokenChange(value: String) = _uiState.update { it.copy(token = value, error = null) }

    fun backToEmail() = _uiState.update { it.copy(stage = SignInStage.ENTER_EMAIL, error = null) }

    fun requestLink() {
        val state = _uiState.value
        if (!state.canRequest) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.requestLink(state.email)) {
                is DataResult.Ok -> {
                    // The server says how the link left it. "email" means it is in the traveller's
                    // inbox and the response carries no token, so there is nothing to prefill and the
                    // note says where to look. "console" means no provider is configured and the link
                    // only reached the server log; outside production the response still carries it,
                    // so the code is prefilled rather than asking a developer to dig it out.
                    val emailed = result.value.delivery.lowercase() == "email"
                    val note = if (emailed) {
                        "We sent a sign-in link to ${state.email}. Open it on this device, or paste the code from it below."
                    } else {
                        "No mail provider is configured on the server, so the sign-in code went to its log instead of your inbox."
                    }
                    val tokenFromUrl = extractToken(result.value.loginUrl)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            stage = SignInStage.ENTER_CODE,
                            deliveryNote = note,
                            token = tokenFromUrl ?: it.token
                        )
                    }
                }
                is DataResult.Fail -> _uiState.update {
                    it.copy(loading = false, error = result.error.message)
                }
            }
        }
    }

    fun verify() {
        val state = _uiState.value
        if (!state.canVerify) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.verify(state.token, state.email.ifBlank { null })) {
                is DataResult.Ok -> _uiState.update { it.copy(loading = false, signedIn = true) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(loading = false, error = "That code did not work. Check it and try again.")
                }
            }
        }
    }

    /**
     * Pull the `token` query parameter out of a magic-link URL.
     *
     * Null in, null out: the server withholds the URL whenever the link was emailed, and always in
     * production, so there is usually nothing here to read.
     */
    private fun extractToken(loginUrl: String?): String? {
        if (loginUrl.isNullOrBlank()) return null
        val query = loginUrl.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "token" }
            ?.get(1)
    }
}
