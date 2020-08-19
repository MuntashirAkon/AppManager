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

package io.github.muntashirakon.AppManager.server.common;

import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.IOException;

import androidx.annotation.NonNull;

@SuppressWarnings("rawtypes")
public class ParamsFixer {
    @NonNull
    public static Caller wrap(@NonNull Caller caller) {
        Object[] params = caller.getParams();
        if (caller.getParamsType() != null && params != null) {
            Class[] paramsType = caller.getParamsType();
            for (int i = 0; i < params.length; i++) {
                params[i] = marshall(paramsType[i], params[i]);
            }
        }
        return caller;
    }

    @NonNull
    public static Caller unwrap(@NonNull Caller caller) {
        Object[] params = caller.getParams();
        if (caller.getParamsType() != null && params != null) {
            Class[] paramsType = caller.getParamsType();
            for (int i = 0; i < params.length; i++) {
                params[i] = unmarshall(paramsType[i], params[i]);
            }
        }
        return caller;
    }

    private static Object marshall(Class type, Object obj) {
        if (FileDescriptor.class.equals(type) && obj instanceof FileDescriptor) {
            try {
                return ParcelFileDescriptor.dup(((FileDescriptor) obj));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return obj;
    }

    private static Object unmarshall(Class type, Object obj) {
        if (FileDescriptor.class.equals(type) && obj instanceof ParcelFileDescriptor) {
            return ((ParcelFileDescriptor) obj).getFileDescriptor();
        }
        return obj;
    }
}
