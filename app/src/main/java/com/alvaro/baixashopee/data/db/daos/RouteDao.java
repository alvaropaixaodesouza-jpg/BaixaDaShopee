package com.alvaro.baixashopee.data.db.daos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alvaro.baixashopee.data.db.entities.RouteEntity;

import java.util.List;

@Dao
public interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RouteEntity route);

    @Update
    void update(RouteEntity route);

    @Query("SELECT * FROM routes WHERE isActive = 1 LIMIT 1")
    RouteEntity getActiveRoute();

    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    List<RouteEntity> getAllRoutes();

    @Query("UPDATE routes SET isActive = 0")
    void deactivateAll();

    @Query("DELETE FROM routes WHERE id = :routeId")
    void deleteRoute(String routeId);
}
