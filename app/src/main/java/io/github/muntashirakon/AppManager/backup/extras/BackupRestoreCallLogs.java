// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logs.Log;

// Converted from https://github.com/tmo1/sms-ie/blob/75d2c3da3ef190731970f97414ac2bb5e483ebe2/app/src/main/java/com/github/tmo1/sms_ie/ImportExportCallLog.kt
// to suit our needs.
class BackupRestoreCallLogs extends BackupRestoreSpecial {
    public static final String TAG = BackupRestoreCallLogs.class.getSimpleName();

    public BackupRestoreCallLogs(@NonNull Context context) {
        super(context);
    }

    @Override
    public void backup(@NonNull Writer out) throws IOException {
        Map<String, String> cachedDisplayNames = new HashMap<>();
        try (Cursor cursor = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, null);
             JsonWriter jsonWriter = new JsonWriter(out)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            jsonWriter.setIndent("  ");
            jsonWriter.beginArray();
            int addressIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            String[] columns = cursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = cursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                // The call logs do have a CACHED_NAME ("name") field, but it may still be useful to add the current display name, if available
                // From the documentation at https://developer.android.com/reference/android/provider/CallLog.Calls#CACHED_NAME
                // "The cached name associated with the phone number, if it exists.
                // This value is typically filled in by the dialer app for the caching purpose, so it's not guaranteed to be present, and may not be current if the contact information associated with this number has changed."
                String address = cursor.getString(addressIndex);
                if (address != null) {
                    String displayName = ContactsUtils.lookupDisplayName(context, cachedDisplayNames, address);
                    if (displayName != null) {
                        jsonWriter.name(ContactsContract.PhoneLookup.DISPLAY_NAME).value(displayName);
                    }
                }
                jsonWriter.endObject();
            } while (cursor.moveToNext());
            jsonWriter.endArray();
        }
    }

    @Override
    public void restore(@NonNull Reader in) throws IOException {
        JsonReader jsonReader = new JsonReader(in);
        List<String> columns;
        try (Cursor callLogCursor = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, null)) {
            if (callLogCursor == null) {
                return;
            }
            columns = new ArrayList<>(Arrays.asList(callLogCursor.getColumnNames()));
            columns.remove(BaseColumns._ID);
            columns.remove(BaseColumns._COUNT);
        }
        jsonReader.beginArray();
        ContentValues callLogMetadata = new ContentValues();
        while (jsonReader.hasNext()) {
            jsonReader.beginObject();
            callLogMetadata.clear();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                String value = jsonReader.nextString();
                if ((columns.contains(name))) {
                    callLogMetadata.put(name, value);
                }
            }
            Uri insertUri;
            if (callLogMetadata.containsKey(CallLog.Calls.NUMBER)) {
                insertUri = cr.insert(CallLog.Calls.CONTENT_URI, callLogMetadata);
                if (insertUri == null) {
                    Log.v(TAG, "Call log insert failed!");
                }
            }
            jsonReader.endObject();
        }
        jsonReader.endArray();
    }
}
