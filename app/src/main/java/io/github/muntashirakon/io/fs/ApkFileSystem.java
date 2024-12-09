// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import android.system.OsConstants;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.internal.zip.LocalFileRecord;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSink;
import com.android.apksig.util.DataSinks;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipFormatException;
import com.j256.simplemagic.ContentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

class ApkFileSystem extends VirtualFileSystem {
    public static final String TYPE = ContentType.APK.getMimeType();

    private final LruCache<String, Node<CentralDirectoryRecord>> mCache = new LruCache<>(100);
    @Nullable
    private RandomAccessFile mIn;
    @Nullable
    private DataSource mApk;
    @Nullable
    private ApkUtils.ZipSections mApkSections;
    @Nullable
    private List<CentralDirectoryRecord> mCdRecords;
    @Nullable
    private Node<CentralDirectoryRecord> mRootNode;

    protected ApkFileSystem(@NonNull Path zipFile) {
        super(zipFile);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected Path onMount() throws IOException {
        if (Objects.requireNonNull(getOptions()).remount && mIn != null && mRootNode != null) {
            // Remount requested, no need to generate anything if they're already generated.
            return Paths.get(this);
        }
        mIn = new RandomAccessFile(Objects.requireNonNull(getFile().getFile()), "r");
        mApk = DataSources.asDataSource(mIn);
        try {
            mApkSections = ApkUtils.findZipSections(mApk);
        } catch (ZipFormatException e) {
            return ExUtils.rethrowAsIOException(e);
        }
        try {
            mCdRecords = ZipUtils.parseZipCentralDirectory(mApk, mApkSections);
        } catch (ApkFormatException e) {
            return ExUtils.rethrowAsIOException(e);
        }
        mRootNode = buildTree(Objects.requireNonNull(mCdRecords));
        return Paths.get(this);
    }

    @Nullable
    @Override
    protected File onUnmount(@NonNull Map<String, List<Action>> actionList) throws IOException {
        mRootNode = null;
        mCache.evictAll();
        mApk = null;
        mApkSections = null;
        mCdRecords = null;
        if (mIn != null) {
            mIn.close();
            mIn = null;
        }
        // Does not support modification
        return null;
    }

    @Nullable
    @Override
    protected Node<?> getNode(String path) {
        checkMounted();
        Node<CentralDirectoryRecord> targetNode = mCache.get(path);
        if (targetNode == null) {
            if (path.equals(File.separator)) {
                targetNode = mRootNode;
            } else {
                targetNode = Objects.requireNonNull(mRootNode).getLastChild(Paths.sanitize(path, true));
            }
            if (targetNode != null) {
                mCache.put(path, targetNode);
            }
        }
        return targetNode;
    }

    @Override
    protected void invalidate(String path) {
        mCache.remove(path);
    }

    @Override
    protected long lastModified(@NonNull Node<?> node) {
        Long time = node.getExtra("mtime");
        if (time == null) {
            return getFile().lastModified();
        }
        return time;
    }

    @Override
    public long length(String path) {
        Node<?> targetNode = getNode(path);
        if (targetNode == null) {
            return -1;
        }
        if (targetNode.isDirectory()) {
            return 0;
        }
        if (!targetNode.isFile()) {
            return -1;
        }
        if (targetNode.getObject() == null) {
            // Check for cache
            File cachedFile = findCachedFile(targetNode);
            if (cachedFile != null) {
                return cachedFile.length();
            }
            return targetNode.isPhysical() ? -1 : 0;
        }
        return ((CentralDirectoryRecord) targetNode.getObject()).getSize();
    }

    @Override
    public boolean checkAccess(String path, int access) {
        Node<?> targetNode = getNode(path);
        if (access == OsConstants.F_OK) {
            return targetNode != null;
        }
        //noinspection RedundantIfStatement
        if (access == OsConstants.R_OK) {
            return true;
        }
        // X_OK, R_OK|X_OK, W_OK, R_OK|W_OK, R_OK|W_OK|X_OK are false
        return false;
    }

    @NonNull
    @Override
    protected InputStream getInputStream(@NonNull Node<?> node) throws IOException {
        return new FileInputStream(getCachedFile(node, false));
    }

    @Override
    protected void cacheFile(@NonNull Node<?> src, @NonNull File sink) throws IOException {
        CentralDirectoryRecord cdRecord = (CentralDirectoryRecord) src.getObject();
        if (cdRecord == null || mApk == null || mApkSections == null) {
            throw new FileNotFoundException("Class definition for " + src.getFullPath() + " is not found.");
        }
        DataSource lfhSection = mApk.slice(0, mApkSections.getZipCentralDirectoryOffset());
        try (FileOutputStream os = new FileOutputStream(sink)) {
            DataSink out = DataSinks.asDataSink(os);
            LocalFileRecord.outputUncompressedData(lfhSection, cdRecord, lfhSection.size(), out);
        } catch (ZipFormatException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private static Node<CentralDirectoryRecord> buildTree(@NonNull List<CentralDirectoryRecord> cdRecords) {
        Node<CentralDirectoryRecord> rootNode = new Node<>(null, File.separator);
        rootNode.addExtra("mtime", System.currentTimeMillis());
        for (CentralDirectoryRecord cdRecord : cdRecords) {
            buildTree(rootNode, cdRecord);
        }
        return rootNode;
    }

    // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
    private static void buildTree(@NonNull Node<CentralDirectoryRecord> rootNode, @NonNull CentralDirectoryRecord cdRecord) {
        String filename = Paths.sanitize(cdRecord.getName(), true);
        if (filename == null) {
            return;
        }
        String[] components = filename.split(File.separator);
        if (components.length < 1) return;
        long lastModTime = convertToUnixMillis(cdRecord.getLastModificationDate(), cdRecord.getLastModificationTime());
        Node<CentralDirectoryRecord> lastNode = rootNode;
        for (int i = 0; i < components.length - 1 /* last one will be set manually */; ++i) {
            Node<CentralDirectoryRecord> newNode = lastNode.getChild(components[i]);
            if (newNode == null) {
                // Add children
                newNode = new Node<>(lastNode, components[i]);
                newNode.addExtra("mtime", lastModTime);
                lastNode.addChild(newNode);
            }
            lastNode = newNode;
        }
        Node<CentralDirectoryRecord> finalNode = new Node<>(lastNode, components[components.length - 1],
                cdRecord.getName().endsWith(File.separator) ? null : cdRecord);
        finalNode.addExtra("mtime", lastModTime);
        lastNode.addChild(finalNode);
    }

    public static long convertToUnixMillis(int date, int time) {
        // Extract date components
        int day = (date & 0x1F);
        int month = ((date >> 5) & 0xF);
        int year = ((date >> 9) & 0x7F) + 1980;

        // Extract time components
        int second = (time & 0x1F) * 2; // Seconds are rounded to the nearest even second
        int minute = ((time >> 5) & 0x3F);
        int hour = ((time >> 11) & 0x1F);

        // Create a Calendar object
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1); // Java months are 0-based
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);

        // Convert to Unix millis
        return calendar.getTimeInMillis();
    }
}
