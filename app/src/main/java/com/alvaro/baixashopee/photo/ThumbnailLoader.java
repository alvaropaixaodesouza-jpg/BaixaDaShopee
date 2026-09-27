package com.alvaro.baixashopee.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private ThumbnailLoader() {}

    public static void loadThumbnail(Context context, Uri uri, int targetSize, ImageView targetView) {
        if (uri == null || targetView == null) return;
        targetView.setImageDrawable(null);
        final String tag = uri.toString();
        targetView.setTag(tag);

        EXECUTOR.execute(() -> {
            try {
                Bitmap bitmap = decodeSampledBitmapFromUri(context, uri, targetSize, targetSize);
                if (bitmap != null) {
                    targetView.post(() -> {
                        if (tag.equals(targetView.getTag())) {
                            targetView.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public static Bitmap decodeSampledBitmapFromUri(Context context, Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, options);
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }
}
