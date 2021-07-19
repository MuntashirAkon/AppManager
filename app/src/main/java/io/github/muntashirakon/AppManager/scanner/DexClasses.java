// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class DexClasses implements Closeable {
    private final ClassLoader loader;
    private DexFile dexFile;

    public DexClasses(@NonNull Context ctx, @NonNull File apkFile) {
        // ClassLoader
        final File optimizedDexFilePath = ctx.getCodeCacheDir();
        this.loader = new DexClassLoader(apkFile.getAbsolutePath(),
                optimizedDexFilePath.getAbsolutePath(), null,
                ctx.getClassLoader().getParent());
        FileUtils.deleteSilently(optimizedDexFilePath);
        // Load dexClass
        File optimizedFile = null;
        try {
            File cacheDir = ctx.getCacheDir();
            optimizedFile = File.createTempFile("opt_", ".dex", cacheDir);
            dexFile = DexFile.loadDex(apkFile.getPath(), optimizedFile.getPath(), 0);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            FileUtils.deleteSilently(optimizedFile);
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
                classes.add(className);
                try {
                    classes.addAll(getImports(className));
                } catch (ClassNotFoundException | LinkageError ignore) {
                }
            }
        }
        return new ArrayList<>(classes);
    }

    @NonNull
    public Reflector getReflector(String className) throws ClassNotFoundException {
        return new Reflector(loadClass(className));
    }

    @Override
    public void close() throws IOException {
        if (dexFile != null) dexFile.close();
    }

    @NonNull
    private Set<String> getImports(String className) throws ClassNotFoundException {
        return new Reflector(loadClass(className)).getImports();
    }

    @NonNull
    private Class<?> loadClass(String className) throws ClassNotFoundException {
        return loader.loadClass(className);
    }
}
