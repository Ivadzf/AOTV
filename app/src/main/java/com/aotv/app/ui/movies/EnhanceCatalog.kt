package com.aotv.app.ui.movies

import org.json.JSONArray
import org.json.JSONObject

// Painel "Melhorar imagem" (Lote mpv 2, v1.37.0) — MODELO.
//
// Base: JOTV Windows 0.2.15 (ver jotv-windows-0215-analysis.md, secoes 2,
// 3.3, 4.4 e 4.5): 3 abas (Upscaler, Filtros, Desempenho), 29 etapas em 5
// grupos, presets Auto + Anime4K A/B/C/A+A/B+B/C+A, qualidade Rapida/Alta,
// saida Ajustar a tela / Sempre 2x / Sempre 4x, Auto por placa (60% do tempo
// do quadro), vigia que desce a qualidade, "Testar a minha placa" e Comparar.
//
// Tudo aqui e SO-MPV: o ExoPlayer nao tem shaders GLSL nem essas opcoes.
// No ExoPlayer o painel abre desabilitado, com explicacao curta (ver
// EnhancePanel) — nunca crasha e nunca toca no caminho do ExoPlayer.
//
// REGRAS DE HONESTIDADE (o catalogo nao inventa nada):
// - Cada etapa mapeia p/ UM arquivo real empacotado em
//   app/src/main/assets/shaders/ (Anime4K v4.0.1, MIT/dominio publico — ver
//   SHADERS-LICENSES.txt) ou p/ uma opcao real do mpv 0.41 (a versao do
//   libmpv 0.1.12, conferida nos strings do .so). Nada de etapa "futura".
// - O mesmo arquivo nunca aparece 2x numa cadeia: os shaders Anime4K usam
//   SAVE com nomes fixos (ex.: conv2d_tf) e repetir o arquivo colide —
//   aviso do proprio documento do Anime4K (md/GLSL_Instructions_Advanced.md
//   v4.x: "You can only use each shader file once"). As cadeias dos presets
//   foram escritas respeitando isso (a 2a ampliacao usa variante S, ou um
//   arquivo diferente — ver presetChain).
// - Custo por etapa: ESTIMATIVA em ms por quadro a 1080p, p/ o Auto e p/ a
//   aba Desempenho. O numero exato so o mpv sabe (vo-passes) e o vigia
//   corrige quando a estimativa erra — a estimativa nunca e apresentada
//   como medida.
// - O mpv 0.41 NAO tem opcao unica "antiring": sao tres
//   (scale-antiring, cscale-antiring, dscale-antiring). O slider unico do
//   painel aplica o mesmo valor nas tres (ver MpvPlayer.applyEnhance).
// - O mpv 0.41 NAO tem parametro de intensidade nos shaders Anime4K (nenhum
//   arquivo define PARAM). A "intensidade da nitidez" do modo Avancado e
//   por isso um controle de 3 posicoes: Desligada / Suave (Deblur_Original)
//   / Forte (Deblur_DoG) — dois shaders reais, com forcas diferentes.

/** Grupo de etapa do catalogo (os 5 grupos do JOTV). */
enum class EnhanceStepGroup(val label: String) {
    CORE("Núcleo"),
    PRE("Pré"),
    POST("Pós"),
    AUTO("Auto"),
    LINE("Efeitos de linha"),
}

/** Familia de etapa — decide conflitos e o corte da saida 2x. */
enum class EnhanceStepFamily {
    /** Amplia x2 (cada uma dobra a resolucao — ver conflictReason). */
    UPSCALER,
    /** Restaura a imagem antes de ampliar (Restore / Restore_Soft). */
    RESTORE,
    /** Reduz ruido (bilateral, sem ampliar). */
    DENOISE,
    /** Utilidade (Fixar brilhos). */
    UTILITY,
    /** Nitidez depois de ampliar (Deblur). */
    DEBLUR,
    /** Reduz p/ o tamanho da tela entre duas ampliacoes. */
    DOWNSCALE,
    /** Escurece / afina linhas. */
    LINE,
}

/** Uma etapa do catalogo. [file] = shader empacotado (null = sem shader). */
data class EnhanceStep(
    val id: String,
    val label: String,
    val sub: String,
    val group: EnhanceStepGroup,
    val family: EnhanceStepFamily,
    val file: String?,
    /** A etapa tem variantes de rede S/M/L (troca de tamanho no Avancado). */
    val networkSizes: Boolean = false,
    /** Custo estimado em ms por quadro a 1080p (estimativa, ver o cabecalho). */
    val costMs: Double,
)

