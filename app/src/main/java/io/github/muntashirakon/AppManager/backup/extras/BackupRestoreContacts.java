// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.extras;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.logs.Log;

// Converted from https://github.com/tmo1/sms-ie/blob/75d2c3da3ef190731970f97414ac2bb5e483ebe2/app/src/main/java/com/github/tmo1/sms_ie/ImportExportContacts.kt
// to suit our needs.
public class BackupRestoreContacts extends BackupRestoreSpecial {
    public static final String TAG = BackupRestoreContacts.class.getSimpleName();

    public BackupRestoreContacts(@NonNull Context context) {
        super(context);
    }


    @Override
    public void backup(@NonNull Writer out) throws IOException {
        try (Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null,
                null, null, null);
             JsonWriter jsonWriter = new JsonWriter(out)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            jsonWriter.setIndent("  ");
            jsonWriter.beginArray();
            int contactsIdIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            String[] columns = cursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = cursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                String contactId = cursor.getString(contactsIdIndex);
                if (contactId != null) {
                    writeContactInfo(jsonWriter, contactId);
                }
                jsonWriter.endObject();
            } while (cursor.moveToNext());
            jsonWriter.endArray();
        }
    }

    @Override
    public void restore(@NonNull Reader in) throws IOException {
        JsonReader jsonReader = new JsonReader(in);
        List<String> contactDataFields = new ArrayList<>();
        for (int i = 1; i < 16; ++i) {
            contactDataFields.add("data" + i);
        }
        contactDataFields.add(ContactsContract.Data.MIMETYPE);
        try {
            jsonReader.beginArray();
            // Loop through Contacts
            while (jsonReader.hasNext()) {
                jsonReader.beginObject();
                // Loop through Contact fields until we find the array of Raw Contacts
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (Objects.equals(name, "raw_contacts")) {
                        jsonReader.beginArray();
                        while (jsonReader.hasNext()) {
                            // See https://developer.android.com/guide/topics/providers/contacts-provider#Transactions
                            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                            ContentProviderOperation.Builder op = ContentProviderOperation
                                    .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null);
                            ops.add(op.build());
                            jsonReader.beginObject();
                            // Loop through Raw Contact fields until we find the array of Contacts Data
                            while (jsonReader.hasNext()) {
                                name = jsonReader.nextName();
                                if (Objects.equals(name, "contacts_data")) {
                                    jsonReader.beginArray();
                                    while (jsonReader.hasNext()) {
                                        jsonReader.beginObject();
                                        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
                                        while (jsonReader.hasNext()) {
                                            name = jsonReader.nextName();
                                            String dataValue = jsonReader.nextString();
                                            boolean base64 = false;
                                            if (name.length() > 10 && name.endsWith("__base64__")) {
                                                base64 = true;
                                                name = name.substring(0, name.length() - 10);
                                            }
                                            if (contactDataFields.contains(name)) {
                                                if (base64) {
                                                    op.withValue(name, Base64.decode(dataValue, Base64.NO_WRAP));
                                                } else {
                                                    op.withValue(name, dataValue);
                                                }
                                            }
                                        }
                                        op.withYieldAllowed(true);
                                        ops.add(op.build());
                                        jsonReader.endObject();
                                    }
                                    jsonReader.endArray();
                                } else {
                                    jsonReader.nextString();
                                }
                            }
                            try {
                                cr.applyBatch(ContactsContract.AUTHORITY, ops);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception encountered while inserting contact", e);
                            }
                            jsonReader.endObject();
                        }
                        jsonReader.endArray();
                    } else {
                        jsonReader.nextString();
                    }
                }
                jsonReader.endObject();
            }
            jsonReader.endArray();
        } catch (Exception e) {
            Log.e(TAG, "Error importing contacts", e);
        }
        // TODO: 10/1/23
    }

    private void writeContactInfo(@NonNull JsonWriter jsonWriter, @NonNull String contactId) throws IOException {
        try (Cursor cursor = cr.query(ContactsContract.RawContacts.CONTENT_URI,
                null, ContactsContract.RawContacts.CONTACT_ID + "=?", new String[]{contactId}, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int rawContactsIdIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            jsonWriter.name("raw_contacts");
            jsonWriter.beginArray();
            String[] columns = cursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    String val = cursor.getString(i);
                    if (val != null) {
                        jsonWriter.name(columns[i]).value(val);
                    }
                }
                String rawContactId = cursor.getString(rawContactsIdIndex);
                if (rawContactId != null) {
                    writeRawContactInfo(jsonWriter, rawContactId);
                }
                jsonWriter.endObject();
            } while (cursor.moveToNext());
            jsonWriter.endArray();
        }
    }

    private void writeRawContactInfo(@NonNull JsonWriter jsonWriter, @NonNull String rawContactId) throws IOException {
        try (Cursor dataCursor = cr.query(ContactsContract.Data.CONTENT_URI, null,
                ContactsContract.Data.RAW_CONTACT_ID + "=?", new String[]{rawContactId}, null, null)) {
            if (dataCursor == null || !dataCursor.moveToFirst()) {
                return;
            }
            jsonWriter.name("contacts_data");
            jsonWriter.beginArray();
            String[] columns = dataCursor.getColumnNames();
            do {
                jsonWriter.beginObject();
                for (int i = 0; i < columns.length; ++i) {
                    if (dataCursor.getType(i) != Cursor.FIELD_TYPE_BLOB) {
                        String val = dataCursor.getString(i);
                        if (val != null) {
                            jsonWriter.name(columns[i]).value(val);
                        }
                    } else {
                        byte[] val = dataCursor.getBlob(i);
                        if (val != null) {
                            jsonWriter.name(columns[i] + "__base64__")
                                    .value(Base64.encodeToString(val, Base64.NO_WRAP));
                        }
                    }
                }
                jsonWriter.endObject();
            } while (dataCursor.moveToNext());
            jsonWriter.endArray();
        }
    }
}
