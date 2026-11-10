package com.aotv.app.data.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionModelsTest {

    private val nuvio = """
        [
          {
            "id": "c1", "title": "Estúdios", "pinToTop": true, "viewMode": "ROWS", "showAllTab": false,
            "folders": [
              {
                "id": "f1", "title": "Marvel", "coverImageUrl": "https://img/marvel.jpg", "tileShape": "wide",
                "sources": [
                  {"provider": "tmdb", "tmdbSourceType": "COMPANY", "tmdbId": 420, "title": "Marvel Studios", "mediaType": "movie", "sortBy": "original"},
                  {"provider": "addon", "addonId": "com.linvo.cinemeta", "type": "movie", "catalogId": "top", "genre": "none"},
                  {"provider": "trakt", "traktListId": 123456, "mediaType": "movie", "sortBy": "rank", "sortHow": "asc"},
                  {"provider": "tmdb", "tmdbSourceType": "DISCOVER", "mediaType": "tv", "sortBy": "primary_release_date.desc",
                   "filters": {"withGenres": "16", "voteAverageGte": 7.5, "year": 2020, "withWatchProviders": "8", "watchRegion": "BR"}}
                ]
              },
              {
                "id": "f2", "title": "Antigo", "coverEmoji": "🎬",
                "catalogSources": [{"addonId": "org.x", "type": "series", "catalogId": "pop", "genre": "Drama"}]
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun leJsonDoNuvioComTodosOsTiposDeFonte() {
        val list = CollectionsJson.parse(nuvio).getOrThrow()
        val c = list.single()
        assertTrue(c.pinToTop)
        assertEquals(false, c.tabbed)
        val f1 = c.folders[0]
        assertEquals("landscape", f1.shape)
        assertEquals(TmdbSourceType.COMPANY, f1.sources[0].tmdbType)
        assertEquals(420, f1.sources[0].tmdbId)
        assertNull("genre none vira sem gênero", f1.sources[1].genre)
        assertTrue(f1.sources[2].isTrakt)
        assertEquals(123456L, f1.sources[2].traktListId)
        assertEquals(7.5, f1.sources[3].filters?.voteAverageGte)
        assertTrue(f1.sources[3].tv)
        // Formato antigo (catalogSources) vira fonte de addon
        val legacy = c.folders[1].sources.single()
        assertTrue(legacy.isAddon)
        assertEquals("Drama", legacy.genre)
        assertEquals("🎬", c.folders[1].coverEmoji)
    }

    @Test
    fun idaEVoltaNaoPerdeNada() {
        val list = CollectionsJson.parse(nuvio).getOrThrow()
        assertEquals(list, CollectionsJson.parse(CollectionsJson.encode(list)).getOrThrow())
        assertEquals(list, CollectionsJson.parse("""{"collections": ${CollectionsJson.encode(list, pretty = true)}}""").getOrThrow())
    }

    @Test
    fun recusaOQueONuvioRecusa() {
        fun err(json: String) = CollectionsJson.parse(json).exceptionOrNull()?.message
        assertEquals("O JSON está vazio", err("  "))
        assertEquals("Há duas coleções com o id a", err("""[{"id":"a","title":"A"},{"id":"a","title":"B"}]"""))
        assertEquals("A coleção a está sem título", err("""[{"id":"a","title":" "}]"""))
        assertEquals("A fonte 1 de P tem lista do Trakt sem id", err("""[{"id":"a","title":"A","folders":[{"id":"p","title":"P","sources":[{"provider":"trakt"}]}]}]"""))
        assertEquals("A fonte 1 de P está incompleta", err("""[{"id":"a","title":"A","folders":[{"id":"p","title":"P","sources":[{"provider":"addon","addonId":"x"}]}]}]"""))
        assertTrue(err("não é json")!!.contains("não é um JSON"))
    }

    @Test
    fun montaODiscoverIgualAoNuvio() {
        val company = CollectionSource(provider = "tmdb", tmdbSourceType = "COMPANY", tmdbId = 420, sortBy = "original")
        assertEquals(mapOf("page" to "2", "sort_by" to "popularity.desc", "with_companies" to "420"), TmdbCollectionRules.discoverQuery(company, 2))

        val network = CollectionSource(provider = "tmdb", tmdbSourceType = "NETWORK", tmdbId = 213, sortBy = "primary_release_date.desc")
        val nq = TmdbCollectionRules.discoverQuery(network, 1)
        assertEquals("213", nq["with_networks"])
        assertEquals("first_air_date.desc", nq["sort_by"])

        val discover = CollectionsJson.parse(nuvio).getOrThrow()[0].folders[0].sources[3]
        val dq = TmdbCollectionRules.discoverQuery(discover, 1)
        assertEquals("16", dq["with_genres"])
        assertEquals("7.5", dq["vote_average.gte"])
        assertEquals("2020", dq["first_air_date_year"])
        assertEquals("8", dq["with_watch_providers"])
        assertEquals("BR", dq["watch_region"])
        assertEquals("flatrate|free|ads|rent|buy", dq["with_watch_monetization_types"])
    }

    @Test
    fun entendeIdOuLinkDoTmdb() {
        assertEquals(10, TmdbCollectionRules.parseTmdbId(" 10 "))
        assertEquals(8204, TmdbCollectionRules.parseTmdbId("https://www.themoviedb.org/list/8204-marvel"))
        assertEquals(1241, TmdbCollectionRules.parseTmdbId("https://www.themoviedb.org/collection/1241-harry-potter-collection"))
        assertEquals(5, TmdbCollectionRules.parseTmdbId("https://x.com/?id=5"))
        assertNull(TmdbCollectionRules.parseTmdbId("marvel"))
    }
}
