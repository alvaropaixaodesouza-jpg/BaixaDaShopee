package com.alvaro.baixashopee;

import org.json.JSONException;
import org.json.JSONObject;

/** Um passo visual editável: toque simples ou deslize. */
public final class AutomationStep {
    public static final String TYPE_TAP = "TAP";
    public static final String TYPE_SWIPE = "SWIPE";

    public String type;
    public int startX;
    public int startY;
    public int endX;
    public int endY;
    public long delayAfterMs;
    public long durationMs;

    public AutomationStep(String type, int startX, int startY, int endX, int endY,
                          long delayAfterMs, long durationMs) {
        this.type = TYPE_SWIPE.equals(type) ? TYPE_SWIPE : TYPE_TAP;
        this.startX = Math.max(0, startX);
        this.startY = Math.max(0, startY);
        this.endX = Math.max(0, endX);
        this.endY = Math.max(0, endY);
        this.delayAfterMs = Math.max(0, delayAfterMs);
        this.durationMs = Math.max(1, durationMs);
    }

    public static AutomationStep tap(int x, int y) {
        return new AutomationStep(TYPE_TAP, x, y, x, y, 600, 60);
    }

    public static AutomationStep swipe(int startX, int startY, int endX, int endY) {
        return new AutomationStep(TYPE_SWIPE, startX, startY, endX, endY, 800, 450);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("type", type);
        object.put("startX", startX);
        object.put("startY", startY);
        object.put("endX", endX);
        object.put("endY", endY);
        object.put("delayAfterMs", delayAfterMs);
        object.put("durationMs", durationMs);
        return object;
    }

    public static AutomationStep fromJson(JSONObject object) {
        return new AutomationStep(
                object.optString("type", TYPE_TAP),
                object.optInt("startX", 300),
                object.optInt("startY", 600),
                object.optInt("endX", 300),
                object.optInt("endY", 600),
                object.optLong("delayAfterMs", 600),
                object.optLong("durationMs", 60)
        );
    }
}
