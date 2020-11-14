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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class DexClasses implements Closeable {
    private ClassLoader loader;
    private DexFile dexFile;

    public DexClasses(@NonNull Context ctx, @NonNull File apkFile) {
        // ClassLoader
        final File optimizedDexFilePath = ctx.getCodeCacheDir();
        this.loader = new DexClassLoader(apkFile.getAbsolutePath(),
                optimizedDexFilePath.getAbsolutePath(), null,
                ctx.getClassLoader().getParent());
        IOUtils.deleteSilently(optimizedDexFilePath);
        // Load dexClass
        File optimizedFile = null;
        try {
            File cacheDir = ctx.getCacheDir();
            optimizedFile = File.createTempFile("opt_", ".dex", cacheDir);
            dexFile = DexFile.loadDex(apkFile.getPath(), optimizedFile.getPath(), 0);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.deleteSilently(optimizedFile);
        }
    }

    @NonNull
    public List<String> getClassNames() {
        Set<String> classes = new HashSet<>();
        if (dexFile != null) {
            Enumeration<String> enumeration = dexFile.entries();
            String className;
            // Get imports for each class
            while (enumeration.hasMoreElements()) {
                className = enumeration.nextElement();
                try {
                    classes.add(className);
                    classes.addAll(getImports(className));
                } catch (ClassNotFoundException|LinkageError ignore) {
                }
            }
        }
        return new ArrayList<>(classes);
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
