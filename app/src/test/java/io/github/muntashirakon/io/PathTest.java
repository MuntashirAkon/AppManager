// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.github.muntashirakon.AppManager.AppManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PathTest {
    private final Context context = AppManager.getContext();
    private Path tmpPath;
    private Path tmpFile;

    @Before
    public void setUp() throws Exception {
        tmpPath = new Path(context, new File("/tmp"));
        tmpFile = tmpPath.createNewFile("am_tmp", null);
    }

    @After
    public void tearDown() {
        tmpFile.delete();
    }

    @Test
    public void getName() {
        assertEquals("tmp", tmpPath.getName());
        assertEquals("am_tmp", tmpFile.getName());
    }

    @Test
    public void getUri() {
        assertEquals("file:///tmp", tmpPath.getUri().toString());
        assertEquals("file:///tmp/am_tmp", tmpFile.getUri().toString());
    }

    @Test
    public void getFile() {
        assertEquals(new File("/tmp"), tmpPath.getFile());
        assertEquals(new File("/tmp/am_tmp"), tmpFile.getFile());
    }

    @Test
    public void getFilePath() {
        assertEquals("/tmp", tmpPath.getFilePath());
        assertEquals("/tmp/am_tmp", tmpFile.getFilePath());
    }

    @Test
    public void getRealFilePath() throws IOException {
        String os = System.getProperty("os.name");
        if (os != null && (os.toLowerCase(Locale.ROOT).contains("darwin") || os.toLowerCase(Locale.ROOT).contains("mac"))) {
            assertEquals("/private/tmp", tmpPath.getRealFilePath());
            assertEquals("/private/tmp/am_tmp", tmpFile.getRealFilePath());
        } else {
            assertEquals("/tmp", tmpPath.getRealFilePath());
            assertEquals("/tmp/am_tmp", tmpFile.getRealFilePath());
        }
    }

    @Test
    public void length() {
        assertNotEquals(0, tmpPath.length());
        assertEquals(0, tmpFile.length());
    }

    @Test
    public void createNewFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        newFile.delete();
    }

    @Test
    public void createNewFileInCurrentDir() throws IOException {
        Path newFile = tmpPath.createNewFile("./am_new_file", null);
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertEquals(0, newFile.length());
        newFile.delete();
    }

    @Test
    public void createNewFileInNonExistingDir() {
        assertThrows(IOException.class, () ->
                tmpPath.createNewFile("non_existing_dir/am_new_file", null));
    }

    @Test
    public void createNewFileTwoTimes() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        try (OutputStream os = newFile.openOutputStream()) {
            os.write("This is a test.".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(newFile.exists());
        assertNotEquals(0, newFile.length());
        Path newNewFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newNewFile.exists());
        assertEquals(0, newFile.length());
        newNewFile.delete();
    }

    @Test
    public void createNewFileOverridingDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.createNewFile("am_new_dir", null));
        newFile.delete();
    }

    public void createNewFileRecursive() throws IOException {
        Path newFile = tmpPath.createNewFileRecursive("non_existing_dir/am_new_dir/am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        tmpPath.findFile("non_existing_dir").delete();
    }

    public void createNewFileRecursiveNoRecursive() throws IOException {
        Path newFile = tmpPath.createNewFileRecursive("/am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        newFile.delete();
    }

    @Test
    public void createNewDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        newFile.delete();
    }

    @Test
    public void createNewDirectoryInCurrentDir() throws IOException {
        Path newFile = tmpPath.createNewDirectory("./am_new_dir");
        assertTrue(tmpPath.hasFile("am_new_dir"));
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        newFile.delete();
    }

    @Test
    public void createNewDirectoryRecursive() {
        assertThrows(IOException.class, () -> tmpPath.createNewDirectory("non_existing_dir/am_new_dir"));
    }

    public void createDirectories() throws IOException {
        Path newFile = tmpPath.createNewDirectory("non_existing_dir/am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        newFile.delete();
    }

    public void createDirectoriesWithExistingFilenameAsComponent() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        assertThrows(IOException.class, () -> tmpPath.createNewDirectory("am_new_file/am_new_dir"));
        newFile.delete();
    }

    @Test
    public void createNewDirectoryOverridingFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertFalse(newFile.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.createNewDirectory("am_new_file"));
        newFile.delete();
    }

    @Test
    public void deleteSingleFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
    }

    @Test
    public void deleteCreateDeleteSingleFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
        newFile.createNewFile();
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
    }

    @Test
    public void deleteDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(newFile.delete());
    }

    @Test
    public void deleteDirectoryWithContents() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        newFile.createNewFile("file_1", null);
        newFile.createNewFile("file_2", null);
        newFile.createNewFile("file_3", null);
        Path subDir = newFile.createNewDirectory("dir_1");
        subDir.createNewFile("file_1", null);
        subDir.createNewFile("file_2", null);
        subDir.createNewFile("file_3", null);
        newFile.createNewDirectory("dir_2");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(newFile.delete());
    }

    @Test
    public void hasFile() {
        assertFalse(tmpPath.hasFile("jfjfasdjfflasdjajsdkasdjjfasdkfjskdaffsadqrurewiasjdsd"));
        assertTrue(tmpPath.hasFile("am_tmp"));
    }

    @Test
    public void findFileExistingFile() throws FileNotFoundException {
        Path path = tmpPath.findFile("am_tmp");
        assertEquals(tmpFile, path);
        assertTrue(path.isFile());
    }

    @Test
    public void findFileNonExistingFile() {
        assertThrows(FileNotFoundException.class, () -> tmpPath.findFile("jfjfasdjfflasdjajsdkasdjjfasdkfjskdaffsadqrurewiasjdsd"));
    }

    @Test
    public void findFileExistingDirectory() throws FileNotFoundException {
        Path path = new Path(context, new File("/")).findFile("tmp");
        assertEquals(tmpPath, path);
        assertTrue(path.isDirectory());
    }

    @Test
    public void findOrCreateFile() throws IOException {
        Path path = tmpPath.findOrCreateFile("am_new_file", null);
        assertTrue(path.exists());
        assertTrue(path.isFile());
        path.delete();
    }

    @Test
    public void findOrCreateDirectory() throws IOException {
        Path path = tmpPath.findOrCreateDirectory("am_new_dir");
        assertTrue(path.exists());
        assertTrue(path.isDirectory());
        path.delete();
    }

    @Test
    public void isDirectory() {
        assertTrue(tmpPath.isDirectory());
        assertFalse(tmpFile.isDirectory());
    }

    @Test
    public void isFile() {
        assertFalse(tmpPath.isFile());
        assertTrue(tmpFile.isFile());
    }

    @Test
    public void renameFileInSameDirectory() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        assertTrue(src.renameTo("am_new_file_2"));
        assertTrue(tmpPath.hasFile("am_new_file_2"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(tmpPath.findFile("am_new_file_2"), src);
        src.delete();
    }

    @Test
    public void renameFileInChildDirectory() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        assertFalse(src.renameTo("am_new_dir/am_new_file_2"));
        assertFalse(child.hasFile("am_new_file_2"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertEquals(tmpPath.findFile("am_new_file"), src);
        child.delete();
        src.delete();
    }

    @Test
    public void moveFileToFile() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        Path dst = child.createNewFile("moved_file", null);
        assertTrue(src.moveTo(dst));
        assertTrue(child.hasFile("moved_file"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(dst, src);
        child.delete();
        src.delete();
    }

    @Test
    public void moveFileToDir() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(src.moveTo(child));
        assertTrue(child.hasFile("am_new_file"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(child.findFile("am_new_file"), src);
        child.delete();
        src.delete();
    }

    @Test
    public void moveDirToDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir_src");
        Path dst = tmpPath.createNewDirectory("am_new_dir_dst");
        src.createNewFile("some_file", null);
        assertTrue(src.moveTo(dst));
        assertFalse(tmpPath.hasFile("am_new_dir_src"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertEquals(tmpPath.findFile("am_new_dir_dst"), src);
        assertTrue(dst.hasFile("some_file"));
        dst.delete();
        src.delete();
    }

    @Test
    public void moveDirToFileFails() throws IOException {
        Path dst = tmpPath.findOrCreateFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        child.createNewFile("some_file", null);
        assertFalse(child.moveTo(dst));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("am_new_dir"));
        child.delete();
        dst.delete();
    }

    @Test
    public void openOutputStreamForFile() throws IOException {
        byte[] bytes = "This is a test.".getBytes(StandardCharsets.UTF_8);
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        try (OutputStream os = newFile.openOutputStream()) {
            os.write(bytes);
        }
        assertTrue(newFile.exists());
        assertEquals(bytes.length, newFile.length());
        newFile.delete();
    }

    @Test
    public void openOutputStreamForFileAppend() throws IOException {
        byte[] bytes = "This is a test.".getBytes(StandardCharsets.UTF_8);
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        try (OutputStream os = newFile.openOutputStream()) {
            os.write(bytes);
        }
        assertTrue(newFile.exists());
        assertEquals(bytes.length, newFile.length());
        try (OutputStream os = newFile.openOutputStream(true)) {
            os.write(bytes);
        }
        assertTrue(newFile.exists());
        assertEquals(bytes.length * 2, newFile.length());
        newFile.delete();
    }

    @Test
    public void openOutputStreamForDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, newFile::openOutputStream);
        newFile.delete();
    }

    @Test
    public void openInputStreamForFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        //noinspection EmptyTryBlock
        try (InputStream ignore = newFile.openInputStream()) {
        }
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        newFile.delete();
    }

    @Test
    public void openInputStreamForDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, newFile::openInputStream);
        newFile.delete();
    }
}
