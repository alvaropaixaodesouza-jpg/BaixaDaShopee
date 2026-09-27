package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alvaro.baixashopee.data.db.entities.RecipientEntity;

import java.util.List;

@Dao
public interface RecipientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RecipientEntity> recipients);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecipientEntity recipient);

    @Update
    void update(RecipientEntity recipient);

    @Query("SELECT * FROM recipients WHERE routeId = :routeId ORDER BY sortOrder ASC")
    List<RecipientEntity> getRecipientsByRoute(String routeId);

    @Query("SELECT * FROM recipients WHERE id = :id LIMIT 1")
    RecipientEntity getById(String id);

    @Query("DELETE FROM recipients WHERE routeId = :routeId")
    void deleteByRoute(String routeId);

    @Query("DELETE FROM recipients WHERE id = :id")
    void deleteById(String id);
}
