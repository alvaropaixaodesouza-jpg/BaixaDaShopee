package com.alvaro.baixashopee;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ImporterSelfTest {
    public static void main(String[] args) throws Exception {
        String csv = "Código de rastreio;Destinatário;Endereço;Número;Bairro;Cidade\n" +
                "BR12AB345678;Ana;Rua A;10;Centro;Saubara\n" +
                "BR98XY765432;Beto;Rua B;20;Cabuçu;Saubara\n" +
                "BR12AB345678;Duplicado;Outra rua;99;Centro;Saubara\n";
        List<Delivery> csvItems = SpreadsheetImporter.importFile(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "teste.csv"
        );
        require(csvItems.size() == 2, "CSV deve remover código repetido");
        require(csvItems.get(0).numericCode().equals("12345678"), "Conversão para somente números incorreta");
        require(csvItems.get(0).address.equals("Rua A, 10, Centro, Saubara"), "Endereço composto incorreto");

        List<Delivery> pasted = SpreadsheetImporter.importPastedCodes(
                "BR111111111;Nome 1;Endereço 1\nBR222222222\n"
        );
        require(pasted.size() == 2, "Importação colada incorreta");
        require(pasted.get(0).customerName.equals("Nome 1"), "Nome colado incorreto");
        require(pasted.get(0).houseId.isEmpty(), "Entrega nova não pode herdar casa indevida");
        require(House.normalizeAddress("Rua São João, nº 10").equals("rua sao joao n 10"),
                "Normalização de endereço incorreta");

        String routeCsv = "AT ID;Sequence;Stop;SPX TN;Destination Address;Bairro;City;Zipcode/Postal code;Latitude;Longitude\n" +
                "AT1;Maria Teste;-;BR265413762121Z;Rua do Porto, 10;Cabuçu;Saubara;44220-000;-12.779;-38.771\n";
        List<Delivery> routeItems = SpreadsheetImporter.importFile(
                new ByteArrayInputStream(routeCsv.getBytes(StandardCharsets.UTF_8)),
                "rota.csv"
        );
        require(routeItems.size() == 1, "Formato original da rota deve ser reconhecido");
        require(routeItems.get(0).customerName.equals("Maria Teste"), "Sequence deve fornecer o nome");
        require(routeItems.get(0).address.contains("Rua do Porto, 10"), "Destination Address deve fornecer o endereço");
        require(routeItems.get(0).neighborhood.equals("Cabuçu"), "Bairro deve ser preservado");
        require(routeItems.get(0).hasDestinationLocation(), "Coordenadas de destino devem ser preservadas");

        if (args.length > 0) {
            try (FileInputStream input = new FileInputStream(args[0])) {
                List<Delivery> xlsxItems = SpreadsheetImporter.importFile(input, "teste.xlsx");
                int expected = args.length > 1 ? Integer.parseInt(args[1]) : 2;
                require(xlsxItems.size() == expected, "Quantidade de entregas XLSX incorreta");
                if (expected == 33) {
                    require(xlsxItems.get(0).trackingCode.equals("BR265413762121Z"), "Primeiro código XLSX incorreto");
                    require(xlsxItems.get(0).customerName.equals("Enos Rian Da Cruz Teles"), "Nome de Sequence incorreto");
                    require(xlsxItems.get(0).address.contains("Rua Do Riacho Doce"), "Endereço XLSX incorreto");
                } else {
                    require(xlsxItems.get(1).trackingCode.equals("BR444444444"), "Código XLSX incorreto");
                    require(xlsxItems.get(1).customerName.equals("Davi"), "Nome XLSX incorreto");
                }
            }
        }
        System.out.println("ImporterSelfTest: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
