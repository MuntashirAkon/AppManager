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

package io.github.muntashirakon.AppManager.backup;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

@SuppressWarnings("SameParameterValue")
final class TarUtils {
    @StringDef(value = {
            TAR_GZIP,
            TAR_BZIP2
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TarType {
    }

    public static final String TAR_GZIP = "z";
    public static final String TAR_BZIP2 = "j";

    // source, type, filters, exclude, size, destination
    private static final String TAR_CREATE = "cd \"%s\" && " + Runner.TOYBOX + " tar -c%sf - %s %s | " + Runner.TOYBOX + " split -b %s - \"%s\"";
    // sources, type, filters, exclude, destination
    private static final String TAR_EXTRACT = Runner.TOYBOX + " cat %s | " + Runner.TOYBOX + "  tar -x%sf - %s %s -C \"%s\"";

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type      Compression type
     * @param source    Source directory
     * @param dest      Destination directory with file name prefix (aa, ab, etc. are added at the end)
     * @param filters   A list of filters without any {@code ./} prefix
     * @param splitSize Size of the split, {@code 1G} will be used if null is supplied
     * @param exclude   Files to be excluded, a list of string without {@code ./} prefix
     * @return {@code true} on success, {@code false} otherwise
     */
    @NonNull
    static File[] create(@NonNull @TarType String type, @NonNull File source, @NonNull File dest, @Nullable String[] filters, @Nullable String splitSize, @Nullable String[] exclude) {
        if (splitSize == null) splitSize = "1G";
        if (Runner.runCommand(String.format(TAR_CREATE, source.getAbsolutePath(),
                type, getFilterStr(filters), getExcludeStr(exclude), splitSize,
                dest.getAbsolutePath())).isSuccessful()) {
            return getAddedFiles(dest);
        }
        return ArrayUtils.EMPTY_FILE;
    }

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type    Compression type
     * @param sources Source directory
     * @param dest    Destination directory with file name prefix (aa, ab, etc. are added at the end)
     * @param filters A list of filters without any {@code ./} prefix
     * @param exclude Files to be excluded, a list of string without {@code ./} prefix
     * @return {@code true} on success, {@code false} otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean extract(@NonNull @TarType String type, @NonNull File[] sources, @NonNull File dest, @Nullable String[] filters, @Nullable String[] exclude) {
        return Runner.runCommand(String.format(TAR_EXTRACT, getSourceStr(sources), type,
                getFilterStr(filters), getExcludeStr(exclude), dest.getAbsolutePath())).isSuccessful();
    }

    @NonNull
    private static String getFilterStr(@Nullable String[] filters) {
        StringBuilder filterStr = new StringBuilder();
        if (filters != null) {
            for (String filter : filters) filterStr.append(" ./").append(filter);
        } else filterStr.append(".");
        return filterStr.toString();
    }

    @NonNull
    private static String getExcludeStr(@Nullable String[] exclude) {
        StringBuilder excludeStr = new StringBuilder();
        if (exclude != null) {
            for (String ex : exclude) excludeStr.append(" --exclude=").append(ex);
        }
        return excludeStr.toString();
    }

    @NonNull
    private static String getSourceStr(@NonNull File[] sources) {
        StringBuilder sourceBuilder = new StringBuilder();
        for (File source : sources) {
            sourceBuilder.append(" \"").append(source.getAbsolutePath()).append("\"");
        }
        return sourceBuilder.toString();
    }

    @NonNull
    private static File[] getAddedFiles(@NonNull File fileName) {
        String filePrefix = fileName.getName();
        File parent = fileName.getParentFile();
        File[] addedFiles = null;
        if (parent != null) {
            addedFiles = parent.listFiles((dir, name) -> name.startsWith(filePrefix));
        }
        return ArrayUtils.defeatNullable(addedFiles);
    }
}
