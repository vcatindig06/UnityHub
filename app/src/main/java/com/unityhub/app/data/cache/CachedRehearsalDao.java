package com.unityhub.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class CachedRehearsalDao {
    @Query(
        "SELECT * FROM cached_rehearsals " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "ORDER BY rehearsal_date ASC, start_time ASC, title COLLATE NOCASE ASC"
    )
    public abstract List<CachedRehearsalEntity> loadRehearsals(
        String userId, String organizationId
    );

    @Query(
        "SELECT * FROM cached_rehearsal_assignments " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND rehearsal_id = :rehearsalId ORDER BY created_at ASC"
    )
    public abstract List<CachedRehearsalAssignmentEntity> loadAssignments(
        String userId, String organizationId, String rehearsalId
    );

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertRehearsals(List<CachedRehearsalEntity> rehearsals);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertAssignments(List<CachedRehearsalAssignmentEntity> assignments);

    @Query(
        "DELETE FROM cached_rehearsals " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    protected abstract void deleteRehearsalsForOrganization(String userId, String organizationId);

    @Query(
        "DELETE FROM cached_rehearsal_assignments " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    protected abstract void deleteAssignmentsForOrganization(String userId, String organizationId);

    @Query(
        "DELETE FROM cached_rehearsal_assignments " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND rehearsal_id = :rehearsalId"
    )
    protected abstract void deleteAssignmentsForRehearsal(
        String userId, String organizationId, String rehearsalId
    );

    @Query("DELETE FROM cached_rehearsals WHERE owner_user_id = :userId")
    protected abstract void deleteRehearsalsForUser(String userId);

    @Query("DELETE FROM cached_rehearsal_assignments WHERE owner_user_id = :userId")
    protected abstract void deleteAssignmentsForUser(String userId);

    @Transaction
    public void replaceRehearsals(
        String userId,
        String organizationId,
        List<CachedRehearsalEntity> rehearsals
    ) {
        deleteRehearsalsForOrganization(userId, organizationId);
        if (rehearsals != null && !rehearsals.isEmpty()) {
            insertRehearsals(rehearsals);
        }
    }

    @Transaction
    public void replaceRoster(
        String userId,
        String organizationId,
        String rehearsalId,
        List<CachedRehearsalAssignmentEntity> assignments
    ) {
        deleteAssignmentsForRehearsal(userId, organizationId, rehearsalId);
        if (assignments != null && !assignments.isEmpty()) {
            insertAssignments(assignments);
        }
    }

    @Transaction
    public void clearOrganization(String userId, String organizationId) {
        deleteAssignmentsForOrganization(userId, organizationId);
        deleteRehearsalsForOrganization(userId, organizationId);
    }

    @Transaction
    public void clearUser(String userId) {
        deleteAssignmentsForUser(userId);
        deleteRehearsalsForUser(userId);
    }
}
