package com.alvaro.baixashopee;

import com.alvaro.baixashopee.data.model.AddressNormalizer;
import com.alvaro.baixashopee.data.model.DeliveryGroup;
import com.alvaro.baixashopee.data.model.NeighborhoodHelper;
import com.alvaro.baixashopee.data.model.QueueOrganizer;
import com.alvaro.baixashopee.data.model.RecipientNameHelper;
import com.alvaro.baixashopee.data.model.SortOrder;
import com.alvaro.baixashopee.export.HouseExporter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class Phase1To6BusinessRulesTest {

    @Test
    public void testJoao3PacotesMaria1PacoteConsecutivos() {
        Delivery j1 = new Delivery("BR001", "João", "Rua 1");
        Delivery j2 = new Delivery("BR002", "João", "Rua 1");
        Delivery m1 = new Delivery("BR003", "Maria", "Rua 2");
        Delivery j3 = new Delivery("BR004", "João", "Rua 1");

        List<Delivery> list = Arrays.asList(j1, j2, m1, j3);
        List<DeliveryGroup> groups = QueueOrganizer.groupDeliveries(list);

        assertEquals("Devem existir 2 grupos (João e Maria)", 2, groups.size());
        assertEquals("João deve ter 3 pacotes", 3, groups.get(0).size());
        assertEquals("Maria deve ter 1 pacote", 1, groups.get(1).size());

        List<Delivery> organized = QueueOrganizer.flatten(groups);
        assertEquals(4, organized.size());
        assertEquals("BR001", organized.get(0).trackingCode);
        assertEquals("BR002", organized.get(1).trackingCode);
        assertEquals("BR004", organized.get(2).trackingCode);
        assertEquals("BR003", organized.get(3).trackingCode);
    }

    @Test
    public void testJoaoSilvaDiferenteDeJoaoSilvaMaiusculas() {
        assertFalse("João Silva não pode ser igual a JOÃO SILVA",
                RecipientNameHelper.areSameRecipient("João Silva", "JOÃO SILVA"));
        assertFalse("João das Dores não pode ser igual a João Das Dores",
                RecipientNameHelper.areSameRecipient("João das Dores", "João Das Dores"));
        assertFalse("João das Dores não pode ser igual a Joao das Dores",
                RecipientNameHelper.areSameRecipient("João das Dores", "Joao das Dores"));
        assertTrue("Nomes idênticos devem ser iguais",
                RecipientNameHelper.areSameRecipient("João das Dores", "João das Dores"));
    }

    @Test
    public void testSequenceVazioOuTracoNaoAgrupaPessoasDiferentes() {
        assertFalse(RecipientNameHelper.areSameRecipient("", ""));
        assertFalse(RecipientNameHelper.areSameRecipient("-", "-"));
        assertFalse(RecipientNameHelper.areSameRecipient("  ", "  "));

        Delivery d1 = new Delivery("BR101", "", "Rua 1");
        Delivery d2 = new Delivery("BR102", "", "Rua 2");
        Delivery d3 = new Delivery("BR103", "-", "Rua 3");

        List<DeliveryGroup> groups = QueueOrganizer.groupDeliveries(Arrays.asList(d1, d2, d3));
        assertEquals("Cada entrega sem nome deve permanecer isolada em seu próprio grupo", 3, groups.size());
    }

    @Test
    public void testNomesCompletamenteEmMaiusculasVaoParaBlocoFinal() {
        assertTrue(RecipientNameHelper.isAllUppercase("JOÃO DAS DORES"));
        assertTrue(RecipientNameHelper.isAllUppercase("MARIA SILVA 2"));
        assertFalse(RecipientNameHelper.isAllUppercase("João das Dores"));
        assertFalse(RecipientNameHelper.isAllUppercase("JOÃO Silva"));
        assertFalse(RecipientNameHelper.isAllUppercase(""));
        assertFalse(RecipientNameHelper.isAllUppercase("-"));

        Delivery dNormal1 = new Delivery("BR01", "Beto Souza", "Rua X");
        Delivery dUpper1 = new Delivery("BR02", "ANA CLARA", "Rua Y");
        Delivery dNormal2 = new Delivery("BR03", "Carlos Lima", "Rua Z");
        Delivery dUpper2 = new Delivery("BR04", "ZECA SILVA", "Rua W");

        List<Delivery> list = Arrays.asList(dNormal1, dUpper1, dNormal2, dUpper2);

        // Mesmo com ordenação A-Z, o bloco de maiúsculas deve ficar depois de todos os normais!
        List<Delivery> organizedAZ = QueueOrganizer.organize(list, SortOrder.NAME_AZ, null);
        assertEquals("Beto Souza", organizedAZ.get(0).customerName);
        assertEquals("Carlos Lima", organizedAZ.get(1).customerName);
        assertEquals("ANA CLARA", organizedAZ.get(2).customerName);
        assertEquals("ZECA SILVA", organizedAZ.get(3).customerName);
    }

    @Test
    public void testNormalizacaoEspecialBairrosCabucuEBomJesus() {
        assertTrue(NeighborhoodHelper.belongToSameGroup("Cabuçu", "CABUÇU"));
        assertTrue(NeighborhoodHelper.belongToSameGroup("cabucu", "Cabuçu."));
        assertTrue(NeighborhoodHelper.belongToSameGroup("Cabuçu -", "Cabuçu com espaços extras"));
        assertEquals("Cabuçu", NeighborhoodHelper.getCanonicalGroup("Cabuçu - "));

        assertTrue(NeighborhoodHelper.belongToSameGroup("Bom Jesus", "Bom Jesus dos Pobres"));
        assertTrue(NeighborhoodHelper.belongToSameGroup("BOM JESUS", "bom jesus dos pobres"));
        assertEquals("Bom Jesus", NeighborhoodHelper.getCanonicalGroup("Bom Jesus dos Pobres"));

        assertEquals("Praia do Sol", NeighborhoodHelper.getCanonicalGroup("Praia do Sol"));
        assertFalse(NeighborhoodHelper.belongToSameGroup("Praia do Sol", "Cabuçu"));
        assertFalse(NeighborhoodHelper.belongToSameGroup("Praia do Sol", "Bom Jesus"));

        assertEquals("Centro", NeighborhoodHelper.getCanonicalGroup("Centro"));
        assertEquals("Monte Serrat", NeighborhoodHelper.getCanonicalGroup("Monte Serrat"));
    }

    @Test
    public void testNormalizacaoEnderecoEProtecaoGenericos() {
        assertTrue(AddressNormalizer.isSpecificAddress("Rua das Flores, 123 - Cabuçu"));
        assertTrue(AddressNormalizer.isSpecificAddress("Avenida Central, S/N, Casa 4"));

        assertFalse("Não pode considerar 'Cabuçu' como casa única",
                AddressNormalizer.isSpecificAddress("Cabuçu"));
        assertFalse("Não pode considerar 'Bom Jesus' como casa única",
                AddressNormalizer.isSpecificAddress("Bom Jesus"));
        assertFalse("Não pode considerar 'Saubara' como casa única",
                AddressNormalizer.isSpecificAddress("Saubara"));
        assertFalse("Não pode considerar 'Rua sem número' genérico como casa única",
                AddressNormalizer.isSpecificAddress("Rua sem número"));
    }

    @Test
    public void testExportacaoCasasParaCsvExcelCompativel() throws IOException {
        House h1 = new House("id1", "Casa do João", "João • Maria", "Rua A, 123", "", "", "Perto do mercado", -12.7, -38.6, 5.0f, 1000L);
        House h2 = new House("id2", "Comércio", "Pedro", "Av B, 456", "", "", "", 0, 0, 0, 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HouseExporter.exportToCsv(Arrays.asList(h1, h2), out);
        byte[] bytes = out.toByteArray();

        // Verifica UTF-8 BOM
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String content = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("Nome;Endereço;Bairro;Cidade;CEP;Latitude;Longitude;Observações"));
        assertTrue(content.contains("João • Maria;Rua A, 123"));
        assertTrue(content.contains("Pedro;Av B, 456"));
    }
}
