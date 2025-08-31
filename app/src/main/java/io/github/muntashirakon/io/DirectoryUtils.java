// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import java.util.Set;

public class DirectoryUtils {
    private static boolean isDirectoryChanged(Path directory, long sinceTimestamp, int maxDepth,
                                              Set<String> ignoredDirs, int currentDepth) {
        // Check depth limit
        if (currentDepth > maxDepth) {
            return false;
        }

        // Ensure it's a valid directory
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }

        try {
            Path[] files = directory.listFiles();
            for (Path file : files) {
                // Skip ignored folders
                if (file.isDirectory() && ignoredDirs.contains(file.getName())) {
                    continue;
                }

                // Check if file/directory has been modified since timestamp
                if (file.lastModified() > sinceTimestamp ||
                        file.creationTime() > sinceTimestamp) {
                    return true;
                }

                // Recursively check subdirectories
                if (file.isDirectory() && currentDepth < maxDepth) {
                    if (isDirectoryChanged(file, sinceTimestamp, maxDepth, ignoredDirs,
                            currentDepth + 1)) {
                        // Early return
                        return true;
                    }
                }
            }
        } catch (SecurityException e) {
            // Handle permission errors gracefully
            return false;
        }
        return false; // No changes detected
    }

    public static boolean isDirectoryChanged(Path directoryPath, long sinceTimestamp,
                                             int maxDepth, Set<String> ignoredDirs) {
        return isDirectoryChanged(directoryPath, sinceTimestamp, maxDepth, ignoredDirs, 0);
    }
}
