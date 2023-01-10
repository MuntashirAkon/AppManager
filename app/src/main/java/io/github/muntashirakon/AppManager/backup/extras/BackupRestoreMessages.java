// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.IoUtils;

// Converted from https://github.com/tmo1/sms-ie/blob/75d2c3da3ef190731970f97414ac2bb5e483ebe2/app/src/main/java/com/github/tmo1/sms_ie/ImportExportMessages.kt
// to suit our needs
public class BackupRestoreMessages extends BackupRestoreSpecial {
    public static final String TAG = BackupRestoreMessages.class.getSimpleName();
    // PduHeaders are referenced here https://developer.android.com/reference/android/provider/Telephony.Mms.Addr#TYPE
    // and defined here https://android.googlesource.com/platform/frameworks/opt/mms/+/4bfcd8501f09763c10255442c2b48fad0c796baa/src/java/com/google/android/mms/pdu/PduHeaders.java
    // but are apparently unavailable in a public class
    public static final String PDU_HEADERS_FROM = "137";
    // FIXME: I can't find an officially documented way of getting the Part table URI for API < 29.
    //  The idea to use "content://mms/part" comes from here: https://stackoverflow.com/a/6446831
    private static final Uri MMS_PART_CONTENT_URI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
            Telephony.Mms.Part.CONTENT_URI : Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "part");

    public BackupRestoreMessages(@NonNull Context context) {
        super(context);
    }

    @Override
    public void backup(@NonNull Writer out) throws IOException {
        Map<String, String> cachedDisplayNames = new HashMap<>();
        try (JsonWriter jsonWriter = new JsonWriter(out)) {
            jsonWriter.setIndent("  ");
            jsonWriter.beginArray();
            writeSms(jsonWriter, cachedDisplayNames);
            writeMms(jsonWriter, cachedDisplayNames);
            jsonWriter.endArray();
        }
    }

    @Override
    public void restore(@NonNull Reader in) throws IOException {
        JsonReader jsonReader = new JsonReader(in);
        // get column names of local SMS, MMS, and MMS part tables
        List<String> smsColumns = new ArrayList<>();
        try (Cursor smsCursor = cr.query(Telephony.Sms.CONTENT_URI, null, null, null, null)) {
            if (smsCursor != null) {
                smsColumns.addAll(Arrays.asList(smsCursor.getColumnNames()));
            }
        }
        List<String> mmsColumns = new ArrayList<>();
        try (Cursor mmsCursor = cr.query(Telephony.Mms.CONTENT_URI, null, null, null, null)) {
            if (mmsCursor != null) {
                mmsColumns.addAll(Arrays.asList(mmsCursor.getColumnNames()));
            }
        }
        List<String> partColumns = new ArrayList<>();
        try (Cursor partCursor = cr.query(MMS_PART_CONTENT_URI, null, null, null, null)) {
            if (partCursor != null) {
                partColumns.addAll(Arrays.asList(partCursor.getColumnNames()));
            }
        }
        Map<String, String> threadIdMap = new HashMap<>();
        ContentValues messageMetadata = new ContentValues();
        Set<ContentValues> addresses = new HashSet<>();
        List<ContentValues> parts = new ArrayList<>();
        List<byte[]> binaryData = new ArrayList<>();
        List<String> defaultRequiredColumns = Arrays.asList(BaseColumns._ID, ContactsContract.PhoneLookup.DISPLAY_NAME);
        List<String> mmsRequiredColumns = Arrays.asList(Telephony.Mms.Addr._ID, Telephony.Mms.Addr._COUNT,
                Telephony.Mms.Addr.MSG_ID, ContactsContract.PhoneLookup.DISPLAY_NAME);
        List<String> partsRequiredColumns = Arrays.asList(Telephony.Mms.Part._ID, Telephony.Mms.Part._COUNT,
                Telephony.Mms.Part._DATA, Telephony.Mms.Part.MSG_ID, ContactsContract.PhoneLookup.DISPLAY_NAME);
        try {
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                jsonReader.beginObject();
                messageMetadata.clear();
                addresses.clear();
                parts.clear();
                binaryData.clear();
                String name;
                String value;
                String oldThreadId = null;
                while (jsonReader.hasNext()) {
                    name = jsonReader.nextName();
                    switch (name) {
                        case "sender_address": {
                            jsonReader.beginObject();
                            ContentValues address = new ContentValues();
                            while (jsonReader.hasNext()) {
                                String name1 = jsonReader.nextName();
                                String value1 = jsonReader.nextString();
                                if (!mmsRequiredColumns.contains(name1)) {
                                    address.put(name1, value1);
                                }
                            }
                            addresses.add(address);
                            jsonReader.endObject();
                            break;
                        }
                        case "recipient_addresses": {
                            jsonReader.beginArray();
                            while (jsonReader.hasNext()) {
                                jsonReader.beginObject();
                                ContentValues address = new ContentValues();
                                while (jsonReader.hasNext()) {
                                    String name1 = jsonReader.nextName();
                                    String value1 = jsonReader.nextString();
                                    if (!mmsRequiredColumns.contains(name1)) {
                                        address.put(name1, value1);
                                    }
                                }
                                addresses.add(address);
                                jsonReader.endObject();
                            }
                            jsonReader.endArray();
                            break;
                        }
                        case "parts": {
                            jsonReader.beginArray();
                            while (jsonReader.hasNext()) {
                                jsonReader.beginObject();
                                ContentValues part = new ContentValues();
                                boolean hasBinaryData = false;
                                while (jsonReader.hasNext()) {
                                    String name1 = jsonReader.nextName();
                                    String value1 = jsonReader.nextString();
                                    if (!partsRequiredColumns.contains(name1)) {
                                        part.put(name1, value1);
                                    }
                                    if (Objects.equals(name1, "binary_data")) {
                                        binaryData.add(Base64.decode(value1, Base64.NO_WRAP));
                                        hasBinaryData = true;
                                    }
                                }
                                if (!hasBinaryData) {
                                    binaryData.add(null);
                                }
                                parts.add(part);
                                jsonReader.endObject();
                            }
                            jsonReader.endArray();
                            break;
                        }
                        case Telephony.Sms.THREAD_ID: {
                            oldThreadId = jsonReader.nextString();
                            if (threadIdMap.containsKey(oldThreadId)) {
                                messageMetadata.put(Telephony.Sms.THREAD_ID, threadIdMap.get(oldThreadId));
                            }
                            break;
                        }
                        default: {
                            value = jsonReader.nextString();
                            if (!defaultRequiredColumns.contains(name)){
                                messageMetadata.put(name, value);
                            }
                            break;
                        }
                    }
                }
                jsonReader.endObject();

                boolean isMMS = messageMetadata.containsKey(Telephony.Mms.MESSAGE_TYPE);
                // If we don't yet have a thread_id (i.e., the message has a new
                // thread_id that we haven't yet encountered and so isn't yet in
                // threadIdMap), then we need to get a new thread_id and record the mapping
                // between the old and new ones in threadIdMap
                if (!messageMetadata.containsKey(Telephony.Sms.THREAD_ID)) {
                    Set<String> addressesSet = new HashSet<>();
                    for (ContentValues address : addresses) {
                        addressesSet.add(address.getAsString(Telephony.Mms.Addr.ADDRESS));
                    }
                    long newThreadId = !isMMS ? Telephony.Threads.getOrCreateThreadId(context,
                            messageMetadata.getAsString(Telephony.TextBasedSmsColumns.ADDRESS))
                            : Telephony.Threads.getOrCreateThreadId(context, addressesSet);
                    messageMetadata.put(Telephony.Sms.THREAD_ID, newThreadId);
                    if (oldThreadId != null) {
                        threadIdMap.put(oldThreadId, String.valueOf(newThreadId));
                    }
                }
                // Log.v(TAG, "Original thread_id: $oldThreadId\t New thread_id: ${messageMetadata.getAsString(Telephony.Sms.THREAD_ID)}")
                if (!isMMS) { // insert SMS
                    Set<String> fieldNames = new HashSet<>(messageMetadata.keySet());
                    for (String key : fieldNames) {
                        if (!smsColumns.contains(key)) {
                            messageMetadata.remove(key);
                        }
                    }
                    Uri insertUri = cr.insert(Telephony.Sms.CONTENT_URI, messageMetadata);
                    if (insertUri == null) {
                        Log.v(TAG, "SMS insert failed!");
                    }
                } else { // insert MMS
                    Set<String> fieldNames = new HashSet<>(messageMetadata.keySet());
                    for (String key : fieldNames) {
                        if (!mmsColumns.contains(key)) {
                            messageMetadata.remove(key);
                        }
                    }
                    Uri insertUri = cr.insert(Telephony.Mms.CONTENT_URI, messageMetadata);
                    if (insertUri == null) {
                        Log.v(TAG, "MMS insert failed!");
                    } else {
                        // Log.v(TAG, "MMS insert succeeded!");
                        String messageId = insertUri.getLastPathSegment();
                        Uri addressUri = Uri.withAppendedPath(Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, messageId), "addr");
                        for (ContentValues address1 : addresses) {
                            address1.put(Telephony.Mms.Addr.MSG_ID, messageId);
                            Uri insertAddressUri = cr.insert(addressUri, address1);
                            if (insertAddressUri == null) {
                                Log.v(TAG, "MMS address insert failed!");
                            } /*else {
                                Log.v(TAG, "MMS address insert succeeded. Address metadata:" + address.toString());
                            }*/
                        }
                        Uri partUri = Uri.withAppendedPath(Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, messageId), "part");
                        for (int j = 0; j < parts.size(); ++j) {
                            ContentValues part1 = parts.get(j);
                            Set<String> partFieldNames = new HashSet<>(part1.keySet());
                            for (String key : partFieldNames) {
                                if (!partColumns.contains(key)) {
                                    part1.remove(key);
                                }
                            }
                            part1.put(Telephony.Mms.Part.MSG_ID, messageId);
                            Uri insertPartUri = cr.insert(partUri, part1);
                            if (insertPartUri == null) {
                                Log.v(TAG, "MMS part insert failed! Part metadata: " + part1);
                            } else {
                                byte[] data = binaryData.get(j);
                                if (data != null) {
                                    try (OutputStream os = cr.openOutputStream(insertPartUri)) {
                                        if (os != null) {
                                            os.write(data);
                                        } else Log.v(TAG, "Failed to open OutputStream!");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            jsonReader.endArray();
        } catch (Exception e) {
            Log.e(TAG, "Error importing messages", e);
        }
    }

    private void writeSms(@NonNull JsonWriter jsonWriter, @NonNull Map<String, String> cachedDisplayNames) throws IOException {
        try (Cursor cursor = cr.query(Telephony.Sms.CONTENT_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            String[] columns = cursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = cursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                String address = cursor.getString(addressIndex);
                if (address != null) {
                    String displayName = ContactsUtils.lookupDisplayName(context, cachedDisplayNames, address);
                    if (displayName != null) {
                        jsonWriter.name(ContactsContract.PhoneLookup.DISPLAY_NAME).value(displayName);
                    }
                }
                jsonWriter.endObject();
            } while (cursor.moveToNext());
        }
    }

    private void writeMms(@NonNull JsonWriter jsonWriter, @NonNull Map<String, String> cachedDisplayNames) throws IOException {
        try (Cursor cursor = cr.query(Telephony.Mms.CONTENT_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int msgIdIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            String[] columns = cursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = cursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                // The following is adapted from https://stackoverflow.com/questions/3012287/how-to-read-mms-data-in-android/6446831#6446831
                String msgId = cursor.getString(msgIdIndex);
                if (msgId != null) {
                    writeMmsAddr(jsonWriter, msgId, cachedDisplayNames);
                    writeMmsPart(jsonWriter, msgId);
                }
                jsonWriter.endObject();
            } while (cursor.moveToNext());
        }
    }

    private void writeMmsAddr(@NonNull JsonWriter jsonWriter, @NonNull String msgId, @NonNull Map<String, String> cachedDisplayNames) throws IOException {
        try (Cursor addressCursor = cr.query(Uri.withAppendedPath(Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, msgId), "addr"), null, null, null, null)) {
            if (addressCursor == null || !addressCursor.moveToFirst()) {
                return;
            }
            int addressTypeIndex = addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE);
            int addressIndex = addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS);
            String[] columns = addressCursor.getColumnNames();
            // write sender address object
            do {
                if (Objects.equals(addressCursor.getString(addressTypeIndex), PDU_HEADERS_FROM)) {
                    jsonWriter.name("sender_address");
                    jsonWriter.beginObject();
                    for (int i = 0; i < columns.length; ++i) {
                        String val = addressCursor.getString(i);
                        if (val != null) {
                            jsonWriter.name(columns[i]).value(val);
                        }
                    }
                    String displayName = ContactsUtils.lookupDisplayName(context, cachedDisplayNames, addressCursor.getString(addressIndex));
                    if (displayName != null) {
                        jsonWriter.name(ContactsContract.PhoneLookup.DISPLAY_NAME).value(displayName);
                    }
                    jsonWriter.endObject();
                    break;
                }
            } while (addressCursor.moveToNext());
            // write array of recipient address objects
            if (!addressCursor.moveToFirst()) {
                return;
            }
            jsonWriter.name("recipient_addresses");
            jsonWriter.beginArray();
            do {
                if (!Objects.equals(addressCursor.getString(addressTypeIndex), PDU_HEADERS_FROM)) {
                    jsonWriter.beginObject();
                    for (int i = 0; i < columns.length; ++i) {
                        String val = addressCursor.getString(i);
                        if (val != null) {
                            jsonWriter.name(columns[i]).value(val);
                        }
                    }
                    String displayName = ContactsUtils.lookupDisplayName(context, cachedDisplayNames, addressCursor.getString(addressIndex));
                    if (displayName != null) {
                        jsonWriter.name(ContactsContract.PhoneLookup.DISPLAY_NAME).value(displayName);
                    }
                    jsonWriter.endObject();
                }
            } while (addressCursor.moveToNext());
            jsonWriter.endArray();
        }
    }

    private void writeMmsPart(@NonNull JsonWriter jsonWriter, @NonNull String msgId) throws IOException {
        try (Cursor partCursor = cr.query(MMS_PART_CONTENT_URI, null,
                "mid=?", new String[]{msgId}, "seq ASC")) {
            if (partCursor == null || !partCursor.moveToFirst()) {
                return;
            }
            // write array of MMS parts
            jsonWriter.name("parts");
            jsonWriter.beginArray();
            int partIdIndex = partCursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID);
            int dataIndex = partCursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA);
            String[] columns = partCursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = partCursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                if (partCursor.getString(dataIndex) != null) {
                    try (InputStream inputStream = cr.openInputStream(Uri.withAppendedPath(MMS_PART_CONTENT_URI, partCursor.getString(partIdIndex)))) {
                        String data = Base64.encodeToString(IoUtils.readFully(inputStream, -1, true), Base64.NO_WRAP);
                        jsonWriter.name("binary_data").value(data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error accessing binary data for MMS message part " + partCursor.getString(partIdIndex), e);
                    }
                }
                jsonWriter.endObject();
            } while (partCursor.moveToNext());
            jsonWriter.endArray();
        }
    }
}
