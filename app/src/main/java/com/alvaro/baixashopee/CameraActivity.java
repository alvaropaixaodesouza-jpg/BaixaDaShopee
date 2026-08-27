package com.alvaro.baixashopee;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ExifInterface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends Activity {
    public static final String EXTRA_PHOTO_URI = "photoUri";
    public static final String EXTRA_PREFIX = "prefix";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ACCURACY = "accuracy";
    public static final String EXTRA_CAPTURED_AT = "capturedAt";
    private static final int REQUEST_CAMERA_PERMISSION = 301;

    private TextureView textureView;
    private ImageView reviewImage;
    private Button captureButton;
    private Button confirmButton;
    private Button retakeButton;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private File temporaryPhoto;
    private int sensorOrientation;
    private String photoPrefix;
    private Size previewSize = new Size(1280, 720);
    private TextView locationStatus;
    private Location capturedLocation;
    private long capturedAt;
    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().setStatusBarColor(getColor(R.color.ink));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        textureView = findViewById(R.id.cameraPreview);
        reviewImage = findViewById(R.id.cameraReview);
        captureButton = findViewById(R.id.cameraCaptureButton);
        confirmButton = findViewById(R.id.cameraConfirmButton);
        retakeButton = findViewById(R.id.cameraRetakeButton);
        TextView title = findViewById(R.id.cameraTitle);
        locationStatus = findViewById(R.id.cameraLocationStatus);

        photoPrefix = safeFilePart(getIntent().getStringExtra(EXTRA_PREFIX));
        String requestedTitle = getIntent().getStringExtra(EXTRA_TITLE);
        title.setText(requestedTitle == null || requestedTitle.trim().isEmpty()
                ? "Tirar foto" : requestedTitle);

        textureView.setSurfaceTextureListener(surfaceListener);
        captureButton.setOnClickListener(v -> capture());
        confirmButton.setOnClickListener(v -> publishAndReturn());
        retakeButton.setOnClickListener(v -> retake());
        findViewById(R.id.cameraCancelButton).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (textureView.isAvailable()) ensurePermissionAndOpen();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            ensurePermissionAndOpen();
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    private void ensurePermissionAndOpen() {
        boolean cameraMissing = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
        boolean locationMissing = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean locationPrompted = getSharedPreferences("camera_options", MODE_PRIVATE)
                .getBoolean("location_prompted", false);
        if (cameraMissing || (locationMissing && !locationPrompted)) {
            if (locationMissing) getSharedPreferences("camera_options", MODE_PRIVATE).edit()
                    .putBoolean("location_prompted", true).apply();
            requestPermissions(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Permita o uso da câmera para registrar as fotos", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openCamera() {
        if (camera != null || cameraHandler == null || !textureView.isAvailable()) return;
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String chosen = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics info = manager.getCameraCharacteristics(id);
                Integer facing = info.get(CameraCharacteristics.LENS_FACING);
                if (chosen == null) chosen = id;
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    chosen = id;
                    break;
                }
            }
            if (chosen == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            CameraCharacteristics info = manager.getCameraCharacteristics(chosen);
            Integer orientation = info.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 90 : orientation;
            StreamConfigurationMap map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size photoSize = choosePhotoSize(map == null ? null : map.getOutputSizes(ImageFormat.JPEG));
            previewSize = choosePreviewSize(map == null ? null : map.getOutputSizes(SurfaceTexture.class));
            imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::saveTemporaryImage, cameraHandler);
            manager.openCamera(chosen, cameraStateCallback, cameraHandler);
        } catch (Exception error) {
            showCameraError(error);
        }
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice opened) {
            camera = opened;
            createPreview();
        }
        @Override public void onDisconnected(CameraDevice disconnected) {
            disconnected.close();
            camera = null;
        }
        @Override public void onError(CameraDevice failed, int error) {
            failed.close();
            camera = null;
            runOnUiThread(() -> showCameraError(new IllegalStateException("Código da câmera: " + error)));
        }
    };

    private void createPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || camera == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            List<Surface> outputs = new ArrayList<>();
            outputs.add(previewSurface);
            outputs.add(imageReader.getSurface());
            camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (camera == null) return;
                    session = configured;
                    try {
                        session.setRepeatingRequest(previewBuilder.build(), null, cameraHandler);
                    } catch (CameraAccessException error) {
                        showCameraError(error);
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    showCameraError(new IllegalStateException("Não foi possível iniciar a visualização."));
                }
            }, cameraHandler);
        } catch (Exception error) {
            showCameraError(error);
        }
    }

    private void capture() {
        if (camera == null || session == null || imageReader == null) {
            Toast.makeText(this, "A câmera ainda está iniciando", Toast.LENGTH_SHORT).show();
            return;
        }
        captureButton.setEnabled(false);
        try {
            CaptureRequest.Builder still = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(imageReader.getSurface());
            still.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            still.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            session.capture(still.build(), null, cameraHandler);
        } catch (Exception error) {
            captureButton.setEnabled(true);
            showCameraError(error);
        }
    }

    private void saveTemporaryImage(ImageReader reader) {
        try (Image image = reader.acquireNextImage()) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            File file = File.createTempFile("baixa_foto_", ".jpg", getCacheDir());
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            }
            temporaryPhoto = file;
            runOnUiThread(() -> showReview(file));
        } catch (Exception error) {
            runOnUiThread(() -> {
                captureButton.setEnabled(true);
                showCameraError(error);
            });
        }
    }

    private void showReview(File file) {
        closeCamera();
        textureView.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        reviewImage.setVisibility(View.VISIBLE);
        reviewImage.setImageURI(Uri.fromFile(file));
        confirmButton.setVisibility(View.VISIBLE);
        retakeButton.setVisibility(View.VISIBLE);
        capturedAt = System.currentTimeMillis();
        captureLocation();
    }

    private void captureLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationStatus.setText("Localização não autorizada • a foto será salva sem coordenadas");
            locationStatus.setVisibility(View.VISIBLE);
            return;
        }
        locationStatus.setText("Obtendo localização… você já pode confirmar a foto");
        locationStatus.setVisibility(View.VISIBLE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        capturedLocation = bestLastLocation();
        if (capturedLocation != null) showLocationReady(capturedLocation);

        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (location == null) return;
                if (capturedLocation == null || location.getAccuracy() <= capturedLocation.getAccuracy() ||
                        location.getTime() > capturedLocation.getTime()) {
                    capturedLocation = location;
                    showLocationReady(location);
                }
                stopLocationUpdates();
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };
        try {
            String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper());
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                stopLocationUpdates();
                if (capturedLocation == null) {
                    locationStatus.setText("GPS ainda sem sinal • a foto pode ser salva sem coordenadas");
                }
            }, 8000);
        } catch (Exception error) {
            if (capturedLocation == null) locationStatus.setText("Não foi possível obter o GPS agora");
        }
    }

    private Location bestLastLocation() {
        Location best = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) continue;
                if (best == null || location.getTime() > best.getTime() ||
                        location.getAccuracy() < best.getAccuracy()) best = location;
            }
        } catch (SecurityException ignored) { }
        return best;
    }

    private void showLocationReady(Location location) {
        locationStatus.setText(String.format(Locale.getDefault(),
                "Localização salva • precisão aproximada: %.0f m", location.getAccuracy()));
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
        }
        locationListener = null;
    }

    private void retake() {
        stopLocationUpdates();
        capturedLocation = null;
        capturedAt = 0;
        locationStatus.setVisibility(View.GONE);
        deleteTemporary();
        reviewImage.setImageDrawable(null);
        reviewImage.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);
        retakeButton.setVisibility(View.GONE);
        textureView.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.VISIBLE);
        captureButton.setEnabled(true);
        ensurePermissionAndOpen();
    }

    private void publishAndReturn() {
        if (temporaryPhoto == null || !temporaryPhoto.exists()) return;
        confirmButton.setEnabled(false);
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, photoPrefix + "_" + timestamp + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Baixa da Shopee");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Não foi possível criar a foto na galeria.");
            try (FileInputStream input = new FileInputStream(temporaryPhoto);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Não foi possível salvar a foto.");
                byte[] chunk = new byte[64 * 1024];
                int count;
                while ((count = input.read(chunk)) >= 0) output.write(chunk, 0, count);
            }
            writeExif(uri);
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            deleteTemporary();
            Intent result = new Intent();
            result.putExtra(EXTRA_PHOTO_URI, uri.toString());
            if (capturedLocation != null) {
                result.putExtra(EXTRA_LATITUDE, capturedLocation.getLatitude());
                result.putExtra(EXTRA_LONGITUDE, capturedLocation.getLongitude());
                result.putExtra(EXTRA_ACCURACY, capturedLocation.getAccuracy());
            }
            result.putExtra(EXTRA_CAPTURED_AT, capturedAt == 0 ? System.currentTimeMillis() : capturedAt);
            setResult(RESULT_OK, result);
            finish();
        } catch (Exception error) {
            confirmButton.setEnabled(true);
            showCameraError(error);
        }
    }

    private void writeExif(Uri uri) {
        if (capturedLocation == null) return;
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "rw")) {
            if (descriptor == null) return;
            ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
            exif.setLatLong(capturedLocation.getLatitude(), capturedLocation.getLongitude());
            long time = capturedAt == 0 ? System.currentTimeMillis() : capturedAt;
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
                    new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date(time)));
            exif.saveAttributes();
        } catch (Exception ignored) {
            // A entrega ainda guarda coordenadas na base e no PDF se o EXIF não for aceito.
        }
    }

    private int jpegOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int deviceDegrees;
        if (rotation == Surface.ROTATION_90) deviceDegrees = 90;
        else if (rotation == Surface.ROTATION_180) deviceDegrees = 180;
        else if (rotation == Surface.ROTATION_270) deviceDegrees = 270;
        else deviceDegrees = 0;
        return (sensorOrientation - deviceDegrees + 360) % 360;
    }

    private Size choosePhotoSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1920, 1080);
        return Arrays.stream(sizes)
                .filter(size -> (long) size.getWidth() * size.getHeight() <= 12_000_000L)
                .max((a, b) -> Long.compare((long) a.getWidth() * a.getHeight(),
                        (long) b.getWidth() * b.getHeight()))
                .orElse(sizes[0]);
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        Size best = sizes[0];
        long target = 1280L * 720L;
        long bestDifference = Math.abs((long) best.getWidth() * best.getHeight() - target);
        for (Size size : sizes) {
            long difference = Math.abs((long) size.getWidth() * size.getHeight() - target);
            if (difference < bestDifference) {
                best = size;
                bestDifference = difference;
            }
        }
        return best;
    }

    private void startCameraThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("BaixaDaShopeeCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) return;
        cameraThread.quitSafely();
        try { cameraThread.join(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        cameraThread = null;
        cameraHandler = null;
    }

    private void closeCamera() {
        if (session != null) session.close();
        if (camera != null) camera.close();
        if (imageReader != null) imageReader.close();
        session = null;
        camera = null;
        imageReader = null;
    }

    private void showCameraError(Exception error) {
        runOnUiThread(() -> Toast.makeText(this,
                "Falha na câmera: " + (error.getMessage() == null ? "tente novamente" : error.getMessage()),
                Toast.LENGTH_LONG).show());
    }

    private void deleteTemporary() {
        if (temporaryPhoto != null && temporaryPhoto.exists()) temporaryPhoto.delete();
        temporaryPhoto = null;
    }

    private String safeFilePart(String value) {
        String safe = value == null ? "FOTO" : value.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isEmpty() ? "FOTO" : safe;
    }

    @Override
    protected void onDestroy() {
        stopLocationUpdates();
        deleteTemporary();
        super.onDestroy();
    }
}
