// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;

import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.baksmali.formatter.BaksmaliFormatter;
import org.jf.baksmali.formatter.BaksmaliWriter;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.analysis.InlineMethodResolver;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedOdexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;

public class DexClasses implements Closeable {
    private final HashMap<String, ClassDef> classDefArraySet = new HashMap<>();
    private final BaksmaliOptions options;

    public DexClasses(@NonNull File apkFile) throws IOException {
        this.options = new BaksmaliOptions();
        // options
        options.deodex = false;
        options.implicitReferences = false;
        options.parameterRegisters = true;
        options.localsDirective = true;
        options.sequentialLabels = true;
        options.debugInfo = BuildConfig.DEBUG;
        options.codeOffsets = false;
        options.accessorComments = false;
        options.registerInfo = 0;
        options.inlineResolver = null;
        BaksmaliFormatter formatter = new BaksmaliFormatter();
        MultiDexContainer<? extends DexBackedDexFile> container = DexFileFactory.loadDexContainer(apkFile, null);
        List<String> dexEntryNames = container.getDexEntryNames();
        for (String dexEntryName : dexEntryNames) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry =
                    Objects.requireNonNull(container.getEntry(dexEntryName));
            DexBackedDexFile dexFile = dexEntry.getDexFile();
            // Store list of classes
            for (ClassDef classDef : dexFile.getClasses()) {
                String name = formatter.getType(classDef.getType());
                if (name.endsWith(";")) name = name.substring(0, name.length() - 1);
                if (name.startsWith("L")) {
                    name = name.substring(1).replace('/', '.');
                }
                classDefArraySet.put(name, classDef);
            }
            if (dexFile.supportsOptimizedOpcodes()) {
                throw new IOException("ODEX isn't supported.");
            }
            if (dexFile instanceof DexBackedOdexFile) {
                options.inlineResolver = InlineMethodResolver.createInlineMethodResolver(
                        ((DexBackedOdexFile) dexFile).getOdexVersion());
            }
        }
    }

    @NonNull
    public List<String> getClassNames() {
        return new ArrayList<>(classDefArraySet.keySet());
    }

    @NonNull
    public String getClassContents(@NonNull String className) throws ClassNotFoundException {
        ClassDef classDef = classDefArraySet.get(className);
        if (classDef == null) throw new ClassNotFoundException(className + " could not be found.");
        StringWriter stringWriter = new StringWriter();
        try (BaksmaliWriter baksmaliWriter = new BaksmaliFormatter().getWriter(stringWriter)) {
            ClassDefinition classDefinition = new ClassDefinition(this.options, classDef);
            classDefinition.writeTo(baksmaliWriter);
        } catch (IOException e) {
            throw new ClassNotFoundException(e.toString());
        }
        return stringWriter.toString();
    }

    @Override
    public void close() throws IOException {
    }
}
