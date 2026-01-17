// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class SBConverterTest {
    private static final String PACKAGE_NAME_FULL = "dnsfilter.android";
    private static final String PACKAGE_NAME_APK_INT = "org.billthefarmer.editor";
    private static final String PACKAGE_NAME_APK = "ademar.textlauncher";
    private static final String PACKAGE_NAME_APK_SPLITS = "com.google.android.samples.dynamicfeatures.ondemand";
    private static final String PACKAGE_NAME_APK_OBB = "com.test.app";

    private final ClassLoader classLoader = getClass().getClassLoader();
    private File backupLocation;
    private Path tmpBackupPath;

    @Before
    public void setUp() throws IOException {
        assert classLoader != null;
        backupLocation = new File(classLoader.getResource("SwiftBackup").getFile());
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("backup-dir");
        Prefs.Storage.setVolumePath(tmpBackupPath.toString());
    }

    @After
    public void tearDown() {
        tmpBackupPath.delete();
    }

    @Test
    public void convertFullTest() throws BackupException, IOException {
        final List<String> internalStorage = Collections.singletonList("code_cache/");
        final List<String> externalStorage = Arrays.asList("files/",
                "files/PersonalDNSFilter/",
                "files/PersonalDNSFilter/dnsfilter.conf",
                "files/PersonalDNSFilter/additionalHosts.txt",
                "files/PersonalDNSFilter/VERSION.TXT");
        Collections.sort(internalStorage);
        Collections.sort(externalStorage);

        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_FULL + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(1)));
        Collections.sort(files);
        assertEquals(externalStorage, files);
    }

    @Test
    public void convertApkInternalStorageTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("code_cache/",
                "shared_prefs/",
                "shared_prefs/org.billthefarmer.editor_preferences.xml");
        Collections.sort(internalStorage);
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_INT + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        List<String> files = TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0)));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles())));
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkSplitsTest() throws BackupException, IOException {
        final List<String> expectedApkFiles = Arrays.asList("base.apk",
                "split_config.en.apk",
                "split_config.hdpi.apk");
        Collections.sort(expectedApkFiles);
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_SPLITS + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
                .findFile(BackupItems.BACKUP_DIRECTORY)
                .listFiles()[0];
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
        // Verify source
        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
        assertEquals("SB", metadataV5.metadata.backupName);
        List<String> actualApkFiles = TarUtilsTest.getFileNamesGZip(
                Arrays.asList(backupItem.getSourceFiles()));
        Collections.sort(actualApkFiles);
        assertEquals(expectedApkFiles, actualApkFiles);
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }


    // FIXME: 14/9/21 The test below is disabled due to Rebolectric unable to parse APK files
//    @Test
//    public void convertApkObbTest() throws BackupException, IOException {
//        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_OBB + ".xml"));
//        SBConverter sbConvert = new SBConverter(xmlFile);
//        sbConvert.convert();
//        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory()
//                .findFile(BackupItems.BACKUP_DIRECTORY)
//                .listFiles()[0];
//        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(newBackupLocation.getName()));
//        // Verify source
//        BackupMetadataV5 metadataV5 = backupItem.getMetadata();
//        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadataV5.info.version);
//        assertEquals("SB", metadataV5.metadata.backupName);
//        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(
//                Arrays.asList(backupItem.getSourceFiles())));
//        assertEquals(Collections.singletonList("test-assets.obb"), TarUtilsTest.getFileNamesGZip(Arrays.asList(backupItem.getDataFiles(0))));
//        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
//    }
}
