package com.aotv.app.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvLayoutTest {
    @Test fun proporcaoDaInterfaceIgualEmHdFullHdEQuatroK() {
        assertEquals(1280f / 120, tvRem(1280, 720), .001f)
        assertEquals(16f, tvRem(1920, 1080), .001f)
        assertEquals(32f, tvRem(3840, 2160), .001f)
    }

    @Test fun telaLargaOuAltaRespeitaADimensaoMenor() {
        assertEquals(16f, tvRem(2560, 1080), .001f)
        assertEquals(16f, tvRem(1920, 1200), .001f)
        assertEquals(12f, tvRem(1440, 1080), .001f)
    }
}
