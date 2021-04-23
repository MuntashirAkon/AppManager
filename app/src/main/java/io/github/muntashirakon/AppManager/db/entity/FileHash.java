/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
