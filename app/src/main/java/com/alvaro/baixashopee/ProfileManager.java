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

    public synchronized void saveAndAssign(AutomationProfile updated) {
        if (!AutomationProfile.KIND_CUSTOM.equals(updated.kind)) {
            List<AutomationProfile> profiles = getProfiles();
            for (AutomationProfile existing : profiles) {
                if (!existing.id.equals(updated.id) && existing.kind.equals(updated.kind)) {
                    existing.kind = AutomationProfile.KIND_CUSTOM;
                }
            }
            write(profiles);
        }
        save(updated);
    }

    public synchronized AutomationProfile create(String name) {
        AutomationProfile profile = new AutomationProfile("", name,
                AutomationProfile.KIND_CUSTOM, "", new ArrayList<>());
        save(profile);
        return profile;
    }

    public synchronized AutomationProfile duplicate(String id) {
        AutomationProfile original = findById(id);
        if (original == null) return create("Nova configuração");
        AutomationProfile copied = original.copyAs(original.name + " — cópia");
        save(copied);
        return copied;
    }

    public synchronized AutomationProfile clear(String id) {
        AutomationProfile profile = findById(id);
        if (profile == null) return null;
        profile.steps.clear();
        save(profile);
        return profile;
    }

    public synchronized AutomationProfile delete(String id) {
        List<AutomationProfile> profiles = getProfiles();
        AutomationProfile removed = null;
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).id.equals(id)) removed = profiles.remove(i);
        }
        write(profiles);
        ensureDefaults();
        List<AutomationProfile> remaining = getProfiles();
        AutomationProfile active = remaining.isEmpty() ? null : remaining.get(0);
        if (active != null) setActive(active.id);
        return removed;
    }

    public synchronized String exportJson() {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        for (AutomationProfile profile : getProfiles()) {
            try { array.put(profile.toJson()); } catch (Exception ignored) { }
        }
        try {
            root.put("format", "BaixaDaShopee-Automacao");
            root.put("version", 1);
            root.put("profiles", array);
        } catch (Exception ignored) { }
        return root.toString();
    }

    public synchronized int importJson(String json) {
        int imported = 0;
        try {
            JSONObject root = new JSONObject(json == null ? "" : json);
            JSONArray array = root.optJSONArray("profiles");
            if (array == null) return 0;
            List<AutomationProfile> profiles = getProfiles();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                AutomationProfile incoming = AutomationProfile.fromJson(item);
                AutomationProfile copy = incoming.copyAs(incoming.name);
                profiles.add(copy);
                imported++;
            }
            if (imported > 0) {
                write(profiles);
                setActive(profiles.get(profiles.size() - 1).id);
            }
        } catch (Exception ignored) { }
        return imported;
    }

    private synchronized void ensureDefaults() {
        List<AutomationProfile> profiles = getProfiles();
        boolean changed = false;
        if (findByKind(profiles, AutomationProfile.KIND_DOWNLOAD) == null) {
            profiles.add(new AutomationProfile("", "Baixar",
                    AutomationProfile.KIND_DOWNLOAD, "", new ArrayList<>()));
            changed = true;
        }
        if (findByKind(profiles, AutomationProfile.KIND_OCCURRENCE) == null) {
            profiles.add(new AutomationProfile("", "Ocorrência",
                    AutomationProfile.KIND_OCCURRENCE, "", new ArrayList<>()));
            changed = true;
        }
        if (findByKind(profiles, AutomationProfile.KIND_REMOVE_OCCURRENCE) == null) {
            profiles.add(new AutomationProfile("", "Tirar de ocorrência",
                    AutomationProfile.KIND_REMOVE_OCCURRENCE, "", new ArrayList<>()));
            changed = true;
        }
        if (changed) write(profiles);
        if (preferences.getString(KEY_ACTIVE, "").isEmpty() && !profiles.isEmpty()) {
            preferences.edit().putString(KEY_ACTIVE, profiles.get(0).id).commit();
        }
    }

    private AutomationProfile findByKind(List<AutomationProfile> profiles, String kind) {
        for (AutomationProfile profile : profiles) if (profile.kind.equals(kind)) return profile;
        return null;
    }

    private void write(List<AutomationProfile> profiles) {
        JSONArray array = new JSONArray();
        for (AutomationProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (Exception ignored) { }
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).commit();
    }
}
