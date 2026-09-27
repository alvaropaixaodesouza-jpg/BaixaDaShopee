package com.alvaro.baixashopee.photo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alvaro.baixashopee.Delivery;
import com.alvaro.baixashopee.DeliveryStore;
import com.alvaro.baixashopee.R;
import com.alvaro.baixashopee.data.db.AppDatabase;
import com.alvaro.baixashopee.data.db.entities.PhotoEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class PhotoAssignerDialog {
    public interface OnPhotoAssignedListener {
        void onPhotoUpdated();
    }

    private final Context context;
    private final List<PhotoEntity> photos;
    private final OnPhotoAssignedListener listener;
    private final AppDatabase db;
    private final DeliveryStore deliveryStore;
    private int currentIndex;
    private Dialog dialog;

    public PhotoAssignerDialog(Context context, List<PhotoEntity> photos, int startIndex, OnPhotoAssignedListener listener) {
        this.context = context;
        this.photos = new ArrayList<>(photos);
        this.currentIndex = Math.max(0, Math.min(startIndex, this.photos.size() - 1));
        this.listener = listener;
        this.db = AppDatabase.getInstance(context);
        this.deliveryStore = new DeliveryStore(context);
    }

    public void show() {
        if (photos.isEmpty()) return;

        dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_pending_photo_viewer);

        Button closeBtn = dialog.findViewById(R.id.viewerCloseButton);
        Button prevBtn = dialog.findViewById(R.id.viewerPrevButton);
        Button nextBtn = dialog.findViewById(R.id.viewerNextButton);
        Button assignBtn = dialog.findViewById(R.id.viewerAssignButton);
        Button deleteBtn = dialog.findViewById(R.id.viewerDeleteButton);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        prevBtn.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                render();
            }
        });
        nextBtn.setOnClickListener(v -> {
            if (currentIndex < photos.size() - 1) {
                currentIndex++;
                render();
            }
        });
        assignBtn.setOnClickListener(v -> showDeliverySearchSelector());
        deleteBtn.setOnClickListener(v -> deleteCurrentPhoto());

        render();
        dialog.show();
    }

    private void render() {
        if (photos.isEmpty()) {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            return;
        }

        TextView title = dialog.findViewById(R.id.viewerHeaderTitle);
        ImageView image = dialog.findViewById(R.id.viewerMainImage);
        TextView detail = dialog.findViewById(R.id.viewerStatusDetail);
        Button prevBtn = dialog.findViewById(R.id.viewerPrevButton);
        Button nextBtn = dialog.findViewById(R.id.viewerNextButton);

        PhotoEntity photo = photos.get(currentIndex);
        title.setText("Foto " + (currentIndex + 1) + " de " + photos.size());
        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < photos.size() - 1);

        ThumbnailLoader.loadThumbnail(context, Uri.parse(photo.uri), 1080, image);

        String statusDesc = "Status: ";
        if (PhotoEntity.STATUS_PENDING_NO_READING.equals(photo.status)) {
            statusDesc += "Sem leitura no barcode/QR e OCR";
        } else if (PhotoEntity.STATUS_PENDING_AMBIGUOUS.equals(photo.status)) {
            statusDesc += "Mais de um código detectado";
        } else {
            statusDesc += "Código lido não confere com a rota";
        }
        if (photo.detectedCode != null && !photo.detectedCode.isEmpty()) {
            statusDesc += " (" + photo.detectedCode + ")";
        }
        detail.setText(statusDesc);
    }

    private void showDeliverySearchSelector() {
        List<Delivery> deliveries = deliveryStore.getDeliveries();
        if (deliveries.isEmpty()) {
            Toast.makeText(context, "A rota está vazia", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        EditText searchInput = new EditText(context);
        searchInput.setHint("Digite o rastreio ou nome...");
        searchInput.setPadding(30, 20, 30, 20);

        List<String> labels = new ArrayList<>();
        List<Delivery> filtered = new ArrayList<>(deliveries);
        for (Delivery d : deliveries) {
            labels.add(d.trackingCode + " — " + d.customerName + "\n" + d.address);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels);
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);

        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(20, 20, 20, 20);
        container.addView(searchInput);
        container.addView(listView);

        AlertDialog searchDialog = new AlertDialog.Builder(context)
                .setTitle("Atribuir a uma entrega")
                .setView(container)
                .setNegativeButton("Cancelar", null)
                .create();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().toLowerCase().trim();
                labels.clear();
                filtered.clear();
                for (Delivery d : deliveries) {
                    if (d.trackingCode.toLowerCase().contains(q)
                            || d.customerName.toLowerCase().contains(q)
                            || d.address.toLowerCase().contains(q)) {
                        filtered.add(d);
                        labels.add(d.trackingCode + " — " + d.customerName + "\n" + d.address);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, v, position, id) -> {
            Delivery chosen = filtered.get(position);
            searchDialog.dismiss();
            assignPhotoToDelivery(chosen);
        });

        searchDialog.show();
    }

    private void assignPhotoToDelivery(Delivery chosen) {
        PhotoEntity photo = photos.get(currentIndex);
        photo.status = PhotoEntity.STATUS_ASSIGNED;
        photo.packageTrackingCode = chosen.trackingCode;

        Executors.newSingleThreadExecutor().execute(() -> {
            db.photoDao().update(photo);

            // Atualiza fila ativa
            List<Delivery> all = deliveryStore.getDeliveries();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).trackingCode.equalsIgnoreCase(chosen.trackingCode)) {
                    deliveryStore.updatePhotoAt(i, true, photo.uri);
                    break;
                }
            }
        });

        Toast.makeText(context, "Foto vinculada a " + chosen.trackingCode, Toast.LENGTH_SHORT).show();

        photos.remove(currentIndex);
        if (listener != null) listener.onPhotoUpdated();

        if (currentIndex >= photos.size()) {
            currentIndex = photos.size() - 1;
        }

        if (photos.isEmpty()) {
            dialog.dismiss();
            Toast.makeText(context, "Todas as fotos pendentes foram tratadas!", Toast.LENGTH_SHORT).show();
        } else {
            render();
        }
    }

    private void deleteCurrentPhoto() {
        PhotoEntity photo = photos.get(currentIndex);
        new AlertDialog.Builder(context)
                .setTitle("Descartar foto")
                .setMessage("Deseja realmente remover esta foto pendente?")
                .setNegativeButton("Não", null)
                .setPositiveButton("Sim, descartar", (d, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> db.photoDao().deleteById(photo.id));
                    photos.remove(currentIndex);
                    if (listener != null) listener.onPhotoUpdated();

                    if (currentIndex >= photos.size()) {
                        currentIndex = photos.size() - 1;
                    }
                    if (photos.isEmpty()) {
                        dialog.dismiss();
                    } else {
                        render();
                    }
                })
                .show();
    }
}
