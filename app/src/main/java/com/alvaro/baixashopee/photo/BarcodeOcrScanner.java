package com.alvaro.baixashopee.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.alvaro.baixashopee.TrackingCode;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BarcodeOcrScanner {

    public enum Status {
        SUCCESS,
        NO_READING,
        NOT_FOUND,
        AMBIGUOUS
    }

    public static class ScanResult {
        public final Status status;
        public final String matchedTrackingCode;
        public final String detectedRaw;

        public ScanResult(Status status, String matchedTrackingCode, String detectedRaw) {
            this.status = status;
            this.matchedTrackingCode = matchedTrackingCode == null ? "" : matchedTrackingCode;
            this.detectedRaw = detectedRaw == null ? "" : detectedRaw;
        }
    }

    private static final Pattern TRACKING_PATTERN = Pattern.compile("\\b([A-Za-z]{2}[0-9]{9,15}[A-Za-z0-9]?|[0-9]{11,16})\\b");

    private BarcodeOcrScanner() {}

    /**
     * Executa reconhecimento determinístico:
     * 1º Barcode/QR Code (suporta todos os formatos comuns)
     * 2º Fallback OCR via ML Kit Text Recognition
     * 3º Validação determinística contra os códigos canônicos da rota
     */
    public static ScanResult scanAndMatch(Context context, Uri imageUri, Set<String> canonicalRouteCodes) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, imageUri);
        } catch (Exception e) {
            return new ScanResult(Status.NO_READING, "", "Erro ao abrir imagem: " + e.getMessage());
        }

        List<String> candidates = new ArrayList<>();

        // PASSO 1: Barcode / QR Code
        try {
            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();
            BarcodeScanner barcodeScanner = BarcodeScanning.getClient(options);
            List<Barcode> barcodes = Tasks.await(barcodeScanner.process(image));
            barcodeScanner.close();

            if (barcodes != null) {
                for (Barcode b : barcodes) {
                    String raw = b.getRawValue();
                    if (raw != null && !raw.trim().isEmpty()) {
                        candidates.add(raw.trim());
                    }
                }
            }
        } catch (Exception ignored) {}

        // Verifica se algum barcode bate com a rota
        ScanResult barcodeMatch = matchAgainstRoute(candidates, canonicalRouteCodes);
        if (barcodeMatch.status == Status.SUCCESS || barcodeMatch.status == Status.AMBIGUOUS) {
            return barcodeMatch;
        }

        // PASSO 2: OCR Fallback com ML Kit Text Recognition
        try {
            TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text ocrText = Tasks.await(textRecognizer.process(image));
            textRecognizer.close();

            if (ocrText != null) {
                String fullText = ocrText.getText();
                if (fullText != null && !fullText.trim().isEmpty()) {
                    candidates.addAll(extractCandidatesFromText(fullText));
                }
            }
        } catch (Exception ignored) {}

        // Avalia candidatos totais contra a rota
        return matchAgainstRoute(candidates, canonicalRouteCodes);
    }

    private static List<String> extractCandidatesFromText(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;

        // Procura por regex de códigos de rastreio
        Matcher matcher = TRACKING_PATTERN.matcher(text);
        while (matcher.find()) {
            list.add(matcher.group(1));
        }

        // Também inclui linhas inteiras tokenizadas
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            for (String token : trimmed.split("[\\s,;:]+")) {
                String clean = TrackingCode.clean(token);
                if (TrackingCode.looksLikeTrackingCode(clean) && !list.contains(clean)) {
                    list.add(clean);
                }
            }
        }
        return list;
    }

    private static ScanResult matchAgainstRoute(List<String> candidates, Set<String> canonicalRouteCodes) {
        if (candidates == null || candidates.isEmpty()) {
            return new ScanResult(Status.NO_READING, "", "Nenhum código ou texto reconhecido");
        }

        Set<String> matched = new HashSet<>();
        for (String candidate : candidates) {
            String clean = TrackingCode.stableId(candidate);
            if (canonicalRouteCodes.contains(clean)) {
                matched.add(clean);
            }
        }

        if (matched.size() == 1) {
            String code = matched.iterator().next();
            return new ScanResult(Status.SUCCESS, code, "Código encontrado: " + code);
        } else if (matched.size() > 1) {
            return new ScanResult(Status.AMBIGUOUS, "", "Mais de um código correspondente encontrado: " + matched);
        } else {
            return new ScanResult(Status.NOT_FOUND, "", "Código lido não pertence à rota atual (" + candidates.get(0) + ")");
        }
    }
}
