package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "pending_service_assignment_responses",
    primaryKeys = {"assignment_id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id"})
    }
)
public class PendingServiceAssignmentResponseEntity {
    @NonNull @ColumnInfo(name = "assignment_id") public String assignmentId = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "member_id") public String memberId = "";
    @NonNull @ColumnInfo(name = "response_status") public String responseStatus = "";
    @NonNull @ColumnInfo(name = "response_note", defaultValue = "''") public String responseNote = "";
    @ColumnInfo(name = "queued_at") public long queuedAt;
}
