package com.aotv.app.di

import com.aotv.app.data.remote.TmdbApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(client: OkHttpClient, prefs: com.aotv.app.data.local.PlatformPrefs): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        // timeouts + cache HTTP (NetworkModule). O filtro infantil fica por cima do cache, então
        // trocar de perfil não reaproveita lista sem filtro.
        .client(client.newBuilder().addInterceptor(com.aotv.app.data.remote.KidsFilterInterceptor { prefs.isKids }).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideTmdbApiService(@Named("tmdb") retrofit: Retrofit): TmdbApiService =
        retrofit.create(TmdbApiService::class.java)

}
