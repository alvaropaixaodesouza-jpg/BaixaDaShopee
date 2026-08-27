package com.alvaro.baixashopee;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Configura perfis persistentes, condições de parada e cada alvo visual. */
public final class AutomationSettingsActivity extends Activity {
    public static final String EXTRA_STEP_INDEX = "stepIndex";
    private static final int REQUEST_IMPORT_PROFILE = 201;
    private static final int REQUEST_EXPORT_PROFILE = 202;

    private static final String[] KIND_LABELS = {
            "Personalizado", "Baixar", "Ocorrência", "Tirar de ocorrência"
    };
    private static final String[] KIND_VALUES = {
            AutomationProfile.KIND_CUSTOM,
            AutomationProfile.KIND_DOWNLOAD,
            AutomationProfile.KIND_OCCURRENCE,
            AutomationProfile.KIND_REMOVE_OCCURRENCE
    };
    private static final String[] STOP_LABELS = {
            "Número de ciclos", "Quantidade de tempo", "Executar indefinidamente"
    };
    private static final String[] STOP_VALUES = {
            AutomationProfile.STOP_CYCLES,
            AutomationProfile.STOP_DURATION,
            AutomationProfile.STOP_INDEFINITE
    };

    private ProfileManager profileManager;
    private AutomationProfile profile;
    private EditText nameInput;
    private EditText cycleInput;
    private EditText durationInput;
    private Spinner kindSpinner;
    private Spinner stopSpinner;
    private Spinner targetSizeSpinner;
    private Spinner panelSizeSpinner;
    private LinearLayout stepsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileManager = new ProfileManager(this);
        profile = profileManager.getActive();
        if (profile == null) profile = profileManager.create("Nova configuração");
        getWindow().setStatusBarColor(getColor(R.color.cream));
        setContentView(buildContent());
        render();

        int selectedStep = getIntent().getIntExtra(EXTRA_STEP_INDEX, -1);
        if (selectedStep >= 0 && selectedStep < profile.steps.size()) {
            stepsContainer.post(() -> showStepEditor(selectedStep));
        }
    }

    private View buildContent() {
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(getColor(R.color.cream));

        content.addView(text("Predefinições do autoclique", 25, true));
        TextView explanation = text(
                "Escolha Baixar, Ocorrência ou Tirar de ocorrência. Todas começam vazias: adicione e mova os alvos no painel. Somente Play inicia a sequência.",
                14, false);
        explanation.setTextColor(getColor(R.color.muted));
        explanation.setPadding(0, dp(6), 0, dp(12));
        content.addView(explanation);

        nameInput = field("Nome da configuração");
        content.addView(nameInput);

        content.addView(label("Usar esta configuração em"));
        kindSpinner = spinner(KIND_LABELS);
        content.addView(kindSpinner);

        LinearLayout profileRow = horizontal();
        Button load = button("Carregar");
        Button create = button("Nova");
        profileRow.addView(load, weighted());
        profileRow.addView(space());
        profileRow.addView(create, weighted());
        content.addView(profileRow);
        load.setOnClickListener(v -> showProfilePicker());
        create.setOnClickListener(v -> {
            saveCurrent();
            profile = profileManager.create("Nova configuração");
            render();
        });

        LinearLayout copyRow = horizontal();
        Button duplicate = button("Duplicar");
        Button delete = button("Excluir");
        copyRow.addView(duplicate, weighted());
        copyRow.addView(space());
        copyRow.addView(delete, weighted());
        content.addView(copyRow);
        duplicate.setOnClickListener(v -> {
            saveCurrent();
            profile = profileManager.duplicate(profile.id);
            render();
        });
        delete.setOnClickListener(v -> confirmDelete());

        TextView stopTitle = text("Parar após", 19, true);
        stopTitle.setPadding(0, dp(18), 0, dp(4));
        content.addView(stopTitle);
        stopSpinner = spinner(STOP_LABELS);
        content.addView(stopSpinner);
        cycleInput = numeric("Número de ciclos", 1);
        durationInput = numeric("Tempo total em segundos", 300);
        content.addView(cycleInput);
        content.addView(durationInput);
        stopSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::updateStopFields));

        content.addView(label("Tamanho dos alvos"));
        targetSizeSpinner = spinner(new String[]{"Pequeno", "Médio", "Grande"});
        content.addView(targetSizeSpinner);
        content.addView(label("Largura do painel de dados"));
        panelSizeSpinner = spinner(new String[]{"Pequeno", "Médio", "Grande"});
        content.addView(panelSizeSpinner);

        TextView stepsTitle = text("Alvos mapeados", 19, true);
        stepsTitle.setPadding(0, dp(18), 0, dp(6));
        content.addView(stepsTitle);
        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(stepsContainer);

        LinearLayout fileRow = horizontal();
        Button importButton = button("Importar perfis");
        Button exportButton = button("Exportar perfis");
        fileRow.addView(importButton, weighted());
        fileRow.addView(space());
        fileRow.addView(exportButton, weighted());
        content.addView(fileRow);
        importButton.setOnClickListener(v -> chooseProfileImport());
        exportButton.setOnClickListener(v -> chooseProfileExport());

        Button clear = button("Limpar todos os alvos desta configuração");
        clear.setOnClickListener(v -> confirmClear());
        content.addView(clear);

        Button occurrences = button("Editar definições de ocorrência");
        occurrences.setOnClickListener(v -> showOccurrenceDefinitions());
        content.addView(occurrences);

        LinearLayout footer = horizontal();
        Button close = button("Fechar");
        Button save = button("Salvar");
        save.setBackgroundResource(R.drawable.button_primary);
        save.setTextColor(getColor(R.color.white));
        footer.addView(close, weighted());
        footer.addView(space());
        footer.addView(save, weighted());
        content.addView(footer);
        close.setOnClickListener(v -> finish());
        save.setOnClickListener(v -> {
            saveCurrent();
            Toast.makeText(this, "Configuração salva", Toast.LENGTH_SHORT).show();
            finish();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void render() {
        nameInput.setText(profile.name);
        kindSpinner.setSelection(indexOf(KIND_VALUES, profile.kind));
        stopSpinner.setSelection(indexOf(STOP_VALUES, profile.stopMode));
        cycleInput.setText(String.valueOf(profile.cycleLimit));
        durationInput.setText(String.valueOf(Math.max(1, profile.runDurationMs / 1_000L)));
        targetSizeSpinner.setSelection(profile.targetSizeDp <= 40 ? 0
                : profile.targetSizeDp >= 60 ? 2 : 1);
        panelSizeSpinner.setSelection(profile.panelWidthDp <= 190 ? 0
                : profile.panelWidthDp >= 270 ? 2 : 1);
        updateStopFields();
        renderSteps();
    }

    private void renderSteps() {
        stepsContainer.removeAllViews();
        if (profile.steps.isEmpty()) {
            TextView empty = text(
                    "Nenhum alvo. Abra o painel e use + para toque ou ↝ para deslize.",
                    14, false);
            empty.setTextColor(getColor(R.color.muted));
            stepsContainer.addView(empty);
            return;
        }
        for (int i = 0; i < profile.steps.size(); i++) {
            AutomationStep step = profile.steps.get(i);
            String kind = AutomationStep.TYPE_SWIPE.equals(step.type) ? "Deslize" : "Toque";
            Button row = button((i + 1) + ". " + kind + " • próximo em "
                    + step.delayAfterMs + " ms");
            final int index = i;
            row.setOnClickListener(v -> showStepEditor(index));
            stepsContainer.addView(row);
        }
    }

    private void updateStopFields() {
        if (stopSpinner == null) return;
        int selected = stopSpinner.getSelectedItemPosition();
        cycleInput.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);
        durationInput.setVisibility(selected == 1 ? View.VISIBLE : View.GONE);
    }

    private void showProfilePicker() {
        saveCurrent();
        List<AutomationProfile> profiles = profileManager.getProfiles();
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).name + " • "
                    + profiles.get(i).steps.size() + " alvo(s)";
        }
        new AlertDialog.Builder(this)
                .setTitle("Carregar configuração")
                .setItems(labels, (dialog, which) -> {
                    profile = profiles.get(which);
                    profileManager.setActive(profile.id);
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showStepEditor(int index) {
        if (index < 0 || index >= profile.steps.size()) return;
        AutomationStep step = profile.steps.get(index);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), 0, dp(20), 0);

        TextView coordinates = text(AutomationStep.TYPE_SWIPE.equals(step.type)
                ? "A: " + step.startX + ", " + step.startY + " → B: "
                + step.endX + ", " + step.endY
                : "Posição: " + step.startX + ", " + step.startY, 14, false);
        long delayValue = step.delayAfterMs;
        int unitIndex = 0;
        if (delayValue >= 60_000 && delayValue % 60_000 == 0) {
            delayValue /= 60_000;
            unitIndex = 2;
        } else if (delayValue >= 1_000 && delayValue % 1_000 == 0) {
            delayValue /= 1_000;
            unitIndex = 1;
        }
        EditText delay = numeric("Atraso antes do próximo clique", delayValue);
        Spinner delayUnit = spinner(new String[]{"MS", "S", "MIN"});
        delayUnit.setSelection(unitIndex);
        EditText duration = numeric("Duração do deslize em ms", step.durationMs);
        form.addView(coordinates);
        form.addView(delay);
        form.addView(delayUnit);
        form.addView(duration);
        duration.setVisibility(AutomationStep.TYPE_SWIPE.equals(step.type)
                ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar alvo " + (index + 1))
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Excluir", null)
                .setPositiveButton("Salvar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long multiplier = delayUnit.getSelectedItemPosition() == 2 ? 60_000L
                        : delayUnit.getSelectedItemPosition() == 1 ? 1_000L : 1L;
                step.delayAfterMs = Math.max(0, number(delay, 600) * multiplier);
                if (AutomationStep.TYPE_SWIPE.equals(step.type)) {
                    step.durationMs = Math.max(1, number(duration, 450));
                }
                profileManager.save(profile);
                FloatingAssistantService.requestRefresh();
                dialog.dismiss();
                renderSteps();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                profile.steps.remove(index);
                profileManager.save(profile);
                FloatingAssistantService.requestRefresh();
                dialog.dismiss();
                renderSteps();
            });
        });
        dialog.show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Limpar os alvos?")
                .setMessage("A configuração continuará salva, mas voltará vazia.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar", (dialog, which) -> {
                    profile.steps.clear();
                    profileManager.save(profile);
                    FloatingAssistantService.requestRefresh();
                    renderSteps();
                }).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir esta configuração?")
                .setMessage(profile.name)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    profileManager.delete(profile.id);
                    profile = profileManager.getActive();
                    render();
                    FloatingAssistantService.requestRefresh();
                }).show();
    }

    private void chooseProfileImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_PROFILE);
    }

    private void chooseProfileExport() {
        saveCurrent();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "BaixaDaShopee-perfis.json");
        startActivityForResult(intent, REQUEST_EXPORT_PROFILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT_PROFILE) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Não foi possível criar o arquivo");
                    output.write(profileManager.exportJson().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Perfis exportados", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_IMPORT_PROFILE) {
                String json;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Não foi possível abrir o arquivo");
                    json = new String(readAll(input), StandardCharsets.UTF_8);
                }
                int count = profileManager.importJson(json);
                if (count == 0) throw new IllegalArgumentException("O arquivo não contém perfis válidos");
                profile = profileManager.getActive();
                render();
                Toast.makeText(this, count + " perfil(is) importado(s)", Toast.LENGTH_LONG).show();
                FloatingAssistantService.requestRefresh();
            }
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showOccurrenceDefinitions() {
        OccurrenceManager manager = new OccurrenceManager(this);
        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setText(String.join("\n", manager.getItems()));
        input.setPadding(dp(20), dp(8), dp(20), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Ocorrências — uma por linha")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    List<String> values = new ArrayList<>();
                    for (String line : input.getText().toString().split("\\R")) values.add(line);
                    manager.replaceAll(values);
                }).show();
    }

    private void saveCurrent() {
        if (profile == null) return;
        profile.name = nameInput.getText().toString().trim();
        if (profile.name.isEmpty()) profile.name = "Nova configuração";
        profile.allowedPackage = "";
        profile.kind = KIND_VALUES[Math.max(0, kindSpinner.getSelectedItemPosition())];
        profile.stopMode = STOP_VALUES[Math.max(0, stopSpinner.getSelectedItemPosition())];
        profile.cycleLimit = (int) Math.max(1, number(cycleInput, 1));
        profile.runDurationMs = Math.max(1, number(durationInput, 300)) * 1_000L;
        profile.targetSizeDp = new int[]{36, 48, 64}[targetSizeSpinner.getSelectedItemPosition()];
        profile.panelWidthDp = new int[]{180, 220, 280}[panelSizeSpinner.getSelectedItemPosition()];
        profileManager.saveAndAssign(profile);
        FloatingAssistantService.requestRefresh();
    }

    private byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("O arquivo de perfis é grande demais");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(getColor(R.color.ink));
        input.setHintTextColor(getColor(R.color.muted));
        input.setBackgroundResource(R.drawable.field_soft);
        input.setPadding(dp(13), dp(8), dp(13), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(6);
        input.setLayoutParams(params);
        return input;
    }

    private EditText numeric(String hint, long value) {
        EditText input = field(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        return input;
    }

    private long number(EditText input, long fallback) {
        try { return Long.parseLong(input.getText().toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setMinimumHeight(dp(52));
        return spinner;
    }

    private TextView label(String value) {
        TextView label = text(value, 15, true);
        label.setPadding(0, dp(12), 0, dp(3));
        return label;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(getColor(R.color.ink));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_secondary);
        button.setTextColor(getColor(R.color.orange_dark));
        button.setMinHeight(dp(50));
        return button;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(52), 1);
    }

    private View space() {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return space;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Adapta um callback simples ao listener verboso do Spinner. */
    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable callback;
        SimpleItemSelectedListener(Runnable callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                             int position, long id) { callback.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
