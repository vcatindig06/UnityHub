package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_service_plan_items",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "service_plan_id", "item_order"})
    }
)
public class CachedServicePlanItemEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "service_plan_id") public String servicePlanId = "";
    @Nullable @ColumnInfo(name = "song_id") public String songId;
    @NonNull @ColumnInfo(name = "title") public String title = "";
    @NonNull @ColumnInfo(name = "item_type") public String itemType = "other";
    @NonNull @ColumnInfo(name = "chordpro_snapshot") public String chordproSnapshot = "";
    @ColumnInfo(name = "item_order") public int itemOrder;
    @Nullable @ColumnInfo(name = "duration_minutes") public Integer durationMinutes;
    @NonNull @ColumnInfo(name = "notes") public String notes = "";
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
