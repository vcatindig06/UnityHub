package com.unityhub.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class CachedServiceAssignmentDao {
    @Query(
        "SELECT * FROM cached_service_assignments " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND service_plan_id = :servicePlanId " +
        "ORDER BY role_label COLLATE NOCASE ASC, created_at ASC"
    )
    public abstract List<CachedServiceAssignmentEntity> loadAssignments(
        String userId, String organizationId, String servicePlanId
    );

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertAssignments(List<CachedServiceAssignmentEntity> assignments);

    @Query(
        "DELETE FROM cached_service_assignments " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND service_plan_id = :servicePlanId"
    )
    protected abstract void deleteAssignmentsForPlan(
        String userId, String organizationId, String servicePlanId
    );

    @Query("DELETE FROM cached_service_assignments WHERE owner_user_id = :userId")
    protected abstract void deleteAssignmentsForUser(String userId);

    @Transaction
    public void replacePlanAssignments(
        String userId,
        String organizationId,
        String servicePlanId,
        List<CachedServiceAssignmentEntity> assignments
    ) {
        deleteAssignmentsForPlan(userId, organizationId, servicePlanId);
        if (assignments != null && !assignments.isEmpty()) {
            insertAssignments(assignments);
        }
    }

    @Transaction
    public void clearUser(String userId) {
        deleteAssignmentsForUser(userId);
    }
}
