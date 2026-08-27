package com.alvaro.baixashopee;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Salva perfis em JSON e mantém Baixa/Ocorrência sempre disponíveis para edição. */
public final class ProfileManager {
    private static final String PREFS = "automation_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_profile";

    private final SharedPreferences preferences;

    public ProfileManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    public synchronized List<AutomationProfile> getProfiles() {
        List<AutomationProfile> profiles = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_PROFILES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) profiles.add(AutomationProfile.fromJson(item));
            }
        } catch (Exception ignored) { }
        return profiles;
    }

    public synchronized AutomationProfile getActive() {
        String id = preferences.getString(KEY_ACTIVE, "");
        AutomationProfile profile = findById(id);
        if (profile != null) return profile;
        List<AutomationProfile> profiles = getProfiles();
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public synchronized AutomationProfile findById(String id) {
        if (id == null) return null;
        for (AutomationProfile profile : getProfiles()) if (profile.id.equals(id)) return profile;
        return null;
    }

    public synchronized AutomationProfile findByKind(String kind) {
        for (AutomationProfile profile : getProfiles()) if (profile.kind.equals(kind)) return profile;
        return null;
    }

    public synchronized void setActive(String id) {
        if (findById(id) != null) preferences.edit().putString(KEY_ACTIVE, id).commit();
    }

    public synchronized void save(AutomationProfile updated) {
        List<AutomationProfile> profiles = getProfiles();
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(updated.id)) {
                profiles.set(i, updated);
                found = true;
                break;
            }
        }
        if (!found) profiles.add(updated);
        write(profiles);
        setActive(updated.id);
    }

    public synchronized AutomationProfile create(String name) {
        AutomationProfile profile = new AutomationProfile("", name,
                AutomationProfile.KIND_CUSTOM, "", new ArrayList<>());
        save(profile);
        return profile;
    }

    private void ensureDefaults() {
        List<AutomationProfile> profiles = getProfiles();
        if (!profiles.isEmpty()) return;
        profiles.add(new AutomationProfile("", "Baixa assistida",
                AutomationProfile.KIND_DOWNLOAD, "", new ArrayList<>()));
        profiles.add(new AutomationProfile("", "Ocorrência assistida",
                AutomationProfile.KIND_OCCURRENCE, "", new ArrayList<>()));
        write(profiles);
        preferences.edit().putString(KEY_ACTIVE, profiles.get(0).id).commit();
    }

    private void write(List<AutomationProfile> profiles) {
        JSONArray array = new JSONArray();
        for (AutomationProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (Exception ignored) { }
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).commit();
    }
}
