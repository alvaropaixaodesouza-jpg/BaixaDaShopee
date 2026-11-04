package br.com.rastrai.scan

import org.junit.Assert.*
import org.junit.Test

class ReplayTypingPolicyTest {
    @Test fun campoVazioAceita() {
        assertTrue(ReplayTypingPolicy.canType("","","",true))
        assertTrue(ReplayTypingPolicy.canType("","","",false))
    }
    @Test fun linhaSeguintePermiteContinuar() {
        assertTrue(ReplayTypingPolicy.canType("BR261087203125T\n","","",true))
        assertTrue(ReplayTypingPolicy.canType("BR1\nBR2\n","","",true))
    }
    @Test fun semQuebraNaoConcatena() {
        assertFalse(ReplayTypingPolicy.canType("BR261087203125T","","",true))
        assertFalse(ReplayTypingPolicy.canType("BR261087203125T\n","","",false))
    }
    @Test fun textoSelecionadoPodeSerSubstituido() {
        assertTrue(ReplayTypingPolicy.canType("prefixo","sufixo","SELECIONADO",true))
    }
    @Test fun cursorForaDoFimBloqueia() {
        assertFalse(ReplayTypingPolicy.canType("BR1\n","BR2","",true))
    }
    @Test fun confirmaQuebraDepoisDoCodigo() {
        assertTrue(ReplayTypingPolicy.newlineWasInserted("BR261087203125T\n","BR261087203125T"))
        assertTrue(ReplayTypingPolicy.newlineWasInserted("BR1\nBR2\n","BR2"))
        assertFalse(ReplayTypingPolicy.newlineWasInserted("BR261087203125T","BR261087203125T"))
    }
}
