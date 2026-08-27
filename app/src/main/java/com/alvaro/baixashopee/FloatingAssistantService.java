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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Une a fila de entregas a um editor de macros visual.
 * Em edição os alvos podem ser arrastados; somente Play inicia os gestos.
 */
public class FloatingAssistantService extends Service {
    public static final String EXTRA_PROFILE_ID = "profileId";
    public static final String EXTRA_RESET_POSITION = "resetPosition";
    private static final String CHANNEL_ID = "floating_delivery_assistant";
    private static final int NOTIFICATION_ID = 42;
    private static volatile FloatingAssistantService instance;

    private final Handler runner = new Handler(Looper.getMainLooper());
    private final List<View> targetViews = new ArrayList<>();
    private WindowManager windowManager;
    private WindowManager.LayoutParams panelParams;
    private View panel;
    private View dataPanel;
    private DeliveryStore store;
    private HouseStore houseStore;
    private ProfileManager profileManager;
    private AutomationProfile activeProfile;
    private TextView progress;
    private TextView person;
    private TextView profileName;
    private Button tracking;
    private Button numeric;
    private Button receiver;
    private Button play;
    private Button addTapButton;
    private Button addSwipeButton;
    private Button removeButton;
    private View settingsButton;
    private View toggleDataButton;
    private Button moveButton;
    private View closeButton;
    private boolean dataVisible = true;
    private boolean compactMode;
    private boolean automationRunning;
    private boolean gestureRunning;
    private int stepIndex;
    private int completedCycles;
    private long runStartedAt;

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
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public static void requestRefresh() {
        FloatingAssistantService service = instance;
        if (service == null || service.panel == null) return;
        service.reloadProfile();
        service.applyPanelSize();
        service.renderDelivery();
        service.renderProfile();
        service.renderTargets();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorize o painel sobre outros aplicativos",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            AutomationProfile selected = profileManager.findById(
                    intent.getStringExtra(EXTRA_PROFILE_ID));
            if (selected != null) {
                if (automationRunning) stopAutomation(null);
                activeProfile = selected;
                profileManager.setActive(selected.id);
            }
        }
        if (panel == null) showPanel();
        if (intent != null && intent.getBooleanExtra(EXTRA_RESET_POSITION, false)) {
            resetPanelPosition();
        }
        reloadProfile();
        applyPanelSize();
        renderDelivery();
        renderProfile();
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
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.START | Gravity.TOP;
        panelParams.x = dp(8);
        panelParams.y = dp(140);

        dataPanel = panel.findViewById(R.id.overlayDataPanel);
        progress = panel.findViewById(R.id.overlayProgress);
        person = panel.findViewById(R.id.overlayPerson);
        profileName = panel.findViewById(R.id.overlayProfileName);
        tracking = panel.findViewById(R.id.overlayTracking);
        numeric = panel.findViewById(R.id.overlayNumeric);
        receiver = panel.findViewById(R.id.overlayReceiver);
        play = panel.findViewById(R.id.overlayPlay);
        addTapButton = panel.findViewById(R.id.overlayAddTap);
        addSwipeButton = panel.findViewById(R.id.overlayAddSwipe);
        removeButton = panel.findViewById(R.id.overlayRemoveTarget);
        settingsButton = panel.findViewById(R.id.overlaySettings);
        toggleDataButton = panel.findViewById(R.id.overlayToggleData);
        moveButton = panel.findViewById(R.id.overlayMove);
        closeButton = panel.findViewById(R.id.overlayClose);

        tracking.setOnClickListener(v -> copyCurrent(0));
        numeric.setOnClickListener(v -> copyCurrent(1));
        receiver.setOnClickListener(v -> copyCurrent(2));
        panel.findViewById(R.id.overlayPrevious).setOnClickListener(v -> {
            store.rewind();
            renderDelivery();
        });
        panel.findViewById(R.id.overlayNext).setOnClickListener(v -> {
            store.advance();
            renderDelivery();
        });
        panel.findViewById(R.id.overlayOpenApp).setOnClickListener(v -> openMainApp());
        closeButton.setOnClickListener(v -> stopSelf());
        play.setOnClickListener(v -> toggleAutomation());
        addTapButton.setOnClickListener(v -> addTap());
        addSwipeButton.setOnClickListener(v -> addSwipe());
        removeButton.setOnClickListener(v -> removeLastStep());
        settingsButton.setOnClickListener(v -> openSettings(-1));
        toggleDataButton.setOnClickListener(v -> {
            dataVisible = !dataVisible;
            dataPanel.setVisibility(dataVisible ? View.VISIBLE : View.GONE);
            panel.post(this::clampPanelPosition);
        });
        moveButton.setOnTouchListener(new PanelDragListener());
        windowManager.addView(panel, panelParams);
        panel.post(this::clampPanelPosition);
    }

    private void addTap() {
        if (!ensureEditableProfile()) return;
        android.graphics.Point size = screenSize();
        activeProfile.steps.add(AutomationStep.tap(size.x / 2, size.y / 2));
        profileManager.save(activeProfile);
        renderTargets();
        renderProfile();
    }

    private void addSwipe() {
        if (!ensureEditableProfile()) return;
        android.graphics.Point size = screenSize();
        activeProfile.steps.add(AutomationStep.swipe(
                size.x / 2, Math.max(dp(120), size.y / 2 - dp(110)),
                size.x / 2, Math.min(size.y - dp(120), size.y / 2 + dp(110))));
        profileManager.save(activeProfile);
        renderTargets();
        renderProfile();
    }

    private boolean ensureEditableProfile() {
        if (automationRunning) {
            Toast.makeText(this, "Pare a sequência antes de editar", Toast.LENGTH_SHORT).show();
            return false;
        }
        reloadProfile();
        if (activeProfile == null) {
            activeProfile = profileManager.create("Nova configuração");
        }
        return true;
    }

    private void removeLastStep() {
        if (!ensureEditableProfile()) return;
        if (activeProfile.steps.isEmpty()) {
            Toast.makeText(this, "Não há alvo para remover", Toast.LENGTH_SHORT).show();
            return;
        }
        activeProfile.steps.remove(activeProfile.steps.size() - 1);
        profileManager.save(activeProfile);
        renderTargets();
        renderProfile();
    }

    private void toggleAutomation() {
        if (automationRunning) {
            stopAutomation("Sequência interrompida");
            return;
        }
        startAutomation();
    }

    private void startAutomation() {
        reloadProfile();
        if (activeProfile == null || activeProfile.steps.isEmpty()) {
            Toast.makeText(this, "Adicione alvos com + ou ↝", Toast.LENGTH_LONG).show();
            return;
        }
        if (!AutomationAccessibilityService.isConnected()) {
            Toast.makeText(this,
                    "Ative a acessibilidade Baixa da Shopee — Automação",
                    Toast.LENGTH_LONG).show();
            return;
        }
        automationRunning = true;
        gestureRunning = false;
        stepIndex = 0;
        completedCycles = 0;
        runStartedAt = System.currentTimeMillis();
        setEditingEnabled(false);
        renderTargets();
        renderProfile();
        executeCurrentStep();
    }

    private void executeCurrentStep() {
        if (!automationRunning || gestureRunning) return;
        reloadProfile();
        if (activeProfile == null || activeProfile.steps.isEmpty()) {
            stopAutomation("A configuração ficou vazia");
            return;
        }
        if (AutomationProfile.STOP_DURATION.equals(activeProfile.stopMode)
                && System.currentTimeMillis() - runStartedAt >= activeProfile.runDurationMs) {
            stopAutomation("Tempo programado concluído");
            return;
        }
        if (stepIndex >= activeProfile.steps.size()) {
            completedCycles++;
            if (AutomationProfile.STOP_CYCLES.equals(activeProfile.stopMode)
                    && completedCycles >= activeProfile.cycleLimit) {
                stopAutomation("Ciclo concluído");
                return;
            }
            stepIndex = 0;
        }

        AutomationStep step = activeProfile.steps.get(stepIndex);
        gestureRunning = true;
        renderProfile();
        AutomationAccessibilityService.execute(activeProfile, step, (success, message) -> {
            gestureRunning = false;
            if (!automationRunning) return;
            if (success) {
                stepIndex++;
                executeCurrentStep();
            } else if (message != null && message.contains("cancelou")) {
                // Um toque manual simultâneo pode cancelar apenas o gesto atual.
                runner.postDelayed(this::executeCurrentStep, 250);
            } else {
                stopAutomation(message);
            }
        });
    }

    private void stopAutomation(String message) {
        automationRunning = false;
        gestureRunning = false;
        runner.removeCallbacksAndMessages(null);
        stepIndex = 0;
        completedCycles = 0;
        setEditingEnabled(true);
        renderTargets();
        renderProfile();
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void setEditingEnabled(boolean enabled) {
        addTapButton.setEnabled(enabled);
        addSwipeButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
        toggleDataButton.setEnabled(enabled);
        moveButton.setEnabled(enabled);
        closeButton.setEnabled(enabled);
        addTapButton.setAlpha(enabled ? 1f : 0.35f);
        addSwipeButton.setAlpha(enabled ? 1f : 0.35f);
        removeButton.setAlpha(enabled ? 1f : 0.35f);
        settingsButton.setAlpha(enabled ? 1f : 0.35f);
        toggleDataButton.setAlpha(enabled ? 1f : 0.35f);
        moveButton.setAlpha(enabled ? 1f : 0.35f);
        closeButton.setAlpha(enabled ? 1f : 0.35f);
        setChildrenEnabled(dataPanel, enabled);
        dataPanel.setAlpha(enabled ? 1f : 0.72f);
        play.setText(enabled ? "▶" : "■");
        play.setTextColor(getColor(enabled
                ? R.color.secondary_accent_color : R.color.primary_accent_color));
    }

    private void setChildrenEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setChildrenEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private void renderTargets() {
        removeTargetViews();
        if (windowManager == null || activeProfile == null) return;
        keepProfileTargetsOnScreen();
        if (hasSwipe()) addSwipeLines();
        for (int i = 0; i < activeProfile.steps.size(); i++) {
            AutomationStep step = activeProfile.steps.get(i);
            addTargetView(i, false, step.startX, step.startY,
                    AutomationStep.TYPE_SWIPE.equals(step.type)
                            ? (i + 1) + "A" : String.valueOf(i + 1));
            if (AutomationStep.TYPE_SWIPE.equals(step.type)) {
                addTargetView(i, true, step.endX, step.endY, (i + 1) + "B");
            }
        }
    }

    private void keepProfileTargetsOnScreen() {
        android.graphics.Point screen = screenSize();
        int radius = dp(activeProfile.targetSizeDp) / 2;
        int minX = Math.min(radius, Math.max(0, screen.x - 1));
        int minY = Math.min(radius, Math.max(0, screen.y - 1));
        int maxX = Math.max(minX, screen.x - radius);
        int maxY = Math.max(minY, screen.y - radius);
        boolean changed = false;
        for (AutomationStep step : activeProfile.steps) {
            int startX = clamp(step.startX, minX, maxX);
            int startY = clamp(step.startY, minY, maxY);
            int endX = clamp(step.endX, minX, maxX);
            int endY = clamp(step.endY, minY, maxY);
            changed |= startX != step.startX || startY != step.startY
                    || endX != step.endX || endY != step.endY;
            step.startX = startX;
            step.startY = startY;
            step.endX = endX;
            step.endY = endY;
        }
        if (changed) profileManager.save(activeProfile);
    }

    private boolean hasSwipe() {
        for (AutomationStep step : activeProfile.steps) {
            if (AutomationStep.TYPE_SWIPE.equals(step.type)) return true;
        }
        return false;
    }

    private void addSwipeLines() {
        View lines = new SwipeLinesView();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;
        windowManager.addView(lines, params);
        targetViews.add(lines);
    }

    private void addTargetView(int index, boolean endpoint, int centerX, int centerY,
                               String label) {
        TextView target = new TextView(this);
        target.setText(label);
        target.setTextColor(getColor(R.color.text_primary_color));
        target.setTextSize(13);
        target.setGravity(Gravity.CENTER);
        target.setTypeface(null, android.graphics.Typeface.BOLD);
        target.setAlpha(automationRunning ? 0.62f : 1f);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(withAlpha(getColor(R.color.target_circle_color), 190));
        circle.setStroke(dp(2), Color.WHITE);
        target.setBackground(circle);

        int diameter = dp(activeProfile.targetSizeDp);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (automationRunning) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                diameter, diameter, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;
        android.graphics.Point screen = screenSize();
        params.x = Math.max(0, Math.min(centerX - diameter / 2,
                Math.max(0, screen.x - diameter)));
        params.y = Math.max(0, Math.min(centerY - diameter / 2,
                Math.max(0, screen.y - diameter)));
        if (!automationRunning) {
            target.setOnTouchListener(new TargetDragListener(index, endpoint, params, diameter));
        }
        windowManager.addView(target, params);
        targetViews.add(target);
    }

    private final class TargetDragListener implements View.OnTouchListener {
        private final int targetIndex;
        private final boolean endpoint;
        private final WindowManager.LayoutParams params;
        private final int diameter;
        private int initialX;
        private int initialY;
        private float downX;
        private float downY;
        private boolean moved;

        TargetDragListener(int targetIndex, boolean endpoint,
                           WindowManager.LayoutParams params, int diameter) {
            this.targetIndex = targetIndex;
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
                android.graphics.Point screen = screenSize();
                params.x = Math.max(0, Math.min(initialX + dx,
                        Math.max(0, screen.x - diameter)));
                params.y = Math.max(0, Math.min(initialY + dy,
                        Math.max(0, screen.y - diameter)));
                windowManager.updateViewLayout(view, params);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                reloadProfile();
                if (activeProfile == null || targetIndex >= activeProfile.steps.size()) return true;
                if (!moved) {
                    openSettings(targetIndex);
                    return true;
                }
                AutomationStep step = activeProfile.steps.get(targetIndex);
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
                renderTargets();
                return true;
            }
            return false;
        }
    }

    private final class PanelDragListener implements View.OnTouchListener {
        private int initialX;
        private int initialY;
        private float downX;
        private float downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                initialX = panelParams.x;
                initialY = panelParams.y;
                downX = event.getRawX();
                downY = event.getRawY();
                moved = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int dx = (int) (event.getRawX() - downX);
                int dy = (int) (event.getRawY() - downY);
                moved |= Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4);
                panelParams.x = initialX + dx;
                panelParams.y = initialY + dy;
                clampPanelCoordinates();
                windowManager.updateViewLayout(panel, panelParams);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moved) toggleCompactMode();
                else clampPanelPosition();
                return true;
            }
            return false;
        }
    }

    private void toggleCompactMode() {
        if (automationRunning) return;
        compactMode = !compactMode;
        addTapButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        addSwipeButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        removeButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        settingsButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        toggleDataButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        dataPanel.setVisibility(!compactMode && dataVisible ? View.VISIBLE : View.GONE);
        moveButton.setText(compactMode ? "▣" : "✥");
        moveButton.setContentDescription(compactMode
                ? "Expandir painel" : "Arrastar ou recolher painel");
        panel.post(this::clampPanelPosition);
    }

    private void resetPanelPosition() {
        if (panelParams == null || panel == null) return;
        panelParams.x = dp(8);
        panelParams.y = dp(110);
        compactMode = false;
        if (addTapButton != null) {
            addTapButton.setVisibility(View.VISIBLE);
            addSwipeButton.setVisibility(View.VISIBLE);
            removeButton.setVisibility(View.VISIBLE);
            settingsButton.setVisibility(View.VISIBLE);
            toggleDataButton.setVisibility(View.VISIBLE);
            dataPanel.setVisibility(dataVisible ? View.VISIBLE : View.GONE);
            moveButton.setText("✥");
        }
        try { windowManager.updateViewLayout(panel, panelParams); }
        catch (Exception ignored) { }
        panel.post(this::clampPanelPosition);
    }

    private void clampPanelPosition() {
        if (panel == null || panelParams == null || windowManager == null) return;
        clampPanelCoordinates();
        try { windowManager.updateViewLayout(panel, panelParams); }
        catch (Exception ignored) { }
    }

    private void clampPanelCoordinates() {
        android.graphics.Point screen = screenSize();
        int width = panel == null || panel.getWidth() <= 0
                ? dp(compactMode ? 58 : 278) : panel.getWidth();
        int height = panel == null || panel.getHeight() <= 0
                ? dp(compactMode ? 140 : 370) : panel.getHeight();
        panelParams.x = Math.max(0, Math.min(panelParams.x,
                Math.max(0, screen.x - Math.min(width, screen.x))));
        panelParams.y = Math.max(0, Math.min(panelParams.y,
                Math.max(0, screen.y - Math.min(height, screen.y))));
    }

    private final class SwipeLinesView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwipeLinesView() {
            super(FloatingAssistantService.this);
            paint.setColor(withAlpha(getColor(R.color.target_circle_color), 190));
            paint.setStrokeWidth(dp(4));
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (activeProfile == null) return;
            for (AutomationStep step : activeProfile.steps) {
                if (!AutomationStep.TYPE_SWIPE.equals(step.type)) continue;
                canvas.drawLine(step.startX, step.startY, step.endX, step.endY, paint);
                double angle = Math.atan2(step.endY - step.startY, step.endX - step.startX);
                float length = dp(15);
                float a1x = (float) (step.endX - length * Math.cos(angle - Math.PI / 6));
                float a1y = (float) (step.endY - length * Math.sin(angle - Math.PI / 6));
                float a2x = (float) (step.endX - length * Math.cos(angle + Math.PI / 6));
                float a2y = (float) (step.endY - length * Math.sin(angle + Math.PI / 6));
                canvas.drawLine(step.endX, step.endY, a1x, a1y, paint);
                canvas.drawLine(step.endX, step.endY, a2x, a2y, paint);
            }
        }
    }

    private void copyCurrent(int kind) {
        Delivery current = store.getCurrent();
        if (current == null) {
            Toast.makeText(this, "Não há entrega selecionada", Toast.LENGTH_SHORT).show();
            return;
        }
        String value = kind == 0 ? current.trackingCode
                : kind == 1 ? current.numericCode() : store.getReceiverName();
        if (value.isEmpty()) {
            Toast.makeText(this, "Esse dado ainda não foi configurado", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Baixa da Shopee", value));
        Toast.makeText(this, "Copiado: " + value, Toast.LENGTH_SHORT).show();
    }

    private void renderDelivery() {
        if (panel == null) return;
        List<Delivery> deliveries = store.getDeliveries();
        Delivery current = store.getCurrent();
        if (current == null) {
            progress.setText(deliveries.isEmpty() ? "Rota vazia" : "Fim da rota");
            person.setVisibility(View.GONE);
            tracking.setEnabled(false);
            numeric.setEnabled(false);
            receiver.setEnabled(false);
            return;
        }
        int index = store.getCurrentIndex();
        progress.setText("Entrega " + (index + 1) + " de " + deliveries.size());
        House house = houseStore.findById(current.houseId);
        String name = house != null && !house.residents.isEmpty()
                ? house.residents : current.customerName;
        if ("-".equals(name.trim())) name = "";
        String address = house != null && !house.address.isEmpty()
                ? house.address : current.address;
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
    }

    private void renderProfile() {
        if (profileName == null) return;
        reloadProfile();
        if (activeProfile == null) {
            profileName.setText("Sem configuração • 0 alvos");
            return;
        }
        String state = automationRunning
                ? " • executando " + (stepIndex + 1) + "/" + activeProfile.steps.size()
                : " • " + activeProfile.steps.size() + " alvo(s) • editar";
        profileName.setText(activeProfile.name + state);
    }

    private void reloadProfile() {
        AutomationProfile saved = activeProfile == null ? profileManager.getActive()
                : profileManager.findById(activeProfile.id);
        if (saved != null) activeProfile = saved;
    }

    private void applyPanelSize() {
        if (dataPanel == null || activeProfile == null) return;
        ViewGroup.LayoutParams params = dataPanel.getLayoutParams();
        int available = Math.max(dp(150), screenSize().x - dp(72));
        params.width = Math.min(dp(activeProfile.panelWidthDp), available);
        dataPanel.setLayoutParams(params);
        dataPanel.post(this::clampPanelPosition);
    }

    private android.graphics.Point screenSize() {
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return size;
    }

    private void openMainApp() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
    }

    private void openSettings(int selectedStep) {
        if (automationRunning) stopAutomation(null);
        if (activeProfile != null) profileManager.setActive(activeProfile.id);
        Intent open = new Intent(this, AutomationSettingsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (selectedStep >= 0) {
            open.putExtra(AutomationSettingsActivity.EXTRA_STEP_INDEX, selectedStep);
        }
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

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Painel flutuante", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém o painel flutuante disponível durante a rota.");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_delivery)
                .setContentTitle("Baixa da Shopee")
                .setContentText("Painel flutuante ativo • toque para abrir")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        automationRunning = false;
        runner.removeCallbacksAndMessages(null);
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
