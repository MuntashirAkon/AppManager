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

package io.github.muntashirakon.AppManager.crypto;

import java.io.File;

import androidx.annotation.NonNull;

public class DummyCrypto implements Crypto {
    File[] newFiles;

    @Override
    public boolean encrypt(@NonNull File[] files) {
        // Have to return new files to be processed further
        newFiles = files;
        return true;
    }

    @Override
    public boolean decrypt(@NonNull File[] files) {
        // The new files will be deleted, so don't send
        newFiles = null;
        return true;
    }

    @NonNull
    @Override
    public File[] getNewFiles() {
        if (newFiles == null) return new File[0];
        return newFiles;
    }

    @Override
    public void close() {
    }
}
