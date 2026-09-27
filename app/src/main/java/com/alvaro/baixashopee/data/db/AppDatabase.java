package com.alvaro.baixashopee.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.alvaro.baixashopee.data.db.daos.CompletedDeliveryDao;
import com.alvaro.baixashopee.data.db.daos.HouseDao;
import com.alvaro.baixashopee.data.db.daos.PackageDao;
import com.alvaro.baixashopee.data.db.daos.PhotoDao;
import com.alvaro.baixashopee.data.db.daos.RecipientDao;
import com.alvaro.baixashopee.data.db.daos.RouteDao;
import com.alvaro.baixashopee.data.db.entities.CompletedDeliveryEntity;
import com.alvaro.baixashopee.data.db.entities.HouseEntity;
import com.alvaro.baixashopee.data.db.entities.PackageEntity;
import com.alvaro.baixashopee.data.db.entities.PhotoEntity;
import com.alvaro.baixashopee.data.db.entities.RecipientEntity;
import com.alvaro.baixashopee.data.db.entities.RouteEntity;

@Database(
    entities = {
        RouteEntity.class,
        RecipientEntity.class,
        PackageEntity.class,
        HouseEntity.class,
        CompletedDeliveryEntity.class,
        PhotoEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "baixas_pacote.db";
    private static volatile AppDatabase INSTANCE;

    public abstract RouteDao routeDao();
    public abstract RecipientDao recipientDao();
    public abstract PackageDao packageDao();
    public abstract HouseDao houseDao();
    public abstract CompletedDeliveryDao completedDeliveryDao();
    public abstract PhotoDao photoDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // NÃO usar fallbackToDestructiveMigration!
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
