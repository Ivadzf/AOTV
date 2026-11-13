package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Salva o progresso de reprodução (posição + fonte) do player de filme/episódio.
 * Usado pelo MoviePlayerScreen para o "Continuar assistindo" retomar no ponto e fonte exatos.
 */
@HiltViewModel
class PlayerProgressViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val prefs: PlatformPrefs,
    private val trakt: com.aotv.app.data.remote.TraktService,
) : ViewModel() {

    /**
     * Modo privado (Lote 2, v1.10.0): assistir não grava nada no perfil —
     * nem progresso, nem Continuar assistindo, nem episódio visto, nem
     * scrobble pro Trakt. O bloqueio mora AQUI (e no MovieDetailViewModel),
     * no ponto central por onde passam TODOS os salvamentos do player (poll
     * de 10s, saída, ON_STOP, troca de episódio/fonte): nenhum deles precisa
     * saber do modo privado. Ler continua liberado — o progresso ANTERIOR
     * (de antes de ligar o privado) segue valendo e o player retoma nele.
     * Abertura aprendida (learnIntro) e som de outra fonte (saveExtAudio)
     * NÃO são bloqueados: não são progresso nem histórico, são metadados
     * de reprodução da série/título (o marcador de abertura é do aparelho,
     * não do perfil, e não revela o que foi assistido).
     */
    private val privateMode get() = prefs.privateMode

    /** Avisa o Trakt (start, pause ou stop). Não faz nada sem conta conectada. */
    fun scrobble(action: String, tmdbId: Int, type: String, season: Int, episode: Int, positionMs: Long, durationMs: Long) {
        if (privateMode) return
        if (durationMs <= 0) return
        trakt.scrobble(action, tmdbId, type, season, episode, positionMs * 100.0 / durationMs)
    }

    /** Abertura aprendida da série (inicioMs, fimMs). null = ainda não aprendeu. */
    fun introMarker(tmdbId: Int): Pair<Long, Long>? = prefs.introMarker(tmdbId)

    /**
     * Aprende a abertura a partir de um pulo manual do usuário. Só aceita salto pra frente
     * de 20s a 4min que começa nos primeiros 8min do episódio — é o formato de abertura.
     * Volta true quando gravou.
     */
    fun learnIntro(tmdbId: Int, fromMs: Long, toMs: Long): Boolean {
        val jump = toMs - fromMs
        if (tmdbId <= 0 || fromMs < 0 || fromMs > 8 * 60_000L) return false
        if (jump < 20_000L || jump > 4 * 60_000L) return false
        prefs.saveIntroMarker(tmdbId, fromMs, toMs)
        return true
    }

    var subSize: Float
        get() = prefs.subSize
        set(v) { prefs.subSize = v }
    var subBg: Boolean
        get() = prefs.subBg
        set(v) { prefs.subBg = v }
    /** Cor do texto da legenda (ARGB; branco por padrão). v1.9.0. */
    var subColor: Int
        get() = prefs.subColor
        set(v) { prefs.subColor = v }
    /** Opacidade do fundo da legenda (0,5–0,9; 0,78 por padrão). v1.9.0. */
    var subBgOpacity: Float
        get() = prefs.subBgOpacity
        set(v) { prefs.subBgOpacity = v }
    /** Contorno preto ao redor do texto quando não há fundo. v1.9.0. */
    var subOutline: Boolean
        get() = prefs.subOutline
        set(v) { prefs.subOutline = v }
    /** Fonte da legenda ("default", "monospace", "serif" ou "sans"). v1.9.0. */
    var subFont: String
        get() = prefs.subFont
        set(v) { prefs.subFont = v }
    /** Velocidade de reprodução salva do perfil (0,5x–2x; 1x por padrão). v1.9.0. */
    var playerSpeed: Float
        get() = prefs.playerSpeed
        set(v) { prefs.playerSpeed = v }
    var subtitleOn: Boolean
        get() = prefs.subtitleOn
        set(v) { prefs.subtitleOn = v }
    val subLang: String get() = prefs.subLang

    /** Som de outra fonte salvo para o título, ou null. */
    fun extAudio(key: String): com.aotv.app.data.local.ExtAudioPick? = prefs.extAudioPicks[key]

    /** Grava (ou apaga, com [sig] null) a escolha de som do título. Guarda no máximo 60 títulos. */
    fun saveExtAudio(key: String, sig: String?, delaySec: Double) {
        val all = prefs.extAudioPicks.toMutableMap()
        if (sig == null) all.remove(key)
        else all[key] = com.aotv.app.data.local.ExtAudioPick(sig, delaySec, System.currentTimeMillis())
        prefs.extAudioPicks = keepNewest(all)
    }

    fun save(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        positionMs: Long,
        durationMs: Long,
        lastEpisode: String,
        sourceUrl: String,
        addon: String = "",
    ) {
        // Modo privado: o poll de 10s, a saída e o ON_STOP passam todos por aqui.
        if (privateMode) return
        if (tmdbId <= 0 || sourceUrl.isBlank() || positionMs <= 0) return
        viewModelScope.launch {
            library.saveProgress(
                tmdbId = tmdbId, type = type, title = title, posterUrl = posterUrl,
                positionMs = positionMs, durationMs = durationMs,
                lastEpisode = lastEpisode, lastSourceUrl = sourceUrl, lastAddon = addon,
            )
        }
    }

    /** Addon escolhido da última vez que essa série/filme tocou, ou "" se nunca. Para o próximo
     * episódio abrir já na mesma fonte, sem o usuário escolher de novo a cada troca. */
    suspend fun lastAddon(tmdbId: Int, type: String): String {
        if (tmdbId <= 0) return ""
        return runCatching { library.getPlayback(tmdbId, type)?.lastAddon }.getOrNull().orEmpty()
    }

    // ── Última fonte escolhida à mão por título (Lote 4 item 6, v1.12.0) ────
    // Identidade estável = addon + qualidade (o rótulo muda a cada episódio e a
    // URL resolvida muda a cada busca, então nenhum dos dois serve de chave —
    // ver [pickPreferredSource]). NUNCA guarda URL (token/assinatura).
    // LER é liberado no modo privado (só lê uma pref, como a posição salva);
    // GRAVAR é bloqueado, como todo o resto que o modo privado impede.

    /** Última fonte escolhida à mão neste título, ou null (sem escolha ou pref desligada). */
    fun lastSourcePick(tmdbId: Int, type: String): com.aotv.app.data.local.LastSourcePick? {
        if (tmdbId <= 0 || !prefs.playerReuseLastSource) return null
        return prefs.lastSourcePicks["$type:$tmdbId"]
    }

    /** Grava a escolha à mão da fonte do título. No-op no modo privado, com a pref desligada ou sem addon. */
    fun saveLastSourcePick(tmdbId: Int, type: String, addon: String, qualityRank: Int) {
        if (privateMode || !prefs.playerReuseLastSource) return
        if (tmdbId <= 0 || addon.isBlank()) return
        val all = prefs.lastSourcePicks.toMutableMap()
        all["$type:$tmdbId"] = com.aotv.app.data.local.LastSourcePick(addon, qualityRank, System.currentTimeMillis())
        prefs.lastSourcePicks = all
    }

    /**
     * Posição salva deste conteúdo, ou 0.
     *
     * Serve pra quando o player é aberto por um caminho que não informa o tempo (clique direto
     * numa fonte da lista, botão dublado/legendado, atalho da TV). Antes só o botão principal
     * mandava a posição, e por qualquer outro caminho o título abria do zero.
     *
     * [lastEpisode] vazio = filme (vale qualquer progresso salvo). Preenchido = só retoma se o
     * progresso salvo for DESSE episódio, senão T2:E1 herdaria o tempo de T1:E8.
     */
    suspend fun savedPosition(tmdbId: Int, type: String, lastEpisode: String): Long {
        if (tmdbId <= 0) return 0
        val pb = runCatching { library.getPlayback(tmdbId, type) }.getOrNull() ?: return 0
        if (lastEpisode.isNotBlank() && pb.lastEpisode != lastEpisode) return 0
        // Quase no fim conta como visto: recomeça em vez de abrir nos créditos.
        if (pb.durationMs > 0 && pb.positionMs >= pb.durationMs * 0.95) return 0
        return pb.positionMs.coerceAtLeast(0L)
    }

    /** Marca o episódio como VISTO (chamado ao passar de 90% da duração). */
    fun markWatched(tmdbId: Int, season: Int, episode: Int) {
        if (privateMode) return
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return
        viewModelScope.launch { library.markEpisodeWatched(tmdbId, season, episode) }
    }
}
