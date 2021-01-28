package org.apache.commons.compress.archivers.tar;

import android.os.RemoteException;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.SplitOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TarArchiveOutputStreamTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void testTar() throws IOException, RemoteException {
        List<File> fileList = new ArrayList<>();
        assert classLoader != null;
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.1").getFile()));

        // Always run tests using SplitOutputStream
        try (SplitOutputStream sot = new SplitOutputStream("/tmp/AppManager_v2.5.22.apks.tar", 1024 * 1024);
        BufferedOutputStream bot = new BufferedOutputStream(sot);
        TarArchiveOutputStream tot = new TarArchiveOutputStream(bot)) {
            for (File file : fileList) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(file, file.getName());
                tot.putArchiveEntry(tarEntry);
                try (InputStream is = new ProxyInputStream(file)) {
                    IOUtils.copy(is, tot);
                }
                tot.closeArchiveEntry();
            }
            tot.finish();
        }

        // Check integrity
        List<String> expectedHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.0").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.1").getFile()));
        fileList.add(new File(classLoader.getResource("AppManager_v2.5.22.apks.tar.2").getFile()));
        for (File file : fileList) {
            expectedHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        List<String> actualHashes = new ArrayList<>();
        fileList.clear();
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.tar.0"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.tar.1"));
        fileList.add(new File("/tmp/AppManager_v2.5.22.apks.tar.2"));
        for (File file : fileList) {
            if (!file.exists()) {
                throw new FileNotFoundException(file + " does not exist.");
            }
            actualHashes.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, file));
        }
        assertEquals(expectedHashes, actualHashes);
    }
}