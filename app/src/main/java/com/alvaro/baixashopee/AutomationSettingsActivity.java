package com.alvaro.baixashopee;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Tela editável dos perfis e das definições de ocorrência. */
public final class AutomationSettingsActivity extends Activity {
    public static final String EXTRA_STEP_INDEX = "stepIndex";

    private ProfileManager profileManager;
    private AutomationProfile profile;
    private EditText nameInput;
    private EditText packageInput;
    private LinearLayout stepsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileManager = new ProfileManager(this);
        profile = profileManager.getActive();
        if (profile == null) profile = profileManager.create("Nova configuração");
        setContentView(buildContent());
        render();

        int stepIndex = getIntent().getIntExtra(EXTRA_STEP_INDEX, -1);
        if (stepIndex >= 0 && stepIndex < profile.steps.size()) {
            final int selected = stepIndex;
            stepsContainer.post(() -> showStepEditor(selected));
        }
    }

    private View buildContent() {
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(getColor(R.color.cream));

        TextView title = text("Configuração do painel assistido", 24, true);
        content.addView(title);

        TextView explanation = text(
                "Cada toque em ▶ executa somente um passo. O perfil funciona apenas no pacote Android autorizado e sempre para antes de uma confirmação manual.",
                14, false);
        explanation.setTextColor(getColor(R.color.muted));
        explanation.setPadding(0, dp(6), 0, dp(12));
        content.addView(explanation);

        nameInput = field("Nome da configuração");
        packageInput = field("Pacote Android autorizado");
        content.addView(nameInput);
        content.addView(packageInput);

        Button useCurrent = button("Usar o aplicativo aberto por último");
        useCurrent.setOnClickListener(v -> {
            String detected = AutomationAccessibilityService.getCurrentPackage();
            if (detected.isEmpty()) {
                Toast.makeText(this, "Abra primeiro o aplicativo que será mapeado", Toast.LENGTH_LONG).show();
            } else {
                packageInput.setText(detected);
            }
        });
        content.addView(useCurrent);

        LinearLayout profileButtons = horizontal();
        Button load = button("Carregar configuração");
        Button create = button("Nova");
        profileButtons.addView(load, weighted());
        profileButtons.addView(space());
        profileButtons.addView(create, weighted());
        content.addView(profileButtons);
        load.setOnClickListener(v -> showProfilePicker());
        create.setOnClickListener(v -> {
            saveCurrent();
            profile = profileManager.create("Nova configuração");
            render();
        });

        TextView stepsTitle = text("Alvos mapeados", 18, true);
        stepsTitle.setPadding(0, dp(16), 0, dp(6));
        content.addView(stepsTitle);
        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(stepsContainer);

        Button occurrences = button("Editar definições de ocorrência");
        occurrences.setOnClickListener(v -> showOccurrenceDefinitions());
        content.addView(occurrences);

        LinearLayout footer = horizontal();
        Button cancel = button("Fechar");
        Button save = button("Salvar");
        footer.addView(cancel, weighted());
        footer.addView(space());
        footer.addView(save, weighted());
        content.addView(footer);
        cancel.setOnClickListener(v -> finish());
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
        packageInput.setText(profile.allowedPackage);
        stepsContainer.removeAllViews();
        if (profile.steps.isEmpty()) {
            TextView empty = text("Nenhum alvo. Use + ou ↝ na barra flutuante para mapear a tela.", 14, false);
            empty.setTextColor(getColor(R.color.muted));
            stepsContainer.addView(empty);
            return;
        }
        for (int i = 0; i < profile.steps.size(); i++) {
            AutomationStep step = profile.steps.get(i);
            String kind = AutomationStep.TYPE_SWIPE.equals(step.type) ? "Deslize" : "Toque";
            Button row = button((i + 1) + ". " + kind + " • espera " + step.delayAfterMs + " ms");
            final int index = i;
            row.setOnClickListener(v -> showStepEditor(index));
            stepsContainer.addView(row);
        }
    }

    private void showProfilePicker() {
        saveCurrent();
        List<AutomationProfile> profiles = profileManager.getProfiles();
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).name + " • " + profiles.get(i).steps.size() + " passo(s)";
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
                ? "A: " + step.startX + ", " + step.startY + "  →  B: " + step.endX + ", " + step.endY
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
        EditText delay = numeric("Atraso antes do próximo passo", delayValue);
        Spinner delayUnit = new Spinner(this);
        delayUnit.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"MS", "S", "MIN"}));
        delayUnit.setSelection(unitIndex);
        EditText duration = numeric("Duração do gesto (ms)", step.durationMs);
        form.addView(coordinates);
        form.addView(delay);
        form.addView(delayUnit);
        form.addView(duration);
        duration.setVisibility(AutomationStep.TYPE_SWIPE.equals(step.type) ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar alvo " + (index + 1))
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Excluir", null)
                .setPositiveButton("Salvar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long multiplier = delayUnit.getSelectedItemPosition() == 2 ? 60_000
                        : delayUnit.getSelectedItemPosition() == 1 ? 1_000 : 1;
                step.delayAfterMs = number(delay, 600) * multiplier;
                if (AutomationStep.TYPE_SWIPE.equals(step.type)) {
                    step.durationMs = Math.max(1, number(duration, 450));
                }
                profileManager.save(profile);
                FloatingAssistantService.requestRefresh();
                dialog.dismiss();
                render();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                profile.steps.remove(index);
                profileManager.save(profile);
                FloatingAssistantService.requestRefresh();
                dialog.dismiss();
                render();
            });
        });
        dialog.show();
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
                })
                .show();
    }

    private void saveCurrent() {
        profile.name = nameInput.getText().toString().trim();
        if (profile.name.isEmpty()) profile.name = "Nova configuração";
        profile.allowedPackage = packageInput.getText().toString().trim();
        profileManager.save(profile);
        FloatingAssistantService.requestRefresh();
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        return field;
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
}
