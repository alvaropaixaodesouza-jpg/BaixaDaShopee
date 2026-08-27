package com.alvaro.baixashopee;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public class House {
    public final String id;
    public String label;
    public String residents;
    public String address;
    public String mapUri;
    public String facadePhotoUri;
    public String notes;
    public double latitude;
    public double longitude;
    public float locationAccuracy;
    public long lastVisitedAt;

    public House(String id, String label, String residents, String address,
                 String mapUri, String facadePhotoUri, String notes) {
        this(id, label, residents, address, mapUri, facadePhotoUri, notes, 0, 0, 0, 0);
    }

    public House(String id, String label, String residents, String address,
                 String mapUri, String facadePhotoUri, String notes,
                 double latitude, double longitude, float locationAccuracy, long lastVisitedAt) {
        this.id = safe(id).isEmpty() ? UUID.randomUUID().toString() : safe(id);
        this.label = safe(label);
        this.residents = safe(residents);
        this.address = safe(address);
        this.mapUri = safe(mapUri);
        this.facadePhotoUri = safe(facadePhotoUri);
        this.notes = safe(notes);
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.lastVisitedAt = lastVisitedAt;
    }

    public static House create(String label, String residents, String address,
                               String mapUri, String notes) {
        return new House("", label, residents, address, mapUri, "", notes);
    }

    public String displayName() {
        if (!label.isEmpty()) return label;
        if (!residents.isEmpty()) return residents;
        return address.isEmpty() ? "Casa sem nome" : address;
    }

    public String normalizedAddress() {
        return normalizeAddress(address);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("label", label);
        object.put("residents", residents);
        object.put("address", address);
        object.put("mapUri", mapUri);
        object.put("facadePhotoUri", facadePhotoUri);
        object.put("notes", notes);
        object.put("latitude", latitude);
        object.put("longitude", longitude);
        object.put("locationAccuracy", locationAccuracy);
        object.put("lastVisitedAt", lastVisitedAt);
        return object;
    }

    public static House fromJson(JSONObject object) {
        return new House(
                object.optString("id"),
                object.optString("label"),
                object.optString("residents"),
                object.optString("address"),
                object.optString("mapUri"),
                object.optString("facadePhotoUri"),
                object.optString("notes"),
                object.optDouble("latitude", 0),
                object.optDouble("longitude", 0),
                (float) object.optDouble("locationAccuracy", 0),
                object.optLong("lastVisitedAt", 0)
        );
    }

    public static String normalizeAddress(String value) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
