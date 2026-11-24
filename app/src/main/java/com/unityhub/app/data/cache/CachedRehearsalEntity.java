package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_rehearsals",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "rehearsal_date"})
    }
)
public class CachedRehearsalEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "title") public String title = "";
    @NonNull @ColumnInfo(name = "rehearsal_date") public String rehearsalDate = "";
    @NonNull @ColumnInfo(name = "start_time") public String startTime = "";
    @Nullable @ColumnInfo(name = "end_time") public String endTime;
    @NonNull @ColumnInfo(name = "location") public String location = "";
    @NonNull @ColumnInfo(name = "notes") public String notes = "";
    @NonNull @ColumnInfo(name = "status") public String status = "scheduled";
    @Nullable @ColumnInfo(name = "service_plan_id") public String servicePlanId;
    @NonNull @ColumnInfo(name = "created_by") public String createdBy = "";
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @NonNull @ColumnInfo(name = "updated_at") public String updatedAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
