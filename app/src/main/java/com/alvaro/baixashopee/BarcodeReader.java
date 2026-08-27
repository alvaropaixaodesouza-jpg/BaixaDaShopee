package com.alvaro.baixashopee;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

/** Lê QR Code com o modelo incorporado no APK, sem depender de internet durante a rota. */
public final class BarcodeReader {
    public interface Callback {
        void onSuccess(String rawValue);
        void onError(String message);
    }

    private BarcodeReader() { }

    public static void scan(Context context, Uri imageUri, Callback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();
            BarcodeScanner scanner = BarcodeScanning.getClient(options);
            scanner.process(image)
                    .addOnSuccessListener(codes -> finish(scanner, codes, callback))
                    .addOnFailureListener(error -> {
                        scanner.close();
                        callback.onError("Não foi possível ler o QR Code: " + error.getMessage());
                    });
        } catch (Exception error) {
            callback.onError("Não foi possível abrir a imagem do QR Code: " + error.getMessage());
        }
    }

    private static void finish(BarcodeScanner scanner, List<Barcode> codes, Callback callback) {
        scanner.close();
        for (Barcode code : codes) {
            String raw = code.getRawValue();
            if (raw != null && !raw.trim().isEmpty()) {
                callback.onSuccess(raw.trim());
                return;
            }
        }
        callback.onError("Nenhum QR Code legível apareceu na foto. Aproxime e tente novamente.");
    }
}
