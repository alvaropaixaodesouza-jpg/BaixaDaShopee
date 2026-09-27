package com.alvaro.baixashopee.data.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.alvaro.baixashopee.Delivery;
import com.alvaro.baixashopee.DeliveryStore;
import com.alvaro.baixashopee.House;
import com.alvaro.baixashopee.HouseStore;
import com.alvaro.baixashopee.data.db.entities.HouseEntity;
import com.alvaro.baixashopee.data.db.entities.PackageEntity;
import com.alvaro.baixashopee.data.db.entities.RouteEntity;
import com.alvaro.baixashopee.data.model.AddressNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DataMigration {
    private static final String TAG = "DataMigration";
    private static final String PREFS_MIGRATION = "app_migrations";
    private static final String KEY_MIGRATION_V1_DONE = "migration_room_v1_done";

    private DataMigration() {}

    public static synchronized void migrateIfNeeded(Context context) {
        SharedPreferences migPrefs = context.getSharedPreferences(PREFS_MIGRATION, Context.MODE_PRIVATE);
        if (migPrefs.getBoolean(KEY_MIGRATION_V1_DONE, false)) {
            return;
        }

        try {
            AppDatabase db = AppDatabase.getInstance(context);

            // 1. Migração de Casas Salvas
            HouseStore houseStore = new HouseStore(context);
            List<House> oldHouses = houseStore.getHouses();

            // Backup preventivo das casas
            if (!oldHouses.isEmpty()) {
                SharedPreferences housePrefs = context.getSharedPreferences("house_memory", Context.MODE_PRIVATE);
                String rawBackup = housePrefs.getString("houses", "[]");
                context.getSharedPreferences("house_memory_backup", Context.MODE_PRIVATE)
                        .edit()
                        .putString("houses_backup_before_v08", rawBackup)
                        .putLong("backup_timestamp", System.currentTimeMillis())
                        .commit();

                List<HouseEntity> houseEntities = new ArrayList<>();
                for (House h : oldHouses) {
                    if (h.id == null || h.id.trim().isEmpty()) continue;
                    String normAddr = AddressNormalizer.normalize(h.address);
                    HouseEntity entity = new HouseEntity(
                            h.id,
                            h.label,
                            h.residents,
                            h.address,
                            h.mapUri,
                            h.facadePhotoUri,
                            h.notes,
                            h.latitude,
                            h.longitude,
                            h.locationAccuracy,
                            h.lastVisitedAt,
                            normAddr,
                            System.currentTimeMillis()
                    );
                    houseEntities.add(entity);
                }
                db.houseDao().insertAll(houseEntities);
            }

            // 2. Migração da Rota Atual (se houver)
            DeliveryStore deliveryStore = new DeliveryStore(context);
            List<Delivery> oldDeliveries = deliveryStore.getDeliveries();
            if (!oldDeliveries.isEmpty()) {
                String routeId = UUID.randomUUID().toString();
                RouteEntity route = new RouteEntity(
                        routeId,
                        "Rota Importada Anterior",
                        System.currentTimeMillis(),
                        true,
                        oldDeliveries.size()
                );
                db.routeDao().deactivateAll();
                db.routeDao().insert(route);

                List<PackageEntity> packageEntities = new ArrayList<>();
                for (int i = 0; i < oldDeliveries.size(); i++) {
                    Delivery d = oldDeliveries.get(i);
                    PackageEntity p = new PackageEntity(
                            d.trackingCode,
                            "",
                            routeId,
                            d.customerName,
                            d.address,
                            d.atId,
                            d.stop,
                            d.neighborhood,
                            d.city,
                            d.postalCode,
                            d.destinationLatitude,
                            d.destinationLongitude,
                            d.status,
                            d.occurrenceType,
                            d.occurrenceNote,
                            d.packagePhotoUri,
                            d.facadePhotoUri,
                            d.houseId,
                            d.latitude,
                            d.longitude,
                            d.locationAccuracy,
                            d.photographedAt,
                            d.reportUri,
                            i
                    );
                    packageEntities.add(p);
                }
                db.packageDao().insertAll(packageEntities);
            }

            // Validação de segurança: certificar que os dados foram persistidos
            int migratedCount = db.houseDao().count();
            if (migratedCount >= oldHouses.size()) {
                migPrefs.edit().putBoolean(KEY_MIGRATION_V1_DONE, true).commit();
                Log.i(TAG, "Migração para Room concluída com sucesso. Casas: " + migratedCount);
            } else {
                Log.w(TAG, "Aviso: contagem de casas divergente após migração. Não marcando como finalizado.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Falha na migração segura de dados para Room", e);
            // NÃO apagar dados originais do SharedPreferences!
        }
    }
}
