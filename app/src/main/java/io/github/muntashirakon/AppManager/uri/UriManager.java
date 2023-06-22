// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.compat.xml.TypedXmlPullParser;
import io.github.muntashirakon.compat.xml.TypedXmlSerializer;
import io.github.muntashirakon.compat.xml.Xml;
import io.github.muntashirakon.io.AtomicExtendedFile;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Paths;

public class UriManager {
    public static final String TAG = "UriManager";

    private final AtomicExtendedFile mGrantFile;

    private final HashMap<String, ArrayList<UriGrant>> mUriGrantsHashMap = new HashMap<>();

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
        mGrantFile = new AtomicExtendedFile(Objects.requireNonNull(Objects.requireNonNull(Paths.build(
                OsEnvironment.getDataSystemDirectory(), "urigrants.xml")).getFile()));
        readGrantedUriPermissions();
    }

    @Nullable
    public ArrayList<UriGrant> getGrantedUris(String packageName) {
        synchronized (this) {
            return mUriGrantsHashMap.get(packageName);
        }
    }

    public void grantUri(@NonNull UriGrant uriGrant) {
        synchronized (this) {
            ArrayList<UriGrant> uriGrants = mUriGrantsHashMap.get(uriGrant.targetPkg);
            if (uriGrants == null) {
                uriGrants = new ArrayList<>();
                mUriGrantsHashMap.put(uriGrant.targetPkg, uriGrants);
            }
            uriGrants.add(uriGrant);
        }
    }

    @SuppressWarnings("OctalInteger")
    public void writeGrantedUriPermissions() {
        // Snapshot permissions so we can persist without lock
        List<UriGrant> persist = new ArrayList<>();
        synchronized (this) {
            for (List<UriGrant> uriGrants : mUriGrantsHashMap.values()) {
                persist.addAll(uriGrants);
            }
        }

        FileOutputStream fos = null;
        try {
            fos = mGrantFile.startWrite();
            TypedXmlSerializer out = Xml.resolveSerializer(fos);
            out.startDocument(null, true);
            out.startTag(null, TAG_URI_GRANTS);
            for (UriGrant perm : persist) {
                out.startTag(null, TAG_URI_GRANT);
                out.attributeInt(null, ATTR_SOURCE_USER_ID, perm.sourceUserId);
                out.attributeInt(null, ATTR_TARGET_USER_ID, perm.targetUserId);
                out.attributeInterned(null, ATTR_SOURCE_PKG, perm.sourcePkg);
                out.attributeInterned(null, ATTR_TARGET_PKG, perm.targetPkg);
                out.attribute(null, ATTR_URI, String.valueOf(perm.uri));
                out.attributeBoolean(null, ATTR_PREFIX, perm.prefix);
                out.attributeInt(null, ATTR_MODE_FLAGS, perm.modeFlags);
                out.attributeLong(null, ATTR_CREATED_TIME, perm.createdTime);
                out.endTag(null, TAG_URI_GRANT);
            }
            out.endTag(null, TAG_URI_GRANTS);
            out.endDocument();
            mGrantFile.finishWrite(fos);
            ExtendedFile file = mGrantFile.getBaseFile();
            file.setMode(0600);
            file.setUidGid(1000, 1000);
            file.restoreSelinuxContext();
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to change file permissions.", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed writing Uri grants", e);
            mGrantFile.failWrite(fos);
        }
    }

    private void readGrantedUriPermissions() {
        final long now = System.currentTimeMillis();
        try (InputStream is = new BufferedInputStream(mGrantFile.openRead())) {
            final TypedXmlPullParser in = Xml.resolvePullParser(is);
            int type;
            while ((type = in.next()) != END_DOCUMENT) {
                final String tag = in.getName();
                if (type == START_TAG) {
                    if (TAG_URI_GRANT.equals(tag)) {
                        final int sourceUserId;
                        final int targetUserId;
                        final int userHandle = in.getAttributeInt(null, ATTR_USER_HANDLE,
                                UserHandleHidden.USER_NULL);
                        if (userHandle != UserHandleHidden.USER_NULL) {
                            // For backwards compatibility.
                            sourceUserId = userHandle;
                            targetUserId = userHandle;
                        } else {
                            sourceUserId = in.getAttributeInt(null, ATTR_SOURCE_USER_ID);
                            targetUserId = in.getAttributeInt(null, ATTR_TARGET_USER_ID);
                        }
                        final String sourcePkg = in.getAttributeValue(null, ATTR_SOURCE_PKG);
                        final String targetPkg = in.getAttributeValue(null, ATTR_TARGET_PKG);
                        final Uri uri = Uri.parse(in.getAttributeValue(null, ATTR_URI));
                        final boolean prefix = in.getAttributeBoolean(null, ATTR_PREFIX, false);
                        final int modeFlags = in.getAttributeInt(null, ATTR_MODE_FLAGS);
                        final long createdTime = in.getAttributeLong(null, ATTR_CREATED_TIME, now);

                        UriGrant uriGrant = new UriGrant(sourceUserId, targetUserId, userHandle,
                                sourcePkg, targetPkg, uri, prefix, modeFlags, createdTime);
                        synchronized (this) {
                            ArrayList<UriGrant> uriGrants = mUriGrantsHashMap.get(targetPkg);
                            if (uriGrants == null) {
                                uriGrants = new ArrayList<>();
                                mUriGrantsHashMap.put(targetPkg, uriGrants);
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
