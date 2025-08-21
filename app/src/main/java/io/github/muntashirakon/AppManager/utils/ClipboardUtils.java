// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.Paths;

public class ClipboardUtils {
    private static final int MAX_CLIPBOARD_SIZE_BYTES = 1024 * 1024;

    /**
     * Copies text to clipboard, using URI fallback if text is larger.
     */
    public static void copyToClipboard(@NonNull Context context, @Nullable CharSequence label, @NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        byte[] textBytes = text.getBytes();
        ClipData clip;
        if (textBytes.length < MAX_CLIPBOARD_SIZE_BYTES) {
            // Small text: copy directly
            clip = ClipData.newPlainText(label, text);
        } else {
            // Large text: save to file and copy Uri reference
            try {
                File cacheFile = FileCache.getGlobalFileCache().getCachedFile(textBytes, "txt");
                // Use FileProvider to get content Uri for the file
                Uri contentUri = FmProvider.getContentUri(Paths.get(cacheFile));
                // Grant temporary read permission
                clip = ClipData.newUri(context.getContentResolver(), label, contentUri);
            } catch (IOException e) {
                e.printStackTrace();
                // Fallback: copy truncated text if writing file fails
                clip = ClipData.newPlainText("text", text.substring(0, MAX_CLIPBOARD_SIZE_BYTES - 1));
            }
        }
        clipboard.setPrimaryClip(clip);
    }


    @Nullable
    public static CharSequence readClipboard(@NonNull Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).coerceToText(context);
        }
        return null;
    }

    @Nullable
    public static String readHashValueFromClipboard(@NonNull Context context) {
        CharSequence clipData = readClipboard(context);
        if (clipData != null) {
            String data = clipData.toString().trim().toLowerCase(Locale.ROOT);
            if (data.matches("[0-9a-f: \n]+")) {
                return data.replaceAll("[: \n]+", "");
            }
        }
        return null;
    }
}
