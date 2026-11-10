package com.aotv.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CACHE_MAX_AGE = 300 // 5 min de cache HTTP curto

    @Provides @Singleton
    fun provideOkHttpClient(@ApplicationContext ctx: Context): OkHttpClient =
        OkHttpClient.Builder()
            // DNS-over-HTTPS Cloudflare: contorna bloqueio de DNS do provedor.
            .dns(com.aotv.app.data.remote.AppDns.cloudflare)
            .callTimeout(45, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Cache HTTP em disco: reabrir o app (ou trocar de modo, que reinicia o processo)
            // não refaz a chamada — resposta vem do disco quase instantânea.
            .cache(Cache(File(ctx.cacheDir, "api_http_cache"), 20L * 1024 * 1024))
            // O backend manda no-store/no-cache; reescrevemos p/ permitir cache curto.
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Expires")
                    .header("Cache-Control", "public, max-age=$CACHE_MAX_AGE")
                    .build()
            }
            // Logging leve: BODY logava o JSON inteiro de ~300 canais a cada chamada
            // (lento em TV box). BASIC em debug, nada em release.
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (com.aotv.app.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                            else HttpLoggingInterceptor.Level.NONE
                },
            )
            .build()
}
