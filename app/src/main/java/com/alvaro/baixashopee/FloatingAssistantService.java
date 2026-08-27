package com.alvaro.baixashopee;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Painel flutuante que une a fila da rota ao mapeador de toques assistido. */
public class FloatingAssistantService extends Service {
    public static final String EXTRA_PROFILE_ID = "profileId";
    private static final String CHANNEL_ID = "floating_delivery_assistant";
    private static final int NOTIFICATION_ID = 42;
    private static volatile FloatingAssistantService instance;

    private WindowManager windowManager;
    private WindowManager.LayoutParams panelParams;
    private View panel;
    private DeliveryStore store;
    private HouseStore houseStore;
    private ProfileManager profileManager;
    private AutomationProfile activeProfile;
    private final List<View> targetViews = new ArrayList<>();
    private TextView progress;
    private TextView person;
    private TextView profileName;
    private Button tracking;
    private Button numeric;
    private Button receiver;
    private Button play;
    private int nextStepIndex;
    private boolean gestureRunning;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new DeliveryStore(this);
        houseStore = new HouseStore(this);
        profileManager = new ProfileManager(this);
        activeProfile = profileManager.getActive();
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public static void requestRefresh() {
        FloatingAssistantService service = instance;
        if (service == null || service.panel == null) return;
        service.reloadProfile();
        service.render();
        service.renderTargets();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorize o painel sobre outros aplicativos", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String requested = intent.getStringExtra(EXTRA_PROFILE_ID);
            AutomationProfile selected = profileManager.findById(requested);
            if (selected != null) {
                activeProfile = selected;
                profileManager.setActive(selected.id);
                nextStepIndex = 0;
            }
        }
        if (panel == null) showPanel();
        reloadProfile();
        render();
        renderTargets();
        return START_NOT_STICKY;
    }

    private void showPanel() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        panel = LayoutInflater.from(this).inflate(R.layout.overlay_delivery_assistant, null);
        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        panelParams.x = 12;
        panelParams.y = 0;
        progress = panel.findViewById(R.id.overlayProgress);
        person = panel.findViewById(R.id.overlayPerson);
        profileName = panel.findViewById(R.id.overlayProfileName);
        tracking = panel.findViewById(R.id.overlayTracking);
        numeric = panel.findViewById(R.id.overlayNumeric);
        receiver = panel.findViewById(R.id.overlayReceiver);
        play = panel.findViewById(R.id.overlayPlay);

        tracking.setOnClickListener(v -> copyCurrent(0));
        numeric.setOnClickListener(v -> copyCurrent(1));
        receiver.setOnClickListener(v -> copyCurrent(2));
        panel.findViewById(R.id.overlayPrevious).setOnClickListener(v -> {
            store.rewind();
            render();
        });
        panel.findViewById(R.id.overlayNext).setOnClickListener(v -> {
            store.advance();
            render();
        });
        panel.findViewById(R.id.overlayOpenApp).setOnClickListener(v -> openMainApp());
        panel.findViewById(R.id.overlayClose).setOnClickListener(v -> stopSelf());
        play.setOnClickListener(v -> executeNextStep());
        panel.findViewById(R.id.overlayAddTap).setOnClickListener(v -> addTap());
        panel.findViewById(R.id.overlayAddSwipe).setOnClickListener(v -> addSwipe());
        panel.findViewById(R.id.overlayRemoveTarget).setOnClickListener(v -> removeLastStep());
        panel.findViewById(R.id.overlaySettings).setOnClickListener(v -> openSettings(-1));
        panel.findViewById(R.id.overlayStop).setOnClickListener(v -> {
            nextStepIndex = 0;
            gestureRunning = false;
            renderProfile();
            Toast.makeText(this, "Sequência interrompida", Toast.LENGTH_SHORT).show();
        });
        panel.findViewById(R.id.overlayMove).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    initialX = panelParams.x;
                    initialY = panelParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    panelParams.x = initialX - (int) (event.getRawX() - initialTouchX);
                    panelParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(panel, panelParams);
                    return true;
                }
                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });
        windowManager.addView(panel, panelParams);
    }

    private void addTap() {
        if (!bindProfileToCurrentApp()) return;
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        activeProfile.steps.add(AutomationStep.tap(size.x / 2, size.y / 2));
        profileManager.save(activeProfile);
        renderTargets();
        renderProfile();
    }

    private void addSwipe() {
        if (!bindProfileToCurrentApp()) return;
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        activeProfile.steps.add(AutomationStep.swipe(
                size.x / 2, Math.max(dp(120), size.y / 2 - dp(100)),
                size.x / 2, Math.min(size.y - dp(120), size.y / 2 + dp(100))));
        profileManager.save(activeProfile);
        renderTargets();
        renderProfile();
    }

    private boolean bindProfileToCurrentApp() {
        reloadProfile();
        String detected = AutomationAccessibilityService.getCurrentPackage();
        if (activeProfile == null) {
            Toast.makeText(this, "Crie uma configuração primeiro", Toast.LENGTH_LONG).show();
            return false;
        }
        if (activeProfile.allowedPackage.isEmpty()) {
            if (detected.isEmpty()) {
                Toast.makeText(this, "Abra o aplicativo que deseja mapear e tente novamente", Toast.LENGTH_LONG).show();
                return false;
            }
            activeProfile.allowedPackage = detected;
            profileManager.save(activeProfile);
        } else if (!detected.isEmpty() && !activeProfile.allowedPackage.equals(detected)) {
            Toast.makeText(this, "Abra o app autorizado: " + activeProfile.allowedPackage, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void removeLastStep() {
        reloadProfile();
        if (activeProfile == null || activeProfile.steps.isEmpty()) {
            Toast.makeText(this, "Não há alvo para remover", Toast.LENGTH_SHORT).show();
            return;
        }
        activeProfile.steps.remove(activeProfile.steps.size() - 1);
        profileManager.save(activeProfile);
        nextStepIndex = Math.min(nextStepIndex, activeProfile.steps.size());
        renderTargets();
        renderProfile();
    }

    private void executeNextStep() {
        reloadProfile();
        if (gestureRunning) return;
        if (activeProfile == null || activeProfile.steps.isEmpty()) {
            Toast.makeText(this, "Adicione alvos com + ou ↝", Toast.LENGTH_LONG).show();
            return;
        }
        if (nextStepIndex >= activeProfile.steps.size()) nextStepIndex = 0;
        AutomationStep step = activeProfile.steps.get(nextStepIndex);
        gestureRunning = true;
        play.setEnabled(false);
        // Os marcadores saem por um instante para o gesto alcançar o app abaixo deles.
        removeTargetViews();
        AutomationAccessibilityService.execute(activeProfile, step, (success, message) -> {
            gestureRunning = false;
            play.setEnabled(true);
            renderTargets();
            if (success) {
                nextStepIndex++;
                if (nextStepIndex >= activeProfile.steps.size()) {
                    nextStepIndex = 0;
                    Toast.makeText(this,
                            "Ciclo mapeado concluído • confira a tela e confirme manualmente",
                            Toast.LENGTH_LONG).show();
                }
                renderProfile();
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderTargets() {
        removeTargetViews();
        if (windowManager == null || activeProfile == null) return;
        for (int i = 0; i < activeProfile.steps.size(); i++) {
            AutomationStep step = activeProfile.steps.get(i);
            addTargetView(i, false, step.startX, step.startY,
                    AutomationStep.TYPE_SWIPE.equals(step.type) ? (i + 1) + "A" : String.valueOf(i + 1));
            if (AutomationStep.TYPE_SWIPE.equals(step.type)) {
                addTargetView(i, true, step.endX, step.endY, (i + 1) + "B");
            }
        }
    }

    private void addTargetView(int stepIndex, boolean endpoint, int centerX, int centerY, String label) {
        TextView target = new TextView(this);
        target.setText(label);
        target.setTextColor(getColor(R.color.text_primary_color));
        target.setTextSize(13);
        target.setGravity(Gravity.CENTER);
        target.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(withAlpha(getColor(R.color.target_circle_color), 190));
        circle.setStroke(dp(2), Color.WHITE);
        target.setBackground(circle);

        int diameter = dp(48);
        WindowManager.LayoutParams targetParams = new WindowManager.LayoutParams(
                diameter, diameter,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        targetParams.gravity = Gravity.START | Gravity.TOP;
        targetParams.x = Math.max(0, centerX - diameter / 2);
        targetParams.y = Math.max(0, centerY - diameter / 2);
        target.setOnTouchListener(new TargetDragListener(stepIndex, endpoint, targetParams, diameter));
        windowManager.addView(target, targetParams);
        targetViews.add(target);
    }

    private final class TargetDragListener implements View.OnTouchListener {
        private final int stepIndex;
        private final boolean endpoint;
        private final WindowManager.LayoutParams params;
        private final int diameter;
        private int initialX;
        private int initialY;
        private float downX;
        private float downY;
        private boolean moved;

        TargetDragListener(int stepIndex, boolean endpoint, WindowManager.LayoutParams params, int diameter) {
            this.stepIndex = stepIndex;
            this.endpoint = endpoint;
            this.params = params;
            this.diameter = diameter;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                initialX = params.x;
                initialY = params.y;
                downX = event.getRawX();
                downY = event.getRawY();
                moved = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int dx = (int) (event.getRawX() - downX);
                int dy = (int) (event.getRawY() - downY);
                moved |= Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3);
                params.x = Math.max(0, initialX + dx);
                params.y = Math.max(0, initialY + dy);
                windowManager.updateViewLayout(view, params);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                reloadProfile();
                if (activeProfile == null || stepIndex >= activeProfile.steps.size()) return true;
                if (!moved) {
                    openSettings(stepIndex);
                    return true;
                }
                AutomationStep step = activeProfile.steps.get(stepIndex);
                int x = params.x + diameter / 2;
                int y = params.y + diameter / 2;
                if (endpoint) {
                    step.endX = x;
                    step.endY = y;
                } else {
                    step.startX = x;
                    step.startY = y;
                    if (AutomationStep.TYPE_TAP.equals(step.type)) {
                        step.endX = x;
                        step.endY = y;
                    }
                }
                profileManager.save(activeProfile);
                return true;
            }
            return false;
        }
    }

    private void copyCurrent(int kind) {
        Delivery current = store.getCurrent();
        if (current == null) {
            Toast.makeText(this, "Não há entrega selecionada", Toast.LENGTH_SHORT).show();
            return;
        }
        String value;
        if (kind == 0) value = current.trackingCode;
        else if (kind == 1) value = current.numericCode();
        else value = store.getReceiverName();
        if (value.isEmpty()) {
            Toast.makeText(this, "Esse dado ainda não foi configurado", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Baixa da Shopee", value));
        Toast.makeText(this, "Copiado: " + value, Toast.LENGTH_SHORT).show();
    }

    private void render() {
        if (panel == null) return;
        List<Delivery> deliveries = store.getDeliveries();
        Delivery current = store.getCurrent();
        if (current == null) {
            progress.setText(deliveries.isEmpty() ? "Rota vazia" : "Fim da rota");
            person.setVisibility(View.GONE);
            tracking.setEnabled(false);
            numeric.setEnabled(false);
            receiver.setEnabled(false);
            renderProfile();
            return;
        }
        int index = store.getCurrentIndex();
        progress.setText("Entrega " + (index + 1) + " de " + deliveries.size());
        House house = houseStore.findById(current.houseId);
        String name = house != null && !house.residents.isEmpty() ? house.residents : current.customerName;
        String address = house != null && !house.address.isEmpty() ? house.address : current.address;
        String detail = name + (name.isEmpty() || address.isEmpty() ? "" : " • ") + address;
        if (current.hasOccurrence()) detail = "⚠ " + current.occurrenceType + "\n" + detail;
        person.setText(detail);
        person.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        tracking.setText(current.trackingCode);
        numeric.setText(current.numericCode());
        String receiverName = store.getReceiverName();
        receiver.setText(receiverName.isEmpty() ? "Configure seu nome" : receiverName);
        tracking.setEnabled(true);
        numeric.setEnabled(true);
        receiver.setEnabled(!receiverName.isEmpty());
        renderProfile();
    }

    private void renderProfile() {
        reloadProfile();
        if (profileName == null) return;
        if (activeProfile == null) {
            profileName.setText("Sem configuração");
            play.setText("▶ Próximo passo");
            return;
        }
        profileName.setText(activeProfile.name + " • " + activeProfile.steps.size() + " alvo(s)");
        int shown = activeProfile.steps.isEmpty() ? 0 : Math.min(nextStepIndex + 1, activeProfile.steps.size());
        play.setText(shown == 0 ? "▶ Próximo passo" : "▶ Passo " + shown + "/" + activeProfile.steps.size());
    }

    private void reloadProfile() {
        AutomationProfile saved = activeProfile == null ? profileManager.getActive()
                : profileManager.findById(activeProfile.id);
        if (saved != null) activeProfile = saved;
    }

    private void openMainApp() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
    }

    private void openSettings(int stepIndex) {
        if (activeProfile != null) profileManager.setActive(activeProfile.id);
        Intent open = new Intent(this, AutomationSettingsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (stepIndex >= 0) open.putExtra(AutomationSettingsActivity.EXTRA_STEP_INDEX, stepIndex);
        startActivity(open);
    }

    private void removeTargetViews() {
        if (windowManager != null) {
            for (View target : targetViews) {
                try { windowManager.removeView(target); } catch (Exception ignored) { }
            }
        }
        targetViews.clear();
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Painel flutuante de entregas", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém os controles flutuantes visíveis durante a rota.");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_delivery)
                .setContentTitle("Baixa da Shopee")
                .setContentText("Painel flutuante assistido ativo")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        removeTargetViews();
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) { }
        }
        panel = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
