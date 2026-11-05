package com.unityhub.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class CachedServicePlanDao {
    @Query(
        "SELECT * FROM cached_service_plans " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "ORDER BY COALESCE(service_date, '9999-12-31') ASC, COALESCE(start_time, '') ASC, title COLLATE NOCASE ASC"
    )
    public abstract List<CachedServicePlanEntity> loadPlans(String userId, String organizationId);

    @Query(
        "SELECT * FROM cached_service_plan_items " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND service_plan_id = :servicePlanId " +
        "ORDER BY item_order ASC, created_at ASC"
    )
    public abstract List<CachedServicePlanItemEntity> loadItems(
        String userId, String organizationId, String servicePlanId
    );

    @Query(
        "SELECT COALESCE(MAX(cached_at), 0) FROM cached_service_plans " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    public abstract long getLastSyncedAtMillis(String userId, String organizationId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertPlans(List<CachedServicePlanEntity> plans);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertItems(List<CachedServicePlanItemEntity> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsertPlan(CachedServicePlanEntity plan);

    @Query(
        "DELETE FROM cached_service_plans " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    protected abstract void deletePlansForOrganization(String userId, String organizationId);

    @Query(
        "DELETE FROM cached_service_plan_items " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    protected abstract void deleteItemsForOrganization(String userId, String organizationId);

    @Query(
        "DELETE FROM cached_service_plan_items " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "AND service_plan_id = :servicePlanId"
    )
    protected abstract void deleteItemsForPlan(String userId, String organizationId, String servicePlanId);

    @Query("DELETE FROM cached_service_plans WHERE owner_user_id = :userId")
    protected abstract void deletePlansForUser(String userId);

    @Query("DELETE FROM cached_service_plan_items WHERE owner_user_id = :userId")
    protected abstract void deleteItemsForUser(String userId);

    @Transaction
    public void replaceOrganization(
        String userId,
        String organizationId,
        List<CachedServicePlanEntity> plans,
        List<CachedServicePlanItemEntity> items
    ) {
        deleteItemsForOrganization(userId, organizationId);
        deletePlansForOrganization(userId, organizationId);
        if (plans != null && !plans.isEmpty()) insertPlans(plans);
        if (items != null && !items.isEmpty()) insertItems(items);
    }

    @Transaction
    public void replacePlanItems(
        CachedServicePlanEntity plan,
        List<CachedServicePlanItemEntity> items
    ) {
        upsertPlan(plan);
        deleteItemsForPlan(plan.ownerUserId, plan.organizationId, plan.id);
        if (items != null && !items.isEmpty()) insertItems(items);
    }

    @Transaction
    public void clearUser(String userId) {
        deleteItemsForUser(userId);
        deletePlansForUser(userId);
    }
}
