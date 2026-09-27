package com.trippin

import android.app.Application
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class TrippinApp : Application(), ImageLoaderFactory {

    /**
     * Coil's own image client, deliberately separate from the API client.
     *
     * A venue photograph was arriving on the wire and never appearing on screen, and the reason was
     * the User-Agent: Wikimedia's image service answers OkHttp's default `okhttp/<version>` with 403,
     * but a descriptive agent with a contact with 200. This sends that agent. It also must stay
     * separate from the API client so an image request never carries the traveller's session token,
     * and this one does not.
     */
    override fun newImageLoader(): ImageLoader {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", PHOTO_USER_AGENT)
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .apply { if (debuggable) logger(DebugLogger()) }
            .build()
    }

    private companion object {
        const val PHOTO_USER_AGENT =
            "TrippinAI/2.0 (Android; https://trippin.ai; contact@trippin.ai)"
    }
}
