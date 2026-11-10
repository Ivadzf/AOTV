package com.aotv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aotv.app.data.local.dao.*
import com.aotv.app.data.local.entity.*

@Database(
    entities = [
        WatchlistEntity::class,
        MovieHistoryEntity::class,
        WatchedEpisodeEntity::class,
        DownloadEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun movieHistoryDao(): MovieHistoryDao
    abstract fun watchedEpisodeDao(): WatchedEpisodeDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /** Versão 7 guarda o addon da última fonte usada, para o próximo episódio herdar. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movie_history ADD COLUMN lastAddon TEXT NOT NULL DEFAULT ''")
            }
        }
        /**
         * Versão 5 tira as tabelas da TV ao vivo (favoritos de canal, histórico de canal e cache
         * do EPG). Minha Lista, histórico de filmes, episódios vistos e downloads ficam intactos.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS favorites")
                db.execSQL("DROP TABLE IF EXISTS history")
                db.execSQL("DROP TABLE IF EXISTS epg_cache")
            }
        }

        /** Versão 6 separa Minha Lista, histórico e episódios vistos por perfil. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach { db.execSQL(it) }
            }
        }
    }
}

/**
 * SQL da migração 5 para 6, separado para o teste rodar o mesmo texto num SQLite de verdade.
 * A chave primária ganha o perfil, e o SQLite não muda chave de tabela pronta. Então cada tabela
 * é recriada e tudo o que já existia vai para o perfil principal ("main"). Downloads ficam iguais,
 * são do aparelho. Os CREATE são os mesmos que o Room gera para as entidades da versão 6.
 */
internal val MIGRATION_5_6_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `watchlist_new` (`profileId` TEXT NOT NULL DEFAULT 'main', `tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, `year` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `tmdbId`, `type`))",
    "INSERT INTO `watchlist_new` (`profileId`, `tmdbId`, `type`, `title`, `posterUrl`, `year`, `addedAt`) SELECT 'main', `tmdbId`, `type`, `title`, `posterUrl`, `year`, `addedAt` FROM `watchlist`",
    "DROP TABLE `watchlist`",
    "ALTER TABLE `watchlist_new` RENAME TO `watchlist`",

    "CREATE TABLE IF NOT EXISTS `movie_history_new` (`profileId` TEXT NOT NULL DEFAULT 'main', `tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, `lastWatchedAt` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `lastEpisode` TEXT NOT NULL, `lastSourceUrl` TEXT NOT NULL, PRIMARY KEY(`profileId`, `tmdbId`, `type`))",
    "INSERT INTO `movie_history_new` (`profileId`, `tmdbId`, `type`, `title`, `posterUrl`, `lastWatchedAt`, `positionMs`, `durationMs`, `lastEpisode`, `lastSourceUrl`) SELECT 'main', `tmdbId`, `type`, `title`, `posterUrl`, `lastWatchedAt`, `positionMs`, `durationMs`, `lastEpisode`, `lastSourceUrl` FROM `movie_history`",
    "DROP TABLE `movie_history`",
    "ALTER TABLE `movie_history_new` RENAME TO `movie_history`",

    "CREATE TABLE IF NOT EXISTS `watched_episodes_new` (`profileId` TEXT NOT NULL DEFAULT 'main', `seriesTmdbId` INTEGER NOT NULL, `season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `seriesTmdbId`, `season`, `episode`))",
    "INSERT INTO `watched_episodes_new` (`profileId`, `seriesTmdbId`, `season`, `episode`, `watchedAt`) SELECT 'main', `seriesTmdbId`, `season`, `episode`, `watchedAt` FROM `watched_episodes`",
    "DROP TABLE `watched_episodes`",
    "ALTER TABLE `watched_episodes_new` RENAME TO `watched_episodes`",
)
