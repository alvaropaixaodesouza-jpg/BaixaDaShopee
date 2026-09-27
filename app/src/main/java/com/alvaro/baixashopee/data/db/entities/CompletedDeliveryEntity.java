package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "completed_deliveries")
public class CompletedDeliveryEntity {
    @PrimaryKey
    @NonNull
    public String trackingCode;
    public String customerName;
    public String address;
    public String atId;
    public String stop;
    public String neighborhood;
    public String city;
    public String postalCode;
    public String houseId;
    public String packagePhotoUri;
    public String facadePhotoUri;
    public String reportUri;
    public long completedAt;
    public int originalIndex;
    public String routeId;

    public CompletedDeliveryEntity(@NonNull String trackingCode, String customerName, String address,
                                   String atId, String stop, String neighborhood, String city,
                                   String postalCode, String houseId, String packagePhotoUri,
                                   String facadePhotoUri, String reportUri, long completedAt,
                                   int originalIndex, String routeId) {
        this.trackingCode = trackingCode;
        this.customerName = customerName == null ? "" : customerName;
        this.address = address == null ? "" : address;
        this.atId = atId == null ? "" : atId;
        this.stop = stop == null ? "" : stop;
        this.neighborhood = neighborhood == null ? "" : neighborhood;
        this.city = city == null ? "" : city;
        this.postalCode = postalCode == null ? "" : postalCode;
        this.houseId = houseId == null ? "" : houseId;
        this.packagePhotoUri = packagePhotoUri == null ? "" : packagePhotoUri;
        this.facadePhotoUri = facadePhotoUri == null ? "" : facadePhotoUri;
        this.reportUri = reportUri == null ? "" : reportUri;
        this.completedAt = completedAt;
        this.originalIndex = originalIndex;
        this.routeId = routeId == null ? "" : routeId;
    }
}
