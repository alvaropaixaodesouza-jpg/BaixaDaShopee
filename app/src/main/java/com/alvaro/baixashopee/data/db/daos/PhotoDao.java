package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alvaro.baixashopee.data.db.entities.PhotoEntity;

import java.util.List;

@Dao
public interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PhotoEntity photo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PhotoEntity> photos);

    @Update
    void update(PhotoEntity photo);

    @Query("SELECT * FROM photos WHERE packageTrackingCode = :trackingCode ORDER BY createdAt DESC")
    List<PhotoEntity> getByPackage(String trackingCode);

    @Query("SELECT * FROM photos WHERE houseId = :houseId ORDER BY createdAt DESC")
    List<PhotoEntity> getByHouse(String houseId);

    @Query("SELECT * FROM photos WHERE status != 'ASSIGNED' ORDER BY createdAt DESC")
    List<PhotoEntity> getUnassignedPhotos();

    @Query("SELECT * FROM photos WHERE status = :status ORDER BY createdAt DESC")
    List<PhotoEntity> getByStatus(String status);

    @Query("SELECT * FROM photos WHERE fileHash = :hash LIMIT 1")
    PhotoEntity getByHash(String hash);

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    PhotoEntity getByUri(String uri);

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    PhotoEntity getById(String id);

    @Query("SELECT COUNT(*) FROM photos WHERE status != 'ASSIGNED'")
    int countUnassigned();

    @Query("DELETE FROM photos WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM photos WHERE type = 'PACKAGE'")
    void deletePackagePhotos();
}
