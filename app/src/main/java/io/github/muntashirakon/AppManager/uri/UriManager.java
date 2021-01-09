package io.github.muntashirakon.AppManager.uri;

import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import static com.android.internal.util.XmlUtils.readBooleanAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readLongAttribute;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

public class UriManager {
    public static final String TAG = "UriManager";

    private final PrivilegedFile mGrantFile;

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
        mGrantFile = new PrivilegedFile(new File(OsEnvironment.getDataSystemDirectory(), "urigrants.xml"));
        readGrantedUriPermissions();
    }

    @Nullable
    public ArrayList<UriGrant> getGrantedUris(String packageName) {
        return uriGrantsHashMap.get(packageName);
    }

    private void readGrantedUriPermissions() {
        final long now = System.currentTimeMillis();
        InputStream fis = null;
        try {
            fis = new ByteArrayInputStream(IOUtils.getFileContent(mGrantFile).getBytes());
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
                        ArrayList<UriGrant> uriGrants = uriGrantsHashMap.get(targetPkg);
                        if (uriGrants == null) {
                            uriGrants = new ArrayList<>();
                            uriGrantsHashMap.put(targetPkg, uriGrants);
                        }
                        uriGrants.add(uriGrant);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Missing grants is okay
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Failed reading Uri grants", e);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public static class UriGrant {
        final int sourceUserId;
        final int targetUserId;
        final int userHandle;
        final String sourcePkg;
        final String targetPkg;
        final Uri uri;
        final boolean prefix;
        final int modeFlags;
        final long createdTime;

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
            return "UriGrant{" +
                    "sourceUserId=" + sourceUserId +
                    ", targetUserId=" + targetUserId +
                    ", userHandle=" + userHandle +
                    ", sourcePkg='" + sourcePkg + '\'' +
                    ", targetPkg='" + targetPkg + '\'' +
                    ", uri=" + uri +
                    ", prefix=" + prefix +
                    ", modeFlags=" + modeFlags +
                    ", createdTime=" + createdTime +
                    '}';
        }

        public String flattenToString() {
            return sourceUserId + "," + targetUserId + "," + userHandle + "," + sourcePkg + "," +
                    targetPkg + "," + prefix + "," + modeFlags + "," + createdTime + "," + uri.toString();
        }

        @NonNull
        public static UriGrant unflattenFromString(@NonNull String string) {
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
