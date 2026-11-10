package com.aotv.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class ProfileDataTest {

    @Test
    fun principalUsaChaveAntigaEOutrosGanhamPrefixo() {
        assertEquals("ext_audio_v1", scopedKey("ext_audio_v1", MAIN_PROFILE))
        assertEquals("p.pabc.ext_audio_v1", scopedKey("ext_audio_v1", "pabc"))
    }

    // CREATE da versão 5, copiado do AppDatabase_Impl gerado pelo Room antes da mudança.
    private val v5 = listOf(
        "CREATE TABLE IF NOT EXISTS `watchlist` (`tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, `year` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`tmdbId`, `type`))",
        "CREATE TABLE IF NOT EXISTS `movie_history` (`tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, `lastWatchedAt` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `lastEpisode` TEXT NOT NULL, `lastSourceUrl` TEXT NOT NULL, PRIMARY KEY(`tmdbId`, `type`))",
        "CREATE TABLE IF NOT EXISTS `watched_episodes` (`seriesTmdbId` INTEGER NOT NULL, `season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`seriesTmdbId`, `season`, `episode`))",
        "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `localUri` TEXT NOT NULL, `lastEpisode` TEXT NOT NULL, `downloadId` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )

    private fun mem(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.rows(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val n = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..n).map { rs.getObject(it) }) }
        }
    }

    @Test
    fun migracao5Para6LevaTudoProPerfilPrincipal() = mem().use { db ->
        v5.forEach { db.exec(it) }
        db.exec("INSERT INTO watchlist VALUES (603, 'MOVIE', 'Matrix', 'p.jpg', '1999', 111)")
        db.exec("INSERT INTO watchlist VALUES (1399, 'SERIES', 'GoT', 'g.jpg', '2011', 222)")
        db.exec("INSERT INTO movie_history VALUES (603, 'MOVIE', 'Matrix', 'p.jpg', 333, 60000, 8000000, '', 'http://x')")
        db.exec("INSERT INTO watched_episodes VALUES (1399, 1, 3, 444)")
        db.exec("INSERT INTO downloads VALUES ('603:MOVIE:', 603, 'MOVIE', 'Matrix', 'p.jpg', 'http://x', '', '', 9, 'done', 1)")

        MIGRATION_5_6_SQL.forEach { db.exec(it) }

        assertEquals(
            listOf(listOf("main", 603, "MOVIE", "Matrix", "p.jpg", "1999", 111), listOf("main", 1399, "SERIES", "GoT", "g.jpg", "2011", 222)),
            db.rows("SELECT * FROM watchlist ORDER BY tmdbId"),
        )
        assertEquals(
            listOf(listOf("main", 603, "MOVIE", "Matrix", "p.jpg", 333, 60000, 8000000, "", "http://x")),
            db.rows("SELECT * FROM movie_history"),
        )
        assertEquals(listOf(listOf("main", 1399, 1, 3, 444)), db.rows("SELECT * FROM watched_episodes"))
        assertEquals(1, db.rows("SELECT * FROM downloads").size)
        // Sobra nenhuma tabela temporária.
        assertEquals(0, db.rows("SELECT name FROM sqlite_master WHERE name LIKE '%_new'").size)

        // Outro perfil salva o mesmo título sem colidir com o principal.
        db.exec("INSERT INTO watchlist (profileId, tmdbId, type, title, posterUrl, year, addedAt) VALUES ('pkid', 603, 'MOVIE', 'Matrix', '', '', 1)")
        assertEquals(2, db.rows("SELECT * FROM watchlist WHERE tmdbId = 603").size)
    }

    /** Depois da migração a tabela fica igual à que o Room cria do zero (o Room confere isso ao abrir). */
    @Test
    fun esquemaMigradoIgualAoDoRoom() {
        val fresh = mem().use { db ->
            MIGRATION_5_6_SQL.filter { it.startsWith("CREATE") }.forEach { db.exec(it.replace("_new`", "`")) }
            tableInfo(db)
        }
        val migrated = mem().use { db ->
            v5.forEach { db.exec(it) }
            MIGRATION_5_6_SQL.forEach { db.exec(it) }
            tableInfo(db)
        }
        assertEquals(fresh, migrated)
    }

    private fun tableInfo(db: Connection) = listOf("watchlist", "movie_history", "watched_episodes")
        .associateWith { db.rows("PRAGMA table_info(`$it`)") }
}
