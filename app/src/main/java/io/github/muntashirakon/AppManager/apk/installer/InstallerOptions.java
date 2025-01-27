// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.UserIdInt;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class InstallerOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public static InstallerOptions getDefault() {
        return new InstallerOptions();
    }

    @UserIdInt
    private int mUserId;
    private int mInstallLocation;
    @Nullable
    private String mInstallerName;
    @Nullable
    private String mOriginatingPackage;
    @Nullable
    private Uri mOriginatingUri;
    private int mPackageSource;
    private int mInstallScenario;
    private boolean mRequestUpdateOwnership;
    private boolean mSignApkFiles;
    private boolean mForceDexOpt;
    private boolean mBlockTrackers;

    private InstallerOptions() {
        mUserId = UserHandleHidden.myUserId();
        mInstallLocation = Prefs.Installer.getInstallLocation();
        mInstallerName = Prefs.Installer.getInstallerPackageName();
        mOriginatingPackage = Prefs.Installer.getOriginatingPackage();
        mOriginatingUri = null;
        mPackageSource = Prefs.Installer.getPackageSource();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // If the user is always installing apps in the background, we expect that the user does
            // want to install an app quite fast.
            mInstallScenario = Prefs.Installer.installInBackground()
                    ? PackageManager.INSTALL_SCENARIO_BULK
                    : PackageManager.INSTALL_SCENARIO_FAST;
        }
        mRequestUpdateOwnership = Prefs.Installer.requestUpdateOwnership();
        mSignApkFiles = Prefs.Installer.canSignApk();
        mForceDexOpt = Prefs.Installer.forceDexOpt();
        mBlockTrackers = Prefs.Installer.blockTrackers();
    }

    protected InstallerOptions(@NonNull Parcel in) {
        mUserId = in.readInt();
        mInstallLocation = in.readInt();
        mInstallerName = in.readString();
        mOriginatingPackage = in.readString();
        mOriginatingUri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mPackageSource = in.readInt();
        mInstallScenario = in.readInt();
        mRequestUpdateOwnership = in.readByte() != 0;
        mSignApkFiles = in.readByte() != 0;
        mForceDexOpt = in.readByte() != 0;
        mBlockTrackers = in.readByte() != 0;
    }

    public void copy(@NonNull InstallerOptions options) {
        mUserId = options.mUserId;
        mInstallLocation = options.mInstallLocation;
        mInstallerName = options.mInstallerName;
        mOriginatingPackage = options.mOriginatingPackage;
        mOriginatingUri = options.mOriginatingUri;
        mPackageSource = options.mPackageSource;
        mInstallScenario = options.mInstallScenario;
        mRequestUpdateOwnership = options.mRequestUpdateOwnership;
        mSignApkFiles = options.mSignApkFiles;
        mForceDexOpt = options.mForceDexOpt;
        mBlockTrackers = options.mBlockTrackers;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mUserId);
        dest.writeInt(mInstallLocation);
        dest.writeString(mInstallerName);
        dest.writeString(mOriginatingPackage);
        dest.writeParcelable(mOriginatingUri, flags);
        dest.writeInt(mPackageSource);
        dest.writeInt(mInstallScenario);
        dest.writeByte((byte) (mRequestUpdateOwnership ? 1 : 0));
        dest.writeByte((byte) (mSignApkFiles ? 1 : 0));
        dest.writeByte((byte) (mForceDexOpt ? 1 : 0));
        dest.writeByte((byte) (mBlockTrackers ? 1 : 0));
    }

    protected InstallerOptions(@NonNull JSONObject jsonObject) throws JSONException {
        mUserId = jsonObject.getInt("user_id");
        mInstallLocation = jsonObject.getInt("install_location");
        mInstallerName = JSONUtils.optString(jsonObject, "installer_name", null);
        mOriginatingPackage = JSONUtils.optString(jsonObject, "originating_package");
        String originatingUri = JSONUtils.optString(jsonObject, "originating_uri", null);
        mOriginatingUri = originatingUri != null ? Uri.parse(originatingUri) : null;
        mPackageSource = jsonObject.getInt("package_source");
        mInstallScenario = jsonObject.getInt("install_scenario");
        mRequestUpdateOwnership = jsonObject.getBoolean("request_update_ownership");
        mSignApkFiles = jsonObject.getBoolean("sign_apk_files");
        mForceDexOpt = jsonObject.getBoolean("force_dex_opt");
        mBlockTrackers = jsonObject.getBoolean("block_trackers");
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user_id", mUserId);
        jsonObject.put("install_location", mInstallLocation);
        jsonObject.put("installer_name", mInstallerName);
        jsonObject.put("originating_package", mOriginatingPackage);
        jsonObject.put("originating_uri", mOriginatingUri != null ? mOriginatingUri.toString() : null);
        jsonObject.put("package_source", mPackageSource);
        jsonObject.put("install_scenario", mInstallScenario);
        jsonObject.put("request_update_ownership", mRequestUpdateOwnership);
        jsonObject.put("sign_apk_files", mSignApkFiles);
        jsonObject.put("force_dex_opt", mForceDexOpt);
        jsonObject.put("block_trackers", mBlockTrackers);
        return jsonObject;
    }

    public static final JsonDeserializer.Creator<InstallerOptions> DESERIALIZER = InstallerOptions::new;

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InstallerOptions> CREATOR = new Creator<InstallerOptions>() {
        @Override
        public InstallerOptions createFromParcel(@NonNull Parcel in) {
            return new InstallerOptions(in);
        }

        @Override
        @NonNull
        public InstallerOptions[] newArray(int size) {
            return new InstallerOptions[size];
        }
    };

    @UserIdInt
    public int getUserId() {
        return mUserId;
    }

    public void setUserId(@UserIdInt int userId) {
        mUserId = userId;
    }

    public int getInstallLocation() {
        return mInstallLocation;
    }

    public void setInstallLocation(int installLocation) {
        mInstallLocation = installLocation;
    }

    @NonNull
    public String getInstallerName() {
        return !TextUtils.isEmpty(mInstallerName) ? mInstallerName : BuildConfig.APPLICATION_ID;
    }

    public void setInstallerName(@Nullable String installerName) {
        mInstallerName = installerName;
    }

    @Nullable
    public String getOriginatingPackage() {
        return mOriginatingPackage;
    }

    public void setOriginatingPackage(@Nullable String originatingPackage) {
        mOriginatingPackage = originatingPackage;
    }

    @Nullable
    public Uri getOriginatingUri() {
        return mOriginatingUri;
    }

    public void setOriginatingUri(@Nullable Uri originatingUri) {
        mOriginatingUri = originatingUri;
    }

    public int getPackageSource() {
        return mPackageSource;
    }

    public void setPackageSource(int packageSource) {
        mPackageSource = packageSource;
    }

    public int getInstallScenario() {
        return mInstallScenario;
    }

    public void setInstallScenario(int installScenario) {
        mInstallScenario = installScenario;
    }

    public boolean requestUpdateOwnership() {
        return mRequestUpdateOwnership;
    }

    public void requestUpdateOwnership(boolean update) {
        mRequestUpdateOwnership = update;
    }

    public boolean isSignApkFiles() {
        return mSignApkFiles;
    }

    public void setSignApkFiles(boolean signApkFiles) {
        mSignApkFiles = signApkFiles;
    }

    public boolean isForceDexOpt() {
        return mForceDexOpt;
    }

    public void setForceDexOpt(boolean forceDexOpt) {
        mForceDexOpt = forceDexOpt;
    }

    public boolean isBlockTrackers() {
        return mBlockTrackers;
    }

    public void setBlockTrackers(boolean blockTrackers) {
        mBlockTrackers = blockTrackers;
    }
}
