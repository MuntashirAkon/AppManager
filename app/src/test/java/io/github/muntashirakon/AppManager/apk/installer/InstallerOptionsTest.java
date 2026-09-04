// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.settings.InstallerPreferences.PKG_SOURCES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Parcel;

import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.AppPref;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InstallerOptionsTest {
    @Test
    public void copyIsIndependentFromSource() throws Exception {
        InstallerOptions source = InstallerOptions.getDefault();
        source.setUserId(10);
        source.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY);
        source.setInstallerName("installer.one");
        source.setOriginatingPackage("origin.one");
        source.setOriginatingUri(Uri.parse("content://origin/one"));
        source.setSetOriginatingPackage(true);
        source.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE);
        source.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST);
        source.requestUpdateOwnership(true);
        source.setDisableApkVerification(true);
        source.setSignApkFiles(true);
        source.setForceDexOpt(true);
        source.setBlockTrackers(true);
        String originalJson = source.serializeToJson().toString();

        InstallerOptions draft = InstallerOptions.copyOf(source);
        draft.setUserId(0);
        draft.setInstallLocation(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL);
        draft.setInstallerName("installer.two");
        draft.setOriginatingPackage("origin.two");
        draft.setOriginatingUri(Uri.parse("content://origin/two"));
        draft.setSetOriginatingPackage(false);
        draft.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE);
        draft.setInstallScenario(PackageManager.INSTALL_SCENARIO_BULK);
        draft.requestUpdateOwnership(false);
        draft.setDisableApkVerification(false);
        draft.setSignApkFiles(false);
        draft.setForceDexOpt(false);
        draft.setBlockTrackers(false);

        assertEquals(originalJson, source.serializeToJson().toString());
        assertNotEquals(originalJson, draft.serializeToJson().toString());
    }

    @Test
    public void invalidApiValuesAreNormalized() {
        InstallerOptions options = InstallerOptions.getDefault();

        options.setInstallLocation(99);
        options.setPackageSource(99);
        options.setInstallScenario(99);

        assertEquals(PackageInfo.INSTALL_LOCATION_AUTO, options.getInstallLocation());
        assertEquals(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED, options.getPackageSource());
        assertEquals(PackageManager.INSTALL_SCENARIO_DEFAULT, options.getInstallScenario());
    }

    @Test
    public void invalidParcelValuesAreNormalized() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(0);
            parcel.writeInt(99);
            parcel.writeString(null);
            parcel.writeString(null);
            parcel.writeParcelable(null, 0);
            ParcelCompat.writeBoolean(parcel, false);
            parcel.writeInt(99);
            parcel.writeInt(0);
            ParcelCompat.writeBoolean(parcel, false);
            ParcelCompat.writeBoolean(parcel, false);
            ParcelCompat.writeBoolean(parcel, false);
            ParcelCompat.writeBoolean(parcel, false);
            ParcelCompat.writeBoolean(parcel, false);
            parcel.setDataPosition(0);

            InstallerOptions options = InstallerOptions.CREATOR.createFromParcel(parcel);

            assertEquals(PackageInfo.INSTALL_LOCATION_AUTO, options.getInstallLocation());
            assertEquals(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED, options.getPackageSource());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void invalidJsonValuesAreNormalized() throws Exception {
        JSONObject json = validOptionsJson();
        json.put("install_location", 99);
        json.put("package_source", 99);

        InstallerOptions options = InstallerOptions.DESERIALIZER.deserialize(json);

        assertEquals(PackageInfo.INSTALL_LOCATION_AUTO, options.getInstallLocation());
        assertEquals(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED, options.getPackageSource());
    }

    @Test
    public void invalidPersistedValuesAreNormalized() {
        int oldInstallLocation = AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        int oldPackageSource = AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT);
        try {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, 99);
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT, 99);

            assertEquals(PackageInfo.INSTALL_LOCATION_AUTO, Prefs.Installer.getInstallLocation());
            assertEquals(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED,
                    Prefs.Installer.getPackageSource());
        } finally {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, oldInstallLocation);
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT, oldPackageSource);
        }
    }

    @Test
    public void packageSourceSpinnerChangesOnlyPackageSource() {
        for (int position = 0; position < PKG_SOURCES.length; ++position) {
            InstallerOptions options = InstallerOptions.getDefault();
            options.setInstallLocation(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL);

            InstallerOptionsFragment.setPackageSourceFromPosition(options, position);

            assertEquals(PKG_SOURCES[position].intValue(), options.getPackageSource());
            assertEquals(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL,
                    options.getInstallLocation());
        }
    }

    @Test
    public void storeOverrideDoesNotLeakToDefaultsOrFollowingItem() throws Exception {
        InstallerOptions defaults = InstallerOptions.getDefault();
        defaults.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE);
        ApkQueueItem storeItem = queueItem("com.looker.droidify");
        ApkQueueItem localItem = queueItem(null);

        storeItem.setInstallerOptions(defaults);
        localItem.setInstallerOptions(defaults);

        assertEquals(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE, defaults.getPackageSource());
        assertEquals(PackageInstaller.PACKAGE_SOURCE_STORE,
                storeItem.getInstallerOptions().getPackageSource());
        assertEquals(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE,
                localItem.getInstallerOptions().getPackageSource());

        InstallerOptions returnedOptions = storeItem.getInstallerOptions();
        returnedOptions.setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER);
        assertEquals(PackageInstaller.PACKAGE_SOURCE_STORE,
                storeItem.getInstallerOptions().getPackageSource());
    }

    @Test
    public void unavailableCapabilitiesAreRemovedFromEffectiveSnapshot() throws Exception {
        InstallerOptions requested = InstallerOptions.getDefault();
        requested.setUserId(10);
        requested.setInstallerName("custom.installer");
        requested.setDisableApkVerification(true);
        requested.setSignApkFiles(true);
        requested.setBlockTrackers(true);
        String requestedJson = requested.serializeToJson().toString();

        InstallerOptions effective = InstallerOptions.resolveEffectiveOptions(requested, 0,
                false, false, false, false, false);

        assertEquals(0, effective.getUserId());
        assertEquals(BuildConfig.APPLICATION_ID, effective.getInstallerName());
        assertEquals(false, effective.isDisableApkVerification());
        assertEquals(false, effective.isSignApkFiles());
        assertEquals(false, effective.isBlockTrackers());
        assertEquals(requestedJson, requested.serializeToJson().toString());
    }

    @Test
    public void trackerCapabilityIsReevaluatedForSelectedUser() {
        InstallerOptions requested = InstallerOptions.getDefault();
        requested.setUserId(10);
        requested.setBlockTrackers(true);

        InstallerOptions allowed = InstallerOptions.resolveEffectiveOptions(requested, 0,
                true, true, true, true, true);
        InstallerOptions unavailable = InstallerOptions.resolveEffectiveOptions(requested, 0,
                true, true, true, true, false);

        assertEquals(10, allowed.getUserId());
        assertEquals(true, allowed.isBlockTrackers());
        assertEquals(10, unavailable.getUserId());
        assertEquals(false, unavailable.isBlockTrackers());
    }

    private static ApkQueueItem queueItem(String originatingPackage) throws Exception {
        JSONObject json = new JSONObject();
        json.put("op_id", "operation-" + String.valueOf(originatingPackage));
        json.put("package_name", "test.package");
        json.put("app_label", "Test");
        json.put("install_existing", false);
        json.put("originating_package", originatingPackage);
        json.put("originating_uri", JSONObject.NULL);
        json.put("apk_source", JSONObject.NULL);
        json.put("installer_options", JSONObject.NULL);
        json.put("selected_splits", new JSONArray());
        return ApkQueueItem.DESERIALIZER.deserialize(json);
    }

    private static JSONObject validOptionsJson() throws Exception {
        return InstallerOptions.getDefault().serializeToJson();
    }
}
