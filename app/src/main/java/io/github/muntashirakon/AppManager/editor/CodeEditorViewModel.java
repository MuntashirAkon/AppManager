// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.IGNORABLE_WHITESPACE;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.Contract;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlEncoder;
import io.github.muntashirakon.AppManager.dex.DexUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.compat.xml.TypedXmlPullParser;
import io.github.muntashirakon.compat.xml.TypedXmlSerializer;
import io.github.muntashirakon.compat.xml.Xml;
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

    @IntDef({XML_TYPE_NONE, XML_TYPE_AXML, XML_TYPE_ABX})
    @Retention(RetentionPolicy.SOURCE)
    private @interface XmlType {
    }

    public static final int XML_TYPE_NONE = 0;
    public static final int XML_TYPE_AXML = 1;
    public static final int XML_TYPE_ABX = 2;

    @Nullable
    private String language;
    private boolean canGenerateJava;
    @XmlType
    private int xmlType = XML_TYPE_NONE;
    @Nullable
    private Path sourceFile;
    private CodeEditorFragment.Options options;
    @Nullable
    private Future<?> contentLoaderResult;
    @Nullable
    private Future<?> javaConverterResult;

    private final FileCache fileCache = new FileCache();
    private final MutableLiveData<String> mContentLiveData = new MutableLiveData<>();
    // Only for smali
    private final SingleLiveEvent<Uri> mJavaFileLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> mSaveFileLiveData = new MutableLiveData<>();

    public CodeEditorViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (contentLoaderResult != null) {
            contentLoaderResult.cancel(true);
        }
        if (javaConverterResult != null) {
            javaConverterResult.cancel(true);
        }
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
        canGenerateJava = options.javaSmaliToggle || "smali".equals(language);
    }

    @Nullable
    public Path getSourceFile() {
        return sourceFile;
    }

    public void loadFileContentIfAvailable() {
        if (sourceFile == null) return;
        if (contentLoaderResult != null) {
            contentLoaderResult.cancel(true);
        }
        contentLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            String content = null;
            if ("xml".equals(language)) {
                byte[] bytes = sourceFile.getContentAsBinary();
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                try {
                    if (AndroidBinXmlDecoder.isBinaryXml(buffer)) {
                        content = AndroidBinXmlDecoder.decode(bytes);
                        xmlType = XML_TYPE_AXML;
                    } else if (Xml.isBinaryXml(buffer)) {
                        // FIXME: 19/5/23 Unfortunately, converting ABX to XML is lossy. Find a way to fix this.
                        //  Until then, the feature is disabled.
                        // content = getXmlFromAbx(bytes);
                        // xmlType = XML_TYPE_ABX;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to convert XML bytes to plain text.", e);
                }
            }
            if (content == null) {
                content = sourceFile.getContentAsString();
                xmlType = XML_TYPE_NONE;
            }
            mContentLiveData.postValue(content);
        });
    }

    public void saveFile(@NonNull String content, @Nullable Path alternativeFile) {
        ThreadUtils.postOnBackgroundThread(() -> {
            if (sourceFile == null && alternativeFile == null) {
                mSaveFileLiveData.postValue(false);
                return;
            }
            // Important: Alternative file gets the top priority
            Path savingPath = alternativeFile != null ? alternativeFile : sourceFile;
            try (OutputStream os = savingPath.openOutputStream()) {
                byte[] realContent;
                switch (xmlType) {
                    case XML_TYPE_AXML:
                        realContent = AndroidBinXmlEncoder.encodeString(content);
                        break;
                    case XML_TYPE_ABX:
                        realContent = getAbxFromXml(content);
                        break;
                    default:
                    case XML_TYPE_NONE:
                        realContent = content.getBytes(StandardCharsets.UTF_8);
                }
                os.write(realContent);
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
        if (javaConverterResult != null) {
            javaConverterResult.cancel(true);
        }
        javaConverterResult = ThreadUtils.postOnBackgroundThread(() -> {
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
                    String content = path.getContentAsString(null);
                    if (content != null) {
                        smaliContents.add(path.getContentAsString());
                    } else {
                        mJavaFileLiveData.postValue(null);
                        return;
                    }
                }
            } else {
                smaliContents = Collections.singletonList(smaliContent);
            }
            if (ThreadUtils.isInterrupted()) {
                return;
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

    private static String getXmlFromAbx(@NonNull byte[] data) throws IOException {
        try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            TypedXmlPullParser parser = Xml.newBinaryPullParser();
            parser.setInput(is, StandardCharsets.UTF_8.name());
            TypedXmlSerializer serializer = Xml.newFastSerializer();
            serializer.setOutput(os, StandardCharsets.UTF_8.name());
            copyXml(parser, serializer);
            return os.toString();
        } catch (XmlPullParserException e) {
            return ExUtils.rethrowAsIOException(e);
        }
    }

    private static byte[] getAbxFromXml(@NonNull String data) throws IOException {
        try (StringReader is = new StringReader(data);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            TypedXmlPullParser parser = Xml.newFastPullParser();
            parser.setInput(is);
            TypedXmlSerializer serializer = Xml.newBinarySerializer();
            serializer.setOutput(os, StandardCharsets.UTF_8.name());
            copyXml(parser, serializer);
            return os.toByteArray();
        } catch (XmlPullParserException e) {
            return ExUtils.rethrowAsIOException(e);
        }
    }

    public static void copyXml(@NonNull TypedXmlPullParser parser, @NonNull TypedXmlSerializer serializer)
            throws IOException, XmlPullParserException {
        serializer.startDocument(null, null);
        int event;
        do {
            event = parser.nextToken();
            switch (event) {
                case START_TAG:
                    serializer.startTag(null, parser.getName());
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String attributeName = parser.getAttributeName(i);
                        serializer.attribute(null, attributeName, parser.getAttributeValue(i));
                    }
                    break;
                case END_TAG:
                    serializer.endTag(null, parser.getName());
                    break;
                case TEXT:
                    serializer.text(parser.getText());
                    break;
                case IGNORABLE_WHITESPACE:
                    serializer.ignorableWhitespace(parser.getText());
                    break;
                case END_DOCUMENT:
                    serializer.endDocument();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        } while (event != END_DOCUMENT);
    }
}
