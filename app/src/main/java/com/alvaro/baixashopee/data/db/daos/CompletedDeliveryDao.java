package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;

import java.util.List;

@Dao
public interface CompletedDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CompletedDeliveryEntity completed);

    @Query("SELECT * FROM completed_deliveries ORDER BY completedAt DESC")
    List<CompletedDeliveryEntity> getAll();

    @Query("SELECT * FROM completed_deliveries WHERE trackingCode = :trackingCode LIMIT 1")
    CompletedDeliveryEntity getByTrackingCode(String trackingCode);

    @Query("SELECT * FROM completed_deliveries WHERE trackingCode LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY completedAt DESC")
    List<CompletedDeliveryEntity> search(String query);

    @Query("SELECT * FROM completed_deliveries ORDER BY completedAt DESC LIMIT 1")
    CompletedDeliveryEntity getLastCompleted();

    @Query("SELECT COUNT(*) FROM completed_deliveries")
    int count();

    @Query("DELETE FROM completed_deliveries WHERE trackingCode = :trackingCode")
    void deleteByTrackingCode(String trackingCode);

    @Query("DELETE FROM completed_deliveries")
    void deleteAll();
}
