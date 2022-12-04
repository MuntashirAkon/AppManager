// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Note: We don't have to test weird paths such as ./, ../, etc. because they're taken care of by the Path API.
@RunWith(RobolectricTestRunner.class)
public class ZipFileSystemTest {
    private final ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
    private Path tmpPath;

    @Before
    public void setUp() throws Exception {
        tmpPath = Paths.get("/tmp");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void isFileOrDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_1");
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
    }

    @Test
    public void isHidden() {
        // TODO: 25/11/22
    }

    @Test
    public void lastAccess() {
        // TODO: 25/11/22
    }

    @Test
    public void creationTime() {
        // TODO: 25/11/22
    }

    @Test
    public void createNewFileReadOnly() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_2");
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip");
        assertThrows(IOException.class, () -> mountPoint.createNewFile("test.txt", null));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
    }

    @Test
    public void createNewFileRWNoChange() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_3");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void createNewFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_4");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createNewFileRWInplace() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path tmpApkFile = apkFile.copyTo(tmpPath);
        assertNotNull(tmpApkFile);
        Path mountPoint = Paths.get("/tmp/am_mount_point_5");
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> false);
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), tmpApkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), tmpApkFile, "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        VirtualFileSystem.unmount(fsId);
        assertTrue(tmpApkFile.delete());
    }

    @Test
    public void deleteReadOnly() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_6");
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip");
        assertFalse(mountPoint.findFile("resources.arsc").delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        VirtualFileSystem.unmount(fsId);
    }

    @Test
    public void deleteRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_7");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertFalse(mountPoint.hasFile("resources.arsc"));
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_8");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewFile("resources.arsc", null);
        assertEquals(0, arsc.length());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertEquals(0, mountPoint.findFile("resources.arsc").length());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateDirFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_9");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewDirectory("resources.arsc");
        assertEquals(0, arsc.length());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isDirectory());
        assertEquals(0, mountPoint.findFile("resources.arsc").length());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void deleteCreateDeleteFromExistingFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_10");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        assertNotEquals(0, mountPoint.findFile("resources.arsc").length());
        assertTrue(mountPoint.findFile("resources.arsc").delete());
        Path arsc = mountPoint.createNewFile("resources.arsc", null);
        assertEquals(0, arsc.length());
        assertTrue(arsc.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertFalse(mountPoint.hasFile("resources.arsc"));
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createDeleteFileRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_11");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testText = mountPoint.createNewFile("test.txt", null);
        assertTrue(testText.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test.txt"));
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void createDeleteDirRW() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_12");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        Path testDir = mountPoint.createNewDirectory("test_dir");
        assertTrue(testDir.delete());
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void list() {
        // TODO: 25/11/22
    }

    @Test
    public void mkdir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_13");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdir("/test_dir"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirMultipleDirs() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_14");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdir("/test_dir/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        VirtualFileSystem.unmount(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void mkdirExistingFileOrDir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_15");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdir("/assets"));
        assertFalse(fs.mkdir("/classes.dex"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertFalse(mountPoint.hasFile("test_dir"));
        VirtualFileSystem.unmount(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void mkdirs() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_16");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdirs("/test_dir/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("test_dir_2").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("test_dir_2").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirsInsideExistingDir() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_17");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.mkdirs("/assets/test_dir_2"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("test_dir_2").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("test_dir_2").isDirectory());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void mkdirsAllExistingDirsOrFiles() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_18");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.mkdirs("/assets/dnsfilter.conf"));
        assertFalse(fs.mkdirs("/res/layout"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void renameToExistingFileSameDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_19");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileDifferentExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_20");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/assets/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("Manifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileDifferentNonExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_21");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/test_dir/Manifest.xml"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("Manifest.xml").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test_dir").isDirectory());
        assertTrue(mountPoint.findFile("test_dir").findFile("Manifest.xml").isFile());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingFileExistingFile() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_22");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        String manifestContents = mountPoint.findFile("AndroidManifest.xml").getContentAsString();
        assertTrue(fs.renameTo("/AndroidManifest.xml", "/assets/dnsfilter.conf"));
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertFalse(mountPoint.hasFile("AndroidManifest.xml"));
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertEquals(manifestContents, mountPoint.findFile("assets").findFile("dnsfilter.conf").getContentAsString());
        VirtualFileSystem.unmount(fsId);
    }

    @Test
    public void renameToNewFileExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_23");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        Path testText = mountPoint.createNewFile("test.txt", null);
        try (OutputStream os = testText.openOutputStream()) {
            os.write("This is a test file".getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(fs.renameTo("/test.txt", "/assets"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertFalse(mountPoint.findFile("assets").hasFile("test.txt"));
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertFalse(mountPoint.findFile("assets").hasFile("test.txt"));
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        assertTrue(mountPoint.findFile("test.txt").isFile());
        assertEquals("This is a test file", mountPoint.findFile("test.txt").getContentAsString());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingDirectoryWithContentsSameDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_24");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/assets", "/abs"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("abs").isDirectory());
        assertTrue(mountPoint.findFile("abs").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("abs").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("abs").isDirectory());
        assertTrue(mountPoint.findFile("abs").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("abs").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void renameToExistingDirectoryWithContentsDifferentExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_25");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertFalse(fs.renameTo("/assets", "/res"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertTrue(mountPoint.findFile("assets").isDirectory());
        assertTrue(mountPoint.findFile("assets").findFile("dnsfilter.conf").isFile());
        assertTrue(mountPoint.findFile("assets").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        assertTrue(mountPoint.findFile("res").isDirectory());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNull(modifiedApk.get());
    }

    @Test
    public void renameToExistingDirectoryWithContentsDifferentNonExistingDirectory() throws IOException {
        Path base = Paths.get(classLoader.getResource("oandbackups/dnsfilter.android").getFile());
        Path apkFile = base.findFile("base.apk");
        Path mountPoint = Paths.get("/tmp/am_mount_point_26");
        AtomicReference<File> modifiedApk = new AtomicReference<>();
        VirtualFileSystem.MountOptions options = getRWOptions((fs, cachedFile) -> {
            modifiedApk.set(cachedFile);
            return true;
        });
        int fsId = VirtualFileSystem.mount(mountPoint.getUri(), apkFile, "application/zip", options);
        VirtualFileSystem fs = Objects.requireNonNull(VirtualFileSystem.getFileSystem(fsId));
        assertTrue(fs.renameTo("/assets", "/res/new"));
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        Path res = mountPoint.findFile("res");
        assertTrue(res.isDirectory());
        assertTrue(res.findFile("new").isDirectory());
        assertTrue(res.findFile("new").findFile("dnsfilter.conf").isFile());
        assertTrue(res.findFile("new").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertNotNull(modifiedApk.get());
        // Remount to verify contents
        fsId = VirtualFileSystem.mount(mountPoint.getUri(), Paths.get(modifiedApk.get()), "application/zip");
        assertTrue(mountPoint.findFile("AndroidManifest.xml").isFile());
        assertFalse(mountPoint.hasFile("assets"));
        assertTrue(mountPoint.findFile("classes.dex").isFile());
        assertTrue(mountPoint.findFile("META-INF").isDirectory());
        res = mountPoint.findFile("res");
        assertTrue(res.isDirectory());
        assertTrue(res.findFile("new").isDirectory());
        assertTrue(res.findFile("new").findFile("dnsfilter.conf").isFile());
        assertTrue(res.findFile("new").findFile("additionalHosts.txt").isFile());
        assertTrue(mountPoint.findFile("resources.arsc").isFile());
        VirtualFileSystem.unmount(fsId);
        assertTrue(modifiedApk.get().delete());
    }

    @Test
    public void setLastModified() {
        // TODO: 25/11/22
    }

    @Test
    public void newOutputStreamAppend() {
        // TODO: 25/11/22
    }

    @Test
    public void lastModified() {
        // TODO: 25/11/22
    }

    @Test
    public void length() {
        // TODO: 25/11/22
    }

    @Test
    public void checkAccess() {
        // TODO: 25/11/22
    }

    @Test
    public void getMode() {
        // TODO: 25/11/22
    }

    private VirtualFileSystem.MountOptions getRWOptions(VirtualFileSystem.OnFileSystemUnmounted event) {
        return new VirtualFileSystem.MountOptions.Builder()
                .setReadWrite(true)
                .setOnFileSystemUnmounted(event)
                .build();
    }
}