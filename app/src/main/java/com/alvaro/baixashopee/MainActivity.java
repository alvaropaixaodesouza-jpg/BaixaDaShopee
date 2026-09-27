package com.alvaro.baixashopee;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alvaro.baixashopee.data.db.AppDatabase;
import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;
import com.alvaro.baixashopee.data.db.entities.PhotoEntity;
import com.alvaro.baixashopee.data.model.DeliveryGroup;
import com.alvaro.baixashopee.data.model.NeighborhoodHelper;
import com.alvaro.baixashopee.data.model.QueueOrganizer;
import com.alvaro.baixashopee.data.model.SortOrder;
import com.alvaro.baixashopee.data.repository.DeliveryRepository;
import com.alvaro.baixashopee.export.HouseExporter;
import com.alvaro.baixashopee.photo.PendingPhotosAdapter;
import com.alvaro.baixashopee.photo.PhotoAssignerDialog;
import com.alvaro.baixashopee.photo.PhotoProcessor;
import com.alvaro.baixashopee.photo.TempMediaStoreManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 101;
    private static final int REQUEST_CAMERA_PACKAGE = 102;
    private static final int REQUEST_CAMERA_FACADE = 103;
    private static final int REQUEST_PICK_GALLERY = 104;
    private static final int REQUEST_EXPORT_HOUSES = 105;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DeliveryStore store;
    private HouseStore houseStore;
    private DeliveryRepository repository;
    private AppDatabase db;
    private PhotoProcessor photoProcessor;

    // Abas de navegação
    private View tabRouteLayout;
    private View tabPhotosLayout;
    private View tabArchiveLayout;
    private View tabSettingsLayout;
    private TextView navTextRoute, navTextPhotos, navTextArchive, navTextSettings;
    private TextView summaryText;

    // Aba Rota
    private TextView currentDeliveryText;
    private Button completeCurrentBtn;
    private Button packagePhotoButton, facadePhotoButton, navigationButton, linkHouseButton, generatePdfButton;
    private Button sortOrderBtn, neighborhoodFilterBtn;
    private TextView routeCountLabel;
    private RecyclerView routeRecyclerView;
    private DeliveryAdapter deliveryAdapter;
    private int selectedIndex = 0;

    // Aba Fotos
    private TextView photoProgressText;
    private TextView pendingPhotosTitle;
    private Button photoFilterBtn;
    private RecyclerView pendingPhotosGrid;
    private PendingPhotosAdapter pendingPhotosAdapter;
    private String currentPhotoFilter = "ALL";

    // Aba Arquivo
    private Button archiveSubtabHouses, archiveSubtabCompleted;
    private View archiveHousesSection, archiveCompletedSection;
    private EditText archiveHouseSearchInput, archiveCompletedSearchInput;
    private TextView archiveCompletedCount;
    private RecyclerView archiveHousesRecyclerView, archiveCompletedRecyclerView;
    private HouseAdapter houseAdapter;
    private CompletedDeliveriesAdapter completedDeliveriesAdapter;

    // Aba Ajustes
    private TextView settingsOverlayChip, settingsAccessibilityChip;
    private RadioGroup settingsNameModeGroup;
    private RadioButton radioUseSequence, radioUseAlternative;
    private EditText settingsAlternativeNameInput;
    private TextView settingsKeyboardHeightLabel;
    private SeekBar settingsKeyboardHeightSeekBar;
    private TextView diagnosticDetailsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureSystemBars();

        store = new DeliveryStore(this);
        houseStore = new HouseStore(this);
        repository = DeliveryRepository.getInstance(this);
        db = AppDatabase.getInstance(this);
        photoProcessor = new PhotoProcessor(this);

        // Limpa temporários expirados na inicialização
        TempMediaStoreManager.cleanExpired(this);

        initViews();
        setupNavigation();
        setupRouteTab();
        setupPhotosTab();
        setupArchiveTab();
        setupSettingsTab();

        switchToTab("ROTA");
        refreshAll();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.cream));
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }
    }

    private void initViews() {
        summaryText = findViewById(R.id.summaryText);
        tabRouteLayout = findViewById(R.id.tabRouteLayout);
        tabPhotosLayout = findViewById(R.id.tabPhotosLayout);
        tabArchiveLayout = findViewById(R.id.tabArchiveLayout);
        tabSettingsLayout = findViewById(R.id.tabSettingsLayout);

        navTextRoute = findViewById(R.id.navTextRoute);
        navTextPhotos = findViewById(R.id.navTextPhotos);
        navTextArchive = findViewById(R.id.navTextArchive);
        navTextSettings = findViewById(R.id.navTextSettings);

        findViewById(R.id.headerImportBtn).setOnClickListener(v -> chooseSpreadsheet());
    }

    private void setupNavigation() {
        findViewById(R.id.navTabRoute).setOnClickListener(v -> switchToTab("ROTA"));
        findViewById(R.id.navTabPhotos).setOnClickListener(v -> switchToTab("FOTOS"));
        findViewById(R.id.navTabArchive).setOnClickListener(v -> switchToTab("ARQUIVO"));
        findViewById(R.id.navTabSettings).setOnClickListener(v -> switchToTab("AJUSTES"));
    }

    private void switchToTab(String tab) {
        tabRouteLayout.setVisibility("ROTA".equals(tab) ? View.VISIBLE : View.GONE);
        tabPhotosLayout.setVisibility("FOTOS".equals(tab) ? View.VISIBLE : View.GONE);
        tabArchiveLayout.setVisibility("ARQUIVO".equals(tab) ? View.VISIBLE : View.GONE);
        tabSettingsLayout.setVisibility("AJUSTES".equals(tab) ? View.VISIBLE : View.GONE);

        int activeColor = getColor(R.color.orange);
        int inactiveColor = getColor(R.color.muted);

        navTextRoute.setTextColor("ROTA".equals(tab) ? activeColor : inactiveColor);
        navTextPhotos.setTextColor("FOTOS".equals(tab) ? activeColor : inactiveColor);
        navTextArchive.setTextColor("ARQUIVO".equals(tab) ? activeColor : inactiveColor);
        navTextSettings.setTextColor("AJUSTES".equals(tab) ? activeColor : inactiveColor);

        if ("FOTOS".equals(tab)) loadPendingPhotos();
        else if ("ARQUIVO".equals(tab)) loadArchiveData();
        else if ("AJUSTES".equals(tab)) loadSettingsData();
    }

    // ==========================================
    // ABA 1: ROTA
    // ==========================================
    private void setupRouteTab() {
        currentDeliveryText = findViewById(R.id.currentDeliveryText);
        completeCurrentBtn = findViewById(R.id.completeCurrentBtn);
        packagePhotoButton = findViewById(R.id.packagePhotoButton);
        facadePhotoButton = findViewById(R.id.facadePhotoButton);
        navigationButton = findViewById(R.id.navigationButton);
        linkHouseButton = findViewById(R.id.linkHouseButton);
        generatePdfButton = findViewById(R.id.generatePdfButton);
        sortOrderBtn = findViewById(R.id.sortOrderBtn);
        neighborhoodFilterBtn = findViewById(R.id.neighborhoodFilterBtn);
        routeCountLabel = findViewById(R.id.routeCountLabel);
        routeRecyclerView = findViewById(R.id.routeRecyclerView);

        routeRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        deliveryAdapter = new DeliveryAdapter(this, new DeliveryAdapter.OnDeliveryClickListener() {
            @Override
            public void onClick(int position, Delivery delivery) {
                selectedIndex = position;
                store.setCurrentIndex(position);
                refreshRoute();
            }

            @Override
            public void onMenu(int position, Delivery delivery) {
                showDeliveryMenu(position, delivery);
            }
        });
        routeRecyclerView.setAdapter(deliveryAdapter);

        findViewById(R.id.useInKeyboardButton).setOnClickListener(v -> {
            store.setCurrentIndex(selectedIndex);
            Toast.makeText(this, "Teclado começará nesta entrega", Toast.LENGTH_SHORT).show();
            refreshRoute();
        });

        completeCurrentBtn.setOnClickListener(v -> {
            Delivery current = getSelectedDelivery();
            if (current != null) {
                repository.completeDelivery(current.trackingCode);
                Toast.makeText(this, "Entrega " + current.trackingCode + " concluída!", Toast.LENGTH_SHORT).show();
                refreshAll();
            }
        });

        packagePhotoButton.setOnClickListener(v -> takePhoto(true));
        facadePhotoButton.setOnClickListener(v -> takePhoto(false));
        navigationButton.setOnClickListener(v -> openNavigation());
        linkHouseButton.setOnClickListener(v -> showLinkHouseDialog());
        generatePdfButton.setOnClickListener(v -> generateDeliveryPdf());

        sortOrderBtn.setOnClickListener(v -> showSortOrderDialog());
        neighborhoodFilterBtn.setOnClickListener(v -> showNeighborhoodFilterDialog());
    }

    private void refreshRoute() {
        List<Delivery> organized = repository.getOrganizedDeliveries();
        if (selectedIndex >= organized.size()) selectedIndex = Math.max(0, organized.size() - 1);
        deliveryAdapter.submit(organized, selectedIndex);

        int pendingCount = organized.size();
        int completedCount = 0;
        try { completedCount = db.completedDeliveryDao().count(); } catch (Exception ignored) {}

        summaryText.setText(pendingCount + " pendente(s) • " + completedCount + " concluída(s)");
        routeCountLabel.setText("Fila (" + pendingCount + " entregas)");

        sortOrderBtn.setText("Ordem: " + repository.getSortOrder().name());
        String currentFilter = repository.getNeighborhoodFilter();
        neighborhoodFilterBtn.setText("Bairro: " + (currentFilter.isEmpty() ? "Todos" : currentFilter));

        Delivery current = getSelectedDelivery();
        if (current == null) {
            currentDeliveryText.setText("Nenhuma entrega ativa na fila");
            setDeliveryActionsEnabled(false);
        } else {
            setDeliveryActionsEnabled(true);
            House house = current.houseId != null && !current.houseId.isEmpty() ? houseStore.findById(current.houseId) : null;
            String houseInfo = house != null ? " [Casa: " + house.displayName() + "]" : "";
            currentDeliveryText.setText(current.trackingCode + " • " + current.customerName + houseInfo + "\n" + current.address);
            linkHouseButton.setText(house != null ? "Casa: " + house.displayName() : "Vincular Casa");
        }
    }

    private Delivery getSelectedDelivery() {
        List<Delivery> list = repository.getOrganizedDeliveries();
        if (list.isEmpty()) return null;
        if (selectedIndex < 0 || selectedIndex >= list.size()) selectedIndex = 0;
        return list.get(selectedIndex);
    }

    private void setDeliveryActionsEnabled(boolean enabled) {
        packagePhotoButton.setEnabled(enabled);
        facadePhotoButton.setEnabled(enabled);
        navigationButton.setEnabled(enabled);
        linkHouseButton.setEnabled(enabled);
        generatePdfButton.setEnabled(enabled);
        completeCurrentBtn.setEnabled(enabled);
        findViewById(R.id.useInKeyboardButton).setEnabled(enabled);
    }

    private void showSortOrderDialog() {
        String[] options = {"Manual (Ordem de importação/arraste)", "Nome A-Z (Maiúsculas no final)", "Bairro (Cabuçu / Bom Jesus / etc.)"};
        new AlertDialog.Builder(this)
                .setTitle("Ordenar entregas")
                .setItems(options, (d, which) -> {
                    if (which == 0) repository.setSortOrder(SortOrder.MANUAL);
                    else if (which == 1) repository.setSortOrder(SortOrder.NAME_AZ);
                    else if (which == 2) repository.setSortOrder(SortOrder.NEIGHBORHOOD);
                    refreshRoute();
                })
                .show();
    }

    private void showNeighborhoodFilterDialog() {
        List<Delivery> all = store.getDeliveries();
        Set<String> neighborhoods = new HashSet<>();
        neighborhoods.add("TODOS OS BAIRROS");
        for (Delivery d : all) {
            String g = NeighborhoodHelper.getCanonicalGroup(d.neighborhood);
            if (!g.isEmpty()) neighborhoods.add(g);
        }

        List<String> items = new ArrayList<>(neighborhoods);
        new AlertDialog.Builder(this)
                .setTitle("Filtrar por Bairro")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String chosen = items.get(which);
                    repository.setNeighborhoodFilter(chosen.startsWith("TODOS") ? "TODOS" : chosen);
                    refreshRoute();
                })
                .show();
    }

    private void showDeliveryMenu(int position, Delivery delivery) {
        String[] options = {"Marcar como Concluída", "Editar detalhes", "Registrar Ocorrência", "Remover da rota"};
        new AlertDialog.Builder(this)
                .setTitle(delivery.trackingCode)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        repository.completeDelivery(delivery.trackingCode);
                        refreshAll();
                    } else if (which == 1) {
                        showEditDeliveryDialog(position, delivery);
                    } else if (which == 2) {
                        showOccurrenceDialog(position, delivery);
                    } else if (which == 3) {
                        store.removeAt(position);
                        refreshRoute();
                    }
                })
                .show();
    }

    private void showEditDeliveryDialog(int position, Delivery delivery) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Nome do destinatário");
        nameInput.setText(delivery.customerName);
        layout.addView(nameInput);

        EditText addrInput = new EditText(this);
        addrInput.setHint("Endereço");
        addrInput.setText(delivery.address);
        layout.addView(addrInput);

        new AlertDialog.Builder(this)
                .setTitle("Editar entrega " + delivery.trackingCode)
                .setView(layout)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    store.updateDetailsAt(position, nameInput.getText().toString(), addrInput.getText().toString());
                    refreshRoute();
                })
                .show();
    }

    private void showOccurrenceDialog(int position, Delivery delivery) {
        String[] types = {"Destinatário Ausente", "Endereço Não Localizado", "Recusado", "Outro"};
        new AlertDialog.Builder(this)
                .setTitle("Registrar Ocorrência")
                .setItems(types, (d, which) -> {
                    store.markOccurrenceAt(position, types[which], "Marcado pelo operador");
                    refreshRoute();
                })
                .show();
    }

    private void showLinkHouseDialog() {
        Delivery current = getSelectedDelivery();
        if (current == null) return;
        List<House> houses = houseStore.getHouses();
        List<String> options = new ArrayList<>();
        options.add("➕ Cadastrar nova casa permanente");
        for (House h : houses) {
            options.add(h.displayName() + " — " + h.address);
        }

        new AlertDialog.Builder(this)
                .setTitle("Vincular casa a " + current.trackingCode)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showHouseEditor(null, selectedIndex);
                    } else {
                        House chosen = houses.get(which - 1);
                        store.linkHouseAt(selectedIndex, chosen.id);
                        refreshRoute();
                    }
                })
                .show();
    }

    private void openNavigation() {
        Delivery current = getSelectedDelivery();
        if (current == null) return;
        House house = current.houseId != null ? houseStore.findById(current.houseId) : null;
        String query = house != null && !house.address.isEmpty() ? house.address : current.address;
        if (query.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
        try { startActivity(intent); } catch (Exception e) {
            Toast.makeText(this, "Nenhum aplicativo de mapa instalado", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateDeliveryPdf() {
        Delivery current = getSelectedDelivery();
        if (current == null) return;
        House house = current.houseId != null ? houseStore.findById(current.houseId) : null;
        try {
            Uri pdf = DeliveryReportGenerator.generate(this, current, house);
            store.updateReportAt(selectedIndex, pdf.toString());
            refreshRoute();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdf, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível gerar relatório", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // ABA 2: FOTOS
    // ==========================================
    private void setupPhotosTab() {
        photoProgressText = findViewById(R.id.photoProgressText);
        pendingPhotosTitle = findViewById(R.id.pendingPhotosTitle);
        photoFilterBtn = findViewById(R.id.photoFilterBtn);
        pendingPhotosGrid = findViewById(R.id.pendingPhotosGrid);

        pendingPhotosGrid.setLayoutManager(new GridLayoutManager(this, 3));
        pendingPhotosAdapter = new PendingPhotosAdapter(this, (position, photo) -> {
            executor.execute(() -> {
                List<PhotoEntity> currentPhotos = db.photoDao().getUnassignedPhotos();
                runOnUiThread(() -> {
                    PhotoAssignerDialog assigner = new PhotoAssignerDialog(this, currentPhotos, position, this::loadPendingPhotos);
                    assigner.show();
                });
            });
        });
        pendingPhotosGrid.setAdapter(pendingPhotosAdapter);

        findViewById(R.id.takePhotosActionBtn).setOnClickListener(v -> takePhoto(true));
        findViewById(R.id.pickGalleryActionBtn).setOnClickListener(v -> pickPhotosFromGallery());
        photoFilterBtn.setOnClickListener(v -> showPhotoFilterDialog());
    }

    private void pickPhotosFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_PICK_GALLERY);
    }

    private void showPhotoFilterDialog() {
        String[] options = {"Todos", "Sem leitura", "Código não encontrado", "Ambíguos"};
        new AlertDialog.Builder(this)
                .setTitle("Filtrar fotos pendentes")
                .setItems(options, (d, which) -> {
                    if (which == 0) currentPhotoFilter = "ALL";
                    else if (which == 1) currentPhotoFilter = PhotoEntity.STATUS_PENDING_NO_READING;
                    else if (which == 2) currentPhotoFilter = PhotoEntity.STATUS_PENDING_NOT_FOUND;
                    else if (which == 3) currentPhotoFilter = PhotoEntity.STATUS_PENDING_AMBIGUOUS;
                    photoFilterBtn.setText("Filtro: " + options[which]);
                    pendingPhotosAdapter.setFilter(currentPhotoFilter);
                })
                .show();
    }

    private void loadPendingPhotos() {
        executor.execute(() -> {
            List<PhotoEntity> photos = db.photoDao().getUnassignedPhotos();
            runOnUiThread(() -> {
                pendingPhotosTitle.setText(photos.size() + " foto(s) sem destinatário");
                pendingPhotosAdapter.submitList(photos);
            });
        });
    }

    // ==========================================
    // ABA 3: ARQUIVO
    // ==========================================
    private void setupArchiveTab() {
        archiveSubtabHouses = findViewById(R.id.archiveSubtabHouses);
        archiveSubtabCompleted = findViewById(R.id.archiveSubtabCompleted);
        archiveHousesSection = findViewById(R.id.archiveHousesSection);
        archiveCompletedSection = findViewById(R.id.archiveCompletedSection);

        archiveHouseSearchInput = findViewById(R.id.archiveHouseSearchInput);
        archiveCompletedSearchInput = findViewById(R.id.archiveCompletedSearchInput);
        archiveCompletedCount = findViewById(R.id.archiveCompletedCount);

        archiveHousesRecyclerView = findViewById(R.id.archiveHousesRecyclerView);
        archiveHousesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        houseAdapter = new HouseAdapter(this, house -> showHouseEditor(house, -1));
        archiveHousesRecyclerView.setAdapter(houseAdapter);

        archiveCompletedRecyclerView = findViewById(R.id.archiveCompletedRecyclerView);
        archiveCompletedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        completedDeliveriesAdapter = new CompletedDeliveriesAdapter(this, item -> {
            repository.undoCompletion(item.trackingCode);
            Toast.makeText(this, "Entrega " + item.trackingCode + " devolvida à fila", Toast.LENGTH_SHORT).show();
            loadArchiveData();
            refreshRoute();
        });
        archiveCompletedRecyclerView.setAdapter(completedDeliveriesAdapter);

        archiveSubtabHouses.setOnClickListener(v -> switchArchiveSubtab(true));
        archiveSubtabCompleted.setOnClickListener(v -> switchArchiveSubtab(false));
        findViewById(R.id.exportHousesBtn).setOnClickListener(v -> chooseExportHousesFile());

        archiveHouseSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterHouses(s.toString().trim());
            }
        });

        archiveCompletedSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCompleted(s.toString().trim());
            }
        });
    }

    private void switchArchiveSubtab(boolean showHouses) {
        archiveHousesSection.setVisibility(showHouses ? View.VISIBLE : View.GONE);
        archiveCompletedSection.setVisibility(showHouses ? View.GONE : View.VISIBLE);
        archiveSubtabHouses.setBackgroundResource(showHouses ? R.drawable.button_primary : R.drawable.button_secondary);
        archiveSubtabHouses.setTextColor(showHouses ? Color.WHITE : getColor(R.color.orange_dark));
        archiveSubtabCompleted.setBackgroundResource(!showHouses ? R.drawable.button_primary : R.drawable.button_secondary);
        archiveSubtabCompleted.setTextColor(!showHouses ? Color.WHITE : getColor(R.color.orange_dark));
        loadArchiveData();
    }

    private void loadArchiveData() {
        filterHouses(archiveHouseSearchInput.getText().toString().trim());
        filterCompleted(archiveCompletedSearchInput.getText().toString().trim());
    }

    private void filterHouses(String query) {
        List<House> all = houseStore.getHouses();
        if (query.isEmpty()) {
            houseAdapter.submitList(all);
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<House> filtered = new ArrayList<>();
        for (House h : all) {
            if (h.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || h.address.toLowerCase(Locale.ROOT).contains(q)
                    || h.residents.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(h);
            }
        }
        houseAdapter.submitList(filtered);
    }

    private void filterCompleted(String query) {
        executor.execute(() -> {
            List<CompletedDeliveryEntity> list = query.isEmpty()
                    ? db.completedDeliveryDao().getAll()
                    : db.completedDeliveryDao().search(query);
            runOnUiThread(() -> {
                archiveCompletedCount.setText(list.size() + " entrega(s) concluída(s)");
                completedDeliveriesAdapter.submitList(list);
            });
        });
    }

    private void chooseExportHousesFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, HouseExporter.generateFileName());
        startActivityForResult(intent, REQUEST_EXPORT_HOUSES);
    }

    private void exportHousesToUri(Uri uri) {
        executor.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    HouseExporter.exportToCsv(houseStore.getHouses(), out);
                    runOnUiThread(() -> Toast.makeText(this, "Casas salvas exportadas com sucesso!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showHouseEditor(House existing, int linkIndex) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText labelInput = new EditText(this);
        labelInput.setHint("Apelido da casa / Ponto de referência");
        labelInput.setText(existing != null ? existing.label : "");
        layout.addView(labelInput);

        EditText residentsInput = new EditText(this);
        residentsInput.setHint("Moradores (separados por •)");
        residentsInput.setText(existing != null ? existing.residents : "");
        layout.addView(residentsInput);

        EditText addressInput = new EditText(this);
        addressInput.setHint("Endereço completo");
        addressInput.setText(existing != null ? existing.address : "");
        layout.addView(addressInput);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nova Casa" : "Editar Casa")
                .setView(layout)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    String id = existing != null ? existing.id : "";
                    House h = new House(id, labelInput.getText().toString(), residentsInput.getText().toString(),
                            addressInput.getText().toString(), existing != null ? existing.mapUri : "",
                            existing != null ? existing.facadePhotoUri : "", existing != null ? existing.notes : "");
                    houseStore.save(h);
                    if (linkIndex >= 0) store.linkHouseAt(linkIndex, h.id);
                    loadArchiveData();
                    refreshRoute();
                })
                .show();
    }

    // ==========================================
    // ABA 4: AJUSTES
    // ==========================================
    private void setupSettingsTab() {
        settingsOverlayChip = findViewById(R.id.settingsOverlayChip);
        settingsAccessibilityChip = findViewById(R.id.settingsAccessibilityChip);
        settingsNameModeGroup = findViewById(R.id.settingsNameModeGroup);
        radioUseSequence = findViewById(R.id.radioUseSequence);
        radioUseAlternative = findViewById(R.id.radioUseAlternative);
        settingsAlternativeNameInput = findViewById(R.id.settingsAlternativeNameInput);
        settingsKeyboardHeightLabel = findViewById(R.id.settingsKeyboardHeightLabel);
        settingsKeyboardHeightSeekBar = findViewById(R.id.settingsKeyboardHeightSeekBar);
        diagnosticDetailsText = findViewById(R.id.diagnosticDetailsText);

        findViewById(R.id.settingsOpenPanelBtn).setOnClickListener(v -> openFloatingPanel());
        findViewById(R.id.settingsAutomationConfigBtn).setOnClickListener(v -> startActivity(new Intent(this, AutomationSettingsActivity.class)));

        settingsOverlayChip.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "Permissão de sobreposição já concedida", Toast.LENGTH_SHORT).show();
            }
        });

        settingsAccessibilityChip.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        SharedPreferences namePrefs = getSharedPreferences("name_settings", MODE_PRIVATE);
        String nameMode = namePrefs.getString("name_mode", "sequence");
        radioUseSequence.setChecked("sequence".equals(nameMode));
        radioUseAlternative.setChecked("alternative".equals(nameMode));
        settingsAlternativeNameInput.setText(store.getReceiverName());

        findViewById(R.id.settingsSaveNameBtn).setOnClickListener(v -> {
            String mode = radioUseSequence.isChecked() ? "sequence" : "alternative";
            String altName = settingsAlternativeNameInput.getText().toString().trim();
            namePrefs.edit().putString("name_mode", mode).apply();
            store.setReceiverName(altName);
            Toast.makeText(this, "Preferências de nome salvas", Toast.LENGTH_SHORT).show();
        });

        // Altura do teclado (220 a 420 dp)
        SharedPreferences kbPrefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE);
        int currentHeight = kbPrefs.getInt("height_dp", 290);
        settingsKeyboardHeightSeekBar.setProgress(currentHeight - 220);
        settingsKeyboardHeightLabel.setText("Altura atual: " + currentHeight + " dp");

        settingsKeyboardHeightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int height = 220 + progress;
                settingsKeyboardHeightLabel.setText("Altura atual: " + height + " dp");
                kbPrefs.edit().putInt("height_dp", height).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.diagnosticExportBtn).setOnClickListener(v -> copyDiagnosticToClipboard());
        findViewById(R.id.clearRouteOnlyBtn).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Limpar Rota")
                    .setMessage("Deseja limpar apenas a rota ativa? As casas salvas e o histórico não serão apagados.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Limpar Rota", (d, which) -> {
                        repository.clearRouteOnly();
                        refreshAll();
                        Toast.makeText(this, "Rota ativa limpa", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
    }

    private void loadSettingsData() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean accessOk = AutomationAccessibilityService.isConnected();

        settingsOverlayChip.setText(overlayOk ? "✓ Sobreposição OK" : "⚠ Ativar Sobreposição");
        settingsOverlayChip.setBackgroundResource(overlayOk ? R.drawable.button_used : R.drawable.chip_neutral);

        settingsAccessibilityChip.setText(accessOk ? "✓ Acessibilidade OK" : "⚠ Ativar Acessibilidade");
        settingsAccessibilityChip.setBackgroundResource(accessOk ? R.drawable.button_used : R.drawable.chip_neutral);

        // Carrega estatísticas do diagnóstico
        executor.execute(() -> {
            int houses = houseStore.getHouses().size();
            int packages = store.getDeliveries().size();
            int recipients = QueueOrganizer.groupDeliveries(store.getDeliveries()).size();
            int completed = 0;
            int unassignedPhotos = 0;
            try {
                completed = db.completedDeliveryDao().count();
                unassignedPhotos = db.photoDao().countUnassigned();
            } catch (Exception ignored) {}

            String diagnostic = "• Versão: 0.8.0 (build 9)\n" +
                    "• Casas salvas permanentes: " + houses + "\n" +
                    "• Pacotes ativos na rota: " + packages + "\n" +
                    "• Destinatários ativos: " + recipients + "\n" +
                    "• Entregas concluídas (Room): " + completed + "\n" +
                    "• Fotos sem destinatário: " + unassignedPhotos + "\n" +
                    "• Ordem da fila: " + repository.getSortOrder() + "\n" +
                    "• Filtro de bairro ativo: " + repository.getNeighborhoodFilter() + "\n" +
                    "• Banco de dados Room: Conectado e ativo\n" +
                    "• Armazenamento: Seguro / SQLite Room + SharedPreferences";

            runOnUiThread(() -> diagnosticDetailsText.setText(diagnostic));
        });
    }

    private void copyDiagnosticToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Diagnóstico Baixas de Pacote", diagnosticDetailsText.getText().toString()));
            Toast.makeText(this, "Diagnóstico copiado para a área de transferência", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFloatingPanel() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative a sobreposição primeiro", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startService(new Intent(this, FloatingAssistantService.class));
        Toast.makeText(this, "Painel flutuante aberto", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // IMPORTAÇÃO E CÂMERA
    // ==========================================
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

    private void takePhoto(boolean forPackage) {
        Delivery current = getSelectedDelivery();
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(CameraActivity.EXTRA_PREFIX, forPackage ? "PACOTE_" : "CASA_");
        intent.putExtra(CameraActivity.EXTRA_TITLE, forPackage ? "Foto do pacote" : "Foto da fachada da casa");
        startActivityForResult(intent, forPackage ? REQUEST_CAMERA_PACKAGE : REQUEST_CAMERA_FACADE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_IMPORT) {
            Uri uri = data.getData();
            if (uri != null) importSpreadsheet(uri);
        } else if (requestCode == REQUEST_CAMERA_PACKAGE) {
            String photoUri = data.getStringExtra(CameraActivity.EXTRA_PHOTO_URI);
            if (photoUri != null && !photoUri.isEmpty()) {
                store.updatePhotoAt(selectedIndex, true, photoUri);
                refreshRoute();
            }
        } else if (requestCode == REQUEST_CAMERA_FACADE) {
            String photoUri = data.getStringExtra(CameraActivity.EXTRA_PHOTO_URI);
            if (photoUri != null && !photoUri.isEmpty()) {
                Delivery current = getSelectedDelivery();
                if (current != null && current.houseId != null && !current.houseId.isEmpty()) {
                    houseStore.updateFacade(current.houseId, photoUri);
                }
                store.updatePhotoAt(selectedIndex, false, photoUri);
                refreshRoute();
            }
        } else if (requestCode == REQUEST_PICK_GALLERY) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }

            if (!uris.isEmpty()) {
                photoProgressText.setVisibility(View.VISIBLE);
                photoProgressText.setText("Processando " + uris.size() + " imagem(ns)...");
                photoProcessor.processGalleryBatch(uris, new PhotoProcessor.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total) {
                        photoProgressText.setText("Processando foto " + current + " de " + total + "...");
                    }

                    @Override
                    public void onComplete(int total, int assigned, int pending, int duplicates) {
                        photoProgressText.setVisibility(View.GONE);
                        String msg = total + " fotos processadas\n" +
                                assigned + " atribuídas automaticamente\n" +
                                pending + " sem destinatário\n" +
                                (duplicates > 0 ? duplicates + " duplicadas ignoradas" : "");
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Processamento Concluído")
                                .setMessage(msg)
                                .setPositiveButton("OK", null)
                                .show();
                        loadPendingPhotos();
                        refreshRoute();
                    }

                    @Override
                    public void onError(String message) {
                        photoProgressText.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else if (requestCode == REQUEST_EXPORT_HOUSES) {
            Uri uri = data.getData();
            if (uri != null) exportHousesToUri(uri);
        }
    }

    private void importSpreadsheet(Uri uri) {
        summaryText.setText("Importando planilha...");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Não foi possível abrir o arquivo.");
                List<Delivery> imported = SpreadsheetImporter.importFile(input, uri.getLastPathSegment());
                runOnUiThread(() -> {
                    repository.importNewRoute(imported);
                    selectedIndex = 0;
                    refreshAll();
                    Toast.makeText(this, "Rota importada (" + imported.size() + " entregas encontradas)", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    refreshRoute();
                    new AlertDialog.Builder(this)
                            .setTitle("Erro na importação")
                            .setMessage(error.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void refreshAll() {
        refreshRoute();
        loadPendingPhotos();
        loadArchiveData();
        loadSettingsData();
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
