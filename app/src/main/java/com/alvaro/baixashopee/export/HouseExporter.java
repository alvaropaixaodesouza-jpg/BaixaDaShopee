package com.alvaro.baixashopee.export;

import com.alvaro.baixashopee.House;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HouseExporter {
    private HouseExporter() {}

    public static String generateFileName() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return "casas_salvas_" + date + ".csv";
    }

    /**
     * Exporta a lista de casas para um arquivo CSV formatado para o Excel (UTF-8 com BOM).
     * Reutiliza as colunas reconhecidas pelo SpreadsheetImporter para facilitar edição e reimportação.
     */
    public static void exportToCsv(List<House> houses, OutputStream outputStream) throws IOException {
        // Grava BOM do UTF-8 para que o Microsoft Excel abra os acentos corretamente
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        outputStream.write(bom);

        StringBuilder sb = new StringBuilder();
        // Cabeçalho compatível
        sb.append("Nome;Endereço;Bairro;Cidade;CEP;Latitude;Longitude;Observações\n");

        if (houses != null) {
            for (House house : houses) {
                if (house == null) continue;
                String labelOrResidents = !house.residents.isEmpty() ? house.residents : house.label;
                sb.append(escape(labelOrResidents)).append(";")
                  .append(escape(house.address)).append(";")
                  .append(escape("")).append(";") // Bairro
                  .append(escape("")).append(";") // Cidade
                  .append(escape("")).append(";") // CEP
                  .append(house.latitude != 0 ? String.valueOf(house.latitude) : "").append(";")
                  .append(house.longitude != 0 ? String.valueOf(house.longitude) : "").append(";")
                  .append(escape(house.notes)).append("\n");
            }
        }

        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static String escape(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.contains(";") || clean.contains("\"") || clean.contains("\n") || clean.contains("\r")) {
            return "\"" + clean.replace("\"", "\"\"") + "\"";
        }
        return clean;
    }
}
