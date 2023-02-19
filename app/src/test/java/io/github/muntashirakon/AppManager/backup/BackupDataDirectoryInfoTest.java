// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupDataDirectoryInfoTest {
    @Test
    public void getInfoInternal() {
        BackupDataDirectoryInfo ceInfo1 = BackupDataDirectoryInfo.getInfo("/data/data/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_INTERNAL, ceInfo1.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_CREDENTIAL_PROTECTED, ceInfo1.subtype);
        assertTrue(ceInfo1.isMounted);
        BackupDataDirectoryInfo ceInfo2 = BackupDataDirectoryInfo.getInfo("/data/user/0/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_INTERNAL, ceInfo2.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_CREDENTIAL_PROTECTED, ceInfo2.subtype);
        assertTrue(ceInfo2.isMounted);
        BackupDataDirectoryInfo ceInfo3 = BackupDataDirectoryInfo.getInfo("/data/user/10/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_UNKNOWN, ceInfo3.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_CUSTOM, ceInfo3.subtype);
        assertTrue(ceInfo3.isMounted);
        BackupDataDirectoryInfo deInfo1 = BackupDataDirectoryInfo.getInfo("/data/user_de/0/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_INTERNAL, deInfo1.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_DEVICE_PROTECTED, deInfo1.subtype);
        assertTrue(deInfo1.isMounted);
        BackupDataDirectoryInfo deInfo2 = BackupDataDirectoryInfo.getInfo("/data/user_de/10/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_UNKNOWN, deInfo2.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_CUSTOM, deInfo2.subtype);
        assertTrue(deInfo2.isMounted);
    }

    @Test
    public void getInfoExternal() {
        BackupDataDirectoryInfo sdcardAndroidData = BackupDataDirectoryInfo.getInfo("/sdcard/Android/data/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_EXTERNAL, sdcardAndroidData.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_ANDROID_DATA, sdcardAndroidData.subtype);
        assertFalse(sdcardAndroidData.isMounted);
        BackupDataDirectoryInfo sdcardAndroidObb = BackupDataDirectoryInfo.getInfo("/sdcard/Android/obb/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_EXTERNAL, sdcardAndroidObb.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_ANDROID_OBB, sdcardAndroidObb.subtype);
        assertFalse(sdcardAndroidObb.isMounted);
        BackupDataDirectoryInfo sdcardAndroidMedia = BackupDataDirectoryInfo.getInfo("/sdcard/Android/media/package.name", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_EXTERNAL, sdcardAndroidMedia.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_ANDROID_MEDIA, sdcardAndroidMedia.subtype);
        assertFalse(sdcardAndroidMedia.isMounted);
        BackupDataDirectoryInfo sdcardCustom = BackupDataDirectoryInfo.getInfo("/sdcard/AppManager", 0);
        assertEquals(BackupDataDirectoryInfo.TYPE_EXTERNAL, sdcardCustom.type);
        assertEquals(BackupDataDirectoryInfo.TYPE_CUSTOM, sdcardCustom.subtype);
        assertFalse(sdcardCustom.isMounted);
    }
}