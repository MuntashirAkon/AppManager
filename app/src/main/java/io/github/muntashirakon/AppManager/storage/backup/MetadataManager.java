package io.github.muntashirakon.AppManager.storage.backup;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class MetadataManager implements Closeable {
    // For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
    public static class MetadataV1 implements Serializable {
        private static final long serialVersionUID = 974L;
        public String label;
        public String packageName;
        public String versionName;
        public long versionCode;
        public String[] sourceDirs;
        public String[] dataDirs;
        public boolean isSystem;
        public boolean isSplitApk;
        public String[] splitNames;
        public String[] splitSources;  // 2 * splitNames.length if public source is different, same index order as splitNames
        public boolean hasRules;
        public long backupTime;
        public String[] certSha256Checksum;
        public String[] sourceDirsSha256Checksum;
        public String[] dataDirsSha256Checksum;
        public int mode = 0;
    }

    private static MetadataManager metadataManager;
    public static MetadataManager getInstance(String packageName) {
        if (metadataManager == null) metadataManager = new MetadataManager(packageName);
        if (!metadataManager.packageName.equals(packageName)) {
            metadataManager.close();
            metadataManager = new MetadataManager(packageName);
        }
        return metadataManager;
    }

    @Override
    public void close() {
        if (dataChanged) writeMetadata();
    }

    private @NonNull String packageName;
    private MetadataV1 metadataV1;
    private AppManager appManager;
    private boolean dataChanged;
    private MetadataManager(@NonNull String packageName) {
        this.packageName = packageName;
        this.appManager = AppManager.getInstance();
        dataChanged = false;
    }

    public MetadataV1 getMetadataV1() {
        return metadataV1;
    }

    public void setMetadataV1(MetadataV1 metadataV1) {
        this.metadataV1 = metadataV1;
        dataChanged = true;
    }

    synchronized private void readMetadata() {
        File metadataFile = getMetadataFile();
        try (FileInputStream fileInputStream = new FileInputStream(metadataFile);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            metadataV1 = (MetadataV1) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    synchronized private void writeMetadata() {
        File metadataFile = getMetadataFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(metadataFile);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(metadataV1);
            dataChanged = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MetadataV1 setupMetadata() throws PackageManager.NameNotFoundException {
        PackageManager pm = appManager.getPackageManager();
        int flagSigningInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        @SuppressLint("PackageManagerGetSignatures")
        PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA | flagSigningInfo);
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        metadataV1 = new MetadataV1();
        metadataV1.label = applicationInfo.loadLabel(pm).toString();
        metadataV1.packageName = packageName;
        metadataV1.versionName = packageInfo.versionName;
        metadataV1.versionCode = PackageUtils.getVersionCode(packageInfo);
        metadataV1.sourceDirs = PackageUtils.getSourceDirs(applicationInfo);
        metadataV1.dataDirs = PackageUtils.getDataDirs(applicationInfo);
        metadataV1.isSystem = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadataV1.isSplitApk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            metadataV1.splitNames = applicationInfo.splitNames;
            if (metadataV1.splitNames != null) {
                metadataV1.isSplitApk = true;
                ArrayList<String> splitSources = new ArrayList<>(Arrays.asList(applicationInfo.splitSourceDirs));
                for (int i = 0; i< metadataV1.splitNames.length; ++i) {
                    if (!applicationInfo.splitSourceDirs[i].equals(applicationInfo.splitPublicSourceDirs[i])) {
                        splitSources.add(applicationInfo.splitPublicSourceDirs[i]);
                    }
                }
                metadataV1.splitSources = splitSources.toArray(new String[0]);
            }
        }
        metadataV1.hasRules = false;
        try (ComponentsBlocker cb = ComponentsBlocker.getInstance(appManager, packageName)) {
            metadataV1.hasRules = cb.entryCount() > 0;
        }
        metadataV1.backupTime = 0;
        metadataV1.certSha256Checksum = PackageUtils.getSigningCertSha256Checksum(packageInfo);
        // Initialize checksums
        metadataV1.sourceDirsSha256Checksum = new String[metadataV1.sourceDirs.length];
        metadataV1.dataDirsSha256Checksum = new String[metadataV1.dataDirs.length];
        return metadataV1;
    }

    private File getMetadataFile() {
        // TODO: Name: meta.am.mv1
        return null;
    }
}
