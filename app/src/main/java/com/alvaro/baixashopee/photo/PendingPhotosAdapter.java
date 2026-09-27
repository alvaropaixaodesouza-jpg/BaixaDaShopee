package com.alvaro.baixashopee.photo;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alvaro.baixashopee.R;
import com.alvaro.baixashopee.data.db.entities.PhotoEntity;

import java.util.ArrayList;
import java.util.List;

public class PendingPhotosAdapter extends RecyclerView.Adapter<PendingPhotosAdapter.PhotoViewHolder> {
    public interface OnPhotoClickListener {
        void onPhotoClick(int position, PhotoEntity photo);
    }

    private final Context context;
    private final OnPhotoClickListener listener;
    private List<PhotoEntity> fullList = new ArrayList<>();
    private List<PhotoEntity> filteredList = new ArrayList<>();
    private String currentFilter = "ALL";

    public PendingPhotosAdapter(Context context, OnPhotoClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<PhotoEntity> photos) {
        this.fullList = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
        applyFilter(currentFilter);
    }

    public void setFilter(String filter) {
        this.currentFilter = filter;
        applyFilter(filter);
    }

    private void applyFilter(String filter) {
        filteredList.clear();
        if ("ALL".equalsIgnoreCase(filter) || "TODOS".equalsIgnoreCase(filter)) {
            filteredList.addAll(fullList);
        } else {
            for (PhotoEntity p : fullList) {
                if (filter.equalsIgnoreCase(p.status)) {
                    filteredList.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pending_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoEntity item = filteredList.get(position);
        Uri uri = Uri.parse(item.uri);
        ThumbnailLoader.loadThumbnail(context, uri, 220, holder.thumbnail);

        String badgeText;
        if (PhotoEntity.STATUS_PENDING_NO_READING.equals(item.status)) {
            badgeText = "Sem leitura";
        } else if (PhotoEntity.STATUS_PENDING_AMBIGUOUS.equals(item.status)) {
            badgeText = "Ambíguo";
        } else {
            badgeText = "Não encontrado";
        }
        holder.statusBadge.setText(badgeText);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPhotoClick(holder.getAdapterPosition(), item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public PhotoEntity getItem(int position) {
        if (position >= 0 && position < filteredList.size()) {
            return filteredList.get(position);
        }
        return null;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView statusBadge;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.photoThumbnail);
            statusBadge = itemView.findViewById(R.id.photoStatusBadge);
        }
    }
}
