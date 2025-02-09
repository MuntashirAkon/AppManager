// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions;

@RunWith(RobolectricTestRunner.class)
public class BatchDexOptOptionsTest {
    @Test
    public void testParcelable() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.packages = new String[]{"android.package"};
        BatchDexOptOptions options = new BatchDexOptOptions(dexOptOptions);
        Parcel parcel = Parcel.obtain();
        options.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BatchDexOptOptions options2 = BatchDexOptOptions.CREATOR.createFromParcel(parcel);
        DexOptOptions dexOptOptions2 = options2.getDexOptOptions();
        assertArrayEquals(dexOptOptions.packages, dexOptOptions2.packages);
        assertEquals(dexOptOptions.compilerFiler, dexOptOptions2.compilerFiler);
        assertEquals(dexOptOptions.compileLayouts, dexOptOptions2.compileLayouts);
        assertEquals(dexOptOptions.clearProfileData, dexOptOptions2.clearProfileData);
        assertEquals(dexOptOptions.checkProfiles, dexOptOptions2.checkProfiles);
        assertEquals(dexOptOptions.bootComplete, dexOptOptions2.bootComplete);
        assertEquals(dexOptOptions.forceCompilation, dexOptOptions2.forceCompilation);
        assertEquals(dexOptOptions.forceDexOpt, dexOptOptions2.forceDexOpt);
    }
}