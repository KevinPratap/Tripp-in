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
        SessionStore.init(context.applicationContext)
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

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val debuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain
                    .request()
                    .newBuilder()
                    .header("Accept", "application/json")

                // The session token is read from the store on every request rather than captured
                // once, so signing in or out takes effect on the very next call. An install with no
                // session sends no identity at all, which is what makes the API answer 401 and the
                // app show the sign-in screen instead of quietly acting as a device.
                SessionStore.token?.let { token ->
                    builder.header("Authorization", "Bearer $token")
                }

                chain.proceed(builder.build())
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
