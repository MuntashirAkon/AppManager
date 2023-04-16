// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.app.Application;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class CodeEditorViewModel extends AndroidViewModel {
    public static final String TAG = CodeEditorViewModel.class.getSimpleName();

    // TODO: 12/9/22 Another option is to store them as assets/resources
    private static final Map<String, String> EXT_TO_LANGUAGE_MAP = new HashMap<String, String>() {{
        // We skip the default ones
        put("cmd", "sh");
        put("htm", "xml");
        put("html", "xml");
        put("kt", "kotlin");
        put("prop", "properties");
        put("tokens", "properties");
        put("xhtml", "xml");
    }};

    @Nullable
    private String language;
    private boolean canGenerateJava;
    @Nullable
    private Path sourceFile;
    private CodeEditorFragment.Options options;

    private final FileCache fileCache = new FileCache();
    private final MutableLiveData<String> mContentLiveData = new MutableLiveData<>();
    // Only for smali
    private final SingleLiveEvent<Uri> mJavaFileLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> mSaveFileLiveData = new MutableLiveData<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public CodeEditorViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        IoUtils.closeQuietly(fileCache);
        super.onCleared();
    }

    public LiveData<String> getContentLiveData() {
        return mContentLiveData;
    }

    public LiveData<Uri> getJavaFileLiveData() {
        return mJavaFileLiveData;
    }

    public LiveData<Boolean> getSaveFileLiveData() {
        return mSaveFileLiveData;
    }

    public void setOptions(@NonNull CodeEditorFragment.Options options) {
        this.options = options;
        sourceFile = options.uri != null ? Paths.get(options.uri) : null;
        String extension = sourceFile != null ? sourceFile.getExtension() : null;
        language = getLanguageFromExt(extension);
        canGenerateJava = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (options.javaSmaliToggle
                || "smali".equals(language));
    }

    @Nullable
    public Path getSourceFile() {
        return sourceFile;
    }

    public void loadFileContentIfAvailable() {
        if (sourceFile == null) return;
        mExecutor.submit(() -> mContentLiveData.postValue(sourceFile.getContentAsString(null)));
    }

    public void saveFile(@NonNull String content, @Nullable Path alternativeFile) {
        mExecutor.submit(() -> {
            if (sourceFile == null && alternativeFile == null) {
                mSaveFileLiveData.postValue(false);
                return;
            }
            // Important: Alternative file gets the top priority
            Path savingPath = alternativeFile != null ? alternativeFile : sourceFile;
            try (OutputStream os = savingPath.openOutputStream()) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
                mSaveFileLiveData.postValue(true);
            } catch (IOException e) {
                Log.e(TAG, "Could not write to file " + savingPath, e);
                mSaveFileLiveData.postValue(false);
            }
        });
    }

    public boolean isReadOnly() {
        return options == null || options.readOnly;
    }

    public boolean canWrite() {
        return !isReadOnly() && sourceFile != null && sourceFile.canWrite();
    }

    public boolean isBackedByAFile() {
        return sourceFile != null;
    }

    @NonNull
    public String getFilename() {
        if (sourceFile == null) {
            return "untitled.txt";
        }
        return sourceFile.getName();
    }

    public boolean canGenerateJava() {
        return canGenerateJava;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void generateJava(String smaliContent) {
        if (!canGenerateJava) {
            return;
        }
        mExecutor.submit(() -> {
            List<String> smaliContents;
            if (sourceFile != null) {
                Path parent = sourceFile.getParentFile();
                String baseName = DexUtils.getClassNameWithoutInnerClasses(Paths.trimPathExtension(sourceFile.getName()));
                String baseSmali = baseName + ".smali";
                String baseStartWith = baseName + "$";
                Path[] paths = parent != null ? parent.listFiles((dir, name) -> name.equals(baseSmali) || name.startsWith(baseStartWith))
                        : new Path[0];
                smaliContents = new ArrayList<>(paths.length + 1);
                smaliContents.add(smaliContent);
                for (Path path : paths) {
                    if (path.equals(sourceFile)) {
                        // We already have this file
                        continue;
                    }
                    try (InputStream is = path.openInputStream()) {
                        smaliContents.add(IoUtils.getInputStreamContent(is));
                    } catch (IOException e) {
                        e.printStackTrace();
                        mJavaFileLiveData.postValue(null);
                        return;
                    }
                }
            } else {
                smaliContents = Collections.singletonList(smaliContent);
            }
            try {
                File cachedFile = fileCache.createCachedFile("java");
                try (PrintStream ps = new PrintStream(cachedFile)) {
                    ps.print(DexUtils.toJavaCode(smaliContents, -1));
                }
                mJavaFileLiveData.postValue(Uri.fromFile(cachedFile));
            } catch (Throwable e) {
                e.printStackTrace();
                mJavaFileLiveData.postValue(null);
            }
        });
    }

    @Contract("!null -> !null")
    @Nullable
    private static String getLanguageFromExt(@Nullable String ext) {
        String lang = EXT_TO_LANGUAGE_MAP.get(ext);
        if (lang != null) return lang;
        return ext;
    }
}
