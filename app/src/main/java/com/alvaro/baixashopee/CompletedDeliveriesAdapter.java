package com.alvaro.baixashopee;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;

import java.util.ArrayList;
import java.util.List;

public class CompletedDeliveriesAdapter extends RecyclerView.Adapter<CompletedDeliveriesAdapter.ViewHolder> {
    public interface OnUndoListener {
        void onUndo(CompletedDeliveryEntity item);
    }

    private final Context context;
    private final OnUndoListener undoListener;
    private List<CompletedDeliveryEntity> items = new ArrayList<>();

    public CompletedDeliveriesAdapter(Context context, OnUndoListener undoListener) {
        this.context = context;
        this.undoListener = undoListener;
    }

    public void submitList(List<CompletedDeliveryEntity> list) {
        this.items = list == null ? new ArrayList<>() : new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_completed_delivery, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CompletedDeliveryEntity item = items.get(position);
        holder.code.setText(item.trackingCode);
        holder.customer.setText(item.customerName);
        holder.address.setText(item.address);

        holder.undoButton.setOnClickListener(v -> {
            if (undoListener != null) {
                undoListener.onUndo(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView code;
        TextView customer;
        TextView address;
        Button undoButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.completedCode);
            customer = itemView.findViewById(R.id.completedCustomer);
            address = itemView.findViewById(R.id.completedAddress);
            undoButton = itemView.findViewById(R.id.completedUndoButton);
        }
    }
}
