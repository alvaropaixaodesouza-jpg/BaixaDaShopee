package com.alvaro.baixashopee;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Perfil persistente do painel assistido. */
public final class AutomationProfile {
    public static final String KIND_DOWNLOAD = "BAIXA";
    public static final String KIND_OCCURRENCE = "OCORRENCIA";
    public static final String KIND_CUSTOM = "PERSONALIZADO";

    public final String id;
    public String name;
    public String kind;
    public String allowedPackage;
    public final List<AutomationStep> steps;

    public AutomationProfile(String id, String name, String kind, String allowedPackage,
                             List<AutomationStep> steps) {
        this.id = clean(id).isEmpty() ? UUID.randomUUID().toString() : clean(id);
        this.name = clean(name).isEmpty() ? "Nova configuração" : clean(name);
        this.kind = clean(kind).isEmpty() ? KIND_CUSTOM : clean(kind);
        this.allowedPackage = clean(allowedPackage);
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("kind", kind);
        object.put("allowedPackage", allowedPackage);
        JSONArray array = new JSONArray();
        for (AutomationStep step : steps) array.put(step.toJson());
        object.put("steps", array);
        return object;
    }

    public static AutomationProfile fromJson(JSONObject object) {
        List<AutomationStep> steps = new ArrayList<>();
        JSONArray array = object.optJSONArray("steps");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject step = array.optJSONObject(i);
                if (step != null) steps.add(AutomationStep.fromJson(step));
            }
        }
        return new AutomationProfile(
                object.optString("id"),
                object.optString("name"),
                object.optString("kind", KIND_CUSTOM),
                object.optString("allowedPackage"),
                steps
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
