package com.aotv.app.data.local

import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Gamepads e controles remotos mapeáveis (Lote 22, v1.32.0, item 5).
 *
 * Presets por marca/modelo (casados pelo nome do aparelho) + mapeamento
 * manual por cima (ação -> keyCode, guardado em JSON na pref). A MainActivity
 * intercepta as teclas do gamepad em [dispatchKeyEvent] e traduz o botão
 * p/ o KeyEvent padrão do Android ([GamepadManager.toAndroidKeyCode]):
 * navegação vira DPAD (o foco do Compose já entende), play/pause vira
 * KEYCODE_MEDIA_PLAY_PAUSE (a MediaSession do player entende) e voltar
 * vira KEYCODE_BACK. Sem isso, BUTTON_A/B/X/Y não fazem nada no app —
 * o Android não traduz botão de gamepad p/ DPAD sozinho.
 *
 * Não mapeado: analógicos (o DPAD cobre a navegação) e o botão PS/Home
 * (o sistema o reserva). O preset "auto" detecta pelo modelo; "generic"
 * vale p/ qualquer controle sem preset próprio.
 */
enum class GamepadAction {
    UP, DOWN, LEFT, RIGHT, OK, BACK,
    PLAY_PAUSE, STOP, REWIND, FAST_FORWARD,
    VOLUME_UP, VOLUME_DOWN, MENU, INFO,
}

data class GamepadPreset(
    val id: String,
    val label: String,
    /** Trechos do nome do aparelho (minúsculo) que casam com o preset. */
    val match: List<String>,
    /** keyCode do Android -> ação. */
    val map: Map<Int, GamepadAction>,
)

object GamepadPresets {
    /** Base comum a todos os presets (DPAD, teclas de mídia, volume, menu). */
    private val common: Map<Int, GamepadAction> = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to GamepadAction.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to GamepadAction.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to GamepadAction.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to GamepadAction.RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER to GamepadAction.OK,
        KeyEvent.KEYCODE_ENTER to GamepadAction.OK,
        KeyEvent.KEYCODE_BACK to GamepadAction.BACK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to GamepadAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY to GamepadAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PAUSE to GamepadAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP to GamepadAction.STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT to GamepadAction.FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to GamepadAction.REWIND,
        KeyEvent.KEYCODE_MEDIA_REWIND to GamepadAction.REWIND,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to GamepadAction.FAST_FORWARD,
        KeyEvent.KEYCODE_VOLUME_UP to GamepadAction.VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN to GamepadAction.VOLUME_DOWN,
        KeyEvent.KEYCODE_MENU to GamepadAction.MENU,
        KeyEvent.KEYCODE_INFO to GamepadAction.INFO,
    )

    private fun withButtons(
        a: GamepadAction, b: GamepadAction, x: GamepadAction, y: GamepadAction,
        start: GamepadAction, select: GamepadAction,
    ): Map<Int, GamepadAction> = common + mapOf(
        KeyEvent.KEYCODE_BUTTON_A to a,
        KeyEvent.KEYCODE_BUTTON_B to b,
        KeyEvent.KEYCODE_BUTTON_X to x,
        KeyEvent.KEYCODE_BUTTON_Y to y,
        KeyEvent.KEYCODE_BUTTON_START to start,
        KeyEvent.KEYCODE_BUTTON_SELECT to select,
        KeyEvent.KEYCODE_BUTTON_L1 to GamepadAction.REWIND,
        KeyEvent.KEYCODE_BUTTON_R1 to GamepadAction.FAST_FORWARD,
    )

    val XBOX = GamepadPreset(
        id = "xbox", label = "Xbox",
        match = listOf("xbox", "microsoft"),
        map = withButtons(
            a = GamepadAction.OK, b = GamepadAction.BACK,
            x = GamepadAction.PLAY_PAUSE, y = GamepadAction.MENU,
            start = GamepadAction.MENU, select = GamepadAction.INFO,
        ),
    )

    val PLAYSTATION = GamepadPreset(
        id = "playstation", label = "PlayStation (DualShock/DualSense)",
        match = listOf("sony", "playstation", "dualshock", "dualsense", "wireless controller"),
        map = withButtons(
            a = GamepadAction.OK, b = GamepadAction.BACK,
            x = GamepadAction.PLAY_PAUSE, y = GamepadAction.MENU,
            start = GamepadAction.MENU, select = GamepadAction.INFO,
        ),
    )

    val EIGHT_BIT_DO = GamepadPreset(
        id = "8bitdo", label = "8BitDo",
        match = listOf("8bitdo"),
        map = withButtons(
            a = GamepadAction.OK, b = GamepadAction.BACK,
            x = GamepadAction.PLAY_PAUSE, y = GamepadAction.MENU,
            start = GamepadAction.MENU, select = GamepadAction.INFO,
        ),
    )

    val GENERIC = GamepadPreset(
        id = "generic", label = "Genérico",
        match = emptyList(),
        map = withButtons(
            a = GamepadAction.OK, b = GamepadAction.BACK,
            x = GamepadAction.PLAY_PAUSE, y = GamepadAction.INFO,
            start = GamepadAction.MENU, select = GamepadAction.BACK,
        ),
    )

    val ALL: List<GamepadPreset> = listOf(XBOX, PLAYSTATION, EIGHT_BIT_DO, GENERIC)

    fun byId(id: String): GamepadPreset = ALL.firstOrNull { it.id == id } ?: GENERIC

    /** Detecta o preset pelo nome do aparelho (minúsculo, sem acento). */
    fun forDevice(device: InputDevice): GamepadPreset {
        val name = device.name.lowercase()
        return ALL.firstOrNull { preset ->
            preset.match.any { name.contains(it) }
        } ?: GENERIC
    }
}

