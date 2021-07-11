// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class OABConvertTest {
    private static final String PACKAGE_NAME_FULL = "dnsfilter.android";
    private static final String PACKAGE_NAME_APK_INT = "org.billthefarmer.editor";
    private static final String PACKAGE_NAME_INT = "ca.cmetcalfe.locationshare";
    private static final String PACKAGE_NAME_APK = "ademar.textlauncher";
    private final ClassLoader classLoader = getClass().getClassLoader();
    private final Context context = AppManager.getContext();

    @Before
    public void setUp() {
        AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, "file:///tmp");
        IOUtils.deleteDir(new File("/tmp/AppManager"));
    }

    @Test
    public void convertFullTest() throws BackupException, IOException {
        final List<String> internalStorage = Collections.emptyList();
        final List<String> externalStorage = Arrays.asList("files/PersonalDNSFilter/dnsfilter.conf",
                "files/PersonalDNSFilter/additionalHosts.txt",
                "files/PersonalDNSFilter/VERSION.TXT",
                "files/PersonalDNSFilter/log/trafficlog/trafficlog_0.log",
                "files/PersonalDNSFilter/dnsperf.info",
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
        assert classLoader != null;
        Path backupLocation = new Path(context, new File(classLoader.getResource(OABConvert.PATH_SUFFIX).getFile()))
                .findFile(PACKAGE_NAME_FULL);
        OABConvert oabConvert = new OABConvert(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = AppPref.getAppManagerDirectory().findFile(PACKAGE_NAME_FULL).findFile("0_OAndBackup");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation
                .findFile("data0.tar.gz.0")));
        Collections.sort(files);
        assertEquals(internalStorage, files);
        files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation.findFile("data1.tar.gz.0")));
        Collections.sort(files);
        assertEquals(externalStorage, files);
    }

    @Test
    public void convertApkInternalStorageTest() throws BackupException, IOException {
        final List<String> internalStorage = Collections.singletonList("shared_prefs/org.billthefarmer.editor_preferences.xml");
        assert classLoader != null;
        Path backupLocation = new Path(context, new File(classLoader.getResource(OABConvert.PATH_SUFFIX).getFile()))
                .findFile(PACKAGE_NAME_APK_INT);
        OABConvert oabConvert = new OABConvert(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = AppPref.getAppManagerDirectory().findFile(PACKAGE_NAME_APK_INT).findFile("0_OAndBackup");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation
                .findFile("data0.tar.gz.0")));
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertInternalStorageOnlyTest() throws BackupException, IOException {
        final List<String> internalStorage = Arrays.asList("shared_prefs/ca.cmetcalfe.locationshare_preferences.xml",
                "shared_prefs/_has_set_default_values.xml");
        assert classLoader != null;
        Path backupLocation = new Path(context, new File(classLoader.getResource(OABConvert.PATH_SUFFIX).getFile()))
                .findFile(PACKAGE_NAME_INT);
        OABConvert oabConvert = new OABConvert(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = AppPref.getAppManagerDirectory().findFile(PACKAGE_NAME_INT).findFile("0_OAndBackup");
        // Verify source
        List<String> files = TarUtilsTest.getFileNamesGZip(Collections.singletonList(newBackupLocation
                .findFile("data0.tar.gz.0")));
        assertEquals(internalStorage, files);
        assertFalse(newBackupLocation.hasFile("source.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }

    @Test
    public void convertApkOnlyTest() throws BackupException, IOException {
        assert classLoader != null;
        Path backupLocation = new Path(context, new File(classLoader.getResource(OABConvert.PATH_SUFFIX).getFile()))
                .findFile(PACKAGE_NAME_APK);
        OABConvert oabConvert = new OABConvert(backupLocation);
        oabConvert.convert();
        Path newBackupLocation = AppPref.getAppManagerDirectory().findFile(PACKAGE_NAME_APK).findFile("0_OAndBackup");
        // Verify source
        assertEquals(Collections.singletonList("base.apk"), TarUtilsTest.getFileNamesGZip(Collections.singletonList(
                newBackupLocation.findFile("source.tar.gz.0"))));
        assertFalse(newBackupLocation.hasFile("data0.tar.gz.0"));
        assertFalse(newBackupLocation.hasFile("data1.tar.gz.0"));
    }
}
