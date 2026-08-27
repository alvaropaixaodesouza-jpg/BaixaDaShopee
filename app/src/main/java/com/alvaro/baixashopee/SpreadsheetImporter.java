package com.alvaro.baixashopee;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SpreadsheetImporter {
    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRY_BYTES = 10 * 1024 * 1024;

    private SpreadsheetImporter() {}

    public static List<Delivery> importFile(InputStream input, String displayName) throws Exception {
        byte[] bytes = readLimited(input, MAX_FILE_BYTES);
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        boolean zipSignature = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';

        if (lowerName.endsWith(".xlsx") || zipSignature) {
            return rowsToDeliveries(readXlsxRows(bytes));
        }
        if (lowerName.endsWith(".xls")) {
            throw new IOException("O formato .xls antigo não é aceito. Salve a planilha como .xlsx ou .csv.");
        }
        return rowsToDeliveries(parseDelimited(decodeText(bytes)));
    }

    public static List<Delivery> importPastedCodes(String text) {
        LinkedHashMap<String, Delivery> unique = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Aceita uma linha simples ou código seguido de nome/endereço por ; ou tabulação.
            String[] parts = line.split("[;\\t]", -1);
            String code = normalizeCellCode(parts[0]);
            if (!TrackingCode.looksLikeTrackingCode(code)) continue;
            String name = parts.length > 1 ? parts[1] : "";
            String address = parts.length > 2 ? parts[2] : "";
            Delivery delivery = new Delivery(code, name, address);
            unique.putIfAbsent(delivery.id(), delivery);
        }
        return new ArrayList<>(unique.values());
    }

    static List<Delivery> rowsToDeliveries(List<List<String>> rows) throws IOException {
        if (rows.isEmpty()) throw new IOException("A planilha está vazia.");

        int firstRow = firstNonEmptyRow(rows);
        if (firstRow < 0) throw new IOException("A planilha está vazia.");

        List<String> possibleHeader = rows.get(firstRow);
        ColumnMap columns = detectHeader(possibleHeader);
        boolean hasRecognizedHeader = columns.tracking >= 0 || columns.customer >= 0 ||
                columns.address >= 0 || columns.number >= 0 ||
                columns.neighborhood >= 0 || columns.city >= 0 ||
                columns.postalCode >= 0 || columns.latitude >= 0 || columns.longitude >= 0;
        int dataStart = hasRecognizedHeader ? firstRow + 1 : firstRow;

        if (columns.tracking < 0) {
            columns.tracking = detectTrackingColumn(rows, dataStart);
        }
        if (columns.tracking < 0) {
            throw new IOException("Não encontrei a coluna de código de rastreio. Use um título como Código de rastreio, Rastreio, Tracking, AWB ou BR.");
        }

        LinkedHashMap<String, Delivery> unique = new LinkedHashMap<>();
        for (int r = dataStart; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String code = normalizeCellCode(valueAt(row, columns.tracking));
            if (!TrackingCode.looksLikeTrackingCode(code)) continue;
            Delivery delivery = new Delivery(
                    code,
                    valueAt(row, columns.customer),
                    joinAddressColumns(row, columns)
            ).withRouteData(
                    valueAt(row, columns.atId),
                    valueAt(row, columns.stop),
                    valueAt(row, columns.neighborhood),
                    valueAt(row, columns.city),
                    valueAt(row, columns.postalCode),
                    parseCoordinate(valueAt(row, columns.latitude)),
                    parseCoordinate(valueAt(row, columns.longitude))
            );
            unique.putIfAbsent(delivery.id(), delivery);
        }

        if (unique.isEmpty()) {
            throw new IOException("A coluna foi encontrada, mas nenhum código de rastreio válido apareceu nela.");
        }
        return new ArrayList<>(unique.values());
    }

    private static ColumnMap detectHeader(List<String> header) {
        ColumnMap map = new ColumnMap();
        for (int i = 0; i < header.size(); i++) {
            String h = TrackingCode.normalizeHeader(header.get(i));
            if (h.isEmpty()) continue;

            if (map.tracking < 0 && (
                    h.equals("br") || h.equals("awb") || h.equals("tracking") ||
                    h.contains("codigo rastreio") || h.contains("cod rastreio") ||
                    h.contains("numero rastreio") || h.contains("tracking code") ||
                    h.contains("waybill") || h.contains("spx")
            )) map.tracking = i;

            if (map.customer < 0 && (
                    h.equals("nome") || h.contains("destinatario") ||
                    h.contains("nome cliente") || h.contains("cliente") ||
                    h.equals("sequence") || h.equals("sequencia")
            )) map.customer = i;

            if (map.address < 0 && (
                    h.equals("endereco") || h.contains("endereco completo") ||
                    h.contains("logradouro") || h.equals("address") ||
                    h.contains("destination address")
            )) map.address = i;

            if (map.number < 0 && (h.equals("numero") || h.equals("n") || h.equals("num"))) {
                map.number = i;
            }
            if (map.neighborhood < 0 && (h.contains("bairro") || h.contains("district"))) {
                map.neighborhood = i;
            }
            if (map.city < 0 && (h.equals("cidade") || h.equals("municipio") || h.equals("city"))) {
                map.city = i;
            }
            if (map.postalCode < 0 && (
                    h.equals("cep") || h.contains("postal code") || h.contains("zipcode")
            )) map.postalCode = i;
            if (map.latitude < 0 && h.equals("latitude")) map.latitude = i;
            if (map.longitude < 0 && h.equals("longitude")) map.longitude = i;
            if (map.atId < 0 && h.equals("at id")) map.atId = i;
            if (map.stop < 0 && (h.equals("stop") || h.equals("parada"))) map.stop = i;
        }
        return map;
    }

    private static int detectTrackingColumn(List<List<String>> rows, int start) {
        int maxColumns = 0;
        for (int i = start; i < Math.min(rows.size(), start + 100); i++) {
            maxColumns = Math.max(maxColumns, rows.get(i).size());
        }
        int bestColumn = -1;
        int bestScore = 0;
        for (int c = 0; c < maxColumns; c++) {
            int score = 0;
            for (int r = start; r < Math.min(rows.size(), start + 100); r++) {
                if (TrackingCode.looksLikeTrackingCode(normalizeCellCode(valueAt(rows.get(r), c)))) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestColumn = c;
            }
        }
        return bestScore >= 1 ? bestColumn : -1;
    }

    private static String joinAddressColumns(List<String> row, ColumnMap columns) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, valueAt(row, columns.address));
        addIfPresent(parts, valueAt(row, columns.number));
        addIfPresent(parts, valueAt(row, columns.neighborhood));
        addIfPresent(parts, valueAt(row, columns.city));
        String postalCode = valueAt(row, columns.postalCode);
        if (!postalCode.isEmpty()) addIfPresent(parts, "CEP " + postalCode);
        return String.join(", ", parts);
    }

    private static double parseCoordinate(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.trim().isEmpty() && !parts.contains(value.trim())) {
            parts.add(value.trim());
        }
    }

    private static String valueAt(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index).trim() : "";
    }

    private static int firstNonEmptyRow(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            for (String value : rows.get(i)) {
                if (value != null && !value.trim().isEmpty()) return i;
            }
        }
        return -1;
    }

    private static String normalizeCellCode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.matches("[0-9]+\\.0")) return value.substring(0, value.length() - 2);
        if (value.matches("[0-9]+(?:\\.[0-9]+)?[Ee][+-]?[0-9]+")) {
            try {
                return new BigDecimal(value).toPlainString();
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static List<List<String>> parseDelimited(String text) {
        char delimiter = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        row.add(cell.toString());
        rows.add(row);
        return rows;
    }

    private static char detectDelimiter(String text) {
        int comma = 0, semicolon = 0, tab = 0;
        boolean quoted = false;
        int limit = Math.min(text.length(), 8000);
        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (!quoted && ch == ',') comma++;
            else if (!quoted && ch == ';') semicolon++;
            else if (!quoted && ch == '\t') tab++;
        }
        if (tab >= comma && tab >= semicolon) return '\t';
        return semicolon >= comma ? ';' : ',';
    }

    private static String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return decodeStrict(bytes);
        } catch (CharacterCodingException ignored) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private static String decodeStrict(byte[] bytes) throws CharacterCodingException {
        CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        return chars.toString();
    }

    private static List<List<String>> readXlsxRows(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzipRelevantEntries(bytes);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));

        List<String> sheets = new ArrayList<>();
        for (String name : entries.keySet()) {
            if (name.matches("xl/worksheets/[^/]+\\.xml")) sheets.add(name);
        }
        Collections.sort(sheets, Comparator.naturalOrder());
        if (sheets.isEmpty()) throw new IOException("O arquivo .xlsx não contém uma planilha legível.");

        // Alguns arquivos da rota possuem uma primeira aba vazia ou somente informativa.
        // Avaliamos todas as abas e escolhemos a que realmente contém os códigos.
        List<List<String>> bestRows = new ArrayList<>();
        int bestScore = -1;
        for (String sheet : sheets) {
            List<List<String>> rows = parseSheet(entries.get(sheet), sharedStrings);
            int score = sheetScore(rows);
            if (score > bestScore) {
                bestScore = score;
                bestRows = rows;
            }
        }
        return bestRows;
    }

    private static int sheetScore(List<List<String>> rows) {
        int nonEmptyRows = 0;
        int trackingCodes = 0;
        for (List<String> row : rows) {
            boolean hasValue = false;
            for (String value : row) {
                String normalized = normalizeCellCode(value);
                if (!normalized.isEmpty()) hasValue = true;
                if (TrackingCode.looksLikeTrackingCode(normalized)) trackingCodes++;
            }
            if (hasValue) nonEmptyRows++;
        }
        return trackingCodes * 1_000 + nonEmptyRows;
    }

    private static Map<String, byte[]> unzipRelevantEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("xl/sharedStrings.xml")
                        || name.matches("xl/worksheets/[^/]+\\.xml")) {
                    entries.put(name, readLimited(zip, MAX_ZIP_ENTRY_BYTES));
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static List<String> parseSharedStrings(byte[] xmlBytes) throws Exception {
        List<String> strings = new ArrayList<>();
        if (xmlBytes == null) return strings;

        XmlPullParser parser = newParser(xmlBytes);
        StringBuilder current = null;
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            String tag = localTag(parser.getName());
            if (parser.getEventType() == XmlPullParser.START_TAG && tag.equals("si")) {
                current = new StringBuilder();
            } else if (parser.getEventType() == XmlPullParser.START_TAG && tag.equals("t") && current != null) {
                current.append(parser.nextText());
            } else if (parser.getEventType() == XmlPullParser.END_TAG && tag.equals("si") && current != null) {
                strings.add(current.toString());
                current = null;
            }
            parser.next();
        }
        return strings;
    }

    private static List<List<String>> parseSheet(byte[] xmlBytes, List<String> sharedStrings) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XmlPullParser parser = newParser(xmlBytes);
        List<String> currentRow = null;
        int currentColumn = -1;
        String cellType = "";
        String cellValue = "";

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            int event = parser.getEventType();
            // Excel, LibreOffice e geradores de IA podem escrever exatamente o
            // mesmo XLSX usando <c>/<v> ou <x:c>/<x:v>. O prefixo não muda o
            // significado da célula e não pode fazer a rota parecer vazia.
            String name = localTag(parser.getName());
            if (event == XmlPullParser.START_TAG && "row".equals(name)) {
                currentRow = new ArrayList<>();
            } else if (event == XmlPullParser.START_TAG && "c".equals(name)) {
                String ref = parser.getAttributeValue(null, "r");
                currentColumn = columnFromReference(ref);
                cellType = parser.getAttributeValue(null, "t");
                cellValue = "";
            } else if (event == XmlPullParser.START_TAG && "v".equals(name)) {
                cellValue = parser.nextText();
            } else if (event == XmlPullParser.START_TAG && "t".equals(name) && "inlineStr".equals(cellType)) {
                cellValue += parser.nextText();
            } else if (event == XmlPullParser.END_TAG && "c".equals(name) && currentRow != null) {
                while (currentRow.size() <= currentColumn) currentRow.add("");
                String value = cellValue;
                if ("s".equals(cellType)) {
                    try {
                        int index = Integer.parseInt(cellValue);
                        value = index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
                    } catch (NumberFormatException ignored) {
                        value = "";
                    }
                }
                currentRow.set(currentColumn, value);
            } else if (event == XmlPullParser.END_TAG && "row".equals(name) && currentRow != null) {
                rows.add(currentRow);
                currentRow = null;
            }
            parser.next();
        }
        return rows;
    }

    private static XmlPullParser newParser(byte[] xmlBytes) throws Exception {
        if (xmlBytes == null) throw new IOException("Conteúdo interno da planilha ausente.");
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new ByteArrayInputStream(xmlBytes), "UTF-8");
        return parser;
    }

    private static String localTag(String name) {
        if (name == null) return "";
        int separator = name.indexOf(':');
        return separator >= 0 ? name.substring(separator + 1) : name;
    }

    private static int columnFromReference(String reference) {
        if (reference == null || reference.isEmpty()) return 0;
        int column = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (!Character.isLetter(ch)) break;
            column = column * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return Math.max(0, column - 1);
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("O arquivo é grande demais para importar com segurança.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class ColumnMap {
        int tracking = -1;
        int customer = -1;
        int address = -1;
        int number = -1;
        int neighborhood = -1;
        int city = -1;
        int postalCode = -1;
        int latitude = -1;
        int longitude = -1;
        int atId = -1;
        int stop = -1;
    }
}
