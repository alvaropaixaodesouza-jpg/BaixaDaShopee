package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routes")
public class RouteEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public long importedAt;
    public boolean isActive;
    public int totalPackages;

    public RouteEntity(@NonNull String id, String name, long importedAt, boolean isActive, int totalPackages) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.importedAt = importedAt;
        this.isActive = isActive;
        this.totalPackages = totalPackages;
    }
}
