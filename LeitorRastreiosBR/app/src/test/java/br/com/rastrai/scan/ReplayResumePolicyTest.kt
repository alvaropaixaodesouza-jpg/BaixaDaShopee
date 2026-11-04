package br.com.rastrai.scan

import org.junit.Assert.*
import org.junit.Test

class ReplayResumePolicyTest {
    @Test fun mesmoCampoRetoma() {
        assertTrue(ReplayResumePolicy.sameField("com.android.chrome",10,1,"New Order",
            "com.android.chrome",10,1,"New Order"))
    }
    @Test fun outroAplicativoNaoRetoma() {
        assertFalse(ReplayResumePolicy.sameField("com.android.chrome",10,1,"",
            "com.whatsapp",10,1,""))
    }
    @Test fun idMudouMasMesmaDicaExplicitaPodeRetomar() {
        assertTrue(ReplayResumePolicy.sameField("com.android.chrome",42,1,"New Order",
            "com.android.chrome",73,1,"New Order"))
    }
    @Test fun idMudouSemDicaNaoRetoma() {
        assertFalse(ReplayResumePolicy.sameField("com.android.chrome",42,1,"",
            "com.android.chrome",73,1,""))
    }
    @Test fun tipoDiferenteNaoRetoma() {
        assertFalse(ReplayResumePolicy.sameField("com.android.chrome",2,1,"",
            "com.android.chrome",2,17,""))
    }
    @Test fun intervaloContinuaDoEnterAnterior() {
        assertEquals(1200L,ReplayResumePolicy.remainingDelay(10000L,3000,11800L))
        assertEquals(300L,ReplayResumePolicy.remainingDelay(10000L,3000,13100L))
    }
}