/** Captura do próximo botão p/ o remapeamento manual (a tela observa). */
object GamepadCapture {
    private val _captured = MutableStateFlow<Int?>(null)
    val captured: StateFlow<Int?> = _captured.asStateFlow()
    @Volatile var capturing: Boolean = false
        private set

    fun start() {
        capturing = true
        _captured.value = null
    }

    fun stop() {
        capturing = false
    }

    fun offer(keyCode: Int) {
        if (capturing) {
            _captured.value = keyCode
            capturing = false
        }
    }

    fun consume() {
        _captured.value = null
    }
}

class GamepadManager(private val prefs: PlatformPrefs) {

    /** Controles conectados agora (gamepad ou joystick). */
    fun connectedGamepads(): List<InputDevice> {
        // Laço explícito com tipos explícitos: a cadeia
        // `getDeviceIds().mapNotNull { ... }.filter { ... }` quebrava a
        // inferência do compilador (os parâmetros das lambdas não eram
        // inferidos e o resto virava cascata). O `and` aqui tem parênteses
        // p/ o teste de bit não depender de precedência de infix.
        val result = mutableListOf<InputDevice>()
        for (id in InputDevice.getDeviceIds()) {
            val device: InputDevice = InputDevice.getDevice(id) ?: continue
            val sources: Int = device.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            ) {
                result.add(device)
            }
        }
        return result
    }

    fun isGamepadEvent(event: KeyEvent): Boolean {
        val sources = event.source
        if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return true
        if (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) return true
        val device = runCatching { InputDevice.getDevice(event.deviceId) }.getOrNull() ?: return false
        return connectedGamepads().any { it.id == device.id }
    }

    /** Preset em vigor p/ o aparelho ("auto" = detecta pelo modelo). */
    fun presetFor(device: InputDevice): GamepadPreset {
        val wanted = prefs.gamepadPreset
        if (wanted != "auto") return GamepadPresets.byId(wanted)
        return GamepadPresets.forDevice(device)
    }

    /** Mapeamento manual (ação -> keyCode). Por cima do preset. */
    fun manualMapping(): Map<GamepadAction, Int> = runCatching {
        val json = JSONObject(prefs.gamepadMappingJson)
        val out = mutableMapOf<GamepadAction, Int>()
        json.keys().forEach { key ->
            runCatching { GamepadAction.valueOf(key) }.getOrNull()?.let { action ->
                out[action] = json.optInt(key, 0)
            }
        }
        out.filterValues { it != 0 }
    }.getOrDefault(emptyMap())

    fun setManualMapping(action: GamepadAction, keyCode: Int) {
        val current = manualMapping().toMutableMap()
        // Um botão só pode ser uma ação: tira ele de outra ação antes.
        current.entries.removeAll { it.value == keyCode }
        current[action] = keyCode
        prefs.gamepadMappingJson = JSONObject(current.mapKeys { it.key.name }).toString()
    }

    fun clearManualMapping(action: GamepadAction) {
        val current = manualMapping().toMutableMap()
        current.remove(action)
        prefs.gamepadMappingJson = JSONObject(current.mapKeys { it.key.name }).toString()
    }

    fun clearAllManualMapping() {
        prefs.gamepadMappingJson = ""
    }

    /** Ação do botão, ou null quando o botão não está mapeado. */
    fun actionFor(event: KeyEvent): GamepadAction? {
        val manual = manualMapping()
        manual.entries.firstOrNull { it.value == event.keyCode }?.let { return it.key }
        val device = runCatching { InputDevice.getDevice(event.deviceId) }.getOrNull()
        val preset = if (device != null) presetFor(device) else GamepadPresets.GENERIC
        return preset.map[event.keyCode]
    }

    companion object {
        /** KeyCode padrão do Android p/ cada ação (o que a MainActivity sintetiza). */
        fun toAndroidKeyCode(action: GamepadAction): Int = when (action) {
            GamepadAction.UP -> KeyEvent.KEYCODE_DPAD_UP
            GamepadAction.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            GamepadAction.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            GamepadAction.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            GamepadAction.OK -> KeyEvent.KEYCODE_DPAD_CENTER
            GamepadAction.BACK -> KeyEvent.KEYCODE_BACK
            GamepadAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            GamepadAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            GamepadAction.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            GamepadAction.FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            GamepadAction.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
            GamepadAction.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            GamepadAction.MENU -> KeyEvent.KEYCODE_MENU
            GamepadAction.INFO -> KeyEvent.KEYCODE_INFO
        }

        /** Nome legível do botão p/ mostrar na tela de remapeamento. */
        fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            KeyEvent.KEYCODE_BUTTON_MODE -> "Home"
            KeyEvent.KEYCODE_DPAD_UP -> "Cima"
            KeyEvent.KEYCODE_DPAD_DOWN -> "Baixo"
            KeyEvent.KEYCODE_DPAD_LEFT -> "Esquerda"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "Direita"
            KeyEvent.KEYCODE_DPAD_CENTER -> "Centro"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_BACK -> "Voltar"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pause"
            KeyEvent.KEYCODE_MEDIA_STOP -> "Stop"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Próximo"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Anterior"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "Voltar"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Avançar"
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume -"
            KeyEvent.KEYCODE_MENU -> "Menu"
            KeyEvent.KEYCODE_INFO -> "Info"
            else -> "Tecla $keyCode"
        }

        /** Nome legível da ação. */
        fun actionLabel(action: GamepadAction): String = when (action) {
            GamepadAction.UP -> "Cima"
            GamepadAction.DOWN -> "Baixo"
            GamepadAction.LEFT -> "Esquerda"
            GamepadAction.RIGHT -> "Direita"
            GamepadAction.OK -> "Confirmar"
            GamepadAction.BACK -> "Voltar"
            GamepadAction.PLAY_PAUSE -> "Play/Pause"
            GamepadAction.STOP -> "Parar"
            GamepadAction.REWIND -> "Voltar 10 s"
            GamepadAction.FAST_FORWARD -> "Avançar 10 s"
            GamepadAction.VOLUME_UP -> "Volume +"
            GamepadAction.VOLUME_DOWN -> "Volume -"
            GamepadAction.MENU -> "Menu"
            GamepadAction.INFO -> "Info"
        }
    }
}
