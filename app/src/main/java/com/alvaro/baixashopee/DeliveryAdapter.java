package com.alvaro.baixashopee;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class DeliveryAdapter extends BaseAdapter {
    public interface MenuListener {
        void onMenu(int position);
    }

    private final LayoutInflater inflater;
    private final HouseStore houseStore;
    private final MenuListener menuListener;
    private List<Delivery> deliveries = new ArrayList<>();
    private int selectedIndex = -1;

    public DeliveryAdapter(Context context, MenuListener menuListener) {
        inflater = LayoutInflater.from(context);
        houseStore = new HouseStore(context);
        this.menuListener = menuListener;
    }

    public void submit(List<Delivery> items, int selectedIndex) {
        this.deliveries = items;
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return deliveries.size(); }
    @Override public Delivery getItem(int position) { return deliveries.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        Holder holder;
        if (view == null) {
            view = inflater.inflate(R.layout.item_delivery, parent, false);
            holder = new Holder();
            holder.position = view.findViewById(R.id.itemPosition);
            holder.code = view.findViewById(R.id.itemCode);
            holder.name = view.findViewById(R.id.itemName);
            holder.address = view.findViewById(R.id.itemAddress);
            holder.photoStatus = view.findViewById(R.id.itemPhotoStatus);
            holder.menu = view.findViewById(R.id.itemMenu);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        Delivery item = getItem(position);
        holder.position.setText(String.valueOf(position + 1));
        holder.code.setText(item.trackingCode);
        House house = houseStore.findById(item.houseId);
        String name = house != null && !house.residents.isEmpty() ? house.residents : item.customerName;
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
        holder.menu.setOnClickListener(v -> {
            if (menuListener != null) menuListener.onMenu(position);
        });
        view.setBackgroundColor(position == selectedIndex ? Color.rgb(255, 240, 232) : Color.WHITE);
        return view;
    }

    private static final class Holder {
        TextView position;
        TextView code;
        TextView name;
        TextView address;
        TextView photoStatus;
        TextView menu;
    }
}
