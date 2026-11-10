package com.aotv.app.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.aotv.app.data.local.PlatformMode

enum class DeviceLayout(val label: String) {
    TV("TV"), TABLET("Tablet"), PHONE("Celular"),
}

internal fun resolveDeviceLayout(
    mode: PlatformMode,
    televisionMode: Boolean,
    leanback: Boolean,
    televisionFeature: Boolean,
    widthDp: Int,
    heightDp: Int,
    fontScale: Float = 1f,
): DeviceLayout {
    val hardwareTv = televisionMode || leanback || televisionFeature
    val textScale = fontScale.coerceAtLeast(1f)
    val tvFits = widthDp / textScale >= 960 && heightDp / textScale >= 540
    if ((mode != PlatformMode.PHONE && hardwareTv) || (mode == PlatformMode.TV && tvFits)) return DeviceLayout.TV

    return if (widthDp >= 600 && heightDp >= 480) DeviceLayout.TABLET else DeviceLayout.PHONE
}

@Suppress("DEPRECATION")
@Composable
fun currentDeviceLayout(mode: PlatformMode): DeviceLayout {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tvSignals = remember(context, configuration.uiMode) {
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        Triple(
            manager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION,
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
        )
    }
    return resolveDeviceLayout(
        mode, tvSignals.first, tvSignals.second, tvSignals.third,
        configuration.screenWidthDp, configuration.screenHeightDp,
        configuration.fontScale,
    )
}
