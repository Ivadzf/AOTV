package com.aotv.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredAudioTest {
    private data class A(val id: String?, val orig: Boolean, val br: Int)
    private fun pick(vararg l: A) = preferredAudio(l.toList(), { it.id }, { it.orig }, { it.br })

    @Test fun portuguesVenceDublagemDeBitrateMaior() {
        assertEquals(A("pt-BR.10", false, 128), pick(A("de-DE.10", false, 160), A("pt-BR.10", false, 128), A("en-US.4", true, 160)))
    }

    @Test fun semPortuguesFicaComOriginal() {
        assertEquals(A("en-US.4", true, 128), pick(A("de-DE.10", false, 160), A("en-US.4", true, 128)))
    }

    @Test fun faixaUnicaSemIdEntra() {
        assertEquals(A(null, false, 160), pick(A(null, false, 48), A(null, false, 160)))
    }
}
