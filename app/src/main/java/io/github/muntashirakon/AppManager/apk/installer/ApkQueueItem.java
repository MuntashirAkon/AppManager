// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.SupportedAppStores.isAppStoreSupported;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class ApkQueueItem implements Parcelable, IJsonSerializer {
    @NonNull
    static List<ApkQueueItem> fromIntent(@NonNull Intent intent,
                                         @Nullable String originatingPackage) {
        List<ApkQueueItem> apkQueueItems = new ArrayList<>();
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null) {
            return apkQueueItems;
        }
        ContentResolver cr = ContextUtils.getContext().getContentResolver();
        String mimeType = intent.getType();
        Uri originatingUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_ORIGINATING_URI, Uri.class);
        int takeFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        for (Uri uri : uris) {
            ApkQueueItem item;
            if ("package".equals(uri.getScheme())) {
                item = new ApkQueueItem(uri.getSchemeSpecificPart(), true);
            } else { // file, content
                item = new ApkQueueItem(ApkSource.getCachedApkSource(uri, mimeType));
                item.mOriginatingUri = originatingUri;
                item.mOriginatingPackage = originatingPackage;
                if (takeFlags > 0) {
                    ExUtils.exceptionAsIgnored(() -> cr.takePersistableUriPermission(uri, takeFlags));
                }
            }
            apkQueueItems.add(item);
        }
        return apkQueueItems;
    }

    @NonNull
    public static ApkQueueItem fromApkSource(@NonNull ApkSource apkSource) {
        return new ApkQueueItem(apkSource.toCachedSource());
    }

    @NonNull
    private final String mOperationId;
    @Nullable
    private String mPackageName;
    @Nullable
    private String mAppLabel;
    private final boolean mInstallExisting;
    private boolean mTestOnly;
    @Nullable
    private String mOriginatingPackage;
    @Nullable
    private Uri mOriginatingUri;
    @Nullable
    private ApkSource mApkSource;
    @Nullable
    private InstallerOptions mInstallerOptions;
    @Nullable
    private ArrayList<String> mSelectedSplits;

    private ApkQueueItem(@NonNull String packageName, boolean installExisting) {
        mOperationId = UUID.randomUUID().toString();
        mPackageName = Objects.requireNonNull(packageName);
        mInstallExisting = installExisting;
        assert installExisting;
    }

    private ApkQueueItem(@NonNull ApkSource apkSource) {
        mOperationId = UUID.randomUUID().toString();
        mApkSource = Objects.requireNonNull(apkSource);
        mInstallExisting = false;
    }

    protected ApkQueueItem(@NonNull Parcel in) {
        mOperationId = Objects.requireNonNull(in.readString());
        mPackageName = in.readString();
        mAppLabel = in.readString();
        mInstallExisting = in.readByte() != 0;
        mTestOnly = in.readByte() != 0;
        mOriginatingPackage = in.readString();
        mOriginatingUri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mApkSource = ParcelCompat.readParcelable(in, ApkSource.class.getClassLoader(), ApkSource.class);
        mInstallerOptions = ParcelCompat.readParcelable(in, InstallerOptions.class.getClassLoader(), InstallerOptions.class);
        mSelectedSplits = new ArrayList<>();
        in.readStringList(mSelectedSplits);
    }

    @NonNull
    public String getOperationId() {
        return mOperationId;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(@Nullable String packageName) {
        mPackageName = packageName;
    }

    public boolean isInstallExisting() {
        return mInstallExisting;
    }

    public boolean isTestOnly() {
        return mTestOnly;
    }

    public void setTestOnly(boolean testOnly) {
        mTestOnly = testOnly;
    }

    @Nullable
    public ApkSource getApkSource() {
        return mApkSource;
    }

    public void setApkSource(@Nullable ApkSource apkSource) {
        mApkSource = apkSource;
    }

    @Nullable
    public InstallerOptions getInstallerOptions() {
        return mInstallerOptions != null ? InstallerOptions.copyOf(mInstallerOptions) : null;
    }

    public void setInstallerOptions(@Nullable InstallerOptions installerOptions) {
        if (installerOptions != null) {
            InstallerOptions effectiveOptions = InstallerOptions.copyOf(installerOptions);
            effectiveOptions.setOriginatingPackage(mOriginatingPackage);
            effectiveOptions.setOriginatingUri(mOriginatingUri);
            // Set package source to PACKAGE_SOURCE_STORE if it's supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && mOriginatingPackage != null
                    && isAppStoreSupported(mOriginatingPackage)) {
                effectiveOptions.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
            }
            mInstallerOptions = effectiveOptions;
        } else {
            mInstallerOptions = null;
        }
    }

    public void setSelectedSplits(@NonNull ArrayList<String> selectedSplits) {
        mSelectedSplits = selectedSplits;
    }

    @Nullable
    public ArrayList<String> getSelectedSplits() {
        return mSelectedSplits;
    }

    @Nullable
    public String getAppLabel() {
        return mAppLabel;
    }

    public void setAppLabel(@Nullable String appLabel) {
        mAppLabel = appLabel;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mOperationId);
        dest.writeString(mPackageName);
        dest.writeString(mAppLabel);
        dest.writeByte((byte) (mInstallExisting ? 1 : 0));
        dest.writeByte((byte) (mTestOnly ? 1 : 0));
        dest.writeString(mOriginatingPackage);
        dest.writeParcelable(mOriginatingUri, flags);
        dest.writeParcelable(mApkSource, flags);
        dest.writeParcelable(mInstallerOptions, flags);
        dest.writeStringList(mSelectedSplits);
    }

    protected ApkQueueItem(@NonNull JSONObject jsonObject) throws JSONException {
        String operationId = JSONUtils.optString(jsonObject, "op_id", null);
        mOperationId = !TextUtils.isEmpty(operationId) ? operationId : UUID.randomUUID().toString();
        mPackageName = JSONUtils.optString(jsonObject, "package_name", null);
        mAppLabel = JSONUtils.optString(jsonObject, "app_label", null);
        mInstallExisting = jsonObject.optBoolean("install_existing", false);
        mTestOnly = jsonObject.optBoolean("test_only", false);
        mOriginatingPackage = JSONUtils.optString(jsonObject, "originating_package", null);
        String originatingUri = JSONUtils.optString(jsonObject, "originating_uri", null);
        mOriginatingUri = originatingUri != null ? Uri.parse(originatingUri) : null;
        JSONObject apkSource = jsonObject.optJSONObject("apk_source");
        mApkSource = apkSource != null ? ApkSource.DESERIALIZER.deserialize(apkSource) : null;
        JSONObject installerOptions = jsonObject.optJSONObject("installer_options");
        mInstallerOptions = installerOptions != null ? InstallerOptions.DESERIALIZER.deserialize(installerOptions) : null;
        mSelectedSplits = JSONUtils.getArray(jsonObject.optJSONArray("selected_splits"));
    }

    public void validateForReplay() throws JSONException {
        if (mInstallExisting) {
            if (TextUtils.isEmpty(mPackageName)) {
                throw new JSONException("Package name is missing.");
            }
        } else {
            if (mApkSource == null) {
                throw new JSONException("APK source is missing.");
            }
            if (mSelectedSplits == null || mSelectedSplits.isEmpty()) {
                throw new JSONException("Selected APK splits are missing.");
            }
        }
        if (mInstallerOptions != null) {
            setInstallerOptions(InstallerOptions.resolveEffectiveOptions(mInstallerOptions,
                    mPackageName, mTestOnly));
        }
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op_id", mOperationId);
        jsonObject.put("package_name", mPackageName);
        jsonObject.put("app_label", mAppLabel);
        jsonObject.put("install_existing", mInstallExisting);
        jsonObject.put("test_only", mTestOnly);
        jsonObject.put("originating_package", mOriginatingPackage);
        jsonObject.put("originating_uri", mOriginatingUri != null ? mOriginatingUri.toString() : null);
        jsonObject.put("apk_source", mApkSource != null ? mApkSource.serializeToJson() : null);
        jsonObject.put("installer_options", mInstallerOptions != null ? mInstallerOptions.serializeToJson() : null);
        jsonObject.put("selected_splits", JSONUtils.getJSONArray(mSelectedSplits));
        return jsonObject;
    }

    public static final JsonDeserializer.Creator<ApkQueueItem> DESERIALIZER = ApkQueueItem::new;

    public static final Creator<ApkQueueItem> CREATOR = new Creator<ApkQueueItem>() {
        @Override
        @NonNull
        public ApkQueueItem createFromParcel(@NonNull Parcel in) {
            return new ApkQueueItem(in);
        }

        @Override
        @NonNull
        public ApkQueueItem[] newArray(int size) {
            return new ApkQueueItem[size];
        }
    };
}
