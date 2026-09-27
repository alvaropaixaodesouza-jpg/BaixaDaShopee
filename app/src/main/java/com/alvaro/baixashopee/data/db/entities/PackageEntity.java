package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "packages")
public class PackageEntity {
    @PrimaryKey
    @NonNull
    public String trackingCode;
    public String recipientId;
    public String routeId;
    public String customerName;
    public String address;
    public String atId;
    public String stop;
    public String neighborhood;
    public String city;
    public String postalCode;
    public double destinationLatitude;
    public double destinationLongitude;
    public String status;
    public String occurrenceType;
    public String occurrenceNote;
    public String packagePhotoUri;
    public String facadePhotoUri;
    public String houseId;
    public double latitude;
    public double longitude;
    public float locationAccuracy;
    public long photographedAt;
    public String reportUri;
    public int orderIndex;

    public PackageEntity(@NonNull String trackingCode, String recipientId, String routeId,
                         String customerName, String address, String atId, String stop,
                         String neighborhood, String city, String postalCode,
                         double destinationLatitude, double destinationLongitude,
                         String status, String occurrenceType, String occurrenceNote,
                         String packagePhotoUri, String facadePhotoUri, String houseId,
                         double latitude, double longitude, float locationAccuracy,
                         long photographedAt, String reportUri, int orderIndex) {
        this.trackingCode = trackingCode;
        this.recipientId = recipientId == null ? "" : recipientId;
        this.routeId = routeId == null ? "" : routeId;
        this.customerName = customerName == null ? "" : customerName;
        this.address = address == null ? "" : address;
        this.atId = atId == null ? "" : atId;
        this.stop = stop == null ? "" : stop;
        this.neighborhood = neighborhood == null ? "" : neighborhood;
        this.city = city == null ? "" : city;
        this.postalCode = postalCode == null ? "" : postalCode;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.status = status == null || status.isEmpty() ? "PENDENTE" : status;
        this.occurrenceType = occurrenceType == null ? "" : occurrenceType;
        this.occurrenceNote = occurrenceNote == null ? "" : occurrenceNote;
        this.packagePhotoUri = packagePhotoUri == null ? "" : packagePhotoUri;
        this.facadePhotoUri = facadePhotoUri == null ? "" : facadePhotoUri;
        this.houseId = houseId == null ? "" : houseId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.photographedAt = photographedAt;
        this.reportUri = reportUri == null ? "" : reportUri;
        this.orderIndex = orderIndex;
    }
}
