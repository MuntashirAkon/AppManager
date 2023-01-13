// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.Nullable;

import java.util.Map;

import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;

final class ContactsUtils {
    @Nullable
    public static String lookupDisplayName(Context context, Map<String, String> cachedDisplayNames, String address) {
        if (TextUtilsCompat.isEmpty(address)) {
            return null;
        }
        String displayName = cachedDisplayNames.get(address);
        if (displayName != null) {
            return displayName;
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address));
        try (Cursor nameCursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {
            if (nameCursor == null || !nameCursor.moveToFirst()) {
                return null;
            }
            displayName = nameCursor.getString(nameCursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME));
            cachedDisplayNames.put(address, displayName);
        }
        return displayName;
    }
}
