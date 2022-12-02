// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex;

import androidx.annotation.NonNull;

import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedOdexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.DexDataStore;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.jf.smali.smaliFlexLexer;
import org.jf.smali.smaliParser;
import org.jf.smali.smaliTreeWalker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.core.utils.files.FileUtils;

public final class DexUtils {
    public static MultiDexContainer<? extends DexBackedDexFile> loadApk(File apkFile, int apiLevel) throws IOException {
        return DexFileFactory.loadDexContainer(apkFile, apiLevel < 0 ? Opcodes.getDefault() : Opcodes.forApi(apiLevel));
    }

    public static void storeDex(@NonNull List<ClassDef> classDefList, @NonNull DexDataStore dataStore, int apiLevel)
            throws IOException {
        Opcodes opcodes = apiLevel < 0 ? Opcodes.getDefault() : Opcodes.forApi(apiLevel);
        DexPool dexPool = new DexPool(opcodes);
        for (ClassDef classDef : classDefList) {
            dexPool.internClass(classDef);
        }
        dexPool.writeTo(dataStore);
    }

    @NonNull
    public static ClassDef toClassDef(@NonNull File smaliFile, int apiLevel) throws IOException, RecognitionException {
        try (InputStreamReader sr = new InputStreamReader(new FileInputStream(smaliFile), StandardCharsets.UTF_8)) {
            return toClassDef(sr, apiLevel);
        }
    }

    @NonNull
    public static ClassDef toClassDef(@NonNull String smaliContents, int apiLevel)
            throws IOException, RecognitionException {
        try (StringReader sr = new StringReader(smaliContents)) {
            return toClassDef(sr, apiLevel);
        }
    }

    @NonNull
    public static ClassDef toClassDef(@NonNull Reader smaliReader, int apiLevel)
            throws IOException, RecognitionException {
        Opcodes opcodes = apiLevel < 0 ? Opcodes.getDefault() : Opcodes.forApi(apiLevel);
        smaliFlexLexer lexer = new smaliFlexLexer(smaliReader, opcodes.api);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        smaliParser parser = new smaliParser(tokens);
        parser.setVerboseErrors(false);
        parser.setAllowOdex(false);
        parser.setApiLevel(opcodes.api);
        smaliParser.smali_file_return result = parser.smali_file();
        if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
            throw new IOException((parser.getNumberOfSyntaxErrors() + lexer.getNumberOfSyntaxErrors())
                    + " syntax errors during parsing and/or lexing.");
        }

        CommonTree t = result.getTree();
        CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
        treeStream.setTokenStream(tokens);

        DexBuilder dexBuilder = new DexBuilder(opcodes);
        smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);
        dexGen.setApiLevel(opcodes.api);
        dexGen.setVerboseErrors(false);
        dexGen.setDexBuilder(dexBuilder);
        ClassDef classDef = dexGen.smali_file();

        if (dexGen.getNumberOfSyntaxErrors() > 0) {
            throw new IOException(dexGen.getNumberOfSyntaxErrors() + " syntax errors during dex creation");
        }

        if (classDef == null) {
            throw new IOException("Could not generate class from smali.");
        }
        return classDef;
    }

    public static String toJavaCode(ClassDef classDef, Opcodes opcodes) throws IOException {
        File tmp = FileUtils.createTempFile(".dex");
        try {
            DexPool pool = new DexPool(opcodes);
            pool.internClass(classDef);
            pool.writeTo(new FileDataStore(tmp));
            return toJavaCode(tmp);
        } finally {
            tmp.delete();
        }
    }

    public static String toJavaCode(String smaliContents, int api) throws IOException {
        Opcodes opcodes = api < 0 ? Opcodes.getDefault() : Opcodes.forApi(api);
        try {
            return toJavaCode(toClassDef(smaliContents, api), opcodes);
        } catch (RecognitionException e) {
            throw new IOException(e);
        }
    }

    public static String toJavaCode(File dexFile) {
        JadxArgs args = new JadxArgs();
        args.setInputFile(dexFile);
        args.setSkipResources(true);
        args.setShowInconsistentCode(true);
        args.setDebugInfo(BuildConfig.DEBUG);
        try (JadxDecompiler decompiler = new JadxDecompiler(args)) {
            decompiler.load();
            JavaClass javaClass = decompiler.getClasses().iterator().next();
            javaClass.decompile();
            return javaClass.getCode();
        }
    }

    @NonNull
    public static DexBackedDexFile loadDexContainer(@NonNull InputStream inputStream, int api) throws IOException {
        Opcodes opcodes = api < 0 ? Opcodes.getDefault() : Opcodes.forApi(api);
        try {
            return DexBackedDexFile.fromInputStream(opcodes, inputStream);
        } catch (DexBackedDexFile.NotADexFile ex) {
            // just eat it
        }
        try {
            return DexBackedOdexFile.fromInputStream(opcodes, inputStream);
        } catch (DexBackedOdexFile.NotAnOdexFile ex) {
            // just eat it
        }
        throw new DexFileFactory.UnsupportedFileTypeException("InputStream is not a dex, odex file.");
    }
}
