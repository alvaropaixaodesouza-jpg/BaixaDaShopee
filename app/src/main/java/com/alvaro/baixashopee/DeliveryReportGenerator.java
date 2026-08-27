package com.alvaro.baixashopee;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DeliveryReportGenerator {
    private static final int PAGE_WIDTH = 1240;
    private static final int PAGE_HEIGHT = 1754;
    private static final int MARGIN = 72;

    private DeliveryReportGenerator() { }

    public static Uri generate(Context context, Delivery delivery, House house) throws Exception {
        PdfDocument document = new PdfDocument();
        Uri outputUri = null;
        try {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page = document.startPage(info);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);

            Paint title = paint(44, Color.rgb(201, 59, 32), true);
            Paint heading = paint(28, Color.rgb(33, 26, 23), true);
            Paint body = paint(25, Color.rgb(45, 40, 37), false);
            Paint muted = paint(21, Color.rgb(100, 92, 87), false);

            int y = MARGIN;
            canvas.drawText("Relatório local da entrega", MARGIN, y, title);
            y += 58;
            canvas.drawText(delivery.trackingCode, MARGIN, y, heading);
            y += 42;

            String residents = house != null && !house.residents.isEmpty()
                    ? house.residents : delivery.customerName;
            String address = house != null && !house.address.isEmpty()
                    ? house.address : delivery.address;
            if (house != null) y = drawWrapped(canvas, "Casa: " + house.displayName(), body, y, PAGE_WIDTH - 2 * MARGIN);
            if (!residents.isEmpty()) y = drawWrapped(canvas, "Pessoa(s): " + residents, body, y, PAGE_WIDTH - 2 * MARGIN);
            if (!address.isEmpty()) y = drawWrapped(canvas, "Endereço: " + address, body, y, PAGE_WIDTH - 2 * MARGIN);
            if (delivery.hasDestinationLocation()) {
                y = drawWrapped(canvas, String.format(Locale.US,
                        "Destino da rota: %.6f, %.6f",
                        delivery.destinationLatitude, delivery.destinationLongitude), body, y,
                        PAGE_WIDTH - 2 * MARGIN);
            }
            if (delivery.hasOccurrence()) {
                String occurrence = "Ocorrência: " + delivery.occurrenceType;
                if (!delivery.occurrenceNote.isEmpty()) occurrence += " — " + delivery.occurrenceNote;
                y = drawWrapped(canvas, occurrence, heading, y, PAGE_WIDTH - 2 * MARGIN);
            }
            if (delivery.hasLocation()) {
                y = drawWrapped(canvas, String.format(Locale.US,
                        "Localização: %.6f, %.6f • precisão aproximada %.0f m",
                        delivery.latitude, delivery.longitude, delivery.locationAccuracy), body, y,
                        PAGE_WIDTH - 2 * MARGIN);
                y = drawWrapped(canvas, String.format(Locale.US,
                        "Mapa: https://maps.google.com/?q=%.6f,%.6f",
                        delivery.latitude, delivery.longitude), muted, y, PAGE_WIDTH - 2 * MARGIN);
            } else {
                y = drawWrapped(canvas, "Localização: não registrada", muted, y, PAGE_WIDTH - 2 * MARGIN);
            }
            long time = delivery.photographedAt == 0 ? System.currentTimeMillis() : delivery.photographedAt;
            String when = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault()).format(new Date(time));
            y = drawWrapped(canvas, "Registro fotográfico: " + when, body, y, PAGE_WIDTH - 2 * MARGIN);
            y += 12;

            String facadeUri = house != null ? house.facadePhotoUri : delivery.facadePhotoUri;
            y = drawPhoto(context, canvas, delivery.packagePhotoUri, "Foto do pacote", y, 475, heading, muted);
            y += 20;
            drawPhoto(context, canvas, facadeUri, "Foto de referência da fachada", y, 475, heading, muted);

            Paint footer = paint(17, Color.rgb(110, 104, 100), false);
            canvas.drawText("Gerado pelo Baixa da Shopee • relatório local para organização e conferência",
                    MARGIN, PAGE_HEIGHT - 32, footer);
            document.finishPage(page);

            ContentResolver resolver = context.getContentResolver();
            archivePhoto(context, delivery.packagePhotoUri, delivery.trackingCode,
                    delivery.trackingCode + "_foto_pacote.jpg");
            String archiveFacadeUri = house != null ? house.facadePhotoUri : delivery.facadePhotoUri;
            archivePhoto(context, archiveFacadeUri, delivery.trackingCode,
                    delivery.trackingCode + "_foto_fachada.jpg");
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, delivery.trackingCode + "_relatorio.pdf");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/BaixaDaShopee/Entregas/" + delivery.trackingCode);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            outputUri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
            if (outputUri == null) throw new IllegalStateException("Não foi possível criar o PDF em Documentos.");
            try (OutputStream output = resolver.openOutputStream(outputUri)) {
                if (output == null) throw new IllegalStateException("Não foi possível gravar o PDF.");
                document.writeTo(output);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(outputUri, ready, null, null);
            return outputUri;
        } catch (Exception error) {
            if (outputUri != null) context.getContentResolver().delete(outputUri, null, null);
            throw error;
        } finally {
            document.close();
        }
    }

    private static int drawPhoto(Context context, Canvas canvas, String uriValue, String label,
                                 int top, int maxHeight, Paint heading, Paint muted) {
        canvas.drawText(label, MARGIN, top + 28, heading);
        int imageTop = top + 45;
        Bitmap bitmap = loadBitmap(context, uriValue);
        if (bitmap == null) {
            canvas.drawText("Imagem não registrada", MARGIN, imageTop + 34, muted);
            return imageTop + 60;
        }
        float availableWidth = PAGE_WIDTH - 2f * MARGIN;
        float scale = Math.min(availableWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF destination = new RectF(MARGIN, imageTop, MARGIN + width, imageTop + height);
        canvas.drawBitmap(bitmap, null, destination, null);
        bitmap.recycle();
        return imageTop + (int) height;
    }

    private static Bitmap loadBitmap(Context context, String uriValue) {
        if (uriValue == null || uriValue.isEmpty()) return null;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriValue))) {
            return input == null ? null : BitmapFactory.decodeStream(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void archivePhoto(Context context, String sourceUri, String trackingCode,
                                     String filename) {
        if (sourceUri == null || sourceUri.isEmpty()) return;
        Uri outputUri = null;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(sourceUri))) {
            if (input == null) return;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/BaixaDaShopee/Entregas/" + trackingCode);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            outputUri = context.getContentResolver().insert(
                    MediaStore.Files.getContentUri("external"), values);
            if (outputUri == null) return;
            try (OutputStream output = context.getContentResolver().openOutputStream(outputUri)) {
                if (output == null) return;
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(outputUri, ready, null, null);
        } catch (Exception error) {
            if (outputUri != null) {
                try { context.getContentResolver().delete(outputUri, null, null); }
                catch (Exception ignored) { }
            }
        }
    }

    private static int drawWrapped(Canvas canvas, String text, Paint paint, int baseline, int maxWidth) {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int y = baseline;
        int lineHeight = (int) (paint.getTextSize() * 1.35f);
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), MARGIN, y, paint);
                y += lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), MARGIN, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private static Paint paint(float size, int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
        return paint;
    }
}
