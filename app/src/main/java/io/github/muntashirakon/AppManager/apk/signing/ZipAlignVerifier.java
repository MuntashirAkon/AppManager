// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import com.reandroid.archive2.Archive;
import com.reandroid.archive2.ArchiveEntry;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;

import io.github.muntashirakon.AppManager.logs.Log;

// Based on the AOSP zipalign library
public class ZipAlignVerifier {
    public static final String TAG = ZipAlignVerifier.class.getSimpleName();

    public static final int kPageAlignment = 4096;

    public static boolean verify(File file, int alignment, boolean pageAlignSharedLibs) {
        Archive zipFile;
        boolean foundBad = false;
        Log.d(TAG, String.format(Locale.ROOT, "Verifying alignment of %s (%d)...\n", file, alignment));

        try {
            zipFile = new Archive(file);
        } catch (IOException e) {
            Log.e(TAG, String.format(Locale.ROOT, "Unable to open '%s' for verification\n", file), e);
            return false;
        }
        List<ArchiveEntry> entries = zipFile.getEntryList();
        long lastFileOffset;
        for (ArchiveEntry pEntry : entries) {
            String name = pEntry.getName();
            lastFileOffset = pEntry.getFileOffset();
            if (pEntry.getMethod() == ZipEntry.DEFLATED) {
                Log.d(TAG, String.format(Locale.ROOT, "%8d %s (OK - compressed)\n", lastFileOffset, name));
            } else if (pEntry.isDirectory()) {
                // Directory entries do not need to be aligned.
                Log.d(TAG, String.format(Locale.ROOT, "%8d %s (OK - directory)\n", lastFileOffset, name));
            } else {
                int alignTo = getAlignment(pageAlignSharedLibs, alignment, pEntry);
                if ((lastFileOffset % alignTo) != 0) {
                    Log.w(TAG, String.format(Locale.ROOT, "%8d %s (BAD - %d)\n", lastFileOffset, name, (lastFileOffset % alignTo)));
                    foundBad = true;
                    break;
                } else {
                    Log.d(TAG, String.format(Locale.ROOT, "%8d %s (OK)\n", lastFileOffset, name));
                }
            }
        }

        Log.d(TAG, String.format(Locale.ROOT, "Verification %s\n", foundBad ? "FAILED" : "successful"));

        return !foundBad;
    }

    private static int getAlignment(boolean pageAlignSharedLibs, int defaultAlignment, ZipEntry pEntry) {
        if (!pageAlignSharedLibs) {
            return defaultAlignment;
        }

        if (pEntry.getName().startsWith(".so")) {
            return kPageAlignment;
        }

        return defaultAlignment;
    }
}
