/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.types;

import java.io.File;

import androidx.annotation.NonNull;

/**
 * Start with a new file, delete old one if existed
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class FreshFile extends PrivilegedFile {
    public FreshFile(@NonNull String pathname) {
        super(pathname);
        delete();
    }

    public FreshFile(@NonNull String parent, @NonNull String child) {
        super(parent, child);
        delete();
    }

    public FreshFile(@NonNull File parent, @NonNull String child) {
        super(parent, child);
        delete();
    }
}
