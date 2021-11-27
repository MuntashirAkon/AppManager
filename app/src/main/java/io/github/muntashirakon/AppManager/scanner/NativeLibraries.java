// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class NativeLibraries {
    private final List<String> libPaths = new ArrayList<>();
    private final Set<String> libs = new HashSet<>();

    @WorkerThread
    public NativeLibraries(@NonNull File apkFile) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.getName().endsWith(".so")) {
                    libPaths.add(zipEntry.getName());
                    libs.add(new File(zipEntry.getName()).getName());
                }
            }
        }
    }

    @WorkerThread
    public NativeLibraries(@NonNull InputStream apkInputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().endsWith(".so")) {
                    libPaths.add(zipEntry.getName());
                    libs.add(new File(zipEntry.getName()).getName());
                }
            }
        }
    }

    @AnyThread
    public NativeLibraries(@NonNull ZipFile zipFile) {
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".so")) {
                libPaths.add(zipEntry.getName());
                libs.add(new File(zipEntry.getName()).getName());
            }
        }
    }

    public List<String> getLibPaths() {
        return libPaths;
    }

    public Collection<String> getLibs() {
        return libs;
    }
}
