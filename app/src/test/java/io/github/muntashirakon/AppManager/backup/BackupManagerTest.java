// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.util.Arrays;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public class BackupManagerTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path rscBackupPath;
    private Path tmpBackupPath;

    @Before
    public void setUp() {
        assert classLoader != null;
        rscBackupPath = Paths.get(classLoader.getResource("backups/v4").getFile());
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir());
    }

    @Test
    public void testBackupV4Default() throws BackupException, IOException {
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.backup(null, null);
        BackupItems.BackupItem backupItem = BackupItems.findBackupItemV4(0, null, "dnsfilter.android");
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertFalse(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV2File().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertNull(metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.CURRENT_BACKUP_META_VERSION, metadata.info.version);
        assertTrue(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify(null);
    }


    @Test
    public void testBackupV4Custom() throws BackupException, IOException {
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110 | BackupFlags.BACKUP_MULTIPLE);
        bm.backup(new String[]{"test_backup"}, null);
        System.out.println(Arrays.toString(Prefs.Storage.getAppManagerDirectory().findFile("dnsfilter.android").listFileNames()));
        BackupItems.BackupItem backupItem = BackupItems.findBackupItemV4(0, "test_backup", "dnsfilter.android");
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertFalse(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV2File().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertEquals("test_backup", metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.CURRENT_BACKUP_META_VERSION, metadata.info.version);
        assertFalse(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify("0_test_backup");
    }

    @Test
    public void testRestoreV4Default() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.restore(null, null);
    }

    @Test
    public void testRestoreV4DefaultAsCustom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.restore(new String[]{"0"}, null);
    }

    @Test
    public void testRestoreV4Custom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.restore(new String[]{"0_test"}, null);
    }

    @Test
    public void testRestoreV4CustomWithoutUserIdFails() {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        assertThrows(IllegalArgumentException.class, () -> bm.restore(new String[]{"test"}, null));
    }

    @Test
    public void testDeleteV4Default() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.deleteBackup(null);
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0_test");
    }

    @Test
    public void testDeleteV4DefaultAsCustom() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.deleteBackup(new String[]{"0"});
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0_test");
    }

    @Test
    public void testDeleteV4Custom() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.deleteBackup(new String[]{"0_test"});
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0");
    }

    @Test
    public void testDeleteV4CustomWithoutUserIdFails() throws IOException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        assertThrows(IllegalArgumentException.class, () -> bm.deleteBackup(new String[]{"test"}));
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0");
        appBackupPath.findFile("0_test");
    }

    @Test
    public void testVerifyV4Default() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.verify(null);
    }

    @Test
    public void testVerifyV4DefaultAsCustom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.verify("0");
    }

    @Test
    public void testVerifyV4Custom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        bm.verify("0_test");
    }

    @Test
    public void testVerifyV4CustomWithoutUserIdFails() {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        UserPackagePair pair = new UserPackagePair("dnsfilter.android", 0);
        BackupManager bm = new BackupManager(pair, 1110);
        assertThrows(IllegalArgumentException.class, () -> bm.verify("test"));
    }
}