// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.IOException;

import androidx.annotation.NonNull;

// Copyright 2017 Zheng Li
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
