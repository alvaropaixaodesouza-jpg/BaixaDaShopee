package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recipients")
public class RecipientEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String routeId;
    public String name;
    public boolean isAllUppercase;
    public int sortOrder;
    public String neighborhood;

    public RecipientEntity(@NonNull String id, String routeId, String name, boolean isAllUppercase, int sortOrder, String neighborhood) {
        this.id = id;
        this.routeId = routeId == null ? "" : routeId;
        this.name = name == null ? "" : name;
        this.isAllUppercase = isAllUppercase;
        this.sortOrder = sortOrder;
        this.neighborhood = neighborhood == null ? "" : neighborhood;
    }
}
