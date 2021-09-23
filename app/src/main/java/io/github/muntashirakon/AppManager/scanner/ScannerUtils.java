// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.BuildConfig;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

public final class ScannerUtils {
    public static MultiDexContainer<? extends DexBackedDexFile> loadApk(File apkFile, int api) throws IOException {
        return DexFileFactory.loadDexContainer(apkFile, api < 0 ? null : Opcodes.forApi(api));
    }

    public static String toJavaCode(ClassDef classDef, Opcodes opcodes) throws IOException {
        File tmp = File.createTempFile("am-", ".dex");
        try {
            DexPool pool = new DexPool(opcodes);
            pool.internClass(classDef);
            pool.writeTo(new FileDataStore(tmp));
            JadxArgs args = new JadxArgs();
            args.setInputFile(tmp);
            args.setSkipResources(true);
            args.setShowInconsistentCode(true);
            args.setDebugInfo(BuildConfig.DEBUG);
            JadxDecompiler decompiler = new JadxDecompiler(args);
            decompiler.load();
            JavaClass javaClass = decompiler.getClasses().iterator().next();
            javaClass.decompile();
            return javaClass.getCode();
        } finally {
            tmp.delete();
        }
    }
}
