package com.unityhub.app.data.cache;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {CachedSongEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class UnityHubCacheDatabase extends RoomDatabase {
    private static volatile UnityHubCacheDatabase instance;

    public abstract CachedSongDao cachedSongDao();

    public static UnityHubCacheDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (UnityHubCacheDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        UnityHubCacheDatabase.class,
                        "unityhub_offline_cache.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
