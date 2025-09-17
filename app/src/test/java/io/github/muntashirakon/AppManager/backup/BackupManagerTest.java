// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public class BackupManagerTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path rscBackupPath;
    private Path tmpBackupPath;
    private int mDefaultMetaVersion;

    @Before
    public void setUp() {
        assert classLoader != null;
        mDefaultMetaVersion = MetadataManager.getCurrentBackupMetaVersion();
        rscBackupPath = Paths.get(classLoader.getResource("backups/v4").getFile());
        tmpBackupPath = Paths.get(RoboUtils.getTestBaseDir());
    }

    @After
    public void tearDown() {
        MetadataManager.setCurrentBackupMetaVersion(mDefaultMetaVersion);
        for (Path path : tmpBackupPath.listFiles()) {
            path.delete();
        }
    }

    @Test
    public void testBackupV5Default() throws BackupException, IOException {
        MetadataManager.setCurrentBackupMetaVersion(5);
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        BackupOpOptions options = new BackupOpOptions("dnsfilter.android", 0, 1110, null, true);
        bm.backup(options, null);
        Path[] backupPaths = Prefs.Storage.getAppManagerDirectory().findFile(BackupItems.BACKUP_DIRECTORY).listFiles();
        assertEquals(1, backupPaths.length);
        Path backupPath = backupPaths[0];
        String backupUuid = backupPath.getName();
        assertTrue(BackupUtils.isUuid(backupUuid));
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(backupUuid));
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertTrue(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV5File(false).exists());
        assertTrue(backupItem.getInfoFile().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertNull(metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadata.info.version);
        assertTrue(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify(BackupUtils.getV5RelativeDir(backupUuid));
    }

    @Test
    public void testBackupV5Custom() throws BackupException, IOException {
        MetadataManager.setCurrentBackupMetaVersion(5);
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        BackupOpOptions options = new BackupOpOptions("dnsfilter.android", 0, 1110 | BackupFlags.BACKUP_MULTIPLE, "test_backup", false);
        bm.backup(options, null);
        Path[] backupPaths = Prefs.Storage.getAppManagerDirectory().findFile(BackupItems.BACKUP_DIRECTORY).listFiles();
        assertEquals(1, backupPaths.length);
        Path backupPath = backupPaths[0];
        String backupUuid = backupPath.getName();
        assertTrue(BackupUtils.isUuid(backupUuid));
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem(BackupUtils.getV5RelativeDir(backupUuid));
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertTrue(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV5File(false).exists());
        assertTrue(backupItem.getInfoFile().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertEquals("test_backup", metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadata.info.version);
        assertFalse(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify(BackupUtils.getV5RelativeDir(backupUuid));
    }

    @Test
    public void testBackupV4Default() throws BackupException, IOException {
        MetadataManager.setCurrentBackupMetaVersion(4);
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        BackupOpOptions options = new BackupOpOptions("dnsfilter.android", 0, 1110, null, true);
        bm.backup(options, null);
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem("dnsfilter.android/0");
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertFalse(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV2File().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertNull(metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadata.info.version);
        assertTrue(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify("dnsfilter.android/0");
    }


    @Test
    public void testBackupV4Custom() throws BackupException, IOException {
        MetadataManager.setCurrentBackupMetaVersion(4);
        // First run restore
        testRestoreV4Default();
        // Do backup
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        BackupOpOptions options = new BackupOpOptions("dnsfilter.android", 0, 1110 | BackupFlags.BACKUP_MULTIPLE, "test_backup", false);
        bm.backup(options, null);
        BackupItems.BackupItem backupItem = BackupItems.findBackupItem("dnsfilter.android/0_test_backup");
        assertEquals(1, backupItem.getSourceFiles().length);
        assertEquals(1, backupItem.getDataFiles(0).length);
        assertEquals(1, backupItem.getDataFiles(1).length);
        assertTrue(backupItem.getIconFile().exists());
        assertFalse(backupItem.isV5AndUp());
        assertTrue(backupItem.getMetadataV2File().exists());
        BackupMetadataV5 metadata = backupItem.getMetadata();
        assertEquals("test_backup", metadata.metadata.backupName);
        assertEquals(0, metadata.info.userId);
        assertEquals(MetadataManager.getCurrentBackupMetaVersion(), metadata.info.version);
        assertFalse(metadata.isBaseBackup());
        assertEquals("dnsfilter.android", metadata.metadata.packageName);
        assertEquals(2, metadata.metadata.dataDirs.length);
        assertEquals(BuildConfig.APPLICATION_ID, metadata.metadata.installer);
        assertFalse(metadata.metadata.isSplitApk);
        bm.verify("dnsfilter.android/0_test_backup");
    }

    @Test
    public void testRestoreV4Default() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        RestoreOpOptions options = new RestoreOpOptions("dnsfilter.android", 0, null, 1110);
        bm.restore(options, null);
    }

    @Test
    public void testRestoreV4DefaultBatchOps() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, null);
        bm.restore(options.getRestoreOpOptions("dnsfilter.android", 0), null);
    }

    @Test
    public void testRestoreV4DefaultAsCustom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        RestoreOpOptions options = new RestoreOpOptions("dnsfilter.android", 0, "dnsfilter.android/0", 1110);
        bm.restore(options, null);
    }

    @Test
    public void testRestoreV4DefaultAsRelativeDirBatchOps() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, new String[]{"dnsfilter.android/0"});
        bm.restore(options.getRestoreOpOptions("dnsfilter.android", 0), null);
    }

    @Test
    public void testRestoreV4Custom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        RestoreOpOptions options = new RestoreOpOptions("dnsfilter.android", 0, "dnsfilter.android/0_test", 1110);
        bm.restore(options, null);
    }

    @Test
    public void testRestoreV4CustomAsBackupNameBatchOps() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, new String[]{"test"}, null);
        bm.restore(options.getRestoreOpOptions("dnsfilter.android", 0), null);
    }

    @Test
    public void testRestoreV4CustomAsRelativeDirBatchOps() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, new String[]{"dnsfilter.android/0_test"});
        bm.restore(options.getRestoreOpOptions("dnsfilter.android", 0), null);
    }

    @Test
    public void testDeleteV4Default() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        DeleteOpOptions options = new DeleteOpOptions("dnsfilter.android", 0, null);
        bm.deleteBackup(options);
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0_test");
    }

    @Test
    public void testDeleteV4DefaultBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, null);
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
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
        BackupManager bm = new BackupManager();
        DeleteOpOptions options = new DeleteOpOptions("dnsfilter.android", 0, new String[]{"dnsfilter.android/0"});
        bm.deleteBackup(options);
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0_test");
    }

    @Test
    public void testDeleteV4DefaultRelativeDirBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, new String[]{"dnsfilter.android/0"});
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
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
        BackupManager bm = new BackupManager();
        DeleteOpOptions options = new DeleteOpOptions("dnsfilter.android", 0, new String[]{"dnsfilter.android/0_test"});
        bm.deleteBackup(options);
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0");
    }

    @Test
    public void testDeleteV4CustomRelativeDirBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, new String[]{"dnsfilter.android/0_test"});
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0");
    }

    @Test
    public void testDeleteV4CustomBackupNameBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, new String[]{"test"}, null);
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
        assertTrue(amDir.exists());
        Path appBackupPath = amDir.findFile("dnsfilter.android");
        appBackupPath.findFile("0");
    }

    @Test
    public void testDeleteV4DefaultAndCustom() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        BackupManager bm = new BackupManager();
        DeleteOpOptions options = new DeleteOpOptions("dnsfilter.android", 0, new String[]{"dnsfilter.android/0", "dnsfilter.android/0_test"});
        bm.deleteBackup(options);
        assertTrue(amDir.exists());
        assertFalse(amDir.hasFile("dnsfilter.android"));
    }

    @Test
    public void testDeleteV4DefaultAndCustomRelativeDirsBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, null, new String[]{"dnsfilter.android/0", "dnsfilter.android/0_test"});
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
        assertTrue(amDir.exists());
        assertFalse(amDir.hasFile("dnsfilter.android"));
    }

    @Test
    public void testDeleteV4DefaultAndCustomBackupNamesBatchOps() throws IOException, BackupException {
        Prefs.Storage.setVolumePath(tmpBackupPath.getUri().toString());
        assertNotNull(rscBackupPath.findFile("AppManager")
                .copyTo(tmpBackupPath, true));
        Path amDir = tmpBackupPath.findFile("AppManager");
        assertTrue(amDir.exists());
        new AppDb().loadInstalledOrBackedUpApplications(ContextUtils.getContext());
        BackupManager bm = new BackupManager();
        BatchBackupOptions options = new BatchBackupOptions(1110, new String[]{null, "test"}, null);
        bm.deleteBackup(options.getDeleteOpOptions("dnsfilter.android", 0));
        assertTrue(amDir.exists());
        assertFalse(amDir.hasFile("dnsfilter.android"));
    }

    @Test
    public void testVerifyV4Default() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        bm.verify("dnsfilter.android/0");
    }

    @Test
    public void testVerifyV4DefaultAsCustom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        bm.verify("dnsfilter.android/0");
    }

    @Test
    public void testVerifyV4Custom() throws BackupException {
        Prefs.Storage.setVolumePath(rscBackupPath.getUri().toString());
        BackupManager bm = new BackupManager();
        bm.verify("dnsfilter.android/0_test");
    }
}