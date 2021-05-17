// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

@SuppressWarnings("NotNullFieldNotInitialized")
@Entity(tableName = "file_hash")
public class FileHash {
    @PrimaryKey
    @ColumnInfo(name = "path")
    @NonNull
    public String path;

    @ColumnInfo(name = "hash")
    public String hash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileHash)) return false;
        FileHash fileHash = (FileHash) o;
        return path.equals(fileHash.path) && Objects.equals(hash, fileHash.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, hash);
    }
}
