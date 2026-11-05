package com.unityhub.app.data.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "cached_songs",
    primaryKeys = {"id", "owner_user_id"},
    indices = {
        @Index(value = {"owner_user_id", "organization_id", "title"})
    }
)
public class CachedSongEntity {
    @NonNull @ColumnInfo(name = "id") public String id = "";
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId = "";
    @NonNull @ColumnInfo(name = "organization_id") public String organizationId = "";
    @NonNull @ColumnInfo(name = "title") public String title = "";
    @NonNull @ColumnInfo(name = "artist") public String artist = "";
    @NonNull @ColumnInfo(name = "key_signature") public String keySignature = "";
    @ColumnInfo(name = "tempo") public Integer tempo;
    @NonNull @ColumnInfo(name = "time_signature") public String timeSignature = "";
    @NonNull @ColumnInfo(name = "chordpro") public String chordpro = "";
    @NonNull @ColumnInfo(name = "notes") public String notes = "";
    @NonNull @ColumnInfo(name = "created_by") public String createdBy = "";
    @NonNull @ColumnInfo(name = "updated_by") public String updatedBy = "";
    @NonNull @ColumnInfo(name = "created_at") public String createdAt = "";
    @NonNull @ColumnInfo(name = "updated_at") public String updatedAt = "";
    @ColumnInfo(name = "cached_at") public long cachedAt;
}
