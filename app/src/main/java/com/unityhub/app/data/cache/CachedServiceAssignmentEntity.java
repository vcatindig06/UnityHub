package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_service_assignments",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "service_plan_id", "role_label"}),
        @Index(value = {"owner_user_id", "organization_id", "service_plan_id", "created_at"})
    }
)
public class CachedServiceAssignmentEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "service_plan_id") public String servicePlanId = "";
    @NonNull @ColumnInfo(name = "member_id") public String memberId = "";
    @ColumnInfo(name = "member_is_current_user") public boolean memberIsCurrentUser;
    @NonNull @ColumnInfo(name = "member_display_name") public String memberDisplayName = "Team member";
    @NonNull @ColumnInfo(name = "role_label") public String roleLabel = "";
    @NonNull @ColumnInfo(name = "response_status") public String responseStatus = "pending";
    @NonNull @ColumnInfo(name = "response_note", defaultValue = "''") public String responseNote = "";
    @Nullable @ColumnInfo(name = "responded_at") public String respondedAt;
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
