package com.aotv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Fontes do TMDB das coleções (formato do Nuvio) ─────────────────────────────

/** Resposta de /list/{id}. A lista mistura filme e série (media_type em cada item). */
data class TmdbListDetailsDto(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    val items: List<TmdbMovieDto> = emptyList(),
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
)

/** Estúdio (/company e /search/company) ou canal de TV (/network). */
data class TmdbCompanyDto(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("logo_path") val logoPath: String? = null,
    @SerializedName("origin_country") val originCountry: String? = null,
)

data class TmdbPersonDto(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("known_for_department") val department: String? = null,
)

/** Palavra-chave (/search/keyword). */
data class TmdbNamedDto(val id: Int = 0, val name: String = "")

/** Streamings de uma região (/watch/providers/movie). */
data class TmdbProvidersDto(val results: List<TmdbProviderDto> = emptyList())

data class TmdbProviderDto(
    @SerializedName("provider_id") val id: Int = 0,
    @SerializedName("provider_name") val name: String = "",
    @SerializedName("logo_path") val logoPath: String? = null,
    @SerializedName("display_priority") val priority: Int = 999,
)
