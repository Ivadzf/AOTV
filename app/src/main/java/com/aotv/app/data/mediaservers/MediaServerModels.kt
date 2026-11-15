package com.aotv.app.data.mediaservers

/** Servidores de mídia pessoais (Lote 10, v1.20.0). Nada é embutido: cada pessoa conecta o SEU servidor com a PRÓPRIA URL e as PRÓPRIAS credenciais. */
enum class MediaServerKind(
    val id: String,
    val label: String,
    val short: String,
) {
    JELLYFIN("jellyfin", "Jellyfin", "JF"),
    EMBY("emby", "Emby", "EM"),
    PLEX("plex", "Plex", "PX");

    companion object {
        fun of(id: String?): MediaServerKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Servidor conectado. [token] é o token de acesso do usuário (Jellyfin/Emby)
 * ou o token do Plex (plex.tv); [userId] é o id do usuário no servidor.
 * Vive em SharedPreferences própria (`aotv_mediaservers`) e NUNCA entra no
 * backup (mesma regra do debrid — ver AotvBackup).
 */
data class MediaServerAccount(
    val id: String,
    val kind: MediaServerKind,
    /** Nome que aparece na tela (o nome do servidor quando ele informa, senão o endereço). */
    val label: String,
    /** Endereço do servidor, sem barra no fim (ex.: http://192.168.1.10:8096). */
    val baseUrl: String,
    val username: String = "",
    val token: String = "",
    val userId: String = "",
)

/** Servidor Plex descoberto pelo plex.tv (o usuário escolhe um para conectar). */
data class DiscoveredPlexServer(
    val name: String,
    /** Endereço do servidor (https quando disponível, senão http local). */
    val uri: String,
    val machineId: String = "",
    val owned: Boolean = false,
    /** Token de acesso a ESTE servidor (o plex.tv devolve um por servidor). */
    val token: String = "",
)

/** Biblioteca do servidor (filmes, séries ou mista). */
data class MediaLibrary(
    val id: String,
    val serverId: String,
    val title: String,
    /** "movie", "series" ou "mixed" (o servidor não disse o tipo). */
    val type: String,
)

/** Filme, série, temporada ou episódio de um servidor. Só traz o que o servidor devolveu. */
data class MediaItem(
    val id: String,
    val serverId: String,
    val libraryId: String = "",
    val title: String,
    /** "movie", "series", "season" ou "episode". */
    val type: String,
    val year: Int = 0,
    /** Sinopse do servidor; vazio quando ele não devolveu. */
    val overview: String = "",
    /** URL do pôster (com token quando o servidor pede); vazio sem imagem. */
    val posterUrl: String = "",
    val backdropUrl: String = "",
    /** Duração em minutos; 0 quando o servidor não informou. */
    val runtimeMin: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    /** Série a que o episódio/temporada pertence (id no servidor). */
    val seriesId: String = "",
    val seriesTitle: String = "",
    val played: Boolean = false,
    /** URL direta do vídeo no servidor (com token); montada na hora de tocar. */
    val streamUrl: String = "",
)
