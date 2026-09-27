package com.alvaro.baixashopee.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.alvaro.baixashopee.Delivery;
import com.alvaro.baixashopee.DeliveryStore;
import com.alvaro.baixashopee.TrackingCode;
import com.alvaro.baixashopee.data.db.AppDatabase;
import com.alvaro.baixashopee.data.db.entities.PhotoEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoProcessor {
    public interface ProgressCallback {
        void onProgress(int current, int total);
        void onComplete(int total, int assigned, int pending, int duplicates);
        void onError(String message);
    }

    private final Context appContext;
    private final AppDatabase db;
    private final DeliveryStore deliveryStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PhotoProcessor(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
        this.deliveryStore = new DeliveryStore(context);
    }

    public void processGalleryBatch(List<Uri> uris, ProgressCallback callback) {
        if (uris == null || uris.isEmpty()) {
            if (callback != null) callback.onError("Nenhuma foto selecionada");
            return;
        }

        // Limita a até 50 fotos por lote
        final List<Uri> limitedUris = uris.size() > 50 ? uris.subList(0, 50) : uris;

        executor.execute(() -> {
            int total = limitedUris.size();
            int assignedCount = 0;
            int pendingCount = 0;
            int duplicateCount = 0;

            // Carrega códigos canônicos ativos da rota
            Set<String> canonicalRouteCodes = new HashSet<>();
            List<Delivery> activeDeliveries = deliveryStore.getDeliveries();
            for (Delivery d : activeDeliveries) {
                if (d != null && d.trackingCode != null && !d.trackingCode.isEmpty()) {
                    canonicalRouteCodes.add(TrackingCode.stableId(d.trackingCode));
                }
            }

            for (int i = 0; i < total; i++) {
                final int currentStep = i + 1;
                Uri uri = limitedUris.get(i);

                if (callback != null) {
                    mainHandler.post(() -> callback.onProgress(currentStep, total));
                }

                // 1. Calcula hash da foto para detectar duplicadas
                String hash = computeFileHash(uri);
                if (hash != null && !hash.isEmpty()) {
                    PhotoEntity existing = db.photoDao().getByHash(hash);
                    if (existing != null) {
                        duplicateCount++;
                        continue;
                    }
                }

                // 2. Reconhecimento Barcode/OCR
                BarcodeOcrScanner.ScanResult result = BarcodeOcrScanner.scanAndMatch(appContext, uri, canonicalRouteCodes);

                String photoId = UUID.randomUUID().toString();
                String status;
                String matchedTracking = "";

                if (result.status == BarcodeOcrScanner.Status.SUCCESS) {
                    status = PhotoEntity.STATUS_ASSIGNED;
                    matchedTracking = result.matchedTrackingCode;
                    assignedCount++;

                    // Atualiza a entrega na fila com a foto
                    for (int dIdx = 0; dIdx < activeDeliveries.size(); dIdx++) {
                        if (activeDeliveries.get(dIdx).trackingCode.equalsIgnoreCase(matchedTracking)) {
                            deliveryStore.updatePhotoAt(dIdx, true, uri.toString());
                            break;
                        }
                    }
                } else {
                    pendingCount++;
                    if (result.status == BarcodeOcrScanner.Status.NO_READING) {
                        status = PhotoEntity.STATUS_PENDING_NO_READING;
                    } else if (result.status == BarcodeOcrScanner.Status.AMBIGUOUS) {
                        status = PhotoEntity.STATUS_PENDING_AMBIGUOUS;
                    } else {
                        status = PhotoEntity.STATUS_PENDING_NOT_FOUND;
                    }
                }

                // 3. Salva a foto no banco (processamento recuperável: persiste a cada foto)
                PhotoEntity entity = new PhotoEntity(
                        photoId,
                        uri.toString(),
                        hash,
                        PhotoEntity.TYPE_PACKAGE,
                        matchedTracking,
                        "",
                        status,
                        result.detectedRaw,
                        System.currentTimeMillis()
                );
                db.photoDao().insert(entity);

                // Garante liberação de memória chamando coleta/limpeza
                System.gc();
            }

            final int finalAssigned = assignedCount;
            final int finalPending = pendingCount;
            final int finalDuplicates = duplicateCount;

            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(total, finalAssigned, finalPending, finalDuplicates));
            }
        });
    }

    private String computeFileHash(Uri uri) {
        try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            // Lê até 512KB para hash rápido e determinístico
            int total = 0;
            while ((read = in.read(buffer)) != -1 && total < 512 * 1024) {
                digest.update(buffer, 0, read);
                total += read;
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return uri.toString();
        }
    }
}
