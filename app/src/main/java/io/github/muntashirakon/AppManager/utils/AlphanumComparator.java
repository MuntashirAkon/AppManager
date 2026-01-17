// SPDX-License-Identifier: MIT AND GPL-3.0

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Comparator;

// Copyright 2007-2017 David Koelle
// Copyright 2025 Muntashir Al-Islam
public class AlphanumComparator implements Comparator<String> {
    private static boolean isDigit(char ch) {
        return ((ch >= 48) && (ch <= 57));
    }

    /**
     * Length of string is passed in for improved efficiency (only need to calculate it once)
     **/
    @NonNull
    private static String getChunk(@NonNull String s, int sLength, int marker) {
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;
        if (isDigit(c)) {
            while (marker < sLength) {
                c = s.charAt(marker);
                if (!isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        } else {
            while (marker < sLength) {
                c = s.charAt(marker);
                if (isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }


    private static int compareString(@Nullable String s1, @Nullable String s2, boolean ignoreCase) {
        if ((s1 == null) || (s2 == null)) {
            return 0;
        }

        int thisMarker = 0;
        int thatMarker = 0;
        int s1Length = s1.length();
        int s2Length = s2.length();

        while (thisMarker < s1Length && thatMarker < s2Length) {
            String thisChunk = getChunk(s1, s1Length, thisMarker);
            thisMarker += thisChunk.length();

            String thatChunk = getChunk(s2, s2Length, thatMarker);
            thatMarker += thatChunk.length();

            // If both chunks contain numeric characters, sort them numerically
            int result;
            if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))) {
                // Simple chunk comparison by length.
                int thisChunkLength = thisChunk.length();
                result = thisChunkLength - thatChunk.length();
                // If equal, the first different number counts
                if (result == 0) {
                    for (int i = 0; i < thisChunkLength; i++) {
                        result = thisChunk.charAt(i) - thatChunk.charAt(i);
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            } else {
                result = ignoreCase
                        ? thisChunk.compareToIgnoreCase(thatChunk)
                        : thisChunk.compareTo(thatChunk);
            }

            if (result != 0)
                return result;
        }

        return s1Length - s2Length;
    }

    public static int compareString(@Nullable String s1, @Nullable String s2) {
        return compareString(s1, s2, false);
    }

    public static int compareStringIgnoreCase(@Nullable String s1, @Nullable String s2) {
        return compareString(s1, s2, true);
    }

    public int compare(String s1, String s2) {
        return compareString(s1, s2);
    }
}
