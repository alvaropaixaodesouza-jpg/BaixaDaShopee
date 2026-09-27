package com.alvaro.baixashopee;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alvaro.baixashopee.photo.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

public class HouseAdapter extends RecyclerView.Adapter<HouseAdapter.ViewHolder> {
    public interface OnHouseClickListener {
        void onEdit(House house);
    }

    private final Context context;
    private final OnHouseClickListener listener;
    private List<House> items = new ArrayList<>();

    public HouseAdapter(Context context, OnHouseClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<House> list) {
        this.items = list == null ? new ArrayList<>() : new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_house, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        House house = items.get(position);
        holder.displayName.setText(house.displayName());
        holder.address.setText(house.address.isEmpty() ? "Endereço não cadastrado" : house.address);

        if (!house.residents.isEmpty()) {
            holder.residents.setVisibility(View.VISIBLE);
            holder.residents.setText("Moradores: " + house.residents);
        } else {
            holder.residents.setVisibility(View.GONE);
        }

        if (!house.facadePhotoUri.isEmpty()) {
            holder.thumbnail.setVisibility(View.VISIBLE);
            ThumbnailLoader.loadThumbnail(context, Uri.parse(house.facadePhotoUri), 120, holder.thumbnail);
        } else {
            holder.thumbnail.setImageResource(R.drawable.chip_neutral);
        }

        holder.editBtn.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(house);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(house);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView displayName;
        TextView address;
        TextView residents;
        Button editBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.houseThumbnail);
            displayName = itemView.findViewById(R.id.houseDisplayName);
            address = itemView.findViewById(R.id.houseAddress);
            residents = itemView.findViewById(R.id.houseResidents);
            editBtn = itemView.findViewById(R.id.houseEditBtn);
        }
    }
}
