package com.alvaro.baixashopee;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 101;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_EXPORT = 103;
    private static final int REQUEST_QR = 104;

    private DeliveryStore store;
    private HouseStore houseStore;
    private OccurrenceManager occurrenceManager;
    private ProfileManager profileManager;
    private DeliveryAdapter adapter;
    private TextView summaryText;
    private TextView currentDeliveryText;
    private EditText receiverNameInput;
    private Button packagePhotoButton;
    private Button facadePhotoButton;
    private Button linkHouseButton;
    private Button navigationButton;
    private Button generatePdfButton;
    private ImageView packagePreview;
    private ImageView facadePreview;
    private boolean pendingPackagePhoto;
    private int selectedIndex;
    private int pendingPhotoIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            configureSystemBars();

            store = new DeliveryStore(this);
            houseStore = new HouseStore(this);
            occurrenceManager = new OccurrenceManager(this);
            profileManager = new ProfileManager(this);
            summaryText = findViewById(R.id.summaryText);
            currentDeliveryText = findViewById(R.id.currentDeliveryText);
            receiverNameInput = findViewById(R.id.receiverNameInput);
            packagePhotoButton = findViewById(R.id.packagePhotoButton);
            facadePhotoButton = findViewById(R.id.facadePhotoButton);
            linkHouseButton = findViewById(R.id.linkHouseButton);
            navigationButton = findViewById(R.id.navigationButton);
            generatePdfButton = findViewById(R.id.generatePdfButton);
            packagePreview = findViewById(R.id.packagePreview);
            facadePreview = findViewById(R.id.facadePreview);
            ListView deliveryList = findViewById(R.id.deliveryList);

            receiverNameInput.setText(store.getReceiverName());
            List<Delivery> initial = store.getDeliveries();
            selectedIndex = initial.isEmpty() ? 0 : Math.min(store.getCurrentIndex(), initial.size() - 1);
            adapter = new DeliveryAdapter(this, this::showDeliveryMenu);
            deliveryList.setAdapter(adapter);
            deliveryList.setOnItemClickListener((parent, view, position, id) -> {
                selectedIndex = position;
                refresh();
            });

            findViewById(R.id.saveNameButton).setOnClickListener(v -> saveReceiverName());
            findViewById(R.id.importButton).setOnClickListener(v -> chooseSpreadsheet());
            findViewById(R.id.pasteListButton).setOnClickListener(v -> showPasteDialog());
            findViewById(R.id.enableKeyboardButton).setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
            findViewById(R.id.selectKeyboardButton).setOnClickListener(v -> {
                InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                manager.showInputMethodPicker();
            });
            findViewById(R.id.houseMemoryButton).setOnClickListener(v -> showHouseMemory());
            findViewById(R.id.clearRouteButton).setOnClickListener(v -> confirmClearRoute());
            findViewById(R.id.floatingPanelButton).setOnClickListener(v -> showFloatingPanelControls());
            findViewById(R.id.scanQrButton).setOnClickListener(v -> scanQrCode());
            packagePhotoButton.setOnClickListener(v -> takePhoto(true));
            facadePhotoButton.setOnClickListener(v -> takePhoto(false));
            linkHouseButton.setOnClickListener(v -> showLinkHouseDialog());
            navigationButton.setOnClickListener(v -> openNavigation());
            generatePdfButton.setOnClickListener(v -> handleReport());
            findViewById(R.id.useInKeyboardButton).setOnClickListener(v -> {
                if (isValidSelection()) {
                    store.setCurrentIndex(selectedIndex);
                    refresh();
                    Toast.makeText(this, "O teclado começará nesta entrega", Toast.LENGTH_SHORT).show();
                }
            });
            findViewById(R.id.exportButton).setOnClickListener(v -> chooseExportDestination());

            if (savedInstanceState != null) {
                pendingPackagePhoto = savedInstanceState.getBoolean("pendingPackagePhoto", false);
                selectedIndex = savedInstanceState.getInt("selectedIndex", selectedIndex);
                pendingPhotoIndex = savedInstanceState.getInt("pendingPhotoIndex", -1);
            }

            if (!getPreferences(MODE_PRIVATE).getBoolean("notice_seen", false)) showSafetyNotice();
            refresh();
        } catch (Throwable error) {
            showStartupRecovery(error);
        }
    }

    private void showStartupRecovery(Throwable error) {
        store = null;
        houseStore = null;
        adapter = null;
        LinearLayout recovery = new LinearLayout(this);
        recovery.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        recovery.setPadding(pad, pad, pad, pad);
        recovery.setBackgroundColor(getColor(R.color.cream));

        TextView title = new TextView(this);
        title.setText("Baixa da Shopee — recuperação");
        title.setTextSize(23);
        title.setTextColor(getColor(R.color.ink));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        recovery.addView(title);

        TextView message = new TextView(this);
        String detail = error.getClass().getSimpleName() +
                (error.getMessage() == null ? "" : ": " + error.getMessage());
        message.setText("O aplicativo encontrou um erro ao abrir, mas seus dados não foram apagados.\n\nErro: " + detail +
                "\n\nTire uma captura desta tela e envie para corrigirmos exatamente a causa.");
        message.setTextSize(16);
        message.setTextColor(getColor(R.color.ink));
        message.setPadding(0, pad, 0, pad);
        recovery.addView(message);

        Button retry = new Button(this);
        retry.setText("Tentar abrir novamente");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> recreate());
        recovery.addView(retry);

        Button clearRoute = new Button(this);
        clearRoute.setText("Limpar somente a rota e tentar");
        clearRoute.setAllCaps(false);
        clearRoute.setOnClickListener(v -> {
            getSharedPreferences("delivery_queue", MODE_PRIVATE).edit()
                    .remove("deliveries")
                    .remove("current_index")
                    .remove("tracking_used")
                    .remove("numeric_used")
                    .remove("name_used")
                    .commit();
            recreate();
        });
        recovery.addView(clearRoute);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(recovery);
        setContentView(scroll);
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.cream));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("pendingPackagePhoto", pendingPackagePhoto);
        outState.putInt("selectedIndex", selectedIndex);
        outState.putInt("pendingPhotoIndex", pendingPhotoIndex);
    }

    private void showSafetyNotice() {
        new AlertDialog.Builder(this)
                .setTitle("Uso responsável")
                .setMessage("Este aplicativo organiza textos, rotas e fotos. Ele não confirma entregas. Use somente dados, imagens e procedimentos autorizados pela sua operação e confira cada encomenda antes de finalizar no aplicativo oficial.")
                .setCancelable(false)
                .setPositiveButton("Entendi", (dialog, which) ->
                        getPreferences(MODE_PRIVATE).edit().putBoolean("notice_seen", true).apply())
                .show();
    }

    private void saveReceiverName() {
        String name = receiverNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            receiverNameInput.setError("Digite o nome autorizado do recebedor");
            return;
        }
        store.setReceiverName(name);
        Toast.makeText(this, "Nome salvo no teclado", Toast.LENGTH_SHORT).show();
    }

    private void chooseSpreadsheet() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv", "text/comma-separated-values", "text/plain"
        });
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void importSpreadsheet(Uri uri) {
        String displayName = queryDisplayName(uri);
        summaryText.setText("Lendo " + displayName + "…");
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Não foi possível abrir o arquivo.");
                List<Delivery> imported = SpreadsheetImporter.importFile(input, displayName);
                runOnUiThread(() -> confirmReplacement(imported));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    refresh();
                    showError(error.getMessage());
                });
            }
        }).start();
    }

    private void confirmReplacement(List<Delivery> imported) {
        new AlertDialog.Builder(this)
                .setTitle(imported.size() + " entregas encontradas")
                .setMessage("A rota do dia será substituída. A memória de casas não será apagada; endereços específicos já conhecidos serão vinculados automaticamente.")
                .setNegativeButton("Cancelar", (dialog, which) -> refresh())
                .setPositiveButton("Importar", (dialog, which) -> {
                    store.replaceDeliveries(imported);
                    selectedIndex = 0;
                    refresh();
                    Toast.makeText(this, "Nova rota pronta", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void showPasteDialog() {
        EditText input = new EditText(this);
        input.setHint("Um código por linha\nBR123456789\nBR987654321");
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setPadding(36, 18, 36, 18);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Colar lista de códigos")
                .setMessage("Também aceita: código; nome; endereço")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Carregar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<Delivery> imported = SpreadsheetImporter.importPastedCodes(input.getText().toString());
            if (imported.isEmpty()) input.setError("Nenhum código válido encontrado");
            else {
                dialog.dismiss();
                confirmReplacement(imported);
            }
        }));
        dialog.show();
    }

    private void confirmClearRoute() {
        if (store.getDeliveries().isEmpty()) {
            Toast.makeText(this, "A rota já está vazia", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Limpar a rota do dia?")
                .setMessage("Os códigos e vínculos desta rota sairão da tela. Casas cadastradas, fotos de fachada e as fotos que já estão na galeria não serão apagadas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar rota", (dialog, which) -> {
                    store.clearDeliveries();
                    selectedIndex = 0;
                    refresh();
                    Toast.makeText(this, "Rota limpa; memória de casas preservada", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void showFloatingPanelControls() {
        new AlertDialog.Builder(this)
                .setTitle("Painel flutuante assistido")
                .setMessage("O painel copia os dados da rota e executa um alvo por vez. A confirmação final continua manual.")
                .setItems(new String[]{
                                "1. Autorizar sobre outros aplicativos",
                                "2. Ativar acessibilidade",
                                "Iniciar painel",
                                "Editar perfis e ocorrências",
                                "Parar painel"
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(permission);
                            } else if (which == 1) {
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                            } else if (which == 2) {
                                if (!Settings.canDrawOverlays(this)) {
                                    Toast.makeText(this, "Autorize primeiro o painel flutuante", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                startFloatingPanel(profileManager.getActive());
                            } else if (which == 3) {
                                startActivity(new Intent(this, AutomationSettingsActivity.class));
                            } else {
                                stopService(new Intent(this, FloatingAssistantService.class));
                            }
                        })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showDeliveryMenu(int position) {
        List<Delivery> deliveries = store.getDeliveries();
        if (position < 0 || position >= deliveries.size()) return;
        selectedIndex = position;
        Delivery delivery = deliveries.get(position);
        String occurrenceAction = delivery.hasOccurrence() ? "Alterar ocorrência" : "Colocar em ocorrência";
        String secondaryAction = delivery.hasOccurrence() ? "Remover ocorrência" : "Editar definições de ocorrência";
        String[] actions = {
                "Editar nome e endereço",
                occurrenceAction,
                secondaryAction,
                "Usar perfil Baixa assistida",
                "Usar perfil Ocorrência assistida",
                "Excluir desta rota"
        };
        new AlertDialog.Builder(this)
                .setTitle(delivery.trackingCode)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showDeliveryEditor(position);
                    else if (which == 1) showOccurrencePicker(position);
                    else if (which == 2 && delivery.hasOccurrence()) {
                        store.clearOccurrenceAt(position);
                        refresh();
                    } else if (which == 2) {
                        startActivity(new Intent(this, AutomationSettingsActivity.class));
                    } else if (which == 3) {
                        startProfileForDelivery(position, AutomationProfile.KIND_DOWNLOAD);
                    } else if (which == 4) {
                        startProfileForDelivery(position, AutomationProfile.KIND_OCCURRENCE);
                    } else if (which == 5) {
                        confirmRemoveDelivery(position);
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showDeliveryEditor(int position) {
        List<Delivery> deliveries = store.getDeliveries();
        if (position < 0 || position >= deliveries.size()) return;
        Delivery delivery = deliveries.get(position);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, 0, pad, 0);
        EditText name = field("Nome da pessoa");
        EditText address = field("Endereço completo");
        name.setText(delivery.customerName);
        address.setText(delivery.address);
        address.setMinLines(2);
        form.addView(name);
        form.addView(address);
        new AlertDialog.Builder(this)
                .setTitle("Editar dados desta entrega")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    store.updateDetailsAt(position, name.getText().toString(), address.getText().toString());
                    refresh();
                })
                .show();
    }

    private void showOccurrencePicker(int position) {
        List<String> definitions = occurrenceManager.getItems();
        List<String> labels = new ArrayList<>(definitions);
        labels.add("＋ Criar nova definição");
        new AlertDialog.Builder(this)
                .setTitle("Ocorrência desta entrega")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which < definitions.size()) {
                        showOccurrenceNote(position, definitions.get(which));
                    } else {
                        EditText input = new EditText(this);
                        input.setHint("Nome da nova ocorrência");
                        new AlertDialog.Builder(this)
                                .setTitle("Nova definição")
                                .setView(input)
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Continuar", (create, selected) -> {
                                    String value = input.getText().toString().trim();
                                    if (!value.isEmpty()) {
                                        occurrenceManager.add(value);
                                        showOccurrenceNote(position, value);
                                    }
                                })
                                .show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showOccurrenceNote(int position, String type) {
        EditText note = new EditText(this);
        note.setHint("Observação opcional");
        note.setMinLines(3);
        Delivery current = store.getDeliveries().get(position);
        if (type.equals(current.occurrenceType)) note.setText(current.occurrenceNote);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        note.setPadding(pad, 8, pad, 8);
        new AlertDialog.Builder(this)
                .setTitle(type)
                .setView(note)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar ocorrência", (dialog, which) -> {
                    store.markOccurrenceAt(position, type, note.getText().toString());
                    refresh();
                })
                .show();
    }

    private void startProfileForDelivery(int position, String kind) {
        store.setCurrentIndex(position);
        selectedIndex = position;
        AutomationProfile profile = profileManager.findByKind(kind);
        if (profile == null) {
            Toast.makeText(this, "Crie este perfil primeiro", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AutomationSettingsActivity.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorize o painel e toque novamente", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startFloatingPanel(profile);
        refresh();
    }

    private void startFloatingPanel(AutomationProfile profile) {
        Intent service = new Intent(this, FloatingAssistantService.class);
        if (profile != null) service.putExtra(FloatingAssistantService.EXTRA_PROFILE_ID, profile.id);
        startForegroundService(service);
    }

    private void confirmRemoveDelivery(int position) {
        Delivery delivery = store.getDeliveries().get(position);
        new AlertDialog.Builder(this)
                .setTitle("Excluir " + delivery.trackingCode + " da rota?")
                .setMessage("A memória permanente da casa não será apagada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    store.removeAt(position);
                    selectedIndex = Math.max(0, Math.min(position, store.getDeliveries().size() - 1));
                    refresh();
                })
                .show();
    }

    private void takePhoto(boolean packagePhoto) {
        if (!isValidSelection()) {
            Toast.makeText(this, "Selecione uma entrega primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        Delivery current = store.getDeliveries().get(selectedIndex);
        if (!packagePhoto && houseStore.findById(current.houseId) == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Vincule a casa primeiro")
                    .setMessage("A foto da fachada fica na memória da casa e poderá ser reconhecida nas próximas rotas.")
                    .setNegativeButton("Agora não", null)
                    .setPositiveButton("Vincular casa", (dialog, which) -> showLinkHouseDialog())
                    .show();
            return;
        }
        pendingPackagePhoto = packagePhoto;
        pendingPhotoIndex = selectedIndex;
        String kind = packagePhoto ? "PACOTE" : "FACHADA";
        Intent camera = new Intent(this, CameraActivity.class);
        camera.putExtra(CameraActivity.EXTRA_PREFIX, current.trackingCode + "_" + kind);
        camera.putExtra(CameraActivity.EXTRA_TITLE,
                packagePhoto ? "Foto do pacote" : "Foto de referência da fachada");
        startActivityForResult(camera, REQUEST_CAMERA);
    }

    private void scanQrCode() {
        if (store.getDeliveries().isEmpty()) {
            Toast.makeText(this, "Importe a rota antes de escanear", Toast.LENGTH_LONG).show();
            return;
        }
        Intent camera = new Intent(this, CameraActivity.class);
        camera.putExtra(CameraActivity.EXTRA_PREFIX, "LEITURA_QR");
        camera.putExtra(CameraActivity.EXTRA_TITLE, "Fotografe o QR Code do pacote");
        startActivityForResult(camera, REQUEST_QR);
    }

    private void finishQrScan(Intent data) {
        if (data == null) return;
        String uriValue = data.getStringExtra(CameraActivity.EXTRA_PHOTO_URI);
        if (uriValue == null || uriValue.trim().isEmpty()) return;
        summaryText.setText("Lendo QR Code offline…");
        BarcodeReader.scan(this, Uri.parse(uriValue), new BarcodeReader.Callback() {
            @Override public void onSuccess(String rawValue) {
                int index = store.findIndexInside(rawValue);
                if (index < 0) {
                    refresh();
                    showError("O QR Code foi lido, mas não corresponde a nenhum pacote da rota.\n\nConteúdo: " + rawValue);
                    return;
                }
                selectedIndex = index;
                store.setCurrentIndex(index);
                refresh();
                Delivery selected = store.getDeliveries().get(index);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Pacote selecionado")
                        .setMessage(selected.trackingCode +
                                (selected.customerName.isEmpty() ? "" : "\n" + selected.customerName) +
                                "\n\nDeseja tirar a foto do pacote agora?")
                        .setNegativeButton("Agora não", null)
                        .setPositiveButton("Abrir câmera", (dialog, which) -> takePhoto(true))
                        .show();
            }

            @Override public void onError(String message) {
                refresh();
                showError(message);
            }
        });
    }

    private void finishPhoto(Intent data) {
        if (data == null || pendingPhotoIndex < 0) return;
        String uri = data.getStringExtra(CameraActivity.EXTRA_PHOTO_URI);
        if (uri == null || uri.trim().isEmpty()) return;
        List<Delivery> deliveries = store.getDeliveries();
        if (pendingPhotoIndex >= deliveries.size()) return;
        Delivery delivery = deliveries.get(pendingPhotoIndex);
        double latitude = data.getDoubleExtra(CameraActivity.EXTRA_LATITUDE, 0);
        double longitude = data.getDoubleExtra(CameraActivity.EXTRA_LONGITUDE, 0);
        float accuracy = data.getFloatExtra(CameraActivity.EXTRA_ACCURACY, 0);
        long capturedAt = data.getLongExtra(CameraActivity.EXTRA_CAPTURED_AT, System.currentTimeMillis());
        if (pendingPackagePhoto) {
            store.updatePhotoAt(pendingPhotoIndex, true, uri);
        } else {
            House house = houseStore.findById(delivery.houseId);
            if (house != null) {
                houseStore.updateFacade(house.id, uri);
                store.syncHouseFacade(house.id, uri);
            }
        }
        if (latitude != 0 || longitude != 0) {
            store.updateLocationAt(pendingPhotoIndex, latitude, longitude, accuracy, capturedAt);
            if (!delivery.houseId.isEmpty()) {
                houseStore.updateLocation(delivery.houseId, latitude, longitude, accuracy, capturedAt);
            }
        }

        List<Delivery> updated = store.getDeliveries();
        Delivery saved = updated.get(pendingPhotoIndex);
        House savedHouse = houseStore.findById(saved.houseId);
        boolean hasFacade = savedHouse != null && !savedHouse.facadePhotoUri.isEmpty();
        if (!saved.packagePhotoUri.isEmpty() && hasFacade && pendingPhotoIndex + 1 < updated.size()) {
            selectedIndex = pendingPhotoIndex + 1;
            Toast.makeText(this, "Fotos prontas • próximo pacote selecionado", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, pendingPackagePhoto ? "Foto do pacote salva" : "Fachada salva na memória da casa",
                    Toast.LENGTH_SHORT).show();
        }
        pendingPhotoIndex = -1;
        refresh();
    }

    private void showHouseMemory() {
        List<House> houses = houseStore.getHouses();
        List<String> labels = new ArrayList<>();
        labels.add("＋ Cadastrar nova casa");
        for (House house : houses) {
            labels.add(house.displayName() + (house.address.isEmpty() ? "" : "\n" + house.address));
        }
        new AlertDialog.Builder(this)
                .setTitle("Memória de casas • " + houses.size())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) showHouseEditor(null, -1);
                    else showHouseEditor(houses.get(which - 1), -1);
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showLinkHouseDialog() {
        if (!isValidSelection()) {
            Toast.makeText(this, "Selecione uma entrega primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        Delivery delivery = store.getDeliveries().get(selectedIndex);
        House linked = houseStore.findById(delivery.houseId);
        List<House> houses = houseStore.getHouses();
        List<String> labels = new ArrayList<>();
        labels.add("＋ Cadastrar nova casa e vincular");
        if (linked != null) labels.add("✎ Editar a casa atual: " + linked.displayName());
        if (linked != null) labels.add("× Remover vínculo desta entrega");
        int fixed = labels.size();
        for (House house : houses) labels.add("Vincular: " + house.displayName() +
                (house.address.isEmpty() ? "" : "\n" + house.address));

        new AlertDialog.Builder(this)
                .setTitle(linked == null ? "Vincular uma casa" : "Casa atual: " + linked.displayName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showHouseEditor(null, selectedIndex);
                    } else if (linked != null && which == 1) {
                        showHouseEditor(linked, selectedIndex);
                    } else if (linked != null && which == 2) {
                        store.linkHouseAt(selectedIndex, "");
                        refresh();
                    } else {
                        House chosen = houses.get(which - fixed);
                        store.linkHouseAt(selectedIndex, chosen.id);
                        refresh();
                        Toast.makeText(this, "Entrega vinculada a " + chosen.displayName(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showHouseEditor(House existing, int linkIndex) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, 4, pad, 0);
        EditText label = field("Apelido da casa (ex.: Casa de Maria)");
        EditText residents = field("Moradores / pessoas deste endereço");
        EditText address = field("Endereço completo, com número");
        EditText map = field("Link do Google Maps ou Waze (opcional)");
        EditText notes = field("Observações (portão, referência, etc.)");
        notes.setMinLines(2);
        if (existing != null) {
            label.setText(existing.label);
            residents.setText(existing.residents);
            address.setText(existing.address);
            map.setText(existing.mapUri);
            notes.setText(existing.notes);
        } else if (linkIndex >= 0 && linkIndex < store.getDeliveries().size()) {
            Delivery delivery = store.getDeliveries().get(linkIndex);
            if (!delivery.customerName.isEmpty()) {
                label.setText("Casa de " + delivery.customerName);
                residents.setText(delivery.customerName);
            }
            address.setText(delivery.address);
            if (delivery.hasDestinationLocation()) {
                map.setText(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f",
                        delivery.destinationLatitude, delivery.destinationLongitude,
                        delivery.destinationLatitude, delivery.destinationLongitude));
            }
        }
        form.addView(label); form.addView(residents); form.addView(address); form.addView(map); form.addView(notes);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Cadastrar casa" : "Editar " + existing.displayName())
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .setNeutralButton(existing == null ? null : "Excluir", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String labelValue = label.getText().toString().trim();
                String addressValue = address.getText().toString().trim();
                if (labelValue.isEmpty() && addressValue.isEmpty()) {
                    label.setError("Informe um apelido ou endereço");
                    return;
                }
                House house = existing == null
                        ? House.create(labelValue, residents.getText().toString(), addressValue,
                        map.getText().toString(), notes.getText().toString())
                        : new House(existing.id, labelValue, residents.getText().toString(), addressValue,
                        map.getText().toString(), existing.facadePhotoUri, notes.getText().toString(),
                        existing.latitude, existing.longitude, existing.locationAccuracy, existing.lastVisitedAt);
                houseStore.save(house);
                if (linkIndex >= 0) store.linkHouseAt(linkIndex, house.id);
                dialog.dismiss();
                refresh();
                Toast.makeText(this, "Casa salva na memória", Toast.LENGTH_SHORT).show();
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Excluir esta casa da memória?")
                            .setMessage("Os códigos da rota serão apenas desvinculados. A foto que já está na galeria não será apagada.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Excluir", (confirm, which) -> {
                                store.unlinkHouseEverywhere(existing.id);
                                houseStore.delete(existing.id);
                                dialog.dismiss();
                                refresh();
                            }).show());
        });
        dialog.show();
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(false);
        field.setMaxLines(3);
        return field;
    }

    private void openNavigation() {
        if (!isValidSelection()) return;
        Delivery delivery = store.getDeliveries().get(selectedIndex);
        House house = houseStore.findById(delivery.houseId);
        String mapUri = house == null ? "" : house.mapUri;
        String address = house != null && !house.address.isEmpty() ? house.address : delivery.address;
        Uri destination;
        if (!mapUri.isEmpty()) {
            destination = Uri.parse(mapUri.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") ? mapUri : "https://" + mapUri);
        } else if (delivery.hasDestinationLocation()) {
            String coordinates = String.format(Locale.US, "%.6f,%.6f",
                    delivery.destinationLatitude, delivery.destinationLongitude);
            destination = Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(coordinates));
        } else if (!address.isEmpty()) {
            destination = Uri.parse("geo:0,0?q=" + Uri.encode(address));
        } else {
            Toast.makeText(this, "Cadastre o endereço ou o link do mapa desta casa", Toast.LENGTH_LONG).show();
            return;
        }
        Intent view = new Intent(Intent.ACTION_VIEW, destination);
        if (view.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "Nenhum aplicativo de mapas foi encontrado", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(Intent.createChooser(view, "Abrir rota com"));
    }

    private void chooseExportDestination() {
        if (store.getDeliveries().isEmpty()) {
            Toast.makeText(this, "Não há lista para exportar", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "entregas_com_memoria.csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void handleReport() {
        if (!isValidSelection()) return;
        Delivery delivery = store.getDeliveries().get(selectedIndex);
        if (delivery.reportUri.isEmpty()) {
            generateReport(selectedIndex);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Relatório desta entrega")
                .setItems(new String[]{"Abrir último PDF", "Gerar PDF atualizado"}, (dialog, which) -> {
                    if (which == 0) openReport(delivery.reportUri);
                    else generateReport(selectedIndex);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void generateReport(int index) {
        List<Delivery> deliveries = store.getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery delivery = deliveries.get(index);
        if (delivery.packagePhotoUri.isEmpty()) {
            Toast.makeText(this, "Tire primeiro a foto do pacote", Toast.LENGTH_LONG).show();
            return;
        }
        House house = houseStore.findById(delivery.houseId);
        generatePdfButton.setEnabled(false);
        generatePdfButton.setText("Gerando PDF…");
        new Thread(() -> {
            try {
                Uri report = DeliveryReportGenerator.generate(this, delivery, house);
                store.updateReportAt(index, report.toString());
                runOnUiThread(() -> {
                    refresh();
                    Toast.makeText(this, "PDF salvo em Documentos/BaixaDaShopee/Entregas", Toast.LENGTH_LONG).show();
                    openReport(report.toString());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    refresh();
                    showError("Falha ao gerar PDF: " + error.getMessage());
                });
            }
        }).start();
    }

    private void openReport(String uriValue) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(Uri.parse(uriValue), "application/pdf");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, "Abrir relatório com"));
        } catch (Exception error) {
            Toast.makeText(this, "PDF salvo, mas nenhum leitor de PDF foi encontrado", Toast.LENGTH_LONG).show();
        }
    }

    private void exportCsv(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Não foi possível criar o arquivo.");
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            StringBuilder csv = new StringBuilder("codigo_rastreio;nome;endereco;codigo_numerico;at_id;stop;bairro;cidade;cep;latitude_destino;longitude_destino;status;ocorrencia;observacao_ocorrencia;casa;link_mapa;latitude_foto;longitude_foto;precisao_metros;horario_foto;foto_pacote;foto_fachada;relatorio_pdf\r\n");
            for (Delivery delivery : store.getDeliveries()) {
                House house = houseStore.findById(delivery.houseId);
                String name = house != null && !house.residents.isEmpty() ? house.residents : delivery.customerName;
                String address = house != null && !house.address.isEmpty() ? house.address : delivery.address;
                csv.append(csvCell(delivery.trackingCode)).append(';')
                        .append(csvCell(name)).append(';')
                        .append(csvCell(address)).append(';')
                        .append(csvCell(delivery.numericCode())).append(';')
                        .append(csvCell(delivery.atId)).append(';')
                        .append(csvCell(delivery.stop)).append(';')
                        .append(csvCell(delivery.neighborhood)).append(';')
                        .append(csvCell(delivery.city)).append(';')
                        .append(csvCell(delivery.postalCode)).append(';')
                        .append(csvCell(delivery.hasDestinationLocation() ? String.valueOf(delivery.destinationLatitude) : "")).append(';')
                        .append(csvCell(delivery.hasDestinationLocation() ? String.valueOf(delivery.destinationLongitude) : "")).append(';')
                        .append(csvCell(delivery.status)).append(';')
                        .append(csvCell(delivery.occurrenceType)).append(';')
                        .append(csvCell(delivery.occurrenceNote)).append(';')
                        .append(csvCell(house == null ? "" : house.displayName())).append(';')
                        .append(csvCell(house == null ? "" : house.mapUri)).append(';')
                        .append(csvCell(delivery.hasLocation() ? String.valueOf(delivery.latitude) : "")).append(';')
                        .append(csvCell(delivery.hasLocation() ? String.valueOf(delivery.longitude) : "")).append(';')
                        .append(csvCell(delivery.hasLocation() ? String.valueOf(delivery.locationAccuracy) : "")).append(';')
                        .append(csvCell(delivery.photographedAt == 0 ? "" : new SimpleDateFormat(
                                "dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(delivery.photographedAt)))).append(';')
                        .append(csvCell(delivery.packagePhotoUri)).append(';')
                        .append(csvCell(house == null ? delivery.facadePhotoUri : house.facadePhotoUri)).append(';')
                        .append(csvCell(delivery.reportUri)).append("\r\n");
            }
            output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Lista com a memória das casas exportada", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            showError(error.getMessage());
        }
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        }
        return "planilha";
    }

    private void refresh() {
        List<Delivery> deliveries = store.getDeliveries();
        int index = store.getCurrentIndex();
        if (!deliveries.isEmpty()) selectedIndex = Math.max(0, Math.min(selectedIndex, deliveries.size() - 1));
        adapter.submit(deliveries, deliveries.isEmpty() ? -1 : selectedIndex);

        if (deliveries.isEmpty()) {
            summaryText.setText("Nenhuma rota carregada • " + houseStore.getHouses().size() + " casas salvas");
            currentDeliveryText.setText("Importe uma lista para começar.");
            setDeliveryActionsEnabled(false);
            showPreview(packagePreview, "");
            showPreview(facadePreview, "");
            return;
        }

        String keyboardPosition = index >= deliveries.size() ? "concluída" : (index + 1) + " de " + deliveries.size();
        summaryText.setText("Teclado: " + keyboardPosition + " • Rota: " + (selectedIndex + 1) + " de " + deliveries.size());
        Delivery current = deliveries.get(selectedIndex);
        House house = houseStore.findById(current.houseId);
        String name = house != null && !house.residents.isEmpty() ? house.residents : current.customerName;
        String address = house != null && !house.address.isEmpty() ? house.address : current.address;
        StringBuilder text = new StringBuilder();
        text.append(current.trackingCode).append("\nSomente números: ").append(current.numericCode());
        if (house != null) text.append("\nCasa: ").append(house.displayName());
        if (!name.isEmpty()) text.append("\nPessoa(s): ").append(name);
        if (!address.isEmpty()) text.append("\n").append(address);
        if (current.hasDestinationLocation()) text.append(String.format(Locale.getDefault(),
                "\nDestino da planilha: %.6f, %.6f",
                current.destinationLatitude, current.destinationLongitude));
        if (current.hasLocation()) text.append(String.format(Locale.getDefault(),
                "\nGPS da foto: %.6f, %.6f (±%.0f m)", current.latitude, current.longitude, current.locationAccuracy));
        if (current.hasOccurrence()) {
            text.append("\nOcorrência: ").append(current.occurrenceType);
            if (!current.occurrenceNote.isEmpty()) text.append(" — ").append(current.occurrenceNote);
        }
        currentDeliveryText.setText(text.toString());
        setDeliveryActionsEnabled(true);

        String facade = house == null ? current.facadePhotoUri : house.facadePhotoUri;
        packagePhotoButton.setText(current.packagePhotoUri.isEmpty() ? "Foto do pacote" : "✓ Foto do pacote");
        facadePhotoButton.setText(facade.isEmpty() ? "Foto da fachada" : "✓ Fachada na memória");
        linkHouseButton.setText(house == null ? "Vincular casa" : "Casa: " + house.displayName());
        navigationButton.setText((house != null && !house.mapUri.isEmpty()) || current.hasDestinationLocation()
                ? "Abrir destino da rota" : "Abrir no mapa");
        generatePdfButton.setText(current.reportUri.isEmpty() ? "Gerar PDF desta entrega" : "✓ Abrir ou atualizar PDF");
        showPreview(packagePreview, current.packagePhotoUri);
        showPreview(facadePreview, facade);
    }

    private void setDeliveryActionsEnabled(boolean enabled) {
        packagePhotoButton.setEnabled(enabled);
        facadePhotoButton.setEnabled(enabled);
        linkHouseButton.setEnabled(enabled);
        navigationButton.setEnabled(enabled);
        generatePdfButton.setEnabled(enabled);
        findViewById(R.id.useInKeyboardButton).setEnabled(enabled);
        findViewById(R.id.exportButton).setEnabled(enabled);
    }

    private void showPreview(ImageView view, String uri) {
        if (uri == null || uri.isEmpty()) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        try {
            view.setImageURI(null);
            view.setImageURI(Uri.parse(uri));
            view.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            view.setVisibility(View.GONE);
        }
    }

    private boolean isValidSelection() {
        return selectedIndex >= 0 && selectedIndex < store.getDeliveries().size();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Não foi possível continuar")
                .setMessage(message == null || message.trim().isEmpty() ? "Ocorreu um erro inesperado." : message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importSpreadsheet(data.getData());
        } else if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            finishPhoto(data);
        } else if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportCsv(data.getData());
        } else if (requestCode == REQUEST_QR && resultCode == RESULT_OK) {
            finishQrScan(data);
        }
    }
}
