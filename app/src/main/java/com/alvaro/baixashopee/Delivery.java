package com.alvaro.baixashopee;

import org.json.JSONException;
import org.json.JSONObject;

public class Delivery {
    public static final String STATUS_PENDING = "PENDENTE";
    public static final String STATUS_OCCURRENCE = "OCORRENCIA";

    public final String trackingCode;
    public final String customerName;
    public final String address;
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

    public Delivery(String trackingCode, String customerName, String address) {
        this(trackingCode, customerName, address, "", "", "", "", "",
                0, 0, STATUS_PENDING, "", "", "", "", "", 0, 0, 0, 0, "");
    }

    public Delivery withRouteData(String atId, String stop, String neighborhood, String city,
                                  String postalCode, double destinationLatitude,
                                  double destinationLongitude) {
        this.atId = safe(atId);
        this.stop = safe(stop);
        this.neighborhood = safe(neighborhood);
        this.city = safe(city);
        this.postalCode = safe(postalCode);
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        return this;
    }

    public Delivery(
            String trackingCode,
            String customerName,
            String address,
            String atId,
            String stop,
            String neighborhood,
            String city,
            String postalCode,
            double destinationLatitude,
            double destinationLongitude,
            String status,
            String occurrenceType,
            String occurrenceNote,
            String packagePhotoUri,
            String facadePhotoUri,
            String houseId,
            double latitude,
            double longitude,
            float locationAccuracy,
            long photographedAt,
            String reportUri
    ) {
        this.trackingCode = TrackingCode.clean(trackingCode);
        this.customerName = safe(customerName);
        this.address = safe(address);
        this.atId = safe(atId);
        this.stop = safe(stop);
        this.neighborhood = safe(neighborhood);
        this.city = safe(city);
        this.postalCode = safe(postalCode);
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.status = safe(status).isEmpty() ? STATUS_PENDING : safe(status);
        this.occurrenceType = safe(occurrenceType);
        this.occurrenceNote = safe(occurrenceNote);
        this.packagePhotoUri = safe(packagePhotoUri);
        this.facadePhotoUri = safe(facadePhotoUri);
        this.houseId = safe(houseId);
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.photographedAt = photographedAt;
        this.reportUri = safe(reportUri);
    }

    public String id() {
        return TrackingCode.stableId(trackingCode);
    }

    public String numericCode() {
        return TrackingCode.onlyDigits(trackingCode);
    }

    public boolean hasLocation() {
        return latitude != 0 || longitude != 0;
    }

    public boolean hasDestinationLocation() {
        return destinationLatitude != 0 || destinationLongitude != 0;
    }

    public boolean hasOccurrence() {
        return STATUS_OCCURRENCE.equals(status) && !occurrenceType.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("trackingCode", trackingCode);
        object.put("customerName", customerName);
        object.put("address", address);
        object.put("atId", atId);
        object.put("stop", stop);
        object.put("neighborhood", neighborhood);
        object.put("city", city);
        object.put("postalCode", postalCode);
        object.put("destinationLatitude", destinationLatitude);
        object.put("destinationLongitude", destinationLongitude);
        object.put("status", status);
        object.put("occurrenceType", occurrenceType);
        object.put("occurrenceNote", occurrenceNote);
        object.put("packagePhotoUri", packagePhotoUri);
        object.put("facadePhotoUri", facadePhotoUri);
        object.put("houseId", houseId);
        object.put("latitude", latitude);
        object.put("longitude", longitude);
        object.put("locationAccuracy", locationAccuracy);
        object.put("photographedAt", photographedAt);
        object.put("reportUri", reportUri);
        return object;
    }

    public static Delivery fromJson(JSONObject object) {
        return new Delivery(
                object.optString("trackingCode"),
                object.optString("customerName"),
                object.optString("address"),
                object.optString("atId"),
                object.optString("stop"),
                object.optString("neighborhood"),
                object.optString("city"),
                object.optString("postalCode"),
                object.optDouble("destinationLatitude", 0),
                object.optDouble("destinationLongitude", 0),
                object.optString("status", STATUS_PENDING),
                object.optString("occurrenceType"),
                object.optString("occurrenceNote"),
                object.optString("packagePhotoUri"),
                object.optString("facadePhotoUri"),
                object.optString("houseId"),
                object.optDouble("latitude", 0),
                object.optDouble("longitude", 0),
                (float) object.optDouble("locationAccuracy", 0),
                object.optLong("photographedAt", 0),
                object.optString("reportUri")
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
