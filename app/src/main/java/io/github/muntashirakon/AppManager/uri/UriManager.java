/*
 * Copyright (C) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.uri;

import android.net.Uri;
import android.os.RemoteException;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyOutputStream;

import static com.android.internal.util.XmlUtils.readBooleanAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readLongAttribute;
import static com.android.internal.util.XmlUtils.writeBooleanAttribute;
import static com.android.internal.util.XmlUtils.writeIntAttribute;
import static com.android.internal.util.XmlUtils.writeLongAttribute;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

public class UriManager {
    public static final String TAG = "UriManager";

    private final ProxyFile mGrantFile;

    private final HashMap<String, ArrayList<UriGrant>> uriGrantsHashMap = new HashMap<>();

    /**
     * XML constants used in {@link #mGrantFile}
     */
    private static final String TAG_URI_GRANTS = "uri-grants";
    private static final String TAG_URI_GRANT = "uri-grant";
    private static final String ATTR_USER_HANDLE = "userHandle";
    private static final String ATTR_SOURCE_USER_ID = "sourceUserId";
    private static final String ATTR_TARGET_USER_ID = "targetUserId";
    private static final String ATTR_SOURCE_PKG = "sourcePkg";
    private static final String ATTR_TARGET_PKG = "targetPkg";
    private static final String ATTR_URI = "uri";
    private static final String ATTR_MODE_FLAGS = "modeFlags";
    private static final String ATTR_CREATED_TIME = "createdTime";
    private static final String ATTR_PREFIX = "prefix";

    public UriManager() {
        mGrantFile = new ProxyFile(new File(OsEnvironment.getDataSystemDirectory(), "urigrants.xml"));
        readGrantedUriPermissions();
    }

    @Nullable
    public ArrayList<UriGrant> getGrantedUris(String packageName) {
        synchronized (this) {
            return uriGrantsHashMap.get(packageName);
        }
    }

    public void grantUri(@NonNull UriGrant uriGrant) {
        synchronized (this) {
            ArrayList<UriGrant> uriGrants = uriGrantsHashMap.get(uriGrant.targetPkg);
            if (uriGrants == null) {
                uriGrants = new ArrayList<>();
                uriGrantsHashMap.put(uriGrant.targetPkg, uriGrants);
            }
            uriGrants.add(uriGrant);
        }
    }

    public void writeGrantedUriPermissions() {
        // Snapshot permissions so we can persist without lock
        List<UriGrant> persist = new ArrayList<>();
        synchronized (this) {
            for (List<UriGrant> uriGrants : uriGrantsHashMap.values()) {
                persist.addAll(uriGrants);
            }
        }

        try {
            File tempFile = IOUtils.getTempFile();
            try (OutputStream fos = new ProxyOutputStream(tempFile)) {
                XmlSerializer out = Xml.newSerializer();
                out.setOutput(fos, "utf-8");
                out.startDocument(null, true);
                out.startTag(null, TAG_URI_GRANTS);
                for (UriGrant perm : persist) {
                    out.startTag(null, TAG_URI_GRANT);
                    writeIntAttribute(out, ATTR_SOURCE_USER_ID, perm.sourceUserId);
                    writeIntAttribute(out, ATTR_TARGET_USER_ID, perm.targetUserId);
                    out.attribute(null, ATTR_SOURCE_PKG, perm.sourcePkg);
                    out.attribute(null, ATTR_TARGET_PKG, perm.targetPkg);
                    out.attribute(null, ATTR_URI, String.valueOf(perm.uri));
                    writeBooleanAttribute(out, ATTR_PREFIX, perm.prefix);
                    writeIntAttribute(out, ATTR_MODE_FLAGS, perm.modeFlags);
                    writeLongAttribute(out, ATTR_CREATED_TIME, perm.createdTime);
                    out.endTag(null, TAG_URI_GRANT);
                }
                out.endTag(null, TAG_URI_GRANTS);
                out.endDocument();
                Runner.runCommand(String.format(Runner.TOYBOX + " cp \"%s\" \"%s\"", tempFile.getAbsolutePath(), mGrantFile.getAbsolutePath()));
                Runner.runCommand(String.format(Runner.TOYBOX + " chmod 600 \"%s\"", mGrantFile.getAbsolutePath()));
                Runner.runCommand(String.format(Runner.TOYBOX + " chown 1000:1000 \"%s\"", mGrantFile.getAbsolutePath()));
                Runner.runCommand(new String[]{"restorecon", mGrantFile.getAbsolutePath()});
                tempFile.delete();
            }
        } catch (IOException | RemoteException e) {
            Log.e(TAG, "Failed writing Uri grants", e);
        }
    }

    private void readGrantedUriPermissions() {
        final long now = System.currentTimeMillis();
        try (InputStream fis = new ByteArrayInputStream(IOUtils.getFileContent(mGrantFile).getBytes())) {
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(fis, null);

            int type;
            while ((type = in.next()) != END_DOCUMENT) {
                final String tag = in.getName();
                if (type == START_TAG) {
                    if (TAG_URI_GRANT.equals(tag)) {
                        final int sourceUserId;
                        final int targetUserId;
                        final int userHandle = readIntAttribute(in,
                                ATTR_USER_HANDLE, Users.USER_NULL);
                        if (userHandle != Users.USER_NULL) {
                            // For backwards compatibility.
                            sourceUserId = userHandle;
                            targetUserId = userHandle;
                        } else {
                            sourceUserId = readIntAttribute(in, ATTR_SOURCE_USER_ID);
                            targetUserId = readIntAttribute(in, ATTR_TARGET_USER_ID);
                        }
                        final String sourcePkg = in.getAttributeValue(null, ATTR_SOURCE_PKG);
                        final String targetPkg = in.getAttributeValue(null, ATTR_TARGET_PKG);
                        final Uri uri = Uri.parse(in.getAttributeValue(null, ATTR_URI));
                        final boolean prefix = readBooleanAttribute(in, ATTR_PREFIX);
                        final int modeFlags = readIntAttribute(in, ATTR_MODE_FLAGS);
                        final long createdTime = readLongAttribute(in, ATTR_CREATED_TIME, now);

                        UriGrant uriGrant = new UriGrant(sourceUserId, targetUserId, userHandle,
                                sourcePkg, targetPkg, uri, prefix, modeFlags, createdTime);
                        synchronized (this) {
                            ArrayList<UriGrant> uriGrants = uriGrantsHashMap.get(targetPkg);
                            if (uriGrants == null) {
                                uriGrants = new ArrayList<>();
                                uriGrantsHashMap.put(targetPkg, uriGrants);
                            }
                            uriGrants.add(uriGrant);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Missing grants is okay
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Failed reading Uri grants", e);
        }
    }

    public static class UriGrant {
        public final int sourceUserId;
        public final int targetUserId;
        public final int userHandle;
        public final String sourcePkg;
        public final String targetPkg;
        public final Uri uri;
        public final boolean prefix;
        public final int modeFlags;
        public final long createdTime;

        public UriGrant(int sourceUserId, int targetUserId, int userHandle, String sourcePkg,
                        String targetPkg, Uri uri, boolean prefix, int modeFlags, long createdTime) {
            this.sourceUserId = sourceUserId;
            this.targetUserId = targetUserId;
            this.userHandle = userHandle;
            this.sourcePkg = sourcePkg;
            this.targetPkg = targetPkg;
            this.uri = uri;
            this.prefix = prefix;
            this.modeFlags = modeFlags;
            this.createdTime = createdTime;
        }

        @NonNull
        @Override
        public String toString() {
            // To preserve compatibility
            return flattenToString();
        }

        public String flattenToString() {
            return sourceUserId + "," + targetUserId + "," + userHandle + "," + sourcePkg + "," +
                    targetPkg + "," + prefix + "," + modeFlags + "," + createdTime + "," + uri.toString();
        }

        @NonNull
        public static UriGrant unflattenFromString(@NonNull String string) {
            Objects.requireNonNull(string);
            StringTokenizer tokenizer = new StringTokenizer(string, ",");
            int sourceUserId = Integer.parseInt(tokenizer.nextElement().toString());
            int targetUserId = Integer.parseInt(tokenizer.nextElement().toString());
            int userHandle = Integer.parseInt(tokenizer.nextElement().toString());
            String sourcePkg = tokenizer.nextElement().toString();
            String targetPkg = tokenizer.nextElement().toString();
            boolean prefix = Boolean.parseBoolean(tokenizer.nextElement().toString());
            int modeFlags = Integer.parseInt(tokenizer.nextElement().toString());
            long createdTime = Long.parseLong(tokenizer.nextElement().toString());
            StringBuilder uriString = new StringBuilder(tokenizer.nextElement().toString());
            while (tokenizer.hasMoreElements()) {
                uriString.append(",").append(tokenizer.nextElement().toString());
            }
            Uri uri = Uri.parse(uriString.toString());
            return new UriGrant(sourceUserId, targetUserId, userHandle, sourcePkg, targetPkg, uri,
                    prefix, modeFlags, createdTime);
        }
    }
}
