// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri;

import android.net.Uri;
import android.os.RemoteException;
import android.util.Xml;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.AtomicProxyFile;
import io.github.muntashirakon.io.ProxyOutputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.android.internal.util.XmlUtils.*;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

public class UriManager {
    public static final String TAG = "UriManager";

    private final AtomicProxyFile mGrantFile;

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
        mGrantFile = new AtomicProxyFile(new File(OsEnvironment.getDataSystemDirectory(), "urigrants.xml"));
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

        ProxyOutputStream fos = null;
        try {
            fos = mGrantFile.startWrite();
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
            mGrantFile.finishWrite(fos);
            Runner.runCommand(new String[]{"chmod", "600", mGrantFile.getBaseFile().getAbsolutePath()});
            Runner.runCommand(new String[]{"chown", "1000:1000", mGrantFile.getBaseFile().getAbsolutePath()});
            Runner.runCommand(new String[]{"restorecon", mGrantFile.getBaseFile().getAbsolutePath()});
        } catch (IOException e) {
            Log.e(TAG, "Failed writing Uri grants", e);
            mGrantFile.failWrite(fos);
        }
    }

    private void readGrantedUriPermissions() {
        final long now = System.currentTimeMillis();
        try (InputStream fis = mGrantFile.openRead()) {
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
        } catch (IOException | XmlPullParserException | RemoteException e) {
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
