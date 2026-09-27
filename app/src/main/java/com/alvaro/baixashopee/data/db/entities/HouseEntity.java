package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "houses")
public class HouseEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String label;
    public String residents;
    public String address;
    public String mapUri;
    public String facadePhotoUri;
    public String notes;
    public double latitude;
    public double longitude;
    public float locationAccuracy;
    public long lastVisitedAt;
    public String normalizedAddress;
    public long createdAt;

    public HouseEntity(@NonNull String id, String label, String residents, String address,
                       String mapUri, String facadePhotoUri, String notes,
                       double latitude, double longitude, float locationAccuracy,
                       long lastVisitedAt, String normalizedAddress, long createdAt) {
        this.id = id;
        this.label = label == null ? "" : label;
        this.residents = residents == null ? "" : residents;
        this.address = address == null ? "" : address;
        this.mapUri = mapUri == null ? "" : mapUri;
        this.facadePhotoUri = facadePhotoUri == null ? "" : facadePhotoUri;
        this.notes = notes == null ? "" : notes;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.lastVisitedAt = lastVisitedAt;
        this.normalizedAddress = normalizedAddress == null ? "" : normalizedAddress;
        this.createdAt = createdAt;
    }
}
