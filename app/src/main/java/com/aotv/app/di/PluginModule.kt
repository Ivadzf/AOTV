package com.aotv.app.di

import android.content.Context
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.plugins.PluginEngine
import com.aotv.app.data.plugins.PluginService
import com.aotv.app.data.plugins.PluginStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Runtime dos plugins JS do Nuvio (QuickJS + cookie jar persistente). */
@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun providePluginStore(
        @ApplicationContext context: Context,
        prefs: PlatformPrefs,
        engine: javax.inject.Provider<PluginEngine>,
    ): PluginStore = PluginStore(context, prefs, engine)

    @Provides
    @Singleton
    fun providePluginEngine(
        @ApplicationContext context: Context,
        prefs: PlatformPrefs,
    ): PluginEngine = PluginEngine(context, prefs)

    @Provides
    @Singleton
    fun providePluginService(
        store: PluginStore,
        engine: PluginEngine,
        prefs: PlatformPrefs,
    ): PluginService = PluginService(store, engine, prefs)
}
