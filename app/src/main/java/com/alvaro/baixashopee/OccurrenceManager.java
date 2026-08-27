package com.alvaro.baixashopee;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** Mantém as ocorrências editáveis sem misturá-las aos dados da rota diária. */
public final class OccurrenceManager {
    private static final String PREFS = "occurrence_definitions";
    private static final String KEY_ITEMS = "items";
    private static final List<String> DEFAULTS = Arrays.asList(
            "Cliente ausente",
            "Endereço não localizado",
            "Destinatário desconhecido",
            "Entrega recusada",
            "Acesso ao local impedido"
    );

    private final SharedPreferences preferences;

    public OccurrenceManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<String> getItems() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        String raw = preferences.getString(KEY_ITEMS, "");
        if (raw == null || raw.isEmpty()) {
            items.addAll(DEFAULTS);
        } else {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (!value.isEmpty()) items.add(value);
            }
        }
        return new ArrayList<>(items);
    }

    public synchronized void add(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return;
        List<String> items = getItems();
        if (!items.contains(clean)) items.add(clean);
        save(items);
    }

    public synchronized void replaceAll(List<String> values) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) clean.add(value.trim());
        }
        if (clean.isEmpty()) clean.addAll(DEFAULTS);
        save(new ArrayList<>(clean));
    }

    private void save(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        preferences.edit().putString(KEY_ITEMS, array.toString()).commit();
    }
}
