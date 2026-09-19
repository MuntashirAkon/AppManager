// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

/**
 * Reads the complete set of EXIF attributes exposed by AndroidX ExifInterface.
 */
final class ImageMetadataReader {
    private static final int MAX_VALUE_LENGTH = 16 * 1024;
    private static final int MAX_OUTPUT_LENGTH = 128 * 1024;

    private ImageMetadataReader() {
    }

    @NonNull
    static CharSequence read(@NonNull Context context, @NonNull Path path) throws IOException {
        SpannableStringBuilder result = new SpannableStringBuilder();
        try (InputStream input = path.openInputStream()) {
            ExifInterface exif = new ExifInterface(input);
            List<String> tags = getExifTags();
            for (String tag : tags) {
                String value;
                try {
                    value = exif.getAttribute(tag);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                append(context, result, toLabel(tag), limit(value));
                if (result.length() >= MAX_OUTPUT_LENGTH) {
                    result.append("\n")
                            .append(UIUtils.getSecondaryText(context, "[output truncated]"));
                    break;
                }
            }
        }
        return result;
    }

    @NonNull
    private static List<String> getExifTags() {
        List<String> tags = new ArrayList<>();
        for (Field field : ExifInterface.class.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != String.class
                    || !field.getName().startsWith("TAG_")) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof String) {
                    tags.add((String) value);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        Collections.sort(tags);
        return tags;
    }

    @NonNull
    private static String toLabel(@NonNull String tag) {
        String label = tag.startsWith("TAG_") ? tag.substring(4) : tag;
        StringBuilder result = new StringBuilder(label.length() + 8);
        for (int i = 0; i < label.length(); ++i) {
            char c = label.charAt(i);
            if (i > 0 && Character.isUpperCase(c)
                    && (Character.isLowerCase(label.charAt(i - 1))
                    || (i + 1 < label.length() && Character.isLowerCase(label.charAt(i + 1))))) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString().replace('_', ' ');
    }

    @NonNull
    private static String limit(@NonNull String value) {
        return value.length() <= MAX_VALUE_LENGTH
                ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }

    private static void append(@NonNull Context context, @NonNull SpannableStringBuilder result,
                               @NonNull String label, @Nullable String value) {
        if (value == null || value.isEmpty() || result.length() >= MAX_OUTPUT_LENGTH) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append(UIUtils.getStyledKeyValue(context, label, value));
    }
}
