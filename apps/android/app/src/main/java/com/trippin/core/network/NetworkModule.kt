package com.trippin.core.network

import android.content.Context
import android.content.pm.ApplicationInfo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val BASE_URL = "https://backend-production-011e.up.railway.app/"

    /**
     * Application context captured once from TrippinApp, so the static `apiService`
     * accessor the screens use can build the client without Hilt injection.
     */
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val apiService: ApiService by lazy {
        val context = requireNotNull(appContext) {
            "NetworkModule.init(context) must be called from TrippinApp.onCreate()"
        }
        provideApiService(provideRetrofit(provideOkHttpClient(context), provideJson()))
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private const val SESSION_PREFS = "trippin_session"
    private const val SESSION_KEY = "guest_session"

    /**
     * Every install keeps its own guest session id. The API allows anonymous reads
     * (so a shared trip link works) but wants an identity for writes, and this is it.
     */
    private fun guestSessionId(context: Context): String {
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        prefs.getString(SESSION_KEY, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(SESSION_KEY, created).apply()
        return created
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val sessionId = guestSessionId(context)
        val debuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain
                    .request()
                    .newBuilder()
                    .header("X-Guest-Session", sessionId)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .apply {
                // Bodies are only dumped on debug builds, never on a release install.
                if (debuggable) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
