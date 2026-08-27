package com.alvaro.baixashopee;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeliveryStore {
    private static final String PREFS = "delivery_queue";
    private static final String KEY_DELIVERIES = "deliveries";
    private static final String KEY_INDEX = "current_index";
    private static final String KEY_RECEIVER_NAME = "receiver_name";
    private static final String KEY_TRACKING_USED = "tracking_used";
    private static final String KEY_NUMERIC_USED = "numeric_used";
    private static final String KEY_NAME_USED = "name_used";

    private final SharedPreferences preferences;
    private final HouseStore houseStore;

    public DeliveryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        houseStore = new HouseStore(context);
    }

    public synchronized List<Delivery> getDeliveries() {
        List<Delivery> deliveries = new ArrayList<>();
        String raw = preferences.getString(KEY_DELIVERIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Delivery delivery = Delivery.fromJson(array.getJSONObject(i));
                if (!delivery.trackingCode.isEmpty()) deliveries.add(delivery);
            }
        } catch (JSONException ignored) {
            // Se os dados locais forem corrompidos, a tela continua utilizável.
        }
        return deliveries;
    }

    public synchronized void replaceDeliveries(List<Delivery> imported) {
        Map<String, Delivery> oldById = new LinkedHashMap<>();
        for (Delivery old : getDeliveries()) oldById.put(old.id(), old);

        LinkedHashMap<String, Delivery> unique = new LinkedHashMap<>();
        for (Delivery item : imported) {
            if (item.trackingCode.isEmpty()) continue;
            Delivery old = oldById.get(item.id());
            if (old != null) {
                item.packagePhotoUri = old.packagePhotoUri;
                item.facadePhotoUri = old.facadePhotoUri;
                item.houseId = old.houseId;
                item.latitude = old.latitude;
                item.longitude = old.longitude;
                item.locationAccuracy = old.locationAccuracy;
                item.photographedAt = old.photographedAt;
                item.reportUri = old.reportUri;
                item.status = old.status;
                item.occurrenceType = old.occurrenceType;
                item.occurrenceNote = old.occurrenceNote;
            } else {
                House matched = houseStore.findBySpecificAddress(item.address);
                if (matched != null) {
                    item.houseId = matched.id;
                    item.facadePhotoUri = matched.facadePhotoUri;
                    houseStore.addResident(matched.id, item.customerName);
                }
            }
            unique.putIfAbsent(item.id(), item);
        }

        writeDeliveries(new ArrayList<>(unique.values()));
        preferences.edit()
                .putInt(KEY_INDEX, 0)
                .putBoolean(KEY_TRACKING_USED, false)
                .putBoolean(KEY_NUMERIC_USED, false)
                .putBoolean(KEY_NAME_USED, false)
                .commit();
    }

    public synchronized void writeDeliveries(List<Delivery> deliveries) {
        JSONArray array = new JSONArray();
        for (Delivery delivery : deliveries) {
            try {
                array.put(delivery.toJson());
            } catch (JSONException ignored) {
                // Um item inválido não deve apagar os demais.
            }
        }
        preferences.edit().putString(KEY_DELIVERIES, array.toString()).commit();
    }

    public synchronized int getCurrentIndex() {
        int size = getDeliveries().size();
        int value = preferences.getInt(KEY_INDEX, 0);
        return Math.max(0, Math.min(value, size));
    }

    public synchronized Delivery getCurrent() {
        List<Delivery> deliveries = getDeliveries();
        int index = getCurrentIndex();
        return index < deliveries.size() ? deliveries.get(index) : null;
    }

    public synchronized int findIndexInside(String rawValue) {
        String haystack = TrackingCode.stableId(rawValue);
        if (haystack.isEmpty()) return -1;
        List<Delivery> deliveries = getDeliveries();
        for (int i = 0; i < deliveries.size(); i++) {
            String code = deliveries.get(i).id();
            if (!code.isEmpty() && haystack.contains(code)) return i;
        }
        return -1;
    }

    public synchronized void setCurrentIndex(int index) {
        int size = getDeliveries().size();
        preferences.edit()
                .putInt(KEY_INDEX, Math.max(0, Math.min(index, size)))
                .putBoolean(KEY_TRACKING_USED, false)
                .putBoolean(KEY_NUMERIC_USED, false)
                .putBoolean(KEY_NAME_USED, false)
                .commit();
    }

    public synchronized void advance() {
        setCurrentIndex(getCurrentIndex() + 1);
    }

    public synchronized void rewind() {
        int index = getCurrentIndex();
        setCurrentIndex(Math.max(0, index - 1));
    }

    public String getReceiverName() {
        return preferences.getString(KEY_RECEIVER_NAME, "").trim();
    }

    public void setReceiverName(String name) {
        preferences.edit().putString(KEY_RECEIVER_NAME, name == null ? "" : name.trim()).commit();
    }

    public boolean isTrackingUsed() {
        return preferences.getBoolean(KEY_TRACKING_USED, false);
    }

    public boolean isNumericUsed() {
        return preferences.getBoolean(KEY_NUMERIC_USED, false);
    }

    public boolean isNameUsed() {
        return preferences.getBoolean(KEY_NAME_USED, false);
    }

    public void markTrackingUsed() {
        preferences.edit().putBoolean(KEY_TRACKING_USED, true).commit();
    }

    public void markNumericUsed() {
        preferences.edit().putBoolean(KEY_NUMERIC_USED, true).commit();
    }

    public void markNameUsed() {
        preferences.edit().putBoolean(KEY_NAME_USED, true).commit();
    }

    public boolean allFieldsUsed() {
        return isTrackingUsed() && isNumericUsed() && isNameUsed();
    }

    public synchronized void updateCurrentPhoto(boolean packagePhoto, String uri) {
        updatePhotoAt(getCurrentIndex(), packagePhoto, uri);
    }

    public synchronized void updatePhotoAt(int index, boolean packagePhoto, String uri) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        if (packagePhoto) {
            deliveries.get(index).packagePhotoUri = uri;
        } else {
            deliveries.get(index).facadePhotoUri = uri;
        }
        writeDeliveries(deliveries);
    }

    public synchronized void linkHouseAt(int index, String houseId) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery delivery = deliveries.get(index);
        delivery.houseId = houseId == null ? "" : houseId.trim();
        House house = houseStore.findById(delivery.houseId);
        delivery.facadePhotoUri = house == null ? "" : house.facadePhotoUri;
        writeDeliveries(deliveries);
    }

    public synchronized void syncHouseFacade(String houseId, String uri) {
        List<Delivery> deliveries = getDeliveries();
        for (Delivery delivery : deliveries) {
            if (delivery.houseId.equals(houseId)) delivery.facadePhotoUri = uri == null ? "" : uri;
        }
        writeDeliveries(deliveries);
    }

    public synchronized void unlinkHouseEverywhere(String houseId) {
        List<Delivery> deliveries = getDeliveries();
        for (Delivery delivery : deliveries) {
            if (delivery.houseId.equals(houseId)) {
                delivery.houseId = "";
                delivery.facadePhotoUri = "";
            }
        }
        writeDeliveries(deliveries);
    }

    public synchronized void updateLocationAt(int index, double latitude, double longitude,
                                              float accuracy, long photographedAt) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery delivery = deliveries.get(index);
        delivery.latitude = latitude;
        delivery.longitude = longitude;
        delivery.locationAccuracy = accuracy;
        delivery.photographedAt = photographedAt;
        writeDeliveries(deliveries);
    }

    public synchronized void updateReportAt(int index, String uri) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        deliveries.get(index).reportUri = uri == null ? "" : uri;
        writeDeliveries(deliveries);
    }

    public synchronized void updateDetailsAt(int index, String customerName, String address) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery old = deliveries.get(index);
        Delivery updated = new Delivery(
                old.trackingCode,
                customerName,
                address,
                old.atId,
                old.stop,
                old.neighborhood,
                old.city,
                old.postalCode,
                old.destinationLatitude,
                old.destinationLongitude,
                old.status,
                old.occurrenceType,
                old.occurrenceNote,
                old.packagePhotoUri,
                old.facadePhotoUri,
                old.houseId,
                old.latitude,
                old.longitude,
                old.locationAccuracy,
                old.photographedAt,
                old.reportUri
        );
        deliveries.set(index, updated);
        writeDeliveries(deliveries);
    }

    public synchronized void markOccurrenceAt(int index, String type, String note) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery delivery = deliveries.get(index);
        delivery.status = Delivery.STATUS_OCCURRENCE;
        delivery.occurrenceType = type == null ? "" : type.trim();
        delivery.occurrenceNote = note == null ? "" : note.trim();
        writeDeliveries(deliveries);
    }

    public synchronized void clearOccurrenceAt(int index) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        Delivery delivery = deliveries.get(index);
        delivery.status = Delivery.STATUS_PENDING;
        delivery.occurrenceType = "";
        delivery.occurrenceNote = "";
        writeDeliveries(deliveries);
    }

    public synchronized void removeAt(int index) {
        List<Delivery> deliveries = getDeliveries();
        if (index < 0 || index >= deliveries.size()) return;
        deliveries.remove(index);
        writeDeliveries(deliveries);
        int current = Math.min(getCurrentIndex(), deliveries.size());
        preferences.edit().putInt(KEY_INDEX, current).commit();
    }

    public synchronized void clearDeliveries() {
        writeDeliveries(new ArrayList<>());
        preferences.edit()
                .putInt(KEY_INDEX, 0)
                .putBoolean(KEY_TRACKING_USED, false)
                .putBoolean(KEY_NUMERIC_USED, false)
                .putBoolean(KEY_NAME_USED, false)
                .commit();
    }
}