/** As 29 etapas do catalogo, em 5 grupos (8 + 7 + 5 + 2 + 7). */
val ENHANCE_STEPS: List<EnhanceStep> = listOf(
    // Núcleo — ampliadores (8).
    EnhanceStep("upscale_cnn_s", "Ampliar CNN S", "Rede pequena, a mais rápida", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_CNN_x2_S.glsl", networkSizes = true, costMs = 1.2),
    EnhanceStep("upscale_cnn_m", "Ampliar CNN M", "Rede média", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_CNN_x2_M.glsl", networkSizes = true, costMs = 3.0),
    EnhanceStep("upscale_cnn_l", "Ampliar CNN L", "Rede grande, a mais pesada", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_CNN_x2_L.glsl", networkSizes = true, costMs = 6.5),
    EnhanceStep("upscale_denoise_s", "Ampliar + ruído S", "Amplia e reduz ruído sem custo extra", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Denoise_CNN_x2_S.glsl", networkSizes = true, costMs = 1.2),
    EnhanceStep("upscale_denoise_m", "Ampliar + ruído M", "Amplia e reduz ruído sem custo extra", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Denoise_CNN_x2_M.glsl", networkSizes = true, costMs = 3.0),
    EnhanceStep("upscale_denoise_l", "Ampliar + ruído L", "Amplia e reduz ruído sem custo extra", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Denoise_CNN_x2_L.glsl", networkSizes = true, costMs = 6.5),
    EnhanceStep("upscale_dog", "Ampliar DoG", "Sem rede — barato, afia as bordas", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_DoG_x2.glsl", costMs = 0.2),
    EnhanceStep("upscale_original", "Ampliar original", "Sem rede — o mais simples", EnhanceStepGroup.CORE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Original_x2.glsl", costMs = 0.3),
    // Pré — restauração antes de ampliar (7).
    EnhanceStep("restore_s", "Restaurar S", "Tira blur e artefatos (rede pequena)", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_S.glsl", networkSizes = true, costMs = 1.0),
    EnhanceStep("restore_m", "Restaurar M", "Tira blur e artefatos (rede média)", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_M.glsl", networkSizes = true, costMs = 2.5),
    EnhanceStep("restore_l", "Restaurar L", "Tira blur e artefatos (rede grande)", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_L.glsl", networkSizes = true, costMs = 5.5),
    EnhanceStep("restore_soft_s", "Restaurar suave S", "P/ downscaling e serrilhado", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_Soft_S.glsl", networkSizes = true, costMs = 1.0),
    EnhanceStep("restore_soft_m", "Restaurar suave M", "P/ downscaling e serrilhado", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_Soft_M.glsl", networkSizes = true, costMs = 2.5),
    EnhanceStep("restore_soft_l", "Restaurar suave L", "P/ downscaling e serrilhado", EnhanceStepGroup.PRE, EnhanceStepFamily.RESTORE, "Anime4K_Restore_CNN_Soft_L.glsl", networkSizes = true, costMs = 5.5),
    EnhanceStep("clamp", "Fixar brilhos", "Evita estouro e ringing — vai no começo", EnhanceStepGroup.PRE, EnhanceStepFamily.UTILITY, "Anime4K_Clamp_Highlights.glsl", costMs = 0.2),
    // Pós — depois de ampliar (5).
    EnhanceStep("deblur_dog", "Nitidez DoG", "Nitidez forte, sem overshoot", EnhanceStepGroup.POST, EnhanceStepFamily.DEBLUR, "Anime4K_Deblur_DoG.glsl", costMs = 0.3),
    EnhanceStep("deblur_original", "Nitidez original", "Nitidez leve", EnhanceStepGroup.POST, EnhanceStepFamily.DEBLUR, "Anime4K_Deblur_Original.glsl", costMs = 0.3),
    EnhanceStep("denoise_mean", "Ruído (média)", "Filtro bilateral — média", EnhanceStepGroup.POST, EnhanceStepFamily.DENOISE, "Anime4K_Denoise_Bilateral_Mean.glsl", costMs = 0.4),
    EnhanceStep("denoise_median", "Ruído (mediana)", "Filtro bilateral — mediana", EnhanceStepGroup.POST, EnhanceStepFamily.DENOISE, "Anime4K_Denoise_Bilateral_Median.glsl", costMs = 0.4),
    EnhanceStep("denoise_mode", "Ruído (moda)", "Filtro bilateral — moda", EnhanceStepGroup.POST, EnhanceStepFamily.DENOISE, "Anime4K_Denoise_Bilateral_Mode.glsl", costMs = 0.4),
    // Auto — redução entre ampliacoes (2).
    EnhanceStep("downscale_x2", "Redução x2", "Reduz p/ a tela entre as ampliações", EnhanceStepGroup.AUTO, EnhanceStepFamily.DOWNSCALE, "Anime4K_AutoDownscalePre_x2.glsl", costMs = 0.1),
    EnhanceStep("downscale_x4", "Redução x4", "Reduz p/ a tela (cadeia x4)", EnhanceStepGroup.AUTO, EnhanceStepFamily.DOWNSCALE, "Anime4K_AutoDownscalePre_x4.glsl", costMs = 0.1),
    // Efeitos de linha (7).
    EnhanceStep("darken_fast", "Escurecer rápido", "Linhas mais escuras (rápido)", EnhanceStepGroup.LINE, EnhanceStepFamily.LINE, "Anime4K_Darken_Fast.glsl", costMs = 0.3),
    EnhanceStep("darken_hq", "Escurecer HQ", "Linhas mais escuras (qualidade)", EnhanceStepGroup.LINE, EnhanceStepFamily.LINE, "Anime4K_Darken_HQ.glsl", costMs = 0.3),
    EnhanceStep("darken_veryfast", "Escurecer muito rápido", "Linhas mais escuras (o mais rápido)", EnhanceStepGroup.LINE, EnhanceStepFamily.LINE, "Anime4K_Darken_VeryFast.glsl", costMs = 0.3),
    EnhanceStep("thin_fast", "Afinar rápido", "Linhas mais finas (rápido)", EnhanceStepGroup.LINE, EnhanceStepFamily.LINE, "Anime4K_Thin_Fast.glsl", costMs = 0.3),
    EnhanceStep("thin_hq", "Afinar HQ", "Linhas mais finas (qualidade)", EnhanceStepGroup.LINE, EnhanceStepFamily.LINE, "Anime4K_Thin_HQ.glsl", costMs = 0.3),
    EnhanceStep("upscale_deblur_dog", "Ampliar + nitidez DoG", "Amplia x2 com nitidez DoG", EnhanceStepGroup.LINE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Deblur_DoG_x2.glsl", costMs = 0.3),
    EnhanceStep("upscale_deblur_original", "Ampliar + nitidez", "Amplia x2 com nitidez leve", EnhanceStepGroup.LINE, EnhanceStepFamily.UPSCALER, "Anime4K_Upscale_Deblur_Original_x2.glsl", costMs = 0.3),
)

/** Etapa pelo id, ou null quando o id nao existe no catalogo. */
fun enhanceStepById(id: String): EnhanceStep? = ENHANCE_STEPS.firstOrNull { it.id == id }

/**
 * Troca o tamanho da rede de uma etapa CNN (S/M/L) mantendo a base.
 * Devolve o id da variante pedida, ou null quando a etapa nao tem variantes
 * ou o tamanho nao existe p/ ela (ex.: trocar o tamanho de "Fixar brilhos").
 */
fun enhanceStepSiblingSize(stepId: String, size: String): String? {
    val step = enhanceStepById(stepId) ?: return null
    if (!step.networkSizes) return null
    val base = stepId.removeSuffix("_s").removeSuffix("_m").removeSuffix("_l")
    val target = "${base}_" + size.lowercase()
    return ENHANCE_STEPS.firstOrNull { it.id == target }?.id
}

/** Tamanho da rede de uma etapa CNN ("S", "M", "L"), ou "" quando nao tem. */
fun enhanceStepSize(stepId: String): String = when {
    stepId.endsWith("_s") -> "S"
    stepId.endsWith("_m") -> "M"
    stepId.endsWith("_l") -> "L"
    else -> ""
}

/**
 * Arquivos que o app pode copiar de assets p/ filesDir e passar p/ o mpv.
 * ALLOWLIST FECHADA: so estes nomes entram — nunca caminho arbitrario (ver
 * MpvPlayer.installShaders). Inclui os 3 shaders de daltonizacao do AOTV.
 */
val ENHANCE_SHADER_ALLOWLIST: Set<String> = ENHANCE_STEPS.mapNotNull { it.file }.toSet() + setOf(
    "aotv_daltonize_deuteranomalia.glsl",
    "aotv_daltonize_protanomalia.glsl",
    "aotv_daltonize_tritanomalia.glsl",
)

/** Shader de daltonizacao do modo, ou null quando desligado. */
fun daltonizeShaderFile(mode: DaltonismMode): String? = when (mode) {
    DaltonismMode.OFF -> null
    DaltonismMode.DEUTERANOMALY -> "aotv_daltonize_deuteranomalia.glsl"
    DaltonismMode.PROTANOMALY -> "aotv_daltonize_protanomalia.glsl"
    DaltonismMode.TRITANOMALY -> "aotv_daltonize_tritanomalia.glsl"
}

// ── Presets ─────────────────────────────────────────────────────────────────

/** Predefinicao do upscaler. [pinsChain] = a qualidade/saida nao mudam a cadeia. */
enum class EnhancePreset(val id: String, val label: String, val sub: String, val pinsChain: Boolean = false) {
    OFF("off", "Desligado", "Sem upscaler — valem só os filtros"),
    AUTO("auto", "Auto", "Pela placa, pelo vídeo e pela tela"),
    ANIME_A("anime_a", "Anime4K A", "Anime 1080p, com blur e artefatos"),
    ANIME_B("anime_b", "Anime4K B", "Anime 720p, pouco blur, com ringing"),
    ANIME_C("anime_c", "Anime4K C", "Anime 480p ou fonte limpa, sem defeito"),
    ANIME_AA("anime_aa", "Anime4K A+A", "Como o A, mais qualidade — mais pesado"),
    ANIME_BB("anime_bb", "Anime4K B+B", "Como o B, mais qualidade — mais pesado"),
    ANIME_CA("anime_ca", "Anime4K C+A", "Como o C, um pouco mais de qualidade"),
    ANIME_FAST("anime_fast", "Anime rápido", "Uma passada só, redes S — o mais barato", pinsChain = true),
    ANIME_QUALITY("anime_quality", "Anime qualidade", "Redes L na primeira ampliação", pinsChain = true),
    MOVIE("movie", "Filme", "Sem Anime4K — escalador de qualidade + debanding", pinsChain = true),
    SERIES("series", "Série", "Sem Anime4K — escalador leve + debanding", pinsChain = true),
    RETRO("retro", "Retrô / pixel art", "Escalador nearest — sem suavizar o pixel", pinsChain = true),
    LIGHT("light", "Leve", "Só um bom escalador, custo mínimo", pinsChain = true);

    companion object {
        fun byId(id: String): EnhancePreset = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Qualidade da cadeia: Rapida usa variantes S, Alta usa M/L. */
enum class EnhanceQuality(val id: String, val label: String) {
    FAST("fast", "Rápida"),
    HIGH("high", "Alta");

    companion object {
        fun byId(id: String): EnhanceQuality = entries.firstOrNull { it.id == id } ?: FAST
    }
}

/** Resolucao de saida do upscaler. */
enum class EnhanceOutput(val id: String, val label: String) {
    FIT("fit", "Ajustar à tela"),
    X2("2x", "Sempre 2x"),
    X4("4x", "Sempre 4x");

    companion object {
        fun byId(id: String): EnhanceOutput = entries.firstOrNull { it.id == id } ?: FIT
    }
}

/**
 * Filtros que um preset sem Anime4K sugere ao ser ESCOLHIDO (uma vez so —
 * o usuario pode mudar os filtros depois, eles sao campos independentes).
 * Filme: escalador de qualidade + debanding; Serie: escalador leve +
 * debanding; Retro: nearest nos tres (sem suavizar o pixel); Leve: so um
 * bom escalador. Presets Anime4K e Auto nao mexem nos filtros.
 */
fun applyEnhancePresetDefaults(state: EnhanceState): EnhanceState = when (state.presetEnum) {
    EnhancePreset.MOVIE -> state.copy(scale = "ewa_lanczos", cscale = "spline36", dscale = "mitchell", deband = true)
    EnhancePreset.SERIES -> state.copy(scale = "spline36", cscale = "spline36", dscale = "mitchell", deband = true)
    EnhancePreset.RETRO -> state.copy(scale = "nearest", cscale = "nearest", dscale = "nearest", deband = false)
    EnhancePreset.LIGHT -> state.copy(scale = "spline36", cscale = "spline36", dscale = "mitchell", deband = false)
    else -> state
}

/**
 * Cadeia de um preset A/B/C/A+A/B+B/C+A, por qualidade.
 * Segue o documento oficial do Anime4K v4.x (A = Restore -> Upscale ->
 * Upscale; B = Restore_Soft -> Upscale -> Upscale; C = Upscale_Denoise ->
 * Upscale; A+A = Restore -> Upscale -> Restore -> Upscale; B+B =
 * Restore_Soft -> Upscale -> Restore_Soft -> Upscale; C+A = Upscale_Denoise
 * -> Restore -> Upscale), com Clamp_Highlights no comeco e
 * AutoDownscalePre_x2 entre as duas ampliacoes — e sem repetir arquivo
 * (a 2a ampliacao e sempre S; a 2a restauracao usa a variante Soft ou
 * normal oposta a 1a, que e outro arquivo). "Rapida" = variantes S (e uma
 * passada so, sem 2a ampliacao); "Alta" = M na restauracao, L na 1a
 * ampliacao, S na 2a (o documento recomenda rede menor depois de ampliar:
 * o custo quadruplica na resolucao maior).
 */
fun enhancePresetChain(preset: EnhancePreset, quality: EnhanceQuality): List<String> {
    val high = quality == EnhanceQuality.HIGH
    return when (preset) {
        EnhancePreset.ANIME_A -> if (high) listOf("clamp", "restore_m", "upscale_cnn_l", "downscale_x2", "upscale_cnn_s")
            else listOf("clamp", "restore_s", "upscale_cnn_s")
        EnhancePreset.ANIME_B -> if (high) listOf("clamp", "restore_soft_m", "upscale_cnn_l", "downscale_x2", "upscale_cnn_s")
            else listOf("clamp", "restore_soft_s", "upscale_cnn_s")
        EnhancePreset.ANIME_C -> if (high) listOf("clamp", "upscale_denoise_l", "downscale_x2", "upscale_cnn_s")
            else listOf("clamp", "upscale_denoise_s")
        EnhancePreset.ANIME_AA -> if (high) listOf("clamp", "restore_m", "upscale_cnn_l", "downscale_x2", "restore_soft_m", "upscale_cnn_s")
            else listOf("clamp", "restore_s", "upscale_cnn_s", "restore_soft_s")
        EnhancePreset.ANIME_BB -> if (high) listOf("clamp", "restore_soft_m", "upscale_cnn_l", "downscale_x2", "restore_m", "upscale_cnn_s")
            else listOf("clamp", "restore_soft_s", "upscale_cnn_s", "restore_s")
        EnhancePreset.ANIME_CA -> if (high) listOf("clamp", "upscale_denoise_l", "downscale_x2", "restore_m", "upscale_cnn_s")
            else listOf("clamp", "upscale_denoise_s", "restore_s")
        EnhancePreset.ANIME_FAST -> listOf("clamp", "restore_s", "upscale_cnn_s")
        EnhancePreset.ANIME_QUALITY -> listOf("clamp", "restore_l", "upscale_cnn_l", "downscale_x2", "upscale_cnn_s")
        else -> emptyList()
    }
}

/**
 * Motivo quando duas etapas nao combinam, ou null quando a cadeia vale.
 * Regra: duas etapas de ampliacao sem uma reducao (Auto) entre elas
 * ampliam 4x sem necessidade e dobram o custo — o padrao documentado do
 * Anime4K e sempre Upscale -> Reducao -> Upscale. A cadeia tambem nao pode
 * repetir etapa (a UI impede; a guarda cobre cadeia vinda de pref antiga).
 */
fun enhanceChainConflict(steps: List<String>): String? {
    if (steps.size != steps.toSet().size) return "A cadeia repete uma etapa — cada shader só pode aparecer uma vez."
    var seenUpscaler = false
    var seenDownscaleAfterUpscaler = false
    for (id in steps) {
        val step = enhanceStepById(id) ?: continue
        when (step.family) {
            EnhanceStepFamily.UPSCALER -> {
                if (seenUpscaler && !seenDownscaleAfterUpscaler) {
                    return "Duas etapas de ampliação sem uma redução no meio — a imagem amplia 4x sem necessidade e o custo dobra. Ponha uma redução (Auto) entre elas."
                }
                seenUpscaler = true
                seenDownscaleAfterUpscaler = false
            }
            EnhanceStepFamily.DOWNSCALE -> if (seenUpscaler) seenDownscaleAfterUpscaler = true
            else -> Unit
        }
    }
    return null
}

// ── Cadeia avancada (JSON da pref) ──────────────────────────────────────────

/**
 * Cadeia propria do usuario (modo Avancado). [steps] sao ids do catalogo,
 * na ordem; [sharp] e a intensidade da nitidez (0 = desligada, 1 = suave
 * com Deblur_Original, 2 = forte com Deblur_DoG — ver o cabecalho).
 */
data class EnhanceChain(val steps: List<String>, val sharp: Int = 0)

/** Le a cadeia da pref (JSON). null = vazia ou invalida (vale o preset). */
fun parseEnhanceChain(json: String): EnhanceChain? {
    if (json.isBlank()) return null
    return runCatching {
        val o = JSONObject(json)
        val arr = o.optJSONArray("steps") ?: return@runCatching null
        val steps = (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotBlank() && enhanceStepById(it) != null }
        }
        if (steps.isEmpty()) return@runCatching null
        EnhanceChain(steps, o.optInt("sharp", 0).coerceIn(0, 2))
    }.getOrNull()
}

/** Guarda a cadeia na pref (JSON). */
fun encodeEnhanceChain(chain: EnhanceChain): String {
    val o = JSONObject()
    o.put("steps", JSONArray(chain.steps))
    o.put("sharp", chain.sharp.coerceIn(0, 2))
    return o.toString()
}

// ── Estado ──────────────────────────────────────────────────────────────────

/** Debanding: iteracoes, limiar, alcance e grao (faixas do painel JOTV, dentro dos limites do mpv). */
data class DebandCfg(val iter: Int, val threshold: Float, val range: Float, val grain: Float)

/** Le "iter,limiar,alcance,grao" (CSV da pref) com defaults e limites. */
fun parseDebandCfg(csv: String): DebandCfg {
    val parts = csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
    val iter = parts.getOrNull(0)?.toInt()?.coerceIn(1, 8) ?: 4
    val threshold = parts.getOrNull(1)?.coerceIn(0f, 200f) ?: 64f
    val range = parts.getOrNull(2)?.coerceIn(1f, 64f) ?: 16f
    val grain = parts.getOrNull(3)?.coerceIn(0f, 100f) ?: 48f
    return DebandCfg(iter, threshold, range, grain)
}

/** Guarda o debanding como CSV ("iter,limiar,alcance,grao"). */
fun encodeDebandCfg(cfg: DebandCfg): String =
    "${cfg.iter.coerceIn(1, 8)},${cfg.threshold.coerceIn(0f, 200f)},${cfg.range.coerceIn(1f, 64f)},${cfg.grain.coerceIn(0f, 100f)}"

/**
 * Estado do painel "Melhorar imagem" (por perfil). Os 4 sliders de cor
 * (brilho/contraste/saturacao/matiz) NAO moram aqui: vivem no painel "Cor"
 * (ColorFilter.kt) e o painel de Filtros so mostra o atalho p/ la. A gama
 * mora aqui porque so o mpv tem a propriedade `gamma`.
 */
data class EnhanceState(
    val preset: String = EnhancePreset.AUTO.id,
    val quality: String = EnhanceQuality.FAST.id,
    val output: String = EnhanceOutput.FIT.id,
    /** Cadeia propria (JSON); vazia = vale a cadeia do preset. */
    val chainJson: String = "",
    val deband: Boolean = false,
    /** "iter,limiar,alcance,grao" (ver parseDebandCfg). */
    val debandCfg: String = "4,64,16,48",
    val smoothMotion: Boolean = false,
    /** "auto" = o padrao do mpv; ou hable / bt.2390 / mobius / spline. */
    val toneMapping: String = "auto",
    val hdrPeak: Boolean = false,
    /** "auto" (decide pelo stream), "yes" ou "no". */
    val deinterlace: String = "auto",
    val scale: String = "ewa_lanczos",
    val cscale: String = "spline36",
    val dscale: String = "mitchell",
    /** Antiring 0..1 — aplica o mesmo valor em scale/cscale/dscale (ver o cabecalho). */
    val antiring: Float = 0f,
    /** Gama do mpv, -100..100 (0 = sem mudanca). */
    val gamma: Float = 0f,
    /** Renderizador do mpv: "gpu" ou "gpu-next". */
    val vo: String = "gpu",
) {
    val presetEnum get() = EnhancePreset.byId(preset)
    val qualityEnum get() = EnhanceQuality.byId(quality)
    val outputEnum get() = EnhanceOutput.byId(output)
    val debandParts get() = parseDebandCfg(debandCfg)
    val customChain get() = parseEnhanceChain(chainJson)

    /** true = painel sem nenhum efeito (o chip fica apagado). */
    val isDefault: Boolean
        get() = preset == EnhancePreset.AUTO.id && quality == EnhanceQuality.FAST.id &&
            output == EnhanceOutput.FIT.id && chainJson.isBlank() && !deband &&
            !smoothMotion && toneMapping == "auto" && !hdrPeak &&
            deinterlace == "auto" && scale == "ewa_lanczos" && cscale == "spline36" &&
            dscale == "mitchell" && antiring == 0f && gamma == 0f && vo == "gpu"
}

/**
 * Estado com TUDO desligado, nos padroes do mpv — e o que o botao Comparar
 * aplica enquanto pressionado (o video volta ao "sem efeito" p/ comparar).
 * Escaladores nos padroes do mpv (bilinear/bilinear/mitchell), sem deband,
 * sem movimento suave, sem tone-mapping forcado, sem gama, sem shader.
 */
val ENHANCE_OFF_STATE: EnhanceState = EnhanceState(
    preset = EnhancePreset.OFF.id,
    deband = false,
    smoothMotion = false,
    toneMapping = "auto",
    hdrPeak = false,
    deinterlace = "no",
    scale = "bilinear",
    cscale = "bilinear",
    dscale = "mitchell",
    antiring = 0f,
    gamma = 0f,
)

/** Escaladores que o painel oferece (conferidos nos strings do mpv 0.41). */
val ENHANCE_SCALERS: List<String> = listOf(
    "bilinear", "spline36", "ewa_lanczos", "ewa_lanczossharp", "lanczos",
    "mitchell", "hermite", "nearest",
)

/** Tone-mapping que o painel oferece ("auto" = o padrao do mpv). */
val ENHANCE_TONE_MAPPINGS: List<String> = listOf("auto", "bt.2390", "hable", "mobius", "spline")

/** Renderizadores que o painel oferece (os dois existem no mpv 0.41 do AAR). */
val ENHANCE_VOS: List<String> = listOf("gpu", "gpu-next")

// ── Resolucao da cadeia ─────────────────────────────────────────────────────

/**
 * Cadeia de shaders que vai p/ o mpv, ja com a saida aplicada e a nitidez
 * do modo Avancado no fim. Ordem: cadeia propria (se houver) ou a do
 * preset; nitidez (sharp 1/2) acrescenta o Deblur no fim.
 *
 * Saida: "Ajustar a tela" usa a cadeia como esta (com a reducao entre as
 * ampliacoes); "Sempre 2x" corta tudo depois da 1a ampliacao (sai 2x do
 * video); "Sempre 4x" tira as reducoes (as duas ampliacoes rodam inteiras,
 * 4x do video, e o mpv reduz p/ a tela no fim).
 */
fun resolveEnhanceChain(state: EnhanceState): List<String> {
    val custom = state.customChain
    var steps = if (custom != null) {
        custom.steps.toMutableList().also {
            when (custom.sharp) {
                1 -> if ("deblur_original" !in it) it.add("deblur_original")
                2 -> if ("deblur_dog" !in it) it.add("deblur_dog")
            }
        }
    } else {
        enhancePresetChain(state.presetEnum, state.qualityEnum).toMutableList()
    }
    when (state.outputEnum) {
        EnhanceOutput.X2 -> {
            val firstUpscaler = steps.indexOfFirst { enhanceStepById(it)?.family == EnhanceStepFamily.UPSCALER }
            if (firstUpscaler >= 0) steps = steps.subList(0, firstUpscaler + 1).toMutableList()
        }
        EnhanceOutput.X4 -> {
            steps = steps.filterNot { enhanceStepById(it)?.family == EnhanceStepFamily.DOWNSCALE }.toMutableList()
        }
        EnhanceOutput.FIT -> Unit
    }
    return steps
}

/** Custo estimado da cadeia em ms por quadro (escala pela resolucao do video). */
fun estimateChainCostMs(steps: List<String>, videoW: Int, videoH: Int): Double {
    val pixels = (videoW.coerceAtLeast(0) * videoH.coerceAtLeast(0)).toDouble()
    val scaleFactor = (pixels / (1920.0 * 1080.0)).coerceIn(0.1, 4.0)
    return steps.sumOf { enhanceStepById(it)?.costMs ?: 0.0 } * scaleFactor
}

/** Tempo de um quadro em ms p/ o fps do video (null sem fps). */
fun frameTimeMs(fps: Double?): Double? = fps?.takeIf { it > 0 }?.let { 1000.0 / it }

/** Orcamento do Auto: 60% do tempo de um quadro. */
fun autoBudgetMs(fps: Double?): Double? = frameTimeMs(fps)?.times(0.6)

/**
 * Auto por placa: escolhe (preset, qualidade) pela resolucao do video e da
 * tela, cabendo no orcamento de 60% do quadro. A escada vai do mais barato
 * ao mais caro; o [watchdogCap] (preset que a placa ja aguentou, guardado
 * pelo vigia) trava o teto — o Auto nunca escolhe acima dele.
 */
fun autoPickPreset(
    videoW: Int,
    videoH: Int,
    screenW: Int,
    screenH: Int,
    fps: Double?,
    watchdogCap: String,
): Pair<String, String> {
    val ladder = listOf("light", "anime_c", "anime_b", "anime_a")
    val capIdx = ladder.indexOf(watchdogCap).takeIf { it >= 0 } ?: (ladder.size - 1)
    val baseIdx = when {
        videoH <= 0 || screenH <= 0 -> ladder.indexOf("anime_b")
        videoH <= 480 -> ladder.indexOf("anime_c")
        videoH <= 720 -> ladder.indexOf("anime_b")
        videoH < screenH -> ladder.indexOf("anime_a")
        else -> ladder.indexOf("light")
    }.coerceAtMost(capIdx)
    val budget = autoBudgetMs(fps)
    var idx = baseIdx
    while (idx > 0) {
        val preset = EnhancePreset.byId(ladder[idx])
        val fastCost = estimateChainCostMs(enhancePresetChain(preset, EnhanceQuality.FAST), videoW, videoH)
        if (budget == null || fastCost <= budget) break
        idx--
    }
    val preset = EnhancePreset.byId(ladder[idx])
    if (preset == EnhancePreset.LIGHT) return preset.id to EnhanceQuality.FAST.id
    val highCost = estimateChainCostMs(enhancePresetChain(preset, EnhanceQuality.HIGH), videoW, videoH)
    val quality = if (budget == null || highCost <= budget) EnhanceQuality.HIGH else EnhanceQuality.FAST
    return preset.id to quality.id
}

/** Escada do vigia: um passo mais barato que o preset atual (null = ja e o mais barato). */
fun watchdogStepDown(presetId: String, qualityId: String): Pair<String, String>? {
    if (qualityId == EnhanceQuality.HIGH.id) return presetId to EnhanceQuality.FAST.id
    return when (presetId) {
        "anime_aa" -> "anime_a" to EnhanceQuality.FAST.id
        "anime_bb" -> "anime_b" to EnhanceQuality.FAST.id
        "anime_ca" -> "anime_c" to EnhanceQuality.FAST.id
        "anime_a" -> "anime_b" to EnhanceQuality.FAST.id
        "anime_b" -> "anime_c" to EnhanceQuality.FAST.id
        "anime_c", "anime_fast", "anime_quality" -> "light" to EnhanceQuality.FAST.id
        else -> null
    }
}

/** Intervalo do vigia entre leituras dos contadores do mpv. */
const val ENHANCE_WATCHDOG_INTERVAL_MS = 5000L

/** Fracao de quadros ruins (perdidos + atrasados) na janela que faz o vigia descer um passo. */
const val ENHANCE_WATCHDOG_BAD_FRACTION = 0.02
