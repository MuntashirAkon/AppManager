// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BatchAppOpsOptionsTest {
    @Test
    public void testParcelable() {
        int[] array = new int[]{12, 13, 14};
        BatchAppOpsOptions options = new BatchAppOpsOptions(array, 0);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchAppOpsOptions options2 = BatchAppOpsOptions.CREATOR.createFromParcel(parcel);
        assertArrayEquals(array, options2.getAppOps());
        assertEquals(0, options2.getMode());
    }
}