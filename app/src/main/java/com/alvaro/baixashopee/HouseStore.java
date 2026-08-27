package com.alvaro.baixashopee;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class HouseStore {
    private static final String PREFS = "house_memory";
    private static final String KEY_HOUSES = "houses";

    private final SharedPreferences preferences;

    public HouseStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<House> getHouses() {
        List<House> houses = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_HOUSES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                House house = House.fromJson(array.getJSONObject(i));
                if (!house.id.isEmpty()) houses.add(house);
            }
        } catch (JSONException ignored) {
            // Mantém o aplicativo utilizável se um cadastro local for interrompido.
        }
        return houses;
    }

    public synchronized House findById(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        for (House house : getHouses()) if (house.id.equals(id)) return house;
        return null;
    }

    public synchronized House findBySpecificAddress(String address) {
        String wanted = House.normalizeAddress(address);
        // Evita vincular uma cidade/bairro genérico a todas as encomendas.
        if (wanted.length() < 12 || !wanted.matches(".*\\d.*")) return null;
        for (House house : getHouses()) {
            if (!house.normalizedAddress().isEmpty() && house.normalizedAddress().equals(wanted)) {
                return house;
            }
        }
        return null;
    }

    public synchronized void save(House updated) {
        List<House> houses = getHouses();
        boolean found = false;
        for (int i = 0; i < houses.size(); i++) {
            if (houses.get(i).id.equals(updated.id)) {
                houses.set(i, updated);
                found = true;
                break;
            }
        }
        if (!found) houses.add(updated);
        write(houses);
    }

    public synchronized void updateFacade(String id, String uri) {
        House house = findById(id);
        if (house == null) return;
        house.facadePhotoUri = uri == null ? "" : uri.trim();
        save(house);
    }

    public synchronized void updateLocation(String id, double latitude, double longitude,
                                            float accuracy, long visitedAt) {
        House house = findById(id);
        if (house == null) return;
        house.latitude = latitude;
        house.longitude = longitude;
        house.locationAccuracy = accuracy;
        house.lastVisitedAt = visitedAt;
        save(house);
    }

    public synchronized void addResident(String id, String resident) {
        House house = findById(id);
        String clean = resident == null ? "" : resident.trim();
        if (house == null || clean.isEmpty()) return;
        String normalized = clean.toLowerCase(java.util.Locale.ROOT);
        for (String known : house.residents.split("\\s*[•;\\n]\\s*")) {
            if (known.trim().toLowerCase(java.util.Locale.ROOT).equals(normalized)) return;
        }
        house.residents = house.residents.isEmpty() ? clean : house.residents + " • " + clean;
        save(house);
    }

    public synchronized void delete(String id) {
        List<House> houses = getHouses();
        houses.removeIf(house -> house.id.equals(id));
        write(houses);
    }

    private void write(List<House> houses) {
        JSONArray array = new JSONArray();
        for (House house : houses) {
            try {
                array.put(house.toJson());
            } catch (JSONException ignored) {
                // Um cadastro inválido não apaga os demais.
            }
        }
        preferences.edit().putString(KEY_HOUSES, array.toString()).commit();
    }
}
