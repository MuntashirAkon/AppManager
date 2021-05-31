// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.TestApplication;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
public class TBConvertTest {
    private static final String PACKAGE_NAME_FULL = "dnsfilter.android";
    private static final String PACKAGE_NAME_APK_INT = "org.billthefarmer.editor";
    private static final String PACKAGE_NAME_INT = "ca.cmetcalfe.locationshare";
    private static final String PACKAGE_NAME_APK = "ademar.textlauncher";

    private final ClassLoader classLoader = getClass().getClassLoader();
    private File backupLocation;

    @Before
    public void setUp() {
        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, "/tmp");
        IOUtils.deleteDir(new File("/tmp/AppManager"));
        assert classLoader != null;
        backupLocation = new File(classLoader.getResource(TBConvert.PATH_SUFFIX).getFile());
    }

    @Test
    public void convertFullTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("code_cache/",
                "code_cache/com.android.opengl.shaders_cache");
        final List<String> externalStorage = Arrays.asList("files/",
                "files/PersonalDNSFilter/",
                "files/PersonalDNSFilter/dnsfilter.conf",
                "files/PersonalDNSFilter/additionalHosts.txt",
                "files/PersonalDNSFilter/VERSION.TXT",
                "files/PersonalDNSFilter/log/",
                "files/PersonalDNSFilter/log/trafficlog/",
                "files/PersonalDNSFilter/log/trafficlog/trafficlog_0.log",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/IDX_VERSION",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx0",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx1",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx2",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx3",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx4",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx5",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx6",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx7",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx8",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx9",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx10",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx11",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx12",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx13",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.idx/idx14",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT",
                "files/PersonalDNSFilter/FILTERHOSTS.TXT.DLD_CNT");
        Collections.sort(internalStorage);
        Collections.sort(externalStorage);
        File propFile = new File(backupLocation, PACKAGE_NAME_FULL + "-20210529-164214.properties");
        TBConvert tbConvert = new TBConvert(propFile);
        tbConvert.convert();
        File newBackupLocation = new File(new File(AppPref.getAppManagerDirectory(), PACKAGE_NAME_FULL), "0_TB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                new File(newBackupLocation, "source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(new File(newBackupLocation, "data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(new File(newBackupLocation, "data1.tar.gz.0")));
        Collections.sort(files);
        assertEquals(externalStorage, files);
    }

    @Test
    public void convertApkInternalStorageTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("code_cache/",
                "code_cache/com.android.opengl.shaders_cache",
                "shared_prefs/",
                "shared_prefs/org.billthefarmer.editor_preferences.xml");
        Collections.sort(internalStorage);
        File propFile = new File(backupLocation, PACKAGE_NAME_APK_INT + "-20210529-164210.properties");
        TBConvert tbConvert = new TBConvert(propFile);
        tbConvert.convert();
        File newBackupLocation = new File(new File(AppPref.getAppManagerDirectory(), PACKAGE_NAME_APK_INT), "0_TB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                new File(newBackupLocation, "source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(new File(newBackupLocation, "data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        assertFalse(new File(newBackupLocation, "data1.tar.gz.0").exists());
    }

    @Test
    public void convertInternalStorageOnlyTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("code_cache/",
                "code_cache/com.android.opengl.shaders_cache",
                "shared_prefs/",
                "shared_prefs/ca.cmetcalfe.locationshare_preferences.xml",
                "shared_prefs/_has_set_default_values.xml");
        Collections.sort(internalStorage);
        File propFile = new File(backupLocation, PACKAGE_NAME_INT + "-20210529-164219.properties");
        TBConvert tbConvert = new TBConvert(propFile);
        tbConvert.convert();
        File newBackupLocation = new File(new File(AppPref.getAppManagerDirectory(), PACKAGE_NAME_INT), "0_TB");
        // Verify source
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(new File(newBackupLocation, "data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        assertFalse(new File(newBackupLocation, "source.tar.gz.0").exists());
        assertFalse(new File(newBackupLocation, "data1.tar.gz.0").exists());
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        File propFile = new File(backupLocation, PACKAGE_NAME_APK + "-20210530-111646.properties");
        TBConvert tbConvert = new TBConvert(propFile);
        tbConvert.convert();
        File newBackupLocation = new File(new File(AppPref.getAppManagerDirectory(), PACKAGE_NAME_APK), "0_TB");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                new File(newBackupLocation, "source.tar.gz.0"))));
        assertFalse(new File(newBackupLocation, "data0.tar.gz.0").exists());
        assertFalse(new File(newBackupLocation, "data1.tar.gz.0").exists());
    }
}