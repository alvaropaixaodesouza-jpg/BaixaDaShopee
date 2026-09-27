package com.alvaro.baixashopee.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "photos")
public class PhotoEntity {
    public static final String TYPE_PACKAGE = "PACKAGE";
    public static final String TYPE_HOUSE = "HOUSE";
    public static final String TYPE_UNASSIGNED = "UNASSIGNED";

    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_PENDING_NO_READING = "NO_READING";
    public static final String STATUS_PENDING_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_PENDING_AMBIGUOUS = "AMBIGUOUS";

    @PrimaryKey
    @NonNull
    public String id;
    public String uri;
    public String fileHash;
    public String type;
    public String packageTrackingCode;
    public String houseId;
    public String status;
    public String detectedCode;
    public long createdAt;

    public PhotoEntity(@NonNull String id, String uri, String fileHash, String type,
                       String packageTrackingCode, String houseId, String status,
                       String detectedCode, long createdAt) {
        this.id = id;
        this.uri = uri == null ? "" : uri;
        this.fileHash = fileHash == null ? "" : fileHash;
        this.type = type == null ? TYPE_UNASSIGNED : type;
        this.packageTrackingCode = packageTrackingCode == null ? "" : packageTrackingCode;
        this.houseId = houseId == null ? "" : houseId;
        this.status = status == null ? STATUS_ASSIGNED : status;
        this.detectedCode = detectedCode == null ? "" : detectedCode;
        this.createdAt = createdAt;
    }
}
