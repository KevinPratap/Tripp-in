package com.trippin.core.common

import retrofit2.HttpException
import java.io.IOException

/**
 * A failure a person can read, with the one distinction the app acts on: whether the session is dead.
 * The cause is only ever named when the app actually knows it. A request that failed for an unknown
 * reason says so plainly rather than guessing between a dead network and a missing resource.
 */
sealed class AppError(val message: String) {
    /** The stored session is no longer good (HTTP 401). The app signs out on this. */
    data object Unauthorized : AppError("Your session has expired. Sign in again.")

    /** The phone could not reach the server at all. */
    data object Offline : AppError("Could not reach Tripp'in. Check your connection.")

    /** The server answered, but with an error. */
    data class Server(val detail: String) : AppError(detail)

    /** Anything the app could not classify. */
    data object Unknown : AppError("Something went wrong. Try again.")
}

/** The outcome of a repository call: a value, or a readable error. */
sealed interface DataResult<out T> {
    data class Ok<T>(val value: T) : DataResult<T>
    data class Fail(val error: AppError) : DataResult<Nothing>
}

/** True when this failure is an expired session, so the caller can drop the stored token. */
val DataResult<*>.isUnauthorized: Boolean
    get() = this is DataResult.Fail && error is AppError.Unauthorized

/**
 * Run a network call and classify any failure into an [AppError]. This is the one place an
 * exception becomes a message, so no screen ever renders a raw "HTTP 404" or a stack trace.
 */
suspend fun <T> runNetwork(block: suspend () -> T): DataResult<T> = try {
    DataResult.Ok(block())
} catch (e: HttpException) {
    if (e.code() == 401) {
        DataResult.Fail(AppError.Unauthorized)
    } else {
        DataResult.Fail(AppError.Server("The server could not complete that (${e.code()})."))
    }
} catch (_: IOException) {
    DataResult.Fail(AppError.Offline)
} catch (_: Exception) {
    DataResult.Fail(AppError.Unknown)
}
