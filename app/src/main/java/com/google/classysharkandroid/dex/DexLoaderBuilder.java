/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.classysharkandroid.dex;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import dalvik.system.DexClassLoader;

public class DexLoaderBuilder {
    private static final int BUF_SIZE = 8 * 1024;

    private DexLoaderBuilder() {}

    @NonNull
    public static ClassLoader fromBytes(@NonNull Context context, final byte[] dexBytes) {
        String dexFileName = "internal.dex";
        final File dexInternalStoragePath = new File(context.getDir("dex", Context.MODE_PRIVATE), dexFileName);
        if (!dexInternalStoragePath.exists()) prepareDex(dexBytes, dexInternalStoragePath);

        final File optimizedDexOutputPath = context.getCodeCacheDir();
        ClassLoader loader = new DexClassLoader(dexInternalStoragePath.getAbsolutePath(),
                optimizedDexOutputPath.getAbsolutePath(), null,
                context.getClassLoader().getParent());

        //noinspection ResultOfMethodCallIgnored
        dexInternalStoragePath.delete();
        //noinspection ResultOfMethodCallIgnored
        optimizedDexOutputPath.delete();

        return loader;
    }

    @NonNull
    public static ClassLoader fromFile(@NonNull Context context, @NonNull File dexInternalStoragePath) {
        final File optimizedDexOutputPath = context.getCodeCacheDir();
        ClassLoader loader = new DexClassLoader(dexInternalStoragePath.getAbsolutePath(),
                optimizedDexOutputPath.getAbsolutePath(), null,
                context.getClassLoader().getParent());
        //noinspection ResultOfMethodCallIgnored
        dexInternalStoragePath.delete();
        //noinspection ResultOfMethodCallIgnored
        optimizedDexOutputPath.delete();
        return loader;
    }

    private static void prepareDex(byte[] bytes, File dexInternalStoragePath) {
        BufferedInputStream bis = null;
        OutputStream dexWriter = null;

        try {
            bis = new BufferedInputStream(new ByteArrayInputStream(bytes));
            dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
            byte[] buf = new byte[BUF_SIZE];
            int len;
            while ((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
                dexWriter.write(buf, 0, len);
            }
            dexWriter.close();
            bis.close();
        } catch (IOException e) {
            if (dexWriter != null) {
                try {
                    dexWriter.close();
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
            try {
                bis.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
