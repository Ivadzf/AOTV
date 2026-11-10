package com.aotv.app.di

import android.content.Context
import androidx.room.Room
import com.aotv.app.data.local.AppDatabase
import com.aotv.app.data.local.dao.MovieHistoryDao
import com.aotv.app.data.local.dao.WatchedEpisodeDao
import com.aotv.app.data.local.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "aotv.db")
            // 4 para 5 e 5 para 6 têm migração explícita, que preserva os dados. Só as versões
            // antigas de antes do lançamento (1 a 3) ainda recriam o banco do zero.
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            // Sideload de um APK com banco mais ANTIGO (ex. adb install -d p/ testar build velho)
            // não pode derrubar o app com IllegalStateException na abertura.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()
    @Provides fun provideMovieHistoryDao(db: AppDatabase): MovieHistoryDao = db.movieHistoryDao()
    @Provides fun provideWatchedEpisodeDao(db: AppDatabase): WatchedEpisodeDao = db.watchedEpisodeDao()
    @Provides fun provideDownloadDao(db: AppDatabase): com.aotv.app.data.local.dao.DownloadDao = db.downloadDao()
}
