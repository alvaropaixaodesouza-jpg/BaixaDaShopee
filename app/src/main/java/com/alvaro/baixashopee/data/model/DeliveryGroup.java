package com.alvaro.baixashopee.data.model;

import com.alvaro.baixashopee.Delivery;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa um destinatário e todos os seus pacotes.
 * Pacotes da mesma pessoa devem permanecer sempre consecutivos na fila.
 */
public class DeliveryGroup {
    private final String recipientKey;
    private final String recipientName;
    private final boolean isAllUppercase;
    private final List<Delivery> packages;
    private String neighborhood;

    public DeliveryGroup(String recipientKey, String recipientName) {
        this.recipientKey = recipientKey;
        this.recipientName = recipientName == null ? "" : recipientName;
        this.isAllUppercase = RecipientNameHelper.isAllUppercase(this.recipientName);
        this.packages = new ArrayList<>();
        this.neighborhood = "";
    }

    public String getRecipientKey() {
        return recipientKey;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public boolean isAllUppercase() {
        return isAllUppercase;
    }

    public List<Delivery> getPackages() {
        return packages;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void addPackage(Delivery delivery) {
        if (delivery == null) return;
        packages.add(delivery);
        if (neighborhood.isEmpty() && delivery.neighborhood != null && !delivery.neighborhood.isEmpty()) {
            neighborhood = delivery.neighborhood;
        }
    }

    public int size() {
        return packages.size();
    }

    public String getDisplayAddress() {
        if (!packages.isEmpty() && packages.get(0).address != null) {
            return packages.get(0).address;
        }
        return "";
    }
}
