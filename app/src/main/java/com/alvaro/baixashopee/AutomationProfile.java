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
    public static final String KIND_REMOVE_OCCURRENCE = "TIRAR_OCORRENCIA";
    public static final String KIND_CUSTOM = "PERSONALIZADO";

    public static final String STOP_CYCLES = "CICLOS";
    public static final String STOP_DURATION = "TEMPO";
    public static final String STOP_INDEFINITE = "INDEFINIDO";

    public final String id;
    public String name;
    public String kind;
    public String allowedPackage;
    public final List<AutomationStep> steps;
    public String stopMode;
    public int cycleLimit;
    public long runDurationMs;
    public int targetSizeDp;
    public int panelWidthDp;

    public AutomationProfile(String id, String name, String kind, String allowedPackage,
                             List<AutomationStep> steps) {
        this(id, name, kind, allowedPackage, steps, STOP_CYCLES, 1,
                5 * 60_000L, 48, 220);
    }

    public AutomationProfile(String id, String name, String kind, String allowedPackage,
                             List<AutomationStep> steps, String stopMode, int cycleLimit,
                             long runDurationMs, int targetSizeDp, int panelWidthDp) {
        this.id = clean(id).isEmpty() ? UUID.randomUUID().toString() : clean(id);
        this.name = clean(name).isEmpty() ? "Nova configuração" : clean(name);
        this.kind = clean(kind).isEmpty() ? KIND_CUSTOM : clean(kind);
        this.allowedPackage = clean(allowedPackage);
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        this.stopMode = validStopMode(stopMode);
        this.cycleLimit = Math.max(1, cycleLimit);
        this.runDurationMs = Math.max(1_000L, runDurationMs);
        this.targetSizeDp = clamp(targetSizeDp, 32, 80);
        this.panelWidthDp = clamp(panelWidthDp, 180, 320);
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
        object.put("stopMode", stopMode);
        object.put("cycleLimit", cycleLimit);
        object.put("runDurationMs", runDurationMs);
        object.put("targetSizeDp", targetSizeDp);
        object.put("panelWidthDp", panelWidthDp);
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
                steps,
                object.optString("stopMode", STOP_CYCLES),
                object.optInt("cycleLimit", 1),
                object.optLong("runDurationMs", 5 * 60_000L),
                object.optInt("targetSizeDp", 48),
                object.optInt("panelWidthDp", 220)
        );
    }

    public AutomationProfile copyAs(String newName) {
        List<AutomationStep> copied = new ArrayList<>();
        for (AutomationStep step : steps) {
            copied.add(new AutomationStep(step.type, step.startX, step.startY,
                    step.endX, step.endY, step.delayAfterMs, step.durationMs));
        }
        return new AutomationProfile("", newName, KIND_CUSTOM, allowedPackage, copied,
                stopMode, cycleLimit, runDurationMs, targetSizeDp, panelWidthDp);
    }

    private static String validStopMode(String value) {
        if (STOP_INDEFINITE.equals(value) || STOP_DURATION.equals(value)) return value;
        return STOP_CYCLES;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
