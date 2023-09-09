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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
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
import io.github.muntashirakon.io.CharSequenceInputStream;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;

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
    private String mLanguage;
    private boolean mCanGenerateJava;
    @XmlType
    private int mXmlType = XML_TYPE_NONE;
    @Nullable
    private Path mSourceFile;
    private CodeEditorFragment.Options mOptions;
    @Nullable
    private Future<?> mContentLoaderResult;
    @Nullable
    private Future<?> mJavaConverterResult;

    private final FileCache mFileCache = new FileCache();
    private final MutableLiveData<Content> mContentLiveData = new MutableLiveData<>();
    // Only for smali
    private final SingleLiveEvent<Uri> mJavaFileLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> mSaveFileLiveData = new MutableLiveData<>();

    public CodeEditorViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mContentLoaderResult != null) {
            mContentLoaderResult.cancel(true);
        }
        if (mJavaConverterResult != null) {
            mJavaConverterResult.cancel(true);
        }
        IoUtils.closeQuietly(mFileCache);
        super.onCleared();
    }

    public LiveData<Content> getContentLiveData() {
        return mContentLiveData;
    }

    public LiveData<Uri> getJavaFileLiveData() {
        return mJavaFileLiveData;
    }

    public LiveData<Boolean> getSaveFileLiveData() {
        return mSaveFileLiveData;
    }

    public void setOptions(@NonNull CodeEditorFragment.Options options) {
        mOptions = options;
        mSourceFile = options.uri != null ? Paths.get(options.uri) : null;
        String extension = mSourceFile != null ? mSourceFile.getExtension() : null;
        mLanguage = getLanguageFromExt(extension);
        mCanGenerateJava = options.javaSmaliToggle || "smali".equals(mLanguage);
    }

    @Nullable
    public Path getSourceFile() {
        return mSourceFile;
    }

    public void loadFileContentIfAvailable() {
        if (mSourceFile == null) return;
        if (mContentLoaderResult != null) {
            mContentLoaderResult.cancel(true);
        }
        mContentLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
            Content content = null;
            if ("xml".equals(mLanguage)) {
                byte[] bytes = mSourceFile.getContentAsBinary();
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                try {
                    if (AndroidBinXmlDecoder.isBinaryXml(buffer)) {
                        content = new Content(AndroidBinXmlDecoder.decode(bytes));
                        mXmlType = XML_TYPE_AXML;
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
                try (InputStream is = mSourceFile.openInputStream()) {
                    content = ContentIO.createFrom(is);
                    mXmlType = XML_TYPE_NONE;
                }catch (IOException e) {
                    Log.e(TAG, "Could not read file %s", e, mSourceFile);
                }
            }
            mContentLiveData.postValue(content);
        });
    }

    public void saveFile(@NonNull Content content, @Nullable Path alternativeFile) {
        ThreadUtils.postOnBackgroundThread(() -> {
            if (mSourceFile == null && alternativeFile == null) {
                mSaveFileLiveData.postValue(false);
                return;
            }
            // Important: Alternative file gets the top priority
            Path savingPath = alternativeFile != null ? alternativeFile : mSourceFile;
            try (OutputStream os = savingPath.openOutputStream()) {
                switch (mXmlType) {
                    case XML_TYPE_AXML: {
                        // TODO: Use serializer from the latest update
                        byte[] realContent = AndroidBinXmlEncoder.encodeString(content.toString());
                        os.write(realContent);
                        break;
                    }
                    case XML_TYPE_ABX: {
                        try (InputStream is = new CharSequenceInputStream(content, StandardCharsets.UTF_8)) {
                            copyAbxFromXml(is, os);
                        }
                        break;
                    }
                    default:
                    case XML_TYPE_NONE:
                        ContentIO.writeTo(content, os, false);
                }
                mSaveFileLiveData.postValue(true);
            } catch (IOException e) {
                Log.e(TAG, "Could not write to file %s", e, savingPath);
                mSaveFileLiveData.postValue(false);
            }
        });
    }

    public boolean isReadOnly() {
        return mOptions == null || mOptions.readOnly;
    }

    public boolean canWrite() {
        return !isReadOnly() && mSourceFile != null && mSourceFile.canWrite();
    }

    public boolean isBackedByAFile() {
        return mSourceFile != null;
    }

    @NonNull
    public String getFilename() {
        if (mSourceFile == null) {
            return "untitled.txt";
        }
        return mSourceFile.getName();
    }

    public boolean canGenerateJava() {
        return mCanGenerateJava;
    }

    @Nullable
    public String getLanguage() {
        return mLanguage;
    }

    public void generateJava(Content smaliContent) {
        if (!mCanGenerateJava) {
            return;
        }
        if (mJavaConverterResult != null) {
            mJavaConverterResult.cancel(true);
        }
        mJavaConverterResult = ThreadUtils.postOnBackgroundThread(() -> {
            List<String> smaliContents;
            if (mSourceFile != null) {
                Path parent = mSourceFile.getParent();
                String baseName = DexUtils.getClassNameWithoutInnerClasses(Paths.trimPathExtension(mSourceFile.getName()));
                String baseSmali = baseName + ".smali";
                String baseStartWith = baseName + "$";
                Path[] paths = parent != null ? parent.listFiles((dir, name) -> name.equals(baseSmali) || name.startsWith(baseStartWith))
                        : new Path[0];
                smaliContents = new ArrayList<>(paths.length + 1);
                smaliContents.add(smaliContent.toString());
                for (Path path : paths) {
                    if (path.equals(mSourceFile)) {
                        // We already have this file
                        continue;
                    }
                    String content = path.getContentAsString(null);
                    if (content != null) {
                        smaliContents.add(content);
                    } else {
                        mJavaFileLiveData.postValue(null);
                        return;
                    }
                }
            } else {
                smaliContents = Collections.singletonList(smaliContent.toString());
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            try {
                File cachedFile = mFileCache.createCachedFile("java");
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

    private static void copyAbxFromXml(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        try (Reader is = new InputStreamReader(in)) {
            TypedXmlPullParser parser = Xml.newFastPullParser();
            parser.setInput(is);
            TypedXmlSerializer serializer = Xml.newBinarySerializer();
            serializer.setOutput(out, StandardCharsets.UTF_8.name());
            copyXml(parser, serializer);
        } catch (XmlPullParserException e) {
            ExUtils.rethrowAsIOException(e);
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
