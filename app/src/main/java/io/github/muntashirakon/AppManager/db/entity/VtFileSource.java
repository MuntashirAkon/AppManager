// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import io.github.muntashirakon.AppManager.scanner.vt.VtScanRepository;

@Entity(tableName = "vt_file_source", foreignKeys = @ForeignKey(
        entity = VtFile.class,
        parentColumns = "id",
        childColumns = "file_id",
        onDelete = ForeignKey.CASCADE
), indices = {
        @Index(name = "index_vt_file_source_file_id", value = {"file_id"}),
        @Index(name = "index_vt_file_source_file_uri", value = {"file_id", "source_uri"}, unique = true)
})
public class VtFileSource {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "file_id")
    public long fileId;

    @NonNull
    @ColumnInfo(name = "source_uri")
    public String sourceUri = "";

    @Nullable
    @ColumnInfo(name = "display_name")
    public String displayName;

    @Nullable
    @ColumnInfo(name = "mime_type")
    public String mimeType;

    @ColumnInfo(name = "last_seen_at")
    public long lastSeenAt;

    @ColumnInfo(name = "is_readable")
    public boolean isReadable;

    @VtScanRepository.FileSource
    @Nullable
    @ColumnInfo(name = "source_type")
    public String sourceType;
}
