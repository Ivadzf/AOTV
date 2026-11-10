package com.aotv.app.ui.common

import com.aotv.app.data.local.PlatformMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLayoutTest {
    private fun layout(
        width: Int = 393,
        height: Int = 873,
        mode: PlatformMode = PlatformMode.AUTO,
        televisionMode: Boolean = false,
        leanback: Boolean = false,
        televisionFeature: Boolean = false,
        fontScale: Float = 1f,
    ) = resolveDeviceLayout(mode, televisionMode, leanback, televisionFeature, width, height, fontScale)

    @Test fun modoTelevisaoUsaControleRemoto() {
        assertEquals(DeviceLayout.TV, layout(televisionMode = true))
    }

    @Test fun leanbackReconheceTvMesmoSemModoTelevisao() {
        assertEquals(DeviceLayout.TV, layout(1280, 720, leanback = true))
    }

    @Test fun recursoAntigoDeTelevisaoTambemReconheceTv() {
        assertEquals(DeviceLayout.TV, layout(960, 540, televisionFeature = true))
    }

    @Test fun tvTemPrioridadeSobreJanelaGrande() {
        assertEquals(DeviceLayout.TV, layout(1920, 1080, televisionMode = true, leanback = true))
    }

    @Test fun celularEmPeUsaBarraInferior() {
        assertEquals(DeviceLayout.PHONE, layout())
    }

    @Test fun celularDeitadoContinuaComLayoutCompacto() {
        assertEquals(DeviceLayout.PHONE, layout(873, 393))
    }

    @Test fun tabletEmPeUsaNavegacaoLateralDeToque() {
        assertEquals(DeviceLayout.TABLET, layout(600, 960))
    }

    @Test fun tabletDeitadoUsaNavegacaoLateralDeToque() {
        assertEquals(DeviceLayout.TABLET, layout(1280, 800))
    }

    @Test fun dobravelAbertoAproveitaJanelaLarga() {
        assertEquals(DeviceLayout.TABLET, layout(720, 900))
    }

    @Test fun tabletEmJanelaEstreitaAdaptaParaNavegacaoCompacta() {
        assertEquals(DeviceLayout.PHONE, layout(400, 960))
    }

    @Test fun limitesDeLarguraEAlturaSaoInclusivos() {
        assertEquals(DeviceLayout.PHONE, layout(599, 960))
        assertEquals(DeviceLayout.PHONE, layout(600, 479))
        assertEquals(DeviceLayout.TABLET, layout(600, 480))
    }

    @Test fun aumentarEReduzirJanelaRecalculaLayout() {
        val sizes = listOf(800 to 1000, 400 to 1000, 1000 to 800)
        assertEquals(
            listOf(DeviceLayout.TABLET, DeviceLayout.PHONE, DeviceLayout.TABLET),
            sizes.map { (width, height) -> layout(width, height) },
        )
    }

    @Test fun modoTvEmCelularPequenoAdaptaParaToque() {
        assertEquals(DeviceLayout.PHONE, layout(mode = PlatformMode.TV))
        assertEquals(DeviceLayout.PHONE, layout(873, 393, PlatformMode.TV))
    }

    @Test fun modoTvUsaTvQuandoAJanelaComporta() {
        assertEquals(DeviceLayout.TV, layout(1280, 800, PlatformMode.TV))
        assertEquals(DeviceLayout.TV, layout(960, 540, PlatformMode.TV))
    }

    @Test fun fonteAmpliadaPodePedirLayoutDeToque() {
        assertEquals(DeviceLayout.TABLET, layout(1280, 800, PlatformMode.TV, fontScale = 2f))
    }

    @Test fun tvFisicaMantemControleRemotoEmResolucaoBaixa() {
        assertEquals(DeviceLayout.TV, layout(640, 360, televisionMode = true, fontScale = 2f))
    }

    @Test fun girarTabletReavaliaModoTvSemMudarPreferencia() {
        assertEquals(DeviceLayout.TABLET, layout(800, 1280, PlatformMode.TV))
        assertEquals(DeviceLayout.TV, layout(1280, 800, PlatformMode.TV))
    }

    @Test fun escolhaManualDeToqueTemPrioridadeNaTv() {
        assertEquals(
            DeviceLayout.TABLET,
            layout(960, 540, PlatformMode.PHONE, televisionMode = true, leanback = true, televisionFeature = true),
        )
    }

    @Test fun modoDeToqueAindaAdaptaTabletECelular() {
        assertEquals(DeviceLayout.PHONE, layout(mode = PlatformMode.PHONE))
        assertEquals(DeviceLayout.TABLET, layout(800, 1000, PlatformMode.PHONE))
    }
}
