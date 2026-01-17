// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class J2meIconExtractor {
    @Nullable
    public static Bitmap generateFromFile(@NonNull File file) {
        try(ZipFile zipFile = new ZipFile(file)) {
            String iconFile = getIconLocation(zipFile, zipFile.getEntry("META-INF/MANIFEST.MF"));
            if (iconFile == null) {
                // Not a J2ME JAR
                return null;
            }
            ZipEntry iconEntry = zipFile.getEntry(iconFile);
            if (iconEntry != null) {
                return BitmapFactory.decodeStream(zipFile.getInputStream(iconEntry));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    private static String getIconLocation(@NonNull ZipFile zipFile, @Nullable ZipEntry zipEntry) {
        if (zipEntry == null) {
            return null;
        }
        try {
            Manifest manifest = new Manifest(zipFile.getInputStream(zipEntry));
            Attributes attributes = manifest.getMainAttributes();
            // The logic is derived from J2ME Loader (ru.woesss.j2me.jar.Descriptor#getIcon())
            String icon = attributes.getValue("MIDlet-Icon");
            if (icon == null || icon.trim().isEmpty()) {
                String midlet = "MIDlet-" + 1;
                icon = attributes.getValue(midlet);
                if (icon == null) {
                    return null;
                }
                int start = icon.indexOf(',');
                if (start != -1) {
                    int end = icon.indexOf(',', ++start);
                    if (end != -1)
                        icon = icon.substring(start, end);
                }
            }
            icon = icon.trim();
            if (icon.isEmpty()) {
                return null;
            }
            while (icon.charAt(0) == '/') {
                icon = icon.substring(1);
            }
            return icon;
        } catch (IOException ignore) {
        }
        return null;
    }
}
