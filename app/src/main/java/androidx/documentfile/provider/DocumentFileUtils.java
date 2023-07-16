// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider;

import androidx.annotation.Nullable;

public final class DocumentFileUtils {
    public static boolean isSingleDocumentFile(@Nullable DocumentFile documentFile) {
        return documentFile instanceof SingleDocumentFile;
    }

    public static boolean isTreeDocumentFile(@Nullable DocumentFile documentFile) {
        return documentFile instanceof TreeDocumentFile;
    }
}
