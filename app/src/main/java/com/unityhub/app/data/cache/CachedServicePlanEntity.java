package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_service_plans",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "service_date"})
    }
)
public class CachedServicePlanEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "title") public String title = "";
    @Nullable @ColumnInfo(name = "service_date") public String serviceDate;
    @Nullable @ColumnInfo(name = "start_time") public String startTime;
    @NonNull @ColumnInfo(name = "notes") public String notes = "";
    @NonNull @ColumnInfo(name = "status") public String status = "draft";
    @NonNull @ColumnInfo(name = "created_by") public String createdBy = "";
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @NonNull @ColumnInfo(name = "updated_at") public String updatedAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
