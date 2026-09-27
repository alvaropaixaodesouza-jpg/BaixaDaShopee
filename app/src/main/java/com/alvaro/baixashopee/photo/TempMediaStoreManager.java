package com.alvaro.baixashopee.photo;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Gerencia a publicação de cópias temporárias no MediaStore (fotos recentes do Android)
 * ao tocar em PACOTE ou CASA no teclado.
 * TTL aproximado: 5 minutos.
 * NUNCA apaga nem move os arquivos originais das casas ou pacotes.
 */
public final class TempMediaStoreManager {
    private static final String TAG = "TempMediaStore";
    private static final String PREFS_TEMP_TRACKER = "temp_mediastore_tracker";
    private static final String KEY_TRACKED_URIS = "tracked_uris";
    public static final long TTL_MS = 5 * 60 * 1000L; // 5 minutos

    private TempMediaStoreManager() {}

    /**
     * Publica uma cópia temporária no MediaStore para aparecer no topo das fotos recentes.
     * Retorna a Uri do item criado no MediaStore ou null em caso de erro.
     */
    public static Uri publishTemporaryCopy(Context context, Uri sourceUri, String titlePrefix) {
        if (context == null || sourceUri == null) return null;

        // Limpa expirados antes de criar um novo
        cleanExpired(context);

        ContentResolver resolver = context.getContentResolver();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String safePrefix = titlePrefix == null || titlePrefix.isEmpty() ? "BAIXA" : titlePrefix.replaceAll("[^A-Za-z0-9_-]", "");
        String displayName = safePrefix + "_TEMP_" + timeStamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BaixasTemp");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri tempUri = null;
        try {
            tempUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (tempUri == null) return null;

            try (InputStream in = resolver.openInputStream(sourceUri);
                 OutputStream out = resolver.openOutputStream(tempUri)) {
                if (in == null || out == null) {
                    resolver.delete(tempUri, null, null);
                    return null;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            if (Build.VERSION.SDK_INT >= 29) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(tempUri, values, null, null);
            }

            // Registra nos rastreados pelo app para expiração segura em 5 minutos
            trackCreatedUri(context, tempUri, System.currentTimeMillis() + TTL_MS);
            Log.i(TAG, "Cópia temporária criada com sucesso: " + tempUri);
            return tempUri;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao publicar cópia temporária", e);
            if (tempUri != null) {
                try {
                    resolver.delete(tempUri, null, null);
                } catch (Exception ignored) {}
            }
            return null;
        }
    }

    /**
     * Limpa cópias temporárias expiradas criadas por este aplicativo.
     */
    public static synchronized void cleanExpired(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP_TRACKER, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_TRACKED_URIS, "[]");

        try {
            JSONArray array = new JSONArray(raw);
            JSONArray remaining = new JSONArray();
            long now = System.currentTimeMillis();
            ContentResolver resolver = context.getContentResolver();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String uriString = obj.optString("uri");
                long expireAt = obj.optLong("expireAt", 0);

                if (now >= expireAt) {
                    try {
                        Uri uri = Uri.parse(uriString);
                        int deleted = resolver.delete(uri, null, null);
                        Log.i(TAG, "Cópia temporária expirada removida: " + uriString + " (result: " + deleted + ")");
                    } catch (Exception ignored) {}
                } else {
                    remaining.put(obj);
                }
            }

            prefs.edit().putString(KEY_TRACKED_URIS, remaining.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Falha ao limpar temporários", e);
        }
    }

    private static synchronized void trackCreatedUri(Context context, Uri uri, long expireAt) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP_TRACKER, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_TRACKED_URIS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            JSONObject obj = new JSONObject();
            obj.put("uri", uri.toString());
            obj.put("expireAt", expireAt);
            array.put(obj);
            prefs.edit().putString(KEY_TRACKED_URIS, array.toString()).apply();
        } catch (Exception ignored) {}
    }
}
