package com.unityhub.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PendingServiceAssignmentResponseDao {
    @Query(
        "SELECT * FROM pending_service_assignment_responses " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "ORDER BY queued_at ASC"
    )
    List<PendingServiceAssignmentResponseEntity> loadPending(
        String userId, String organizationId
    );

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingServiceAssignmentResponseEntity response);

    @Query(
        "DELETE FROM pending_service_assignment_responses " +
        "WHERE owner_user_id = :userId AND assignment_id = :assignmentId"
    )
    void remove(String userId, String assignmentId);

    @Query(
        "DELETE FROM pending_service_assignment_responses " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    void clearOrganization(String userId, String organizationId);

    @Query("DELETE FROM pending_service_assignment_responses WHERE owner_user_id = :userId")
    void clearUser(String userId);
}
