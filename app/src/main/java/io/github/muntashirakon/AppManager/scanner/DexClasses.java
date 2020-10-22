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

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class DexClasses implements Closeable {
    ClassLoader loader;
    DexFile dexFile;
    Context context;
    File apkFile;

    public DexClasses(@NonNull Context ctx, File apkFile) {
        this.context = ctx;
        this.apkFile = apkFile;
        // ClassLoader
        final File optimizedDexFilePath = context.getCodeCacheDir();
        this.loader = new DexClassLoader(this.apkFile.getAbsolutePath(),
                optimizedDexFilePath.getAbsolutePath(), null,
                context.getClassLoader().getParent());
        IOUtils.deleteSilently(optimizedDexFilePath);
        // Load dexClass
        File optimizedFile = null;
        try {
            File cacheDir = context.getCacheDir();
            optimizedFile = File.createTempFile("opt_", ".dex", cacheDir);
            dexFile = DexFile.loadDex(this.apkFile.getPath(), optimizedFile.getPath(), 0);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.deleteSilently(optimizedFile);
        }
    }

    @NonNull
    public List<String> getClassNames() {
        if (dexFile != null) {
            return Collections.list(dexFile.entries());
        } else return Collections.emptyList();
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return loader.loadClass(className);
    }

    public Set<String> getImports(String className) throws ClassNotFoundException {
        return new Reflector(loadClass(className)).getImports();
    }

    public Reflector getReflector(String className) throws ClassNotFoundException {
        return new Reflector(loadClass(className));
    }

    @Override
    public void close() throws IOException {
        dexFile.close();
    }
}
