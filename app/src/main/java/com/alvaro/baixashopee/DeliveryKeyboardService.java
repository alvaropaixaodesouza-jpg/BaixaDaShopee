package com.alvaro.baixashopee;

import android.content.Intent;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class DeliveryKeyboardService extends InputMethodService {
    private DeliveryStore store;
    private HouseStore houseStore;
    private TextView progress;
    private TextView personName;
    private TextView address;
    private TextView hint;
    private Button trackingButton;
    private Button numericButton;
    private Button nameButton;
    private Button previousButton;
    private Button nextButton;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new DeliveryStore(this);
        houseStore = new HouseStore(this);
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.keyboard_delivery, null);
        progress = view.findViewById(R.id.keyboardProgress);
        personName = view.findViewById(R.id.keyboardPersonName);
        address = view.findViewById(R.id.keyboardAddress);
        hint = view.findViewById(R.id.keyboardHint);
        trackingButton = view.findViewById(R.id.trackingButton);
        numericButton = view.findViewById(R.id.numericButton);
        nameButton = view.findViewById(R.id.nameButton);
        previousButton = view.findViewById(R.id.previousButton);
        nextButton = view.findViewById(R.id.nextButton);
        Button switchButton = view.findViewById(R.id.switchKeyboardButton);

        trackingButton.setOnClickListener(v -> insertTracking());
        numericButton.setOnClickListener(v -> insertNumeric());
        nameButton.setOnClickListener(v -> insertReceiverName());
        previousButton.setOnClickListener(v -> {
            store.rewind();
            render();
        });
        nextButton.setOnClickListener(v -> {
            store.advance();
            render();
        });
        switchButton.setOnClickListener(v -> switchKeyboard());
        progress.setOnClickListener(v -> openManager());
        personName.setOnClickListener(v -> openManager());
        address.setOnClickListener(v -> openManager());

        render();
        return view;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        render();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
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
            Toast.makeText(this, "Este código não contém números", Toast.LENGTH_SHORT).show();
            return;
        }
        if (commit(digits)) {
            store.markNumericUsed();
            advanceWhenReady();
        }
    }

    private void insertReceiverName() {
        String name = store.getReceiverName();
        if (name.isEmpty()) {
            Toast.makeText(this, "Abra o app e salve seu nome primeiro", Toast.LENGTH_LONG).show();
            return;
        }
        if (commit(name)) {
            store.markNameUsed();
            advanceWhenReady();
        }
    }

    private boolean commit(String value) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || value == null || value.isEmpty()) {
            Toast.makeText(this, "Toque primeiro no campo que deseja preencher", Toast.LENGTH_SHORT).show();
            return false;
        }
        return connection.commitText(value, 1);
    }

    private void advanceWhenReady() {
        if (store.allFieldsUsed()) {
            store.advance();
            Delivery next = store.getCurrent();
            Toast.makeText(this,
                    next == null ? "Fim da fila" : "Próxima: " + next.trackingCode,
                    Toast.LENGTH_SHORT).show();
        }
        render();
    }

    private void render() {
        if (progress == null) return;
        List<Delivery> deliveries = store.getDeliveries();
        int index = store.getCurrentIndex();
        Delivery current = store.getCurrent();

        if (deliveries.isEmpty()) {
            progress.setText("Lista vazia — toque para abrir");
            personName.setVisibility(View.GONE);
            address.setVisibility(View.GONE);
            hint.setText("Nenhum dado será inserido");
            setDataButtonsEnabled(false);
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }

        if (current == null) {
            progress.setText("Fila concluída");
            personName.setVisibility(View.GONE);
            address.setVisibility(View.VISIBLE);
            address.setText(deliveries.size() + " entregas percorridas");
            hint.setText("Use Voltar para revisar uma entrega");
            setDataButtonsEnabled(false);
            previousButton.setEnabled(true);
            nextButton.setEnabled(false);
            return;
        }

        setDataButtonsEnabled(true);
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(index + 1 <= deliveries.size());
        progress.setText("Entrega " + (index + 1) + " de " + deliveries.size());
        House house = houseStore.findById(current.houseId);
        String displayName = house != null && !house.residents.isEmpty()
                ? house.residents : current.customerName;
        String displayAddress = house != null && !house.address.isEmpty()
                ? house.address : current.address;
        personName.setText(displayName);
        personName.setVisibility(displayName.isEmpty() ? View.GONE : View.VISIBLE);
        address.setText(displayAddress);
        address.setVisibility(displayAddress.isEmpty() ? View.GONE : View.VISIBLE);

        trackingButton.setText("Código completo  •  " + current.trackingCode);
        numericButton.setText("Somente números  •  " + current.numericCode());
        String receiverName = store.getReceiverName();
        nameButton.setText(receiverName.isEmpty() ? "Configure seu nome no aplicativo" : "Recebedor  •  " + receiverName);

        styleDataButton(trackingButton, store.isTrackingUsed(), true);
        styleDataButton(numericButton, store.isNumericUsed(), false);
        styleDataButton(nameButton, store.isNameUsed(), false);

        int used = (store.isTrackingUsed() ? 1 : 0) +
                (store.isNumericUsed() ? 1 : 0) +
                (store.isNameUsed() ? 1 : 0);
        hint.setText(used == 0
                ? "Avança quando os três dados forem inseridos"
                : used + " de 3 dados inseridos nesta entrega");
    }

    private void styleDataButton(Button button, boolean used, boolean primaryWhenUnused) {
        if (used) {
            button.setBackgroundResource(R.drawable.button_used);
            button.setTextColor(getColor(R.color.green));
        } else if (primaryWhenUnused) {
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
            manager.showInputMethodPicker();
        }
    }

    private void openManager() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
