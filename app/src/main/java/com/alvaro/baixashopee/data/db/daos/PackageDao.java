package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alvaro.baixashopee.data.db.entities.PackageEntity;

import java.util.List;

@Dao
public interface PackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PackageEntity> packages);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PackageEntity pkg);

    @Update
    void update(PackageEntity pkg);

    @Query("SELECT * FROM packages WHERE trackingCode = :trackingCode LIMIT 1")
    PackageEntity getByTrackingCode(String trackingCode);

    @Query("SELECT * FROM packages WHERE routeId = :routeId ORDER BY orderIndex ASC")
    List<PackageEntity> getPackagesByRoute(String routeId);

    @Query("SELECT * FROM packages WHERE recipientId = :recipientId ORDER BY orderIndex ASC")
    List<PackageEntity> getPackagesByRecipient(String recipientId);

    @Query("SELECT * FROM packages ORDER BY orderIndex ASC")
    List<PackageEntity> getAll();

    @Query("SELECT COUNT(*) FROM packages WHERE routeId = :routeId")
    int countByRoute(String routeId);

    @Query("DELETE FROM packages WHERE trackingCode = :trackingCode")
    void deleteByTrackingCode(String trackingCode);

    @Query("DELETE FROM packages WHERE routeId = :routeId")
    void deleteByRoute(String routeId);

    @Query("DELETE FROM packages")
    void deleteAll();
}
