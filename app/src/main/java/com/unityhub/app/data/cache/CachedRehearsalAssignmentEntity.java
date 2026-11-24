package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_rehearsal_assignments",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "rehearsal_id", "created_at"})
    }
)
public class CachedRehearsalAssignmentEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "rehearsal_id") public String rehearsalId = "";
    @NonNull @ColumnInfo(name = "member_id") public String memberId = "";
    @NonNull @ColumnInfo(name = "member_user_id") public String memberUserId = "";
    @NonNull @ColumnInfo(name = "member_display_name") public String memberDisplayName = "Team member";
    @NonNull @ColumnInfo(name = "member_team_role") public String memberTeamRole = "team_member";
    @NonNull @ColumnInfo(name = "response_status") public String responseStatus = "pending";
    @NonNull @ColumnInfo(name = "response_note") public String responseNote = "";
    @NonNull @ColumnInfo(name = "assigned_by") public String assignedBy = "";
    @Nullable @ColumnInfo(name = "responded_at") public String respondedAt;
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
