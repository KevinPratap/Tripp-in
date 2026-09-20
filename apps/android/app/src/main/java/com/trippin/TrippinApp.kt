package com.trippin

import android.app.Application
import android.content.pm.ApplicationInfo
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.trippin.core.network.NetworkModule
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class TrippinApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // The static ApiService accessor needs the application context for the
        // per-install guest session id it sends with every request.
        NetworkModule.init(this)
    }

    /**
     * Coil's own image client, deliberately separate from the API client.
     *
     * This exists because a photograph was arriving on the wire and never appearing on screen, and the
     * reason was the User-Agent. Coil builds on OkHttp, whose default User-Agent is `okhttp/<version>`,
     * and Wikimedia's image service answers that with 403 Forbidden. Measured directly against the
     * exact venue photo the API returns:
     *
     *   okhttp/4.12.0                          -> 403 (126 bytes, text/plain)
     *   Coil/2.6.0, curl/8.5.0, a contact UA    -> 200 (140128 bytes, image/jpeg)
     *
     * Wikimedia's policy asks for a descriptive agent with a contact, so this sends one. It is also
     * why the image client must stay separate from the API client: an image request must never carry
     * the traveller's session token, and this one does not.
     */
    override fun newImageLoader(): ImageLoader {
        val debuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", PHOTO_USER_AGENT)
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .apply {
                if (debuggable) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    private companion object {
        const val PHOTO_USER_AGENT =
            "TrippinAI/1.0 (Android; https://trippin.ai; contact@trippin.ai)"
    }
}
