// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.SplitInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TarUtilsTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private File testRoot;
    private File tarGzFile;
    private File[] tarGzFilesForExtractTest;

    @Before
    public void setUp() throws Throwable {
        assert classLoader != null;
        List<File> resFiles = new ArrayList<>();
        resFiles.add(new File(classLoader.getResource("plain.txt").getFile()));
        resFiles.add(new File(classLoader.getResource("raw/exclude.txt").getFile()));
        resFiles.add(new File(classLoader.getResource("raw/include.txt").getFile()));
        resFiles.add(new File(classLoader.getResource("prefixed/prefixed_exclude.txt").getFile()));
        resFiles.add(new File(classLoader.getResource("prefixed/prefixed_include.txt").getFile()));
        File tmpRoot = new File("/tmp");
        List<File> tmpFiles = new ArrayList<>();
        testRoot = new File(tmpRoot, "test");
        testRoot.mkdir();
        new File(testRoot, "raw").mkdir();
        new File(testRoot, "prefixed").mkdir();
        tmpFiles.add(new File(testRoot, "plain.txt"));
        tmpFiles.add(new File(testRoot, "raw/exclude.txt"));
        tmpFiles.add(new File(testRoot, "raw/include.txt"));
        tmpFiles.add(new File(testRoot, "prefixed/prefixed_exclude.txt"));
        tmpFiles.add(new File(testRoot, "prefixed/prefixed_include.txt"));
        // Copy files to tmpRoot
        for (int i = 0; i < resFiles.size(); ++i) {
            IOUtils.copy(resFiles.get(i), tmpFiles.get(i));
        }
        tarGzFile = new File(tmpRoot, "am.tar.gz");
        tarGzFilesForExtractTest = TarUtils.create(TarUtils.TAR_GZIP, testRoot, new File(tmpRoot, "am_ex.tar.gz"),
                null, null, null, false).toArray(new File[0]);
    }

    @Test
    public void testCreateTarGZipWithFilter() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{".*include\\.txt"}, null,
                Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithFilter() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*include\\.txt"},
                null, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryFilter() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{"prefixed/.*"}, null,
                Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt"));
    }

    @Test
    public void testExtractTarGZipWithDirectoryFilter() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{"prefixed/.*"}, null,
                Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt"));
    }

    @Test
    public void testCreateTarGZipWithMultipleFilters() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{".*include\\.txt", "plain.*"}, null,
                Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithMultipleFilters() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */new String[]{".*include\\.txt", "plain.*"},
                null, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/",
                        "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryAndMultipleFilters() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{".*include\\.txt", "plain.*", "prefixed/.*"},
                null, Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt",
                        "prefixed/prefixed_exclude.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithDirectoryAndMultipleFilters() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*include\\.txt", "plain.*",
                        "prefixed/.*"}, null, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt",
                        "prefixed/prefixed_exclude.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithExclude() throws Throwable {
        createTest(tarGzFile, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt"},
                Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithExclude() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt"}, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt",
                        "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithExcludeDirectory() throws Throwable {
        createTest(tarGzFile, testRoot, null, /* language=regexp */ new String[]{"raw/.*"}, Arrays.asList("/",
                "prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt", "plain.txt"));
    }

    @Test
    public void testExtractTarGZipWithExcludeDirectory() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */ new String[]{"raw/.*"},
                Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt",
                        "plain.txt", "raw/"));
    }

    @Test
    public void testCreateTarGZipWithMultipleExcludes() throws Throwable {
        createTest(tarGzFile, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt", "plain.*"},
                Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithMultipleExcludes() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt", "plain.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryAndMultipleExcludes() throws Throwable {
        createTest(tarGzFile, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt", "plain.*",
                        "raw/.*"}, Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt"));
    }

    @Test
    public void testExtractTarGZipWithDirectoryAndMultipleExcludes() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt", "plain.*", "raw/.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "raw/"));
    }

    @Test
    public void testCreateTarGZipWithFilterAndExclude() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{".*\\.txt"}, /* language=regexp */
                new String[]{".*exclude\\.txt"}, Arrays.asList("/", "prefixed/", "prefixed/prefixed_include.txt",
                        "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithFilterAndExclude() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*\\.txt"},
                /* language=regexp */new String[]{".*exclude\\.txt"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithFilterAndExcludeContainingDirectory() throws Throwable {
        createTest(tarGzFile, testRoot, /* language=regexp */ new String[]{".*\\.txt", "include/.*"},
                /* language=regexp */ new String[]{".*exclude\\.txt", "raw/.*"}, Arrays.asList("/", "prefixed/",
                        "prefixed/prefixed_include.txt", "plain.txt"));
    }

    @Test
    public void testExtractTarGZipWithFilterAndExcludeContainingDirectory() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*\\.txt", "include/.*"},
                /* language=regexp */ new String[]{".*exclude\\.txt", "raw/.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "plain.txt"));
    }

    @Test
    public void testCreateTarGZipWithNoFiltersOrExcludes() throws Throwable {
        createTest(tarGzFile, testRoot, null, null, Arrays.asList("/", "prefixed/",
                "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt", "plain.txt", "raw/",
                "raw/include.txt", "raw/exclude.txt"));
    }

    @Test
    public void testExtractTarGZipWithNoFiltersOrExcludes() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, null, Arrays.asList("", "prefixed/",
                "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt", "plain.txt", "raw/",
                "raw/include.txt", "raw/exclude.txt"));
    }

    @Test
    public void testGetRelativePath() {
        File basePath = new File("/data/data/package.name");
        File[] absolutes = new File[]{
                new File(basePath, "app_lib"),
                new File(basePath, "app_webview"),
                new File(basePath, "app_webview/variations_seed_new"),
                new File(basePath, "app_webview/pref_store"),
                new File(basePath, "app_webview/webview_data.lock"),
                new File(basePath, "app_webview/variations_stamp"),
                new File(basePath, "app_webview/variations_seed"),
                new File(basePath, "app_webview/Default"),
                new File(basePath, "app_webview/Default/Session Storage"),
                new File(basePath, "app_webview/Default/Session Storage"),
                new File(basePath, "app_webview/Default/Session Storage/CURRENT"),
                new File(basePath, "app_webview/Default/Session Storage/LOG"),
                new File(basePath, "app_webview/Default/Session Storage/MANIFEST-000001"),
                new File(basePath, "app_webview/Default/Session Storage/000003.log"),
                new File(basePath, "app_webview/Default/Session Storage/LOCK"),
                new File(basePath, "app_webview/Default/Web Data-journal"),
                new File(basePath, "app_webview/Default/blob_storage"),
        };
        String[] expectedPaths = new String[]{
                "app_lib",
                "app_webview",
                "app_webview/variations_seed_new",
                "app_webview/pref_store",
                "app_webview/webview_data.lock",
                "app_webview/variations_stamp",
                "app_webview/variations_seed",
                "app_webview/Default",
                "app_webview/Default/Session Storage",
                "app_webview/Default/Session Storage",
                "app_webview/Default/Session Storage/CURRENT",
                "app_webview/Default/Session Storage/LOG",
                "app_webview/Default/Session Storage/MANIFEST-000001",
                "app_webview/Default/Session Storage/000003.log",
                "app_webview/Default/Session Storage/LOCK",
                "app_webview/Default/Web Data-journal",
                "app_webview/Default/blob_storage",
        };
        String[] actualPaths = new String[expectedPaths.length];
        for (int i = 0; i < actualPaths.length; ++i) {
            actualPaths[i] = TarUtils.getRelativePath(absolutes[i], basePath, "/");
        }
        assertArrayEquals(expectedPaths, actualPaths);
    }

    @Test
    public void testGetRelativePathsUnix() {
        assertEquals("stuff/xyz.dat", TarUtils.getRelativePath(new File("/var/data/stuff/xyz.dat"),
                new File("/var/data/"), "/"));
        assertEquals("../../b/c", TarUtils.getRelativePath(new File("/a/b/c"),
                new File("/a/x/y/"), "/"));
        assertEquals("../../b/c", TarUtils.getRelativePath(new File("/m/n/o/a/b/c"),
                new File("/m/n/o/a/x/y/"), "/"));
    }

    @Test
    public void testGetRelativePathFileToFileDoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts\\chs_boot.ttf");
        File base = new File("C:\\Windows\\Speech\\Common\\sapisvr.exe");
        File workingBase = new File("C:\\Windows\\Speech\\Common\\");

        assertNotEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", TarUtils.getRelativePath(target, base, "\\"));
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", TarUtils.getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathDirectoryToFile() {
        File target = new File("C:\\Windows\\Boot\\Fonts\\chs_boot.ttf");
        File base = new File("C:\\Windows\\Speech\\Common\\");

        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", TarUtils.getRelativePath(target, base, "\\"));
    }

    @Test
    public void testGetRelativePathFileToDirectoryDoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts");
        File base = new File("C:\\Windows\\Speech\\Common\\foo.txt");
        File workingBase = new File("C:\\Windows\\Speech\\Common\\");

        assertNotEquals("..\\..\\Boot\\Fonts", TarUtils.getRelativePath(target, base, "\\"));
        assertEquals("..\\..\\Boot\\Fonts", TarUtils.getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathFileToDirectory2DoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts");
        File base = new File("C:\\foo.txt");
        File workingBase = new File("C:\\");

        assertNotEquals("Windows\\Boot\\Fonts", TarUtils.getRelativePath(target, base, "\\"));
        assertEquals("Windows\\Boot\\Fonts", TarUtils.getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathDirectoryToDirectory() {
        File target = new File("C:\\Windows\\Boot\\");
        File base = new File("C:\\Windows\\Speech\\Common\\");
        String expected = "..\\..\\Boot\\";

        String relPath = TarUtils.getRelativePath(target, base, "\\");
        assertEquals(expected, relPath);
    }

    @Test
    public void testGetRelativePathDifferentDriveLetters() {
        File target = new File("D:\\sources\\recovery\\RecEnv.exe");
        File base = new File("C:\\Java\\workspace\\AcceptanceTests\\Standard test data\\geo\\");
        assertEquals(target.getAbsolutePath(), TarUtils.getRelativePath(target, base, "\\"));
    }

    @NonNull
    private static List<String> getFileNamesGZip(@NonNull List<File> tarFiles) throws IOException {
        List<String> fileNames = new ArrayList<>();
        try (SplitInputStream sis = new SplitInputStream(tarFiles);
             BufferedInputStream bis = new BufferedInputStream(sis);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(bis, true);
             TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {
            ArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                fileNames.add(entry.getName());
            }
        }
        return fileNames;
    }

    private static void createTest(@NonNull File source, @NonNull File testRoot, @Nullable String[] include,
                                   @Nullable String[] exclude, @NonNull List<String> expectedPaths) throws Throwable {
        List<File> files = TarUtils.create(TarUtils.TAR_GZIP, testRoot, source, include, null, exclude, false);
        List<String> actualPaths = getFileNamesGZip(files);
        Collections.sort(expectedPaths);
        Collections.sort(actualPaths);
        assertEquals(expectedPaths, actualPaths);
    }

    private static void extractTest(@NonNull File[] sourceFiles, @NonNull File testRoot, @Nullable String[] include,
                                    @Nullable String[] exclude, @NonNull List<String> expectedPaths) throws Throwable {
        List<String> actualPaths = new ArrayList<>();
        recreateDir(testRoot);
        TarUtils.extract(TarUtils.TAR_GZIP, sourceFiles, testRoot, include, exclude);
        gatherFiles(actualPaths, testRoot, testRoot);
        Collections.sort(expectedPaths);
        Collections.sort(actualPaths);
        assertEquals(expectedPaths, actualPaths);
    }

    private static void recreateDir(File dir) {
        IOUtils.deleteDir(dir);
        dir.mkdirs();
    }

    private static void gatherFiles(@NonNull List<String> files, @NonNull File basePath, @NonNull File source) {
        files.add(TarUtils.getRelativePath(source, basePath, File.separator));
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) {
                gatherFiles(files, basePath, child);
            }
        }
    }
}
