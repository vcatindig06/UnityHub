package com.unityhub.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class CachedSongDao {
    @Query(
        "SELECT * FROM cached_songs " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId " +
        "ORDER BY title COLLATE NOCASE ASC"
    )
    public abstract List<CachedSongEntity> loadSongs(String userId, String organizationId);

    @Query(
        "SELECT COALESCE(MAX(cached_at), 0) FROM cached_songs " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    public abstract long getLastSyncedAtMillis(String userId, String organizationId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertAll(List<CachedSongEntity> songs);

    @Query("DELETE FROM cached_songs WHERE owner_user_id = :userId")
    protected abstract void deleteAllForUser(String userId);

    @Query(
        "DELETE FROM cached_songs " +
        "WHERE owner_user_id = :userId AND organization_id = :organizationId"
    )
    protected abstract void deleteForOrganization(String userId, String organizationId);

    @Transaction
    public void replaceOrganization(
        String userId,
        String organizationId,
        List<CachedSongEntity> songs
    ) {
        deleteForOrganization(userId, organizationId);
        if (songs != null && !songs.isEmpty()) {
            insertAll(songs);
        }
    }

    @Transaction
    public void clearUser(String userId) {
        deleteAllForUser(userId);
    }
}
