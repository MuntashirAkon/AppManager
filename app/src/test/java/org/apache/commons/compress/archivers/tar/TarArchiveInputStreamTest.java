// SPDX-License-Identifier: GPL-3.0-or-later

package org.apache.commons.compress.archivers.tar;

import android.os.RemoteException;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyOutputStream;
import io.github.muntashirakon.io.SplitInputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TarArchiveInputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void TestUnTar() throws IOException, RemoteException {
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.1").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.2").getFile()));

        // Always run tests using SplitInputStream
        try (SplitInputStream sis = new SplitInputStream(fileList);
             BufferedInputStream bis = new BufferedInputStream(sis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                // create a new path, remember check zip slip attack
                File file = new File("/tmp", entry.getName());
                // copy TarArchiveInputStream to newPath
                try (OutputStream os = new ProxyOutputStream(file)) {
                    IOUtils.copy(tis, os);
                }
            }
        }

        // Check integrity
        List<String> expectedHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        List<String> actualHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.1"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        assertEquals(expectedHashes, actualHashes);
    }
}