package com.unityhub.app.data.cache;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        CachedSongEntity.class,
        CachedServicePlanEntity.class,
        CachedServicePlanItemEntity.class,
        CachedServiceAssignmentEntity.class
    },
    version = 3,
    exportSchema = false
)
public abstract class UnityHubCacheDatabase extends RoomDatabase {
    private static volatile UnityHubCacheDatabase instance;

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS cached_service_plans (" +
                "id TEXT NOT NULL, owner_user_id TEXT NOT NULL, organization_id TEXT NOT NULL, " +
                "title TEXT NOT NULL, service_date TEXT, start_time TEXT, notes TEXT NOT NULL, " +
                "status TEXT NOT NULL, created_by TEXT NOT NULL, created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, cached_at INTEGER NOT NULL, " +
                "PRIMARY KEY(id, owner_user_id))"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_service_plans_owner_user_id_organization_id_service_date " +
                "ON cached_service_plans(owner_user_id, organization_id, service_date)"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS cached_service_plan_items (" +
                "id TEXT NOT NULL, owner_user_id TEXT NOT NULL, organization_id TEXT NOT NULL, " +
                "service_plan_id TEXT NOT NULL, song_id TEXT, title TEXT NOT NULL, item_type TEXT NOT NULL, " +
                "chordpro_snapshot TEXT NOT NULL, item_order INTEGER NOT NULL, duration_minutes INTEGER, " +
                "notes TEXT NOT NULL, created_at TEXT NOT NULL, cached_at INTEGER NOT NULL, " +
                "PRIMARY KEY(id, owner_user_id))"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_service_plan_items_owner_user_id_organization_id_service_plan_id_item_order " +
                "ON cached_service_plan_items(owner_user_id, organization_id, service_plan_id, item_order)"
            );
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS cached_service_assignments (" +
                "id TEXT NOT NULL, owner_user_id TEXT NOT NULL, organization_id TEXT NOT NULL, " +
                "service_plan_id TEXT NOT NULL, member_id TEXT NOT NULL, member_user_id TEXT, " +
                "member_display_name TEXT NOT NULL, member_team_role TEXT NOT NULL, role_label TEXT NOT NULL, " +
                "response_status TEXT NOT NULL, response_note TEXT NOT NULL, assigned_by TEXT NOT NULL, " +
                "responded_at TEXT, created_at TEXT NOT NULL, cached_at INTEGER NOT NULL, " +
                "PRIMARY KEY(id, owner_user_id))"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_service_assignments_owner_user_id_organization_id_service_plan_id_role_label " +
                "ON cached_service_assignments(owner_user_id, organization_id, service_plan_id, role_label)"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_cached_service_assignments_owner_user_id_organization_id_service_plan_id_created_at " +
                "ON cached_service_assignments(owner_user_id, organization_id, service_plan_id, created_at)"
            );
        }
    };

    public abstract CachedSongDao cachedSongDao();
    public abstract CachedServicePlanDao cachedServicePlanDao();
    public abstract CachedServiceAssignmentDao cachedServiceAssignmentDao();

    public static UnityHubCacheDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (UnityHubCacheDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        UnityHubCacheDatabase.class,
                        "unityhub_offline_cache.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return instance;
    }
}
