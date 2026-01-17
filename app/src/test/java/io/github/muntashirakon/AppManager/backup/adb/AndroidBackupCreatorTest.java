// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static org.junit.Assert.*;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.TarUtilsTest;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class AndroidBackupCreatorTest {
    private final ClassLoader classLoader = getClass().getClassLoader();
    private Path testDir;
    private Path tarPath;

    @Before
    public void setUp() throws Exception {
        assert classLoader != null;
        testDir = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("test-dir");
        tarPath = Paths.get(classLoader.getResource("backups/adb/all_data.tar").getFile());
    }

    @After
    public void tearDown() throws Exception {
        testDir.delete();
    }

    @Test
    public void testTarToAb() throws IOException {
        Path tmpAbPath = testDir.createNewFile("output.ab", null);
        Path tmpTarPath = testDir.createNewFile("output.tar", null);
        AndroidBackupCreator.fromTar(tarPath, tmpAbPath, null, 36, true);
        AndroidBackupExtractor.toTar(tmpAbPath, tmpTarPath, null);
        // We cannot compare AB files directly because fromTar method directly processes the tar
        //  file which cause a slight mismatch in the footer section of the file.
        assertEquals(DigestUtils.getHexDigest(DigestUtils.SHA_256, tarPath),
                DigestUtils.getHexDigest(DigestUtils.SHA_256, tmpTarPath));
    }

    @Test
    public void testAmToAdbBackup() throws IOException {
        assert classLoader != null;
        Path sourceFile = Paths.get(classLoader.getResource("backups/adb/source.tar.gz.0").getFile());
        Map<Integer, List<Path>> categoryFilesMap = new HashMap<Integer, List<Path>>() {{
            put(BackupCategories.CAT_SRC, Collections.singletonList(sourceFile));
            put(BackupCategories.CAT_INT_CE, Collections.singletonList(Paths.get(classLoader.getResource("backups/adb/data0.tar.gz.0").getFile())));
            put(BackupCategories.CAT_INT_DE, Collections.singletonList(Paths.get(classLoader.getResource("backups/adb/data1.tar.gz.0").getFile())));
            put(BackupCategories.CAT_EXT, Collections.singletonList(Paths.get(classLoader.getResource("backups/adb/data2.tar.gz.0").getFile())));
        }};
        TarUtils.extract(TarUtils.TAR_GZIP, new Path[]{sourceFile}, testDir, null, null, null);
        Path baseApk = testDir.findFile("base.apk");
        PackageManager packageManager = RuntimeEnvironment.getApplication().getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(Objects.requireNonNull(baseApk.getFilePath()), 0);
        assert packageInfo != null;
        // Robolectric does not yet support generating signatures
        packageInfo.signatures = new Signature[]{new Signature("308202e4308201cc020101300d06092a864886f70d01010b050030373116301406035504030c0d416e64726f69642044656275673110300e060355040a0c07416e64726f6964310b30090603550406130255533020170d3235303931383032353031335a180f32303535303931313032353031335a30373116301406035504030c0d416e64726f69642044656275673110300e060355040a0c07416e64726f6964310b300906035504061302555330820122300d06092a864886f70d01010105000382010f003082010a0282010100c189f17de4aa0e706c5710e49a990cc5ef6231988372500ded8e67ee817e928301adee36cbf815467ac75f3918a1dd8ecf33f3cede61cb7bc00852407fc0b8c90a5372226923cc930d19146e9f76129d3add81b63c986e73f05a1f5522017e655b3291fb7a1f7e0f28e7d019d57301629c34ac0d3f801fdea639766900fec630e5be6c703ec8d59e6273daf3c88b7f1f506eb334a51578587978791a5e7899f7b3c5825a706b85f6c64eebdb9dc5c556f2759db5f4efe0bdc95f6eb6ae05f783919eba91e9aaa8978288b775414f4e4dbc0cd4b1db6bc459978938f34f5b31866e36cc2ac9c14441f83f61c27b2af23f2814b99bf14d3efa6388c18262e398790203010001300d06092a864886f70d01010b050003820101008fc815e3fd2df80b52678fda6baa994f7160f677bbede6d51e3d5f7ce0babc7db2941d4b3be9af4a2b2d39cfce90776cb74d79cbc00a8fa6ca443e746074351ec2a64e7b14a59f6560dfa3de4276c1e28ab16a567f9b970ee0a1810cffa5bb4dd5b6c9475edd0eb4449433af476e75604cc7c3114536288f036e6d0265e5f59edd6d20ee4da55b6706b006d2e822cee940b21caa7d90142d171a217b02ad15b57714ffbbdb61022def6b96d0cf9f04a85d47954ee804b7e1cb8e59922c456fed040fe620aa86b9e542d3cdfa073539582818080b17133e895ad2947c4f9290c7826a91bea226be5660ef33b41e27682994bb7e5111524cd9747637f9cea238f1")};
        try(AndroidBackupCreator creator = new AndroidBackupCreator(categoryFilesMap, testDir, packageInfo, null, TarUtils.TAR_GZIP)) {
            Path newAbFile = creator.getBackupFile(0);
            Path tmpTarPath = testDir.createNewFile("output.tar", null);
            AndroidBackupExtractor.toTar(newAbFile, tmpTarPath, null);
            // We cannot do a direct tar hash matching because the alignment of files is incorrect
            List<String> expected = TarUtilsTest.getFileNamesNoCompress(Collections.singletonList(tarPath));
            List<String> actual = TarUtilsTest.getFileNamesNoCompress(Collections.singletonList(tmpTarPath));
            Collections.sort(expected);
            Collections.sort(actual);
            assertEquals(expected, actual);
            assertEquals(tarPath.length(), tmpTarPath.length());
        }
    }
}
