package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.isUnauthorized
import com.trippin.core.common.runNetwork
import com.trippin.core.datastore.SessionManager
import com.trippin.core.datastore.SignedInAccount
import com.trippin.core.network.ApiService
import com.trippin.core.network.RequestMagicLinkDto
import com.trippin.core.network.RequestedMagicLinkDto
import com.trippin.core.network.VerifiedSessionDto
import com.trippin.core.network.VerifyMagicLinkDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Identity: request a magic link, verify it into a session, sign out, and re-check a stored token. */
@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    val account: Flow<SignedInAccount?> = sessionManager.account

    suspend fun requestLink(email: String): DataResult<RequestedMagicLinkDto> =
        runNetwork { api.requestMagicLink(RequestMagicLinkDto(email = email.trim())) }

    suspend fun verify(token: String, email: String?): DataResult<VerifiedSessionDto> {
        val result = runNetwork {
            api.verifyMagicLink(VerifyMagicLinkDto(token = token.trim(), email = email?.trim()))
        }
        if (result is DataResult.Ok) sessionManager.save(result.value)
        return result
    }

    suspend fun signOut() = sessionManager.clear()

    /**
     * Check a stored token against the server. GET /me/trips is identity-gated, so a 401 means the
     * token is dead and we drop it; any other failure (offline) is not a failed session and is kept.
     */
    suspend fun validateSession(): DataResult<Unit> {
        val result = runNetwork { api.getMyTrips() }
        if (result.isUnauthorized) sessionManager.clear()
        return when (result) {
            is DataResult.Ok -> DataResult.Ok(Unit)
            is DataResult.Fail -> result
        }
    }
}
