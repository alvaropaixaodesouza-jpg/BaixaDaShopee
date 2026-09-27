package com.alvaro.baixashopee;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alvaro.baixashopee.data.db.AppDatabase;
import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;
import com.alvaro.baixashopee.data.repository.DeliveryRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletedDeliveriesActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private DeliveryRepository repository;
    private CompletedDeliveriesAdapter adapter;
    private EditText searchInput;
    private TextView countText;
    private TextView emptyText;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_completed_deliveries);

        db = AppDatabase.getInstance(this);
        repository = DeliveryRepository.getInstance(this);

        findViewById(R.id.completedBackBtn).setOnClickListener(v -> finish());
        searchInput = findViewById(R.id.completedSearchInput);
        countText = findViewById(R.id.completedCountText);
        emptyText = findViewById(R.id.completedEmptyText);

        RecyclerView recyclerView = findViewById(R.id.completedRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CompletedDeliveriesAdapter(this, this::onUndoDelivery);
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                loadData();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadData();
    }

    private void loadData() {
        executor.execute(() -> {
            List<CompletedDeliveryEntity> list;
            if (currentQuery.isEmpty()) {
                list = db.completedDeliveryDao().getAll();
            } else {
                list = db.completedDeliveryDao().search(currentQuery);
            }
            runOnUiThread(() -> {
                adapter.submitList(list);
                countText.setText(list.size() + " entrega(s) concluída(s)");
                emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void onUndoDelivery(CompletedDeliveryEntity item) {
        executor.execute(() -> {
            boolean success = repository.undoCompletion(item.trackingCode);
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "Entrega " + item.trackingCode + " restaurada para a fila", Toast.LENGTH_SHORT).show();
                    loadData();
                } else {
                    Toast.makeText(this, "Não foi possível restaurar", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
