// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.backup.convert.ImportType;

@RunWith(RobolectricTestRunner.class)
public class BatchBackupImportOptionsTest {
    @Test
    public void testParcelable() {
        Uri uri = Uri.parse("file:///sdcard/OAndBackup");
        BatchBackupImportOptions options = new BatchBackupImportOptions(ImportType.OAndBackup, uri, false);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchBackupImportOptions options2 = BatchBackupImportOptions.CREATOR.createFromParcel(parcel);
        assertEquals(ImportType.OAndBackup, options2.getImportType());
        assertEquals(uri, options2.getDirectory());
        assertFalse(options2.isRemoveImportedDirectory());
    }
}