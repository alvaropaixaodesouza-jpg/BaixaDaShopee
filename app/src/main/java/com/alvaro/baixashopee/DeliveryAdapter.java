package com.alvaro.baixashopee;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.ViewHolder> {
    public interface OnDeliveryClickListener {
        void onClick(int position, Delivery delivery);
        void onMenu(int position, Delivery delivery);
    }

    private final Context context;
    private final HouseStore houseStore;
    private final OnDeliveryClickListener listener;
    private List<Delivery> deliveries = new ArrayList<>();
    private int selectedIndex = -1;

    public DeliveryAdapter(Context context, OnDeliveryClickListener listener) {
        this.context = context;
        this.houseStore = new HouseStore(context);
        this.listener = listener;
    }

    public void submit(List<Delivery> items, int selectedIndex) {
        this.deliveries = items == null ? new ArrayList<>() : new ArrayList<>(items);
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_delivery, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Delivery item = deliveries.get(position);
        holder.position.setText(String.valueOf(position + 1));
        holder.code.setText(item.trackingCode);

        House house = item.houseId != null && !item.houseId.isEmpty() ? houseStore.findById(item.houseId) : null;
        String name = item.customerName;
        if (name.isEmpty() || "-".equals(name.trim())) {
            name = house != null && !house.residents.isEmpty() ? house.residents : "";
        }
        String address = house != null && !house.address.isEmpty() ? house.address : item.address;
        String facade = house == null ? item.facadePhotoUri : house.facadePhotoUri;

        holder.name.setText(name);
        holder.name.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        holder.address.setText(address);
        holder.address.setVisibility(address.isEmpty() ? View.GONE : View.VISIBLE);

        holder.photoStatus.setText(
                (item.hasOccurrence() ? "⚠ " : "") +
                (item.packagePhotoUri.isEmpty() ? "📦○" : "📦✓") + " " +
                (facade.isEmpty() ? "🏠○" : "🏠✓")
        );

        holder.itemView.setBackgroundResource(position == selectedIndex
                ? R.drawable.delivery_card_selected : R.drawable.delivery_card);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onClick(pos, deliveries.get(pos));
            }
        });

        holder.menu.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onMenu(pos, deliveries.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return deliveries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView position;
        TextView code;
        TextView name;
        TextView address;
        TextView photoStatus;
        TextView menu;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            position = itemView.findViewById(R.id.itemPosition);
            code = itemView.findViewById(R.id.itemCode);
            name = itemView.findViewById(R.id.itemName);
            address = itemView.findViewById(R.id.itemAddress);
            photoStatus = itemView.findViewById(R.id.itemPhotoStatus);
            menu = itemView.findViewById(R.id.itemMenu);
        }
    }
}
