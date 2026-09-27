package com.trippin.core.network

import com.trippin.core.datastore.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs every request with the stored session, read fresh each time so signing in or out lands on
 * the very next call. An install with no token sends no identity, which is what makes a write answer
 * 401 and the app show the sign-in screen instead of quietly acting as a device.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
        sessionManager.currentToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
