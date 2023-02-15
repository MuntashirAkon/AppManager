// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupUtilsTest {

    @Test
    public void getWritableDataDirectory() {
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/data/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/0/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/10/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/0/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/10/com.example.package", 0, 10));
        // Single user
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
        // Multiple user todo
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
    }
}
