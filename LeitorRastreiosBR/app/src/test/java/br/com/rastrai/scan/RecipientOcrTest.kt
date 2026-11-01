package br.com.rastrai.scan

import org.junit.Assert.*
import org.junit.Test

class RecipientOcrTest {
    @Test fun extraiEtiquetaComDadosRotulados() {
        val info=RecipientOcr.parse(
            """
            SHOPEE EXPRESS
            Código: BR2624148890977
            Destinatário: Maria Aparecida Santos
            Endereço: Rua da Praia, 25
            Bairro: Centro
            CEP: 44220-000
            """.trimIndent()
        )
        assertEquals("Maria",info.firstName)
        assertEquals("Maria Aparecida Santos",info.name)
        assertEquals("Rua da Praia, 25",info.street)
        assertEquals("Centro",info.district)
        assertEquals("44220-000",info.zip)
    }
    @Test fun naoInventaNomePeloCodigo() {
        val info=RecipientOcr.parse("SPXBR123456789012\nRastreio: BR2624148890977\nPedido 12345")
        assertEquals("",info.name)
        assertEquals("",info.firstName)
        assertEquals("",info.zip)
    }
    @Test fun nomeNaLinhaSeguinte() {
        val info=RecipientOcr.parse("DESTINATARIO\nJoão Carlos de Souza\nBAIRRO\nCabuçu\nCEP 44220 000")
        assertEquals("João",info.firstName)
        assertEquals("Cabuçu",info.district)
        assertEquals("44220-000",info.zip)
    }
    @Test fun naoAtribuiRuaAoCampoNome() {
        val info=RecipientOcr.parse("Nome da rua\nRua da Praia, 27")
        assertEquals("",info.firstName)
    }
}
