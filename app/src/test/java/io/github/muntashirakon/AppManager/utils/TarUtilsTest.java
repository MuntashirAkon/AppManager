// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitInputStream;

@RunWith(RobolectricTestRunner.class)
public class TarUtilsTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path testRoot;
    private Path tmpRoot;
    private Path[] tarGzFilesForExtractTest;

    @Before
    public void setUp() throws Throwable {
        assert classLoader != null;
        List<Path> resFiles = new ArrayList<>();
        resFiles.add(Paths.get(classLoader.getResource("plain.txt").getFile()));
        resFiles.add(Paths.get(classLoader.getResource("raw/exclude.txt").getFile()));
        resFiles.add(Paths.get(classLoader.getResource("raw/include.txt").getFile()));
        resFiles.add(Paths.get(classLoader.getResource("prefixed/prefixed_exclude.txt").getFile()));
        resFiles.add(Paths.get(classLoader.getResource("prefixed/prefixed_include.txt").getFile()));
        tmpRoot = Paths.get("/tmp");
        List<Path> tmpFiles = new ArrayList<>();
        testRoot = tmpRoot.findOrCreateDirectory("test");
        testRoot.findOrCreateDirectory("raw");
        testRoot.findOrCreateDirectory("prefixed");
        tmpFiles.add(testRoot.findOrCreateFile("plain.txt", null));
        tmpFiles.add(testRoot.findOrCreateDirectory("raw").findOrCreateFile("exclude.txt", null));
        tmpFiles.add(testRoot.findOrCreateDirectory("raw").findOrCreateFile("include.txt", null));
        tmpFiles.add(testRoot.findOrCreateDirectory("prefixed").findOrCreateFile("prefixed_exclude.txt", null));
        tmpFiles.add(testRoot.findOrCreateDirectory("prefixed").findOrCreateFile("prefixed_include.txt", null));
        // Copy files to tmpRoot
        for (int i = 0; i < resFiles.size(); ++i) {
            IoUtils.copy(resFiles.get(i), tmpFiles.get(i));
        }
        tarGzFilesForExtractTest = TarUtils.create(TarUtils.TAR_GZIP, testRoot, tmpRoot, "am_ex.tar.gz",
                null, null, null, false).toArray(new Path[0]);
    }

    @After
    public void tearDown() throws FileNotFoundException {
        testRoot.delete();
        tarGzFilesForExtractTest[0].delete();
        if (tmpRoot.hasFile("am.tar.gz.0")) {
            tmpRoot.findFile("am.tar.gz.0").delete();
        }
    }

    @Test
    public void testCreateTarGZipWithFilter() throws Throwable {
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{".*include\\.txt"}, null,
                Arrays.asList("prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithFilter() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*include\\.txt"},
                null, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryFilter() throws Throwable {
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{"prefixed/.*"}, null,
                Arrays.asList("prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt"));
    }

    @Test
    public void testExtractTarGZipWithDirectoryFilter() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{"prefixed/.*"}, null,
                Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "prefixed/prefixed_exclude.txt", "raw/"));
    }

    @Test
    public void testCreateTarGZipWithMultipleFilters() throws Throwable {
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{".*include\\.txt", "plain.*"}, null,
                Arrays.asList("prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithMultipleFilters() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */new String[]{".*include\\.txt", "plain.*"},
                null, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/",
                        "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryAndMultipleFilters() throws Throwable {
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{".*include\\.txt", "plain.*", "prefixed/.*"},
                null, Arrays.asList("prefixed/", "prefixed/prefixed_include.txt",
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
        createTest(tmpRoot, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt"},
                Arrays.asList("prefixed/", "prefixed/prefixed_include.txt", "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithExclude() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt"}, Arrays.asList("", "prefixed/", "prefixed/prefixed_include.txt",
                        "plain.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithExcludeDirectory() throws Throwable {
        createTest(tmpRoot, testRoot, null, /* language=regexp */ new String[]{"raw/.*"}, Arrays.asList(
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
        createTest(tmpRoot, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt", "plain.*"},
                Arrays.asList("prefixed/", "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testExtractTarGZipWithMultipleExcludes() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt", "plain.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "raw/", "raw/include.txt"));
    }

    @Test
    public void testCreateTarGZipWithDirectoryAndMultipleExcludes() throws Throwable {
        createTest(tmpRoot, testRoot, null, /* language=regexp */ new String[]{".*exclude\\.txt", "plain.*",
                "raw/.*"}, Arrays.asList("prefixed/", "prefixed/prefixed_include.txt"));
    }

    @Test
    public void testExtractTarGZipWithDirectoryAndMultipleExcludes() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, null, /* language=regexp */
                new String[]{".*exclude\\.txt", "plain.*", "raw/.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "raw/"));
    }

    @Test
    public void testCreateTarGZipWithFilterAndExclude() throws Throwable {
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{".*\\.txt"}, /* language=regexp */
                new String[]{".*exclude\\.txt"}, Arrays.asList("prefixed/", "prefixed/prefixed_include.txt",
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
        createTest(tmpRoot, testRoot, /* language=regexp */ new String[]{".*\\.txt", "include/.*"},
                /* language=regexp */ new String[]{".*exclude\\.txt", "raw/.*"}, Arrays.asList("prefixed/",
                        "prefixed/prefixed_include.txt", "plain.txt"));
    }

    @Test
    public void testExtractTarGZipWithFilterAndExcludeContainingDirectory() throws Throwable {
        extractTest(tarGzFilesForExtractTest, testRoot, /* language=regexp */ new String[]{".*\\.txt", "include/.*"},
                /* language=regexp */ new String[]{".*exclude\\.txt", "raw/.*"}, Arrays.asList("", "prefixed/",
                        "prefixed/prefixed_include.txt", "plain.txt", "raw/"));
    }

    @Test
    public void testCreateTarGZipWithNoFiltersOrExcludes() throws Throwable {
        createTest(tmpRoot, testRoot, null, null, Arrays.asList("prefixed/",
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
            actualPaths[i] = getRelativePath(absolutes[i], basePath, "/");
        }
        assertArrayEquals(expectedPaths, actualPaths);
    }

    @Test
    public void testGetRelativePathsUnix() {
        assertEquals("stuff/xyz.dat", getRelativePath(new File("/var/data/stuff/xyz.dat"),
                new File("/var/data/"), "/"));
        assertEquals("../../b/c", getRelativePath(new File("/a/b/c"),
                new File("/a/x/y/"), "/"));
        assertEquals("../../b/c", getRelativePath(new File("/m/n/o/a/b/c"),
                new File("/m/n/o/a/x/y/"), "/"));
    }

    @Test
    public void testGetRelativePathFileToFileDoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts\\chs_boot.ttf");
        File base = new File("C:\\Windows\\Speech\\Common\\sapisvr.exe");
        File workingBase = new File("C:\\Windows\\Speech\\Common\\");

        assertNotEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", getRelativePath(target, base, "\\"));
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathDirectoryToFile() {
        File target = new File("C:\\Windows\\Boot\\Fonts\\chs_boot.ttf");
        File base = new File("C:\\Windows\\Speech\\Common\\");

        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", getRelativePath(target, base, "\\"));
    }

    @Test
    public void testGetRelativePathFileToDirectoryDoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts");
        File base = new File("C:\\Windows\\Speech\\Common\\foo.txt");
        File workingBase = new File("C:\\Windows\\Speech\\Common\\");

        assertNotEquals("..\\..\\Boot\\Fonts", getRelativePath(target, base, "\\"));
        assertEquals("..\\..\\Boot\\Fonts", getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathFileToDirectory2DoesNotWork() {
        File target = new File("C:\\Windows\\Boot\\Fonts");
        File base = new File("C:\\foo.txt");
        File workingBase = new File("C:\\");

        assertNotEquals("Windows\\Boot\\Fonts", getRelativePath(target, base, "\\"));
        assertEquals("Windows\\Boot\\Fonts", getRelativePath(target, workingBase, "\\"));
    }

    @Test
    public void testGetRelativePathDirectoryToDirectory() {
        File target = new File("C:\\Windows\\Boot\\");
        File base = new File("C:\\Windows\\Speech\\Common\\");
        String expected = "..\\..\\Boot\\";

        String relPath = getRelativePath(target, base, "\\");
        assertEquals(expected, relPath);
    }

    @Test
    public void testGetRelativePathDifferentDriveLetters() {
        File target = new File("D:\\sources\\recovery\\RecEnv.exe");
        File base = new File("C:\\Java\\workspace\\AcceptanceTests\\Standard test data\\geo\\");
        assertEquals(target.getAbsolutePath(), getRelativePath(target, base, "\\"));
    }

    @Test
    public void testGetAbsolutePathToDataApp() {
        String[] brokenPaths = new String[]{
                "/data/app",
                "/data/app/",
                "/data/app/example.app",
                "/data/app/example.app/",
                "/data/app/example.app/lib",
                "/data/app/example.app/lib/",
                "/data/app/example.app/oat",
                "/data/app/example.app/base.apk",
                "/data/app/~~random-things==/example.app-more_random_things==",
                "/data/app/~~random-things==/example.app-more_random_things==/",
                "/data/app/~~random-things==/example.app-more_random_things==/lib",
                "/data/app/~~random-things==/example.app-more_random_things==/lib/",
                "/data/app/~~random-things==/example.app-more_random_things==/base.apk",
        };
        String realPath = "/data/app/~~new-random-things==/example.app-more_new_random_things==";
        String[] expectedPaths = new String[]{
                "/data/app",
                "/data/app",
                realPath,
                realPath,
                realPath + "/lib",
                realPath + "/lib",
                realPath + "/oat",
                realPath + "/base.apk",
                realPath,
                realPath,
                realPath + "/lib",
                realPath + "/lib",
                realPath + "/base.apk",
        };
        for (int i = 0; i < brokenPaths.length; ++i) {
            assertEquals("Failed in index " + i, expectedPaths[i], TarUtils.getAbsolutePathToDataApp(brokenPaths[i], realPath));
        }
    }

    @NonNull
    public static List<String> getFileNamesGZip(@NonNull List<Path> tarFiles) throws IOException {
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

    private static void createTest(@NonNull Path source, @NonNull Path testRoot, @Nullable String[] include,
                                   @Nullable String[] exclude, @NonNull List<String> expectedPaths) throws Throwable {
        List<Path> files = TarUtils.create(TarUtils.TAR_GZIP, testRoot, source, "am.tar.gz", include,
                null, exclude, false);
        List<String> actualPaths = getFileNamesGZip(files);
        Collections.sort(expectedPaths);
        Collections.sort(actualPaths);
        assertEquals(expectedPaths, actualPaths);
    }

    private static void extractTest(@NonNull Path[] sourceFiles, @NonNull Path testRoot, @Nullable String[] include,
                                    @Nullable String[] exclude, @NonNull List<String> expectedPaths) throws Throwable {
        List<String> actualPaths = new ArrayList<>();
        recreateDir(testRoot);
        TarUtils.extract(TarUtils.TAR_GZIP, sourceFiles, testRoot, include, exclude, null);
        gatherFiles(actualPaths, testRoot, testRoot);
        Collections.sort(expectedPaths);
        Collections.sort(actualPaths);
        assertEquals(expectedPaths, actualPaths);
    }

    private static void recreateDir(@NonNull Path dir) {
        dir.delete();
        dir.mkdirs();
    }

    private static void gatherFiles(@NonNull List<String> files, @NonNull Path basePath, @NonNull Path source) {
        files.add(Paths.relativePath(source, basePath));
        if (source.isDirectory()) {
            Path[] children = source.listFiles();
            if (children.length == 0) return;
            for (Path child : children) {
                gatherFiles(files, basePath, child);
            }
        }
    }

    @VisibleForTesting
    @NonNull
    static String getRelativePath(@NonNull File file, @NonNull File basePath, @NonNull String separator) {
        String baseDir = basePath.toURI().getPath();
        String targetPath = file.toURI().getPath();
        return Paths.relativePath(targetPath, baseDir, separator);
    }
}
