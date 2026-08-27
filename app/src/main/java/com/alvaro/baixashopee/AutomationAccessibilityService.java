package com.alvaro.baixashopee;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * Executa somente um passo por comando explícito do usuário no painel.
 * A confirmação final da entrega permanece fora do fluxo automático.
 */
public final class AutomationAccessibilityService extends AccessibilityService {
    public interface GestureCallback {
        void onResult(boolean success, String message);
    }

    private static volatile AutomationAccessibilityService instance;
    private static volatile String currentPackage = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        if (!getPackageName().equals(packageName)) currentPackage = packageName;
    }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static String getCurrentPackage() {
        return currentPackage == null ? "" : currentPackage;
    }

    public static void execute(AutomationProfile profile, AutomationStep step,
                               GestureCallback callback) {
        AutomationAccessibilityService service = instance;
        if (service == null) {
            callback.onResult(false, "Ative a acessibilidade do Baixa da Shopee");
            return;
        }
        String allowed = profile == null ? "" : profile.allowedPackage.trim();
        if (allowed.isEmpty()) {
            callback.onResult(false, "Mapeie este perfil dentro do aplicativo autorizado primeiro");
            return;
        }
        if (!allowed.equals(getCurrentPackage())) {
            callback.onResult(false, "Abra o aplicativo autorizado para este perfil: " + allowed);
            return;
        }
        service.dispatch(profile, step, callback);
    }

    private void dispatch(AutomationProfile profile, AutomationStep step, GestureCallback callback) {
        Path path = new Path();
        path.moveTo(step.startX, step.startY);
        long duration = Math.max(1, step.durationMs);
        if (AutomationStep.TYPE_SWIPE.equals(step.type)) {
            path.lineTo(step.endX, step.endY);
        } else {
            duration = Math.min(250, duration);
        }
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                mainHandler.postDelayed(() -> callback.onResult(true, "Passo executado"),
                        Math.max(0, step.delayAfterMs));
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                callback.onResult(false, "O Android cancelou este gesto");
            }
        }, mainHandler);
        if (!accepted) callback.onResult(false, "O Android não aceitou este gesto");
    }
}
