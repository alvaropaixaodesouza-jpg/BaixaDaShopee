package com.alvaro.baixashopee;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.alvaro.baixashopee.data.model.DeliveryGroup;
import com.alvaro.baixashopee.data.model.QueueOrganizer;
import com.alvaro.baixashopee.data.repository.DeliveryRepository;
import com.alvaro.baixashopee.photo.TempMediaStoreManager;

import java.util.List;

public class DeliveryKeyboardService extends InputMethodService {
    public static final String PREFS_KEYBOARD = "keyboard_prefs";
    public static final String KEY_HEIGHT_DP = "height_dp";
    public static final int MIN_HEIGHT_DP = 220;
    public static final int MAX_HEIGHT_DP = 420;
    public static final int DEFAULT_HEIGHT_DP = 290;

    private DeliveryStore store;
    private HouseStore houseStore;
    private DeliveryRepository repository;

    private View rootContainer;
    private TextView progress;
    private TextView statusHint;
    private TextView personName;
    private TextView address;
    private Button trackingButton;
    private Button numericButton;
    private Button nameButton;
    private Button packageButton;
    private Button houseButton;
    private Button undoButton;
    private Button previousButton;
    private Button nextButton;
    private Button switchKeyboardButton;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new DeliveryStore(this);
        houseStore = new HouseStore(this);
        repository = DeliveryRepository.getInstance(this);

