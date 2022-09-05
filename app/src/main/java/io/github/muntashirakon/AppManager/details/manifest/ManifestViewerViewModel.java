// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlParser;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class ManifestViewerViewModel extends AndroidViewModel {
    public static final String TAG = ManifestViewerViewModel.class.getSimpleName();


    private static final Pattern QUOTATIONS = Pattern.compile("\"([^\"]*)\"", Pattern.MULTILINE);
    private static final Pattern MANIFEST_TAGS = Pattern.compile
            ("(</?(manifest|application|compatible-screens|instrumentation|permission" +
                            "(-group|-tree)?|supports-(gl-texture|screens)|" +
                            "uses-(configuration|feature|permission(-sdk-(23|m))?|sdk|split|static-library)|" +
                            "activity(-alias)?|meta-data|service|receiver|provider|uses-library|static-library|" +
                            "intent-filter|layout|eat-comment|" +
                            "grant-uri-permissions|path-permission|action|category|data|protected-" +
                            "broadcast|overlay|library|original-package|restrict-update|" +
                            "adopt-permissions|feature-group|key-sets|package|package-verifier|" +
                            "attribution|queries|supports-input|" +
                            "profileable)\\b|/?>)",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private ApkFile apkFile;
    @Nullable
    private CharSequence title;
    private int tagColor;
    private int attrValueColor;

    private final MutableLiveData<CharSequence> manifestContent = new MutableLiveData<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public ManifestViewerViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        FileUtils.closeQuietly(apkFile);
        mExecutor.shutdownNow();
        super.onCleared();
    }

    @Nullable
    public CharSequence getTitle() {
        return title;
    }

    @Nullable
    public String getPackageName() {
        if (apkFile != null) {
            return apkFile.getPackageName();
        }
        return null;
    }

    public LiveData<CharSequence> getManifestContent() {
        return manifestContent;
    }

    public void setTagColor(int tagColor) {
        this.tagColor = tagColor;
    }

    public void setAttrValueColor(int attrValueColor) {
        this.attrValueColor = attrValueColor;
    }

    public void loadApkFile(@Nullable Uri packageUri, @Nullable String type, @Nullable String packageName) {
        mExecutor.submit(() -> {
            final PackageManager pm = getApplication().getPackageManager();
            if (packageUri != null) {
                try {
                    int key = ApkFile.createInstance(packageUri, type);
                    apkFile = ApkFile.getInstance(key);
                } catch (ApkFile.ApkFileException e) {
                    Log.e(TAG, "Error: ", e);
                    return;
                }
                String archiveFilePath;
                try {
                    archiveFilePath = apkFile.getBaseEntry().getRealCachedFile().getAbsolutePath();
                } catch (IOException e) {
                    return;
                }
                PackageInfo packageInfo = pm.getPackageArchiveInfo(archiveFilePath, 0);
                if (packageInfo != null) {
                    final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    applicationInfo.publicSourceDir = archiveFilePath;
                    applicationInfo.sourceDir = archiveFilePath;
                    title = applicationInfo.loadLabel(pm);
                } // else Could be a split apk
            } else {
                try {
                    ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
                    int key = ApkFile.createInstance(applicationInfo);
                    apkFile = ApkFile.getInstance(key);
                    title = applicationInfo.loadLabel(pm);
                } catch (PackageManager.NameNotFoundException | ApkFile.ApkFileException e) {
                    Log.e(TAG, "Error: ", e);
                }
            }
            if (apkFile != null) {
                ByteBuffer byteBuffer = apkFile.getBaseEntry().manifest;
                // Reset properties
                byteBuffer.position(0);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                try {
                    manifestContent.postValue(getFormattedManifestContent(AndroidBinXmlDecoder.decode(byteBuffer, true)));
                } catch (AndroidBinXmlParser.XmlParserException e) {
                    Log.e(TAG, "Could not parse APK", e);
                }
            }
        });
    }

    @NonNull
    private CharSequence getFormattedManifestContent(String manifestContent) {
        SpannableString formattedContent = new SpannableString(manifestContent);
        Matcher matcher = MANIFEST_TAGS.matcher(manifestContent);
        while (matcher.find()) {
            formattedContent.setSpan(new ForegroundColorSpan(tagColor), matcher.start(),
                    matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        matcher.usePattern(QUOTATIONS);
        while (matcher.find()) {
            formattedContent.setSpan(new ForegroundColorSpan(attrValueColor), matcher.start(),
                    matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return formattedContent;
    }
}
