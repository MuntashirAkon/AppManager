// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PathTest {
    private Path tmpPath;
    private Path tmpFile;

    @Before
    public void setUp() throws Exception {
        tmpPath = Paths.get("/tmp");
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
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewFileInCurrentDir() throws IOException {
        Path newFile = tmpPath.createNewFile("./am_new_file", null);
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertEquals(0, newFile.length());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewFileTrailingSlash() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file/", null);
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertEquals(0, newFile.length());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewFileInNonExistingDir() {
        assertThrows(IllegalArgumentException.class, () ->
                tmpPath.createNewFile("non_existing_dir/am_new_file", null));
    }

    @Test
    public void createNewFileInExistingDir() throws IOException {
        Path dir = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IllegalArgumentException.class, () ->
                tmpPath.createNewFile("am_new_dir/am_new_file", null));
        assertTrue(dir.delete());
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
        assertTrue(newNewFile.delete());
    }

    @Test
    public void createNewFileOverridingDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.createNewFile("am_new_dir", null));
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewFileRecursive() throws IOException {
        Path newFile = tmpPath.createNewArbitraryFile("non_existing_dir/am_new_dir/am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(tmpPath.findFile("non_existing_dir").delete());
    }

    @Test
    public void createNewFileRecursiveNoRecursive() throws IOException {
        Path newFile = tmpPath.createNewArbitraryFile("/am_new_file/", null);
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewFileRecursiveParent() throws IOException {
        Path dir = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, () ->
                dir.createNewArbitraryFile("../am_new_file", null));
        assertFalse(dir.hasFile("am_new_file"));
        assertFalse(tmpFile.hasFile("am_new_file"));
        assertTrue(dir.delete());
    }

    @Test
    public void createNewDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewDirectoryInCurrentDir() throws IOException {
        Path newFile = tmpPath.createNewDirectory("./am_new_dir/");
        assertTrue(tmpPath.hasFile("am_new_dir"));
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewDirectoryWithFileSeparator() {
        assertThrows(IllegalArgumentException.class, () -> tmpPath.createNewDirectory("non_existing_dir/am_new_dir"));
    }

    @Test
    public void createDirectories() throws IOException {
        Path newFile = tmpPath.createDirectories("non_existing_dir/am_new_dir");
        assertTrue(tmpPath.hasFile("non_existing_dir"));
        Path dir = tmpPath.findFile("non_existing_dir");
        assertTrue(dir.hasFile("am_new_dir"));
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(dir.delete());
    }

    @Test
    public void createDirectoriesExistingDir() throws IOException {
        Path dir = tmpPath.createNewDirectory("existing_dir");
        Path newFile = tmpPath.createDirectories("existing_dir/am_new_dir");
        assertTrue(tmpPath.hasFile("existing_dir"));
        assertTrue(dir.hasFile("am_new_dir"));
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(dir.delete());
        assertFalse(newFile.exists());
    }

    @Test
    public void createDirectoriesAllExisting() throws IOException {
        Path dir = tmpPath.createNewDirectory("existing_dir");
        Path childDir = dir.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, () -> tmpPath.createDirectories("existing_dir/am_new_dir"));
        assertTrue(dir.hasFile("am_new_dir"));
        assertEquals(dir.findFile("am_new_dir"), childDir);
        assertTrue(dir.delete());
    }

    @Test
    public void createDirectoriesInParent() throws IOException {
        Path parent = tmpPath.createNewDirectory("existing_dir");
        assertThrows(IOException.class, () -> parent.createDirectories("../am_new_dir"));
        assertFalse(tmpPath.hasFile("am_new_dir"));
        assertFalse(parent.hasFile("am_new_dir"));
        assertTrue(parent.delete());
    }

    @Test
    public void createDirectoriesWithExistingFilenameAsComponent() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        assertThrows(IOException.class, () -> tmpPath.createDirectories("am_new_file/am_new_dir"));
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewDirectoryOverridingFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertFalse(newFile.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.createNewDirectory("am_new_file"));
        assertTrue(newFile.delete());
    }

    @Test
    public void createNewDirectoriesOverridingFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertFalse(newFile.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.createDirectories("am_new_file/another_dir"));
        assertTrue(newFile.delete());
    }

    @Test
    public void deleteSingleFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
        assertFalse(newFile.exists());
    }

    @Test
    public void deleteCreateDeleteSingleFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
        assertFalse(newFile.exists());
        assertTrue(newFile.recreate());
        assertTrue(newFile.exists());
        assertTrue(newFile.isFile());
        assertTrue(newFile.delete());
        assertFalse(newFile.exists());
    }

    @Test
    public void deleteDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(newFile.exists());
        assertTrue(newFile.isDirectory());
        assertTrue(newFile.delete());
        assertFalse(newFile.exists());
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
        assertFalse(newFile.exists());
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
        Path path = Paths.get(File.separator).findFile("tmp");
        assertEquals(tmpPath, path);
        assertTrue(path.isDirectory());
    }

    @Test
    public void findOrCreateFile() throws IOException {
        Path path = tmpPath.findOrCreateFile("am_new_file", null);
        assertTrue(path.isFile());
        assertTrue(path.delete());
    }

    @Test
    public void findOrCreateFileExistingFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        Path path = tmpPath.findOrCreateFile("am_new_file", null);
        assertEquals(newFile, path);
        assertTrue(path.isFile());
        assertTrue(path.delete());
    }

    @Test
    public void findOrCreateFileInAnotherDirectory() throws IOException {
        Path dir = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(dir.isDirectory());
        assertThrows(IllegalArgumentException.class, () -> tmpPath.findOrCreateFile("am_new_dir/am_new_file", null));
        assertFalse(dir.hasFile("am_new_file"));
        assertTrue(dir.delete());
    }

    @Test
    public void findOrCreateFileExistingDirectory() throws IOException {
        Path dir = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(dir.isDirectory());
        assertThrows(IOException.class, () -> tmpPath.findOrCreateFile("am_new_dir", null));
        assertTrue(dir.delete());
    }

    @Test
    public void findOrCreateDirectory() throws IOException {
        Path path = tmpPath.findOrCreateDirectory("am_new_dir");
        assertTrue(path.isDirectory());
        assertTrue(path.delete());
    }

    @Test
    public void findOrCreateDirectoryExistingDirectory() throws IOException {
        Path dir = tmpPath.createNewDirectory("am_new_dir");
        Path path = tmpPath.findOrCreateDirectory("am_new_dir");
        assertEquals(dir, path);
        assertTrue(path.isDirectory());
        assertTrue(path.delete());
    }

    @Test
    public void findOrCreateDirectoryInAnotherDirectory() throws IOException {
        Path path = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(path.isDirectory());
        assertThrows(IllegalArgumentException.class, () -> tmpPath.findOrCreateDirectory("am_new_dir/am_another_dir"));
        assertFalse(path.hasFile("am_another_dir"));
        assertTrue(path.delete());
    }

    @Test
    public void findOrCreateDirectoryExistingFile() throws IOException {
        Path path = tmpPath.createNewFile("am_new_file", null);
        assertTrue(path.isFile());
        assertThrows(IOException.class, () -> tmpPath.findOrCreateDirectory("am_new_file"));
        assertTrue(path.delete());
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
        assertTrue(src.delete());
    }

    @Test
    public void renameDirInSameDirectory() throws IOException {
        Path src = tmpPath.findOrCreateDirectory("am_new_dir_src");
        Path childFile = src.findOrCreateFile("test_file", null);
        Path childDir = src.findOrCreateDirectory("test_dir");
        assertTrue(src.renameTo("am_new_dir_dst"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertFalse(tmpPath.hasFile("am_new_dir_src"));
        Path dst = tmpPath.findFile("am_new_dir_dst");
        assertEquals(dst, src);
        assertFalse(childFile.exists());
        assertFalse(childDir.exists());
        assertTrue(dst.hasFile("test_file"));
        assertTrue(dst.hasFile("test_dir"));
        assertTrue(dst.delete());
        assertFalse(src.delete());
    }

    @Test
    public void renameFileInSameDirectoryTargetExists() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        Path dst = tmpPath.findOrCreateFile("am_new_file_2", null);
        assertFalse(src.renameTo("am_new_file_2"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("am_new_file_2"));
        assertEquals(tmpPath.findFile("am_new_file"), src);
        assertEquals(tmpPath.findFile("am_new_file_2"), dst);
        assertTrue(src.delete());
        assertTrue(dst.delete());
    }

    @Test
    public void renameFileInChildDirectoryFails() throws IOException {
        Path src = tmpPath.findOrCreateFile("am_new_file", null);
        Path dst = tmpPath.createNewDirectory("am_new_dir");
        assertFalse(src.renameTo("am_new_dir/am_new_file_2"));
        assertFalse(dst.hasFile("am_new_file_2"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertEquals(tmpPath.findFile("am_new_file"), src);
        assertTrue(dst.delete());
        assertTrue(src.delete());
    }

    @Test
    public void renameFileInParentDirectoryFails() throws IOException {
        Path parent = tmpPath.createNewDirectory("am_new_dir");
        Path childDir = parent.createNewDirectory("am_child_dir");
        Path childChildFile = childDir.createNewFile("am_new_file", null);
        assertFalse(childChildFile.renameTo("../am_new_file_2"));
        assertFalse(childDir.hasFile("am_new_file_2"));
        assertTrue(childDir.hasFile("am_new_file"));
        assertFalse(parent.hasFile("am_new_file_2"));
        assertTrue(parent.delete());
    }

    @Test
    public void moveFileToFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewFile("moved_file", null);
        assertTrue(src.moveTo(dst));
        assertTrue(tmpPath.hasFile("moved_file"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(dst, src);
        assertTrue(src.delete());
        assertFalse(dst.delete());
    }

    @Test
    public void moveToSameFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        assertFalse(src.moveTo(src));
        assertTrue(src.delete());
    }

    @Test
    public void moveFileToFileNoOverride() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewFile("moved_file", null);
        assertFalse(src.moveTo(dst, false));
        assertTrue(tmpPath.hasFile("moved_file"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertNotEquals(dst, src);
        assertTrue(src.delete());
        assertTrue(dst.delete());
    }

    @Test
    public void moveFileToDir() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewDirectory("am_new_dir");
        assertTrue(src.moveTo(dst));
        assertTrue(dst.hasFile("am_new_file"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(dst.findFile("am_new_file"), src);
        assertTrue(dst.delete());
        assertFalse(src.delete());
    }

    @Test
    public void moveFileToDirAndFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        Path dst = child.createNewFile("moved_file", null);
        assertTrue(src.moveTo(dst));
        assertTrue(child.hasFile("moved_file"));
        assertFalse(tmpPath.hasFile("am_new_file"));
        assertEquals(dst, src);
        assertTrue(child.delete());
        assertFalse(src.delete());
    }

    @Test
    public void moveDirToDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir_src");
        Path dst = tmpPath.createNewDirectory("am_new_dir_dst");
        src.createNewFile("some_file", null);
        assertTrue(dst.delete());
        assertTrue(src.moveTo(dst));
        assertFalse(tmpPath.hasFile("am_new_dir_src"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertTrue(dst.hasFile("some_file"));
        assertEquals(dst, src);
        assertTrue(dst.delete());
        assertFalse(src.delete());
    }

    @Test
    public void moveToSameDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir");
        assertFalse(src.moveTo(src));
        assertTrue(src.delete());
    }

    @Test
    public void moveToSameChildDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir");
        Path child = src.createNewDirectory("am_child_dir");
        assertFalse(src.moveTo(child));
        assertTrue(src.delete());
    }

    @Test
    public void moveDirToExistingDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir_src");
        Path dst = tmpPath.createNewDirectory("am_new_dir_dst");
        src.createNewFile("some_file", null);
        assertTrue(src.moveTo(dst));
        assertFalse(tmpPath.hasFile("am_new_dir_src"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertTrue(dst.hasFile("am_new_dir_src"));
        assertTrue(dst.findFile("am_new_dir_src").hasFile("some_file"));
        assertEquals(dst.findFile("am_new_dir_src"), src);
        assertTrue(dst.delete());
        assertFalse(src.delete());
    }

    @Test
    public void moveDirToFileFails() throws IOException {
        Path dst = tmpPath.createNewFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        child.createNewFile("some_file", null);
        assertFalse(child.moveTo(dst));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("am_new_dir"));
        assertTrue(child.delete());
        assertTrue(dst.delete());
    }

    @Test
    public void copyFileToFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewFile("copied_file", null);
        assertNotNull(src.copyTo(dst));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("copied_file"));
        assertNotEquals(dst, src);
        assertTrue(src.delete());
        assertTrue(dst.delete());
    }

    @Test
    public void copyToSameFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        assertNull(src.copyTo(src));
        assertTrue(src.delete());
    }

    @Test
    public void copyFileToFileNoOverride() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewFile("copied_file", null);
        assertNull(src.copyTo(dst, false));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("copied_file"));
        assertNotEquals(dst, src);
        assertTrue(src.delete());
        assertTrue(dst.delete());
    }

    @Test
    public void copyFileToDir() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path dst = tmpPath.createNewDirectory("am_new_dir");
        assertNotNull(src.copyTo(dst));
        assertTrue(dst.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertNotEquals(dst.findFile("am_new_file"), src);
        assertTrue(dst.delete());
        assertTrue(src.delete());
    }

    @Test
    public void copyFileToDirAndFile() throws IOException {
        Path src = tmpPath.createNewFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        Path dst = child.createNewFile("copied_file", null);
        assertNotNull(src.copyTo(dst));
        assertTrue(child.hasFile("copied_file"));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertNotEquals(dst, src);
        assertTrue(child.delete());
        assertTrue(src.delete());
    }

    @Test
    public void copyDirToDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir_src");
        Path dst = tmpPath.createNewDirectory("am_new_dir_dst");
        src.createNewFile("some_file", null);
        assertTrue(dst.delete());
        assertNotNull(src.copyTo(dst));
        assertTrue(tmpPath.hasFile("am_new_dir_src"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertTrue(src.hasFile("some_file"));
        assertTrue(dst.hasFile("some_file"));
        assertNotEquals(dst, src);
        assertTrue(dst.delete());
        assertTrue(src.delete());
    }

    @Test
    public void copyToSameDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir");
        assertNull(src.copyTo(src));
        assertTrue(src.delete());
    }

    @Test
    public void copyToSameChildDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir");
        Path child = src.createNewDirectory("am_child_dir");
        assertNull(src.copyTo(child));
        assertTrue(src.delete());
    }

    @Test
    public void copyDirToExistingDir() throws IOException {
        Path src = tmpPath.createNewDirectory("am_new_dir_src");
        Path dst = tmpPath.createNewDirectory("am_new_dir_dst");
        src.createNewFile("some_file", null);
        assertNotNull(src.copyTo(dst));
        assertTrue(tmpPath.hasFile("am_new_dir_src"));
        assertTrue(tmpPath.hasFile("am_new_dir_dst"));
        assertTrue(dst.hasFile("am_new_dir_src"));
        assertTrue(dst.findFile("am_new_dir_src").hasFile("some_file"));
        assertNotEquals(dst.findFile("am_new_dir_src"), src);
        assertTrue(dst.delete());
        assertTrue(src.delete());
    }

    @Test
    public void copyDirToFileFails() throws IOException {
        Path dst = tmpPath.createNewFile("am_new_file", null);
        Path child = tmpPath.createNewDirectory("am_new_dir");
        child.createNewFile("some_file", null);
        assertNull(child.copyTo(dst));
        assertTrue(tmpPath.hasFile("am_new_file"));
        assertTrue(tmpPath.hasFile("am_new_dir"));
        assertTrue(child.delete());
        assertTrue(dst.delete());
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
        assertTrue(newFile.delete());
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
        assertTrue(newFile.delete());
    }

    @Test
    public void openOutputStreamForDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, newFile::openOutputStream);
        assertTrue(newFile.delete());
    }

    @Test
    public void openInputStreamForFile() throws IOException {
        Path newFile = tmpPath.createNewFile("am_new_file", null);
        //noinspection EmptyTryBlock
        try (InputStream ignore = newFile.openInputStream()) {
        }
        assertTrue(newFile.exists());
        assertEquals(0, newFile.length());
        assertTrue(newFile.delete());
    }

    @Test
    public void openInputStreamForDirectory() throws IOException {
        Path newFile = tmpPath.createNewDirectory("am_new_dir");
        assertThrows(IOException.class, newFile::openInputStream);
        assertTrue(newFile.delete());
    }
}