        // Limpa temporários expirados na inicialização do serviço
        TempMediaStoreManager.cleanExpired(this);
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.keyboard_delivery, null);
        rootContainer = view.findViewById(R.id.keyboardRootContainer);
        progress = view.findViewById(R.id.keyboardProgress);
        statusHint = view.findViewById(R.id.keyboardStatusHint);
        personName = view.findViewById(R.id.keyboardPersonName);
        address = view.findViewById(R.id.keyboardAddress);

        trackingButton = view.findViewById(R.id.trackingButton);
        numericButton = view.findViewById(R.id.numericButton);
        nameButton = view.findViewById(R.id.nameButton);
        packageButton = view.findViewById(R.id.keyboardPackageButton);
        houseButton = view.findViewById(R.id.keyboardHouseButton);
        undoButton = view.findViewById(R.id.keyboardUndoButton);
        previousButton = view.findViewById(R.id.previousButton);
        nextButton = view.findViewById(R.id.nextButton);
        switchKeyboardButton = view.findViewById(R.id.switchKeyboardButton);

        trackingButton.setOnClickListener(v -> insertTracking());
        numericButton.setOnClickListener(v -> insertNumeric());
        nameButton.setOnClickListener(v -> insertConfiguredName());

        packageButton.setOnClickListener(v -> sharePackagePhoto());
        houseButton.setOnClickListener(v -> shareHousePhoto());
        undoButton.setOnClickListener(v -> performUndo());

        previousButton.setOnClickListener(v -> {
            store.rewind();
            render();
        });
        nextButton.setOnClickListener(v -> onNextClicked());
        switchKeyboardButton.setOnClickListener(v -> switchKeyboard());

        progress.setOnClickListener(v -> openManager());
        personName.setOnClickListener(v -> openManager());
        address.setOnClickListener(v -> openManager());

        applyConfiguredHeight();
        render();
        return view;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyConfiguredHeight();
        render();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    private void applyConfiguredHeight() {
        if (rootContainer == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_KEYBOARD, Context.MODE_PRIVATE);
        int heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP);
        heightDp = Math.max(MIN_HEIGHT_DP, Math.min(heightDp, MAX_HEIGHT_DP));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = (int) (heightDp * dm.density);

        ViewGroup.LayoutParams params = rootContainer.getLayoutParams();
        if (params != null) {
            params.height = heightPx;
            rootContainer.setLayoutParams(params);
        }
    }

    private void insertTracking() {
        Delivery current = store.getCurrent();
        if (current == null) return;
        if (commit(current.trackingCode)) {
            store.markTrackingUsed();
            advanceWhenReady();
        }
    }

    private void insertNumeric() {
        Delivery current = store.getCurrent();
        if (current == null) return;
        String digits = current.numericCode();
        if (digits.isEmpty()) {
            Toast.makeText(this, "Código não contém números", Toast.LENGTH_SHORT).show();
            return;
        }
        if (commit(digits)) {
            store.markNumericUsed();
            advanceWhenReady();
        }
    }

    private void insertConfiguredName() {
        Delivery current = store.getCurrent();
        String nameToInsert = resolveNameToInsert(current);

        if (nameToInsert.isEmpty()) {
            Toast.makeText(this, "Configure o nome nas preferências do aplicativo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (commit(nameToInsert)) {
            store.markNameUsed();
            advanceWhenReady();
        }
    }

    private String resolveNameToInsert(Delivery current) {
        SharedPreferences namePrefs = getSharedPreferences("name_settings", MODE_PRIVATE);
        String mode = namePrefs.getString("name_mode", "sequence");
        String fallback = store.getReceiverName();

        if ("sequence".equalsIgnoreCase(mode)) {
            if (current != null && current.customerName != null && !current.customerName.trim().isEmpty()
                    && !"-".equals(current.customerName.trim())) {
                return current.customerName.trim();
            }
        }
        return fallback;
    }

    private void sharePackagePhoto() {
        Delivery current = store.getCurrent();
        if (current == null) return;
        if (current.packagePhotoUri == null || current.packagePhotoUri.isEmpty()) {
            Toast.makeText(this, "Nenhuma foto de pacote vinculada", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri temp = TempMediaStoreManager.publishTemporaryCopy(this, Uri.parse(current.packagePhotoUri), "PACOTE_" + current.trackingCode);
        if (temp != null) {
            Toast.makeText(this, "Foto do pacote copiada para fotos recentes (5 min)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Não foi possível copiar foto", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareHousePhoto() {
        Delivery current = store.getCurrent();
        if (current == null) return;

        House house = null;
        if (current.houseId != null && !current.houseId.isEmpty()) {
            house = houseStore.findById(current.houseId);
        }
        if (house == null && current.address != null && !current.address.isEmpty()) {
            house = houseStore.findBySpecificAddress(current.address);
        }

        String facadeUri = house != null ? house.facadePhotoUri : current.facadePhotoUri;
        if (facadeUri == null || facadeUri.isEmpty()) {
            Toast.makeText(this, "Nenhuma foto de casa vinculada", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri temp = TempMediaStoreManager.publishTemporaryCopy(this, Uri.parse(facadeUri), "CASA_" + current.trackingCode);
        if (temp != null) {
            Toast.makeText(this, "Foto da casa copiada para fotos recentes (5 min)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Não foi possível copiar foto", Toast.LENGTH_SHORT).show();
        }
    }

    private void performUndo() {
        boolean success = repository.undoLastCompletion();
        if (success) {
            Toast.makeText(this, "Última baixa desfeita!", Toast.LENGTH_SHORT).show();
            render();
        } else {
            Toast.makeText(this, "Nenhuma baixa recente para desfazer", Toast.LENGTH_SHORT).show();
        }
    }

    private void onNextClicked() {
        Delivery current = store.getCurrent();
        if (current != null) {
            // Se os campos foram preenchidos, conclui e avança
            if (store.allFieldsUsed()) {
                repository.completeDelivery(current.trackingCode);
            } else {
                store.advance();
            }
        }
        render();
    }

    private boolean commit(String value) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || value == null || value.isEmpty()) {
            Toast.makeText(this, "Toque primeiro no campo de texto", Toast.LENGTH_SHORT).show();
            return false;
        }
        return connection.commitText(value, 1);
    }

    private void advanceWhenReady() {
        if (store.allFieldsUsed()) {
            Delivery current = store.getCurrent();
            if (current != null) {
                // Conclui e remove da fila ativa
                repository.completeDelivery(current.trackingCode);
                Delivery next = store.getCurrent();
                Toast.makeText(this,
                        next == null ? "Fila concluída!" : "Próxima: " + next.trackingCode,
                        Toast.LENGTH_SHORT).show();
            }
        }
        render();
    }

    private void render() {
        if (progress == null) return;

        List<Delivery> deliveries = store.getDeliveries();
        int index = store.getCurrentIndex();
        Delivery current = store.getCurrent();

        // Botão Desfazer é FIXO: continua ocupando o mesmo espaço, habilitado se houver histórico
        boolean canUndo = repository.hasUndoableCompletion();
        undoButton.setEnabled(canUndo);
        undoButton.setAlpha(canUndo ? 1.0f : 0.45f);

        if (deliveries.isEmpty()) {
            progress.setText("Fila vazia — toque para abrir");
            personName.setText("Nenhuma entrega ativa");
            address.setText("Importe uma rota no aplicativo");
            setDataButtonsEnabled(false);
            packageButton.setEnabled(false);
            houseButton.setEnabled(false);
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }

        if (current == null) {
            progress.setText("Fila concluída (" + deliveries.size() + " entregas)");
            personName.setText("Todas as entregas foram percorridas");
            address.setText("Use Voltar ou Desfazer para revisar");
            setDataButtonsEnabled(false);
            packageButton.setEnabled(false);
            houseButton.setEnabled(false);
            previousButton.setEnabled(true);
            nextButton.setEnabled(false);
            return;
        }

        // Calcula Pessoa X/Y e Pacote A/B
        List<DeliveryGroup> groups = QueueOrganizer.groupDeliveries(deliveries);
        int personIndex = 1;
        int totalPersons = Math.max(1, groups.size());
        int packageIndex = 1;
        int totalPackagesOfPerson = 1;

        for (int i = 0; i < groups.size(); i++) {
            DeliveryGroup g = groups.get(i);
            int pIdx = g.getPackages().indexOf(current);
            if (pIdx >= 0) {
                personIndex = i + 1;
                packageIndex = pIdx + 1;
                totalPackagesOfPerson = g.size();
                break;
            }
        }

        progress.setText("Pessoa " + personIndex + "/" + totalPersons + " • Pacote " + packageIndex + "/" + totalPackagesOfPerson);
        // Destinatário atual NUNCA é substituído pelos moradores da casa
        String custName = current.customerName.isEmpty() ? "Destinatário não informado" : current.customerName;
        personName.setText(custName);
        address.setText(current.address.isEmpty() ? "Endereço não informado" : current.address);

        setDataButtonsEnabled(true);
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(true);

        // Atualiza textos dos botões
        trackingButton.setText("BR • " + current.trackingCode);
        numericButton.setText("RG • " + current.numericCode());

        String nameToInsert = resolveNameToInsert(current);
        nameButton.setText("Nome • " + (nameToInsert.isEmpty() ? "..." : nameToInsert));

        // Estilos quando utilizados
        styleButtonState(trackingButton, store.isTrackingUsed(), true);
        styleButtonState(numericButton, store.isNumericUsed(), false);
        styleButtonState(nameButton, store.isNameUsed(), false);

        // Habilita botões de pacote e casa se possuírem fotos
        boolean hasPackagePhoto = current.packagePhotoUri != null && !current.packagePhotoUri.isEmpty();
        packageButton.setEnabled(true);
        packageButton.setAlpha(hasPackagePhoto ? 1.0f : 0.65f);

        House house = current.houseId != null ? houseStore.findById(current.houseId) : null;
        boolean hasHousePhoto = (house != null && !house.facadePhotoUri.isEmpty()) || !current.facadePhotoUri.isEmpty();
        houseButton.setEnabled(true);
        houseButton.setAlpha(hasHousePhoto ? 1.0f : 0.65f);
    }

    private void styleButtonState(Button button, boolean used, boolean isPrimary) {
        if (used) {
            button.setBackgroundResource(R.drawable.button_used);
            button.setTextColor(getColor(R.color.green));
        } else if (isPrimary) {
            button.setBackgroundResource(R.drawable.button_primary);
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackgroundResource(R.drawable.button_secondary);
            button.setTextColor(getColor(R.color.orange_dark));
        }
    }

    private void setDataButtonsEnabled(boolean enabled) {
        trackingButton.setEnabled(enabled);
        numericButton.setEnabled(enabled);
        nameButton.setEnabled(enabled);
    }

    private void switchKeyboard() {
        if (shouldOfferSwitchingToNextInputMethod()) {
            switchToNextInputMethod(false);
        } else {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }
    }

    private void openManager() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
