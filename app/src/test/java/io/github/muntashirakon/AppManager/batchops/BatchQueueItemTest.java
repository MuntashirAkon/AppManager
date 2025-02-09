// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.*;

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupImportOptions;

@RunWith(RobolectricTestRunner.class)
public class BatchQueueItemTest {
    @Test
    public void testBackupImportParcelable() {
        Uri uri = Uri.parse("file:///sdcard/OAndBackup");
        BatchBackupImportOptions options = new BatchBackupImportOptions(ImportType.OAndBackup, uri, false);
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(BatchOpsManager.OP_IMPORT_BACKUPS, null, null, options);
        Parcel parcel = Parcel.obtain();
        queueItem.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchQueueItem queueItem2 = BatchQueueItem.CREATOR.createFromParcel(parcel);
        assertEquals(BatchOpsManager.OP_IMPORT_BACKUPS, queueItem2.getOp());
        BatchBackupImportOptions options2 = (BatchBackupImportOptions) Objects.requireNonNull(queueItem2.getOptions());
        assertEquals(ImportType.OAndBackup, options2.getImportType());
        assertEquals(uri, options2.getDirectory());
        assertFalse(options2.isRemoveImportedDirectory());
    }
}