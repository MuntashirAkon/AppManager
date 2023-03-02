// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
import io.github.muntashirakon.AppManager.settings.Prefs;
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

    @Before
    public void setUp() {
        Prefs.Storage.setVolumePath("/tmp");
        Paths.get("/tmp/AppManager").delete();
        assert classLoader != null;
        backupLocation = new File(classLoader.getResource("SwiftBackup").getFile());
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
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory().findFile(PACKAGE_NAME_FULL).findFile("0_SB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation.findFile("data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation.findFile("data1.tar.gz.0")));
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
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory().findFile(PACKAGE_NAME_APK_INT).findFile("0_SB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation.findFile("data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK + ".xml"));
        SBConverter sbConvert = new SBConverter(xmlFile);
        sbConvert.convert();
        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory().findFile(PACKAGE_NAME_APK).findFile("0_SB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    // FIXME: 14/9/21 The tests below are disabled due to Rebolectric unable to parse APK files
//    @Test
//    public void convertApkSplitsTest() throws BackupException, IOException {
//        final List<String> expectedApkFiles = Arrays.asList("base.apk",
//                "split_config.en.apk",
//                "split_config.hdpi.apk");
//        Collections.sort(expectedApkFiles);
//        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_SPLITS + ".xml"));
//        SBConverter sbConvert = new SBConverter(xmlFile);
//        sbConvert.convert();
//        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory().findFile(PACKAGE_NAME_APK_SPLITS).findFile("0_SB");
//        // Verify source
//        List<String> actualApkFiles = TarUtilsTest.getFileNamesGZip(Collections.singletonList(
//                newBackupLocation.findFile("source.tar.gz.0")));
//        Collections.sort(actualApkFiles);
//        assertEquals(expectedApkFiles, actualApkFiles);
//        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
//        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
//    }
//
//
//    @Test
//    public void convertApkObbTest() throws BackupException, IOException {
//        Path xmlFile = Paths.get(new File(backupLocation, PACKAGE_NAME_APK_OBB + ".xml"));
//        SBConverter sbConvert = new SBConverter(xmlFile);
//        sbConvert.convert();
//        Path newBackupLocation = Prefs.Storage.getAppManagerDirectory().findFile(PACKAGE_NAME_APK_OBB).findFile("0_SB");
//        // Verify source
//        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
//                newBackupLocation.findFile("source.tar.gz.0"))));
//        assertEquals(Collections.singletonList("test-assets.obb"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
//                newBackupLocation.findFile("data0.tar.gz.0"))));
//        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
//    }
}
