package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alvaro.baixashopee.data.db.entities.HouseEntity;

import java.util.List;

@Dao
public interface HouseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HouseEntity house);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HouseEntity> houses);

    @Update
    void update(HouseEntity house);

    @Query("SELECT * FROM houses ORDER BY lastVisitedAt DESC, createdAt DESC")
    List<HouseEntity> getAll();

    @Query("SELECT * FROM houses WHERE id = :id LIMIT 1")
    HouseEntity getById(String id);

    @Query("SELECT * FROM houses WHERE normalizedAddress = :normalizedAddress LIMIT 1")
    HouseEntity getByNormalizedAddress(String normalizedAddress);

    @Query("SELECT * FROM houses WHERE label LIKE '%' || :query || '%' OR residents LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY lastVisitedAt DESC")
    List<HouseEntity> search(String query);

    @Query("SELECT COUNT(*) FROM houses")
    int count();

    @Query("DELETE FROM houses WHERE id = :id")
    void deleteById(String id);
}
