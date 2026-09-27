package com.alvaro.baixashopee.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.alvaro.baixashopee.Delivery;
import com.alvaro.baixashopee.DeliveryStore;
import com.alvaro.baixashopee.House;
import com.alvaro.baixashopee.HouseStore;
import com.alvaro.baixashopee.data.db.AppDatabase;
import com.alvaro.baixashopee.data.db.DataMigration;
import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;
import com.alvaro.baixashopee.data.db.entities.HouseEntity;
import com.alvaro.baixashopee.data.model.AddressNormalizer;
import com.alvaro.baixashopee.data.model.DeliveryGroup;
import com.alvaro.baixashopee.data.model.QueueOrganizer;
import com.alvaro.baixashopee.data.model.RecipientNameHelper;
import com.alvaro.baixashopee.data.model.SortOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeliveryRepository {
    private static volatile DeliveryRepository INSTANCE;
    private static final String PREFS_QUEUE_SETTINGS = "queue_settings";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_NEIGHBORHOOD_FILTER = "neighborhood_filter";

    private final Context appContext;
    private final AppDatabase db;
    private final DeliveryStore deliveryStore;
    private final HouseStore houseStore;
    private final SharedPreferences settingsPrefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static DeliveryRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DeliveryRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeliveryRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private DeliveryRepository(Context context) {
        this.appContext = context;
        this.db = AppDatabase.getInstance(context);
        this.deliveryStore = new DeliveryStore(context);
        this.houseStore = new HouseStore(context);
        this.settingsPrefs = context.getSharedPreferences(PREFS_QUEUE_SETTINGS, Context.MODE_PRIVATE);

        // Executa migração na inicialização
        executor.execute(() -> DataMigration.migrateIfNeeded(context));
    }

    public SortOrder getSortOrder() {
        String saved = settingsPrefs.getString(KEY_SORT_ORDER, SortOrder.MANUAL.name());
        return SortOrder.fromString(saved);
    }

    public void setSortOrder(SortOrder sortOrder) {
        settingsPrefs.edit().putString(KEY_SORT_ORDER, sortOrder.name()).apply();
    }

    public String getNeighborhoodFilter() {
        return settingsPrefs.getString(KEY_NEIGHBORHOOD_FILTER, "TODOS");
    }

    public void setNeighborhoodFilter(String filter) {
        settingsPrefs.edit().putString(KEY_NEIGHBORHOOD_FILTER, filter == null ? "TODOS" : filter).apply();
    }

    /**
     * Retorna a lista organizada da fila atual (agrupada, filtrada e ordenada).
     */
    public synchronized List<Delivery> getOrganizedDeliveries() {
        List<Delivery> raw = deliveryStore.getDeliveries();
        return QueueOrganizer.organize(raw, getSortOrder(), getNeighborhoodFilter());
    }

    /**
     * Retorna os grupos de entrega por destinatário.
     */
    public synchronized List<DeliveryGroup> getDeliveryGroups() {
        List<Delivery> raw = deliveryStore.getDeliveries();
        List<DeliveryGroup> groups = QueueOrganizer.groupDeliveries(raw);
        groups = QueueOrganizer.filterByNeighborhood(groups, getNeighborhoodFilter());
        return QueueOrganizer.sortGroups(groups, getSortOrder());
    }

    /**
     * Substitui a rota atual com a lista importada, aplicando:
     * 1. Ignorar ou não reativar pacotes já concluídos.
     * 2. Vincular ou criar casas salvas permanentes para cada endereço específico.
     */
    public synchronized void importNewRoute(List<Delivery> imported) {
        Set<String> completedCodes = new HashSet<>();
        try {
            List<CompletedDeliveryEntity> completedList = db.completedDeliveryDao().getAll();
            for (CompletedDeliveryEntity c : completedList) {
                completedCodes.add(c.trackingCode);
            }
        } catch (Exception ignored) {}

        List<Delivery> activeOnly = new ArrayList<>();
        for (Delivery d : imported) {
            if (d == null || d.trackingCode == null || d.trackingCode.isEmpty()) continue;
            // Se já foi concluído anteriormente, não reativa!
            if (completedCodes.contains(d.trackingCode)) continue;

            // Busca ou cadastra casa permanente
            if (AddressNormalizer.isSpecificAddress(d.address)) {
                House house = houseStore.findBySpecificAddress(d.address);
                if (house != null) {
                    d.houseId = house.id;
                    d.facadePhotoUri = house.facadePhotoUri;
                    houseStore.addResident(house.id, d.customerName);
                } else {
                    // Cria nova casa automaticamente
                    House newHouse = House.create("", d.customerName, d.address, "", "");
                    houseStore.save(newHouse);
                    d.houseId = newHouse.id;
                    executor.execute(() -> {
                        HouseEntity entity = new HouseEntity(
                                newHouse.id,
                                newHouse.label,
                                newHouse.residents,
                                newHouse.address,
                                newHouse.mapUri,
                                newHouse.facadePhotoUri,
                                newHouse.notes,
                                newHouse.latitude,
                                newHouse.longitude,
                                newHouse.locationAccuracy,
                                newHouse.lastVisitedAt,
                                AddressNormalizer.normalize(newHouse.address),
                                System.currentTimeMillis()
                        );
                        db.houseDao().insert(entity);
                    });
                }
            }

            activeOnly.add(d);
        }

        // Organiza e grava na fila
        List<Delivery> organized = QueueOrganizer.organize(activeOnly, getSortOrder(), getNeighborhoodFilter());
        deliveryStore.replaceDeliveries(organized);
    }

    /**
     * Conclui a entrega atual ou uma entrega específica pelo trackingCode.
     * Remove imediatamente da fila e grava em Concluídas.
     */
    public synchronized Delivery completeDelivery(String trackingCode) {
        if (trackingCode == null || trackingCode.isEmpty()) return null;
        List<Delivery> currentList = deliveryStore.getDeliveries();
        int foundIndex = -1;
        Delivery target = null;

        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).trackingCode.equalsIgnoreCase(trackingCode)) {
                foundIndex = i;
                target = currentList.get(i);
                break;
            }
        }

        if (target != null && foundIndex >= 0) {
            deliveryStore.removeAt(foundIndex);

            final Delivery toSave = target;
            final int origIdx = foundIndex;
            executor.execute(() -> {
                CompletedDeliveryEntity entity = new CompletedDeliveryEntity(
                        toSave.trackingCode,
                        toSave.customerName,
                        toSave.address,
                        toSave.atId,
                        toSave.stop,
                        toSave.neighborhood,
                        toSave.city,
                        toSave.postalCode,
                        toSave.houseId,
                        toSave.packagePhotoUri,
                        toSave.facadePhotoUri,
                        toSave.reportUri,
                        System.currentTimeMillis(),
                        origIdx,
                        ""
                );
                db.completedDeliveryDao().insert(entity);
            });
            return target;
        }
        return null;
    }

    /**
     * Conclui a entrega que está atualmente no topo/cursor da fila.
     */
    public synchronized Delivery completeCurrent() {
        Delivery current = deliveryStore.getCurrent();
        if (current != null) {
            return completeDelivery(current.trackingCode);
        }
        return null;
    }

    /**
     * Desfaz uma conclusão e devolve a entrega para a fila ativa na posição correta.
     * Se o destinatário ainda tiver outros pacotes ativos, reinserir no bloco dele.
     */
    public synchronized boolean undoCompletion(String trackingCode) {
        if (trackingCode == null || trackingCode.isEmpty()) return false;
        try {
            CompletedDeliveryEntity entity = db.completedDeliveryDao().getByTrackingCode(trackingCode);
            if (entity == null) return false;

            // Remove de concluídas
            db.completedDeliveryDao().deleteByTrackingCode(trackingCode);

            Delivery restored = new Delivery(
                    entity.trackingCode,
                    entity.customerName,
                    entity.address,
                    entity.atId,
                    entity.stop,
                    entity.neighborhood,
                    entity.city,
                    entity.postalCode,
                    0, 0,
                    Delivery.STATUS_PENDING,
                    "", "",
                    entity.packagePhotoUri,
                    entity.facadePhotoUri,
                    entity.houseId,
                    0, 0, 0, 0,
                    entity.reportUri
            );

            List<Delivery> current = deliveryStore.getDeliveries();
            int insertIndex = -1;

            // Procura bloco do destinatário
            for (int i = 0; i < current.size(); i++) {
                if (RecipientNameHelper.areSameRecipient(current.get(i).customerName, restored.customerName)) {
                    insertIndex = i + 1; // Insere junto ao bloco
                }
            }

            if (insertIndex < 0) {
                // Se não há pacotes desse destinatário, devolve à posição original razoável
                insertIndex = Math.min(entity.originalIndex, current.size());
            }

            current.add(insertIndex, restored);
            deliveryStore.writeDeliveries(current);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Desfaz a última conclusão realizada.
     */
    public synchronized boolean undoLastCompletion() {
        try {
            CompletedDeliveryEntity last = db.completedDeliveryDao().getLastCompleted();
            if (last != null) {
                return undoCompletion(last.trackingCode);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Verifica se existe alguma conclusão que possa ser desfeita.
     */
    public boolean hasUndoableCompletion() {
        try {
            return db.completedDeliveryDao().count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Limpa apenas a rota ativa, preservando casas salvas e histórico.
     */
    public synchronized void clearRouteOnly() {
        deliveryStore.clearDeliveries();
        executor.execute(() -> {
            try {
                db.packageDao().deleteAll();
                db.routeDao().deactivateAll();
                db.photoDao().deletePackagePhotos();
            } catch (Exception ignored) {}
        });
    }

    /**
     * Reordena manualmente blocos de destinatários (drag-and-drop).
     */
    public synchronized void moveGroup(int fromIndex, int toIndex) {
        List<DeliveryGroup> groups = getDeliveryGroups();
        if (fromIndex < 0 || fromIndex >= groups.size() || toIndex < 0 || toIndex >= groups.size()) {
            return;
        }
        DeliveryGroup item = groups.remove(fromIndex);
        groups.add(toIndex, item);
        List<Delivery> flattened = QueueOrganizer.flatten(groups);
        deliveryStore.writeDeliveries(flattened);
        setSortOrder(SortOrder.MANUAL);
    }
}
