// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DexOptOptionsTest {
    @Test
    public void testParcelable() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.packages = new String[]{"android.package"};
        Parcel parcel = Parcel.obtain();
        dexOptOptions.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DexOptOptions dexOptOptions2 = DexOptOptions.CREATOR.createFromParcel(parcel);
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
