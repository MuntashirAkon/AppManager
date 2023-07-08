// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SELinux;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.os.LocaleListCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;

import java.security.Provider;
import java.security.Security;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.proc.ProcFs;
import io.github.muntashirakon.util.LocalizedString;

public class DeviceInfo2 implements LocalizedString {
    public final String osVersion = Build.VERSION.RELEASE;
    public final String bootloader = Build.BOOTLOADER;
    public final String vm = getVmVersion();
    public final String kernel = getKernel();
    public final String brandName = Build.BRAND;
    public final String model = Build.MODEL;
    public final String board = Build.BOARD;
    public final String manufacturer = Build.MANUFACTURER;
    // SDK
    public final int maxSdk = Build.VERSION.SDK_INT;
    public final int minSdk = SystemProperties.getInt("ro.build.version.min_supported_target_sdk", 0);
    // Security
    public boolean hasRoot;
    public int selinux;
    public String encryptionStatus;
    public final String patchLevel;
    public final Provider[] securityProviders = Security.getProviders();
    // CPU Info
    @Nullable
    public String cpuHardware;
    public final String[] supportedAbis = Build.SUPPORTED_ABIS;
    public int availableProcessors;
    // GPU Info
    public String glEsVersion;
    // Memory
    public final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    // Battery
    public double batteryCapacityMAh;
    // Display
    public final int displayDensityDpi = StaticDataset.DEVICE_DENSITY;
    public final String displayDensity = getDensity();
    public float scalingFactor;
    public int actualWidthPx;
    public int actualHeightPx;
    public int windowWidthPx;
    public int windowHeightPx;
    public float refreshRate;
    // Locales
    public final LocaleListCompat systemLocales = LocaleListCompat.getDefault();
    // Users
    @Nullable
    public List<UserInfo> users;
    // Packages
    public ArrayMap<Integer, Pair<Integer, Integer>> userPackages = new ArrayMap<>(1);
    // Features
    public FeatureInfo[] features;

    private final FragmentActivity mActivity;
    private final ActivityManager mActivityManager;
    private final PackageManager mPm;
    private final Display mDisplay;

    public DeviceInfo2(@NonNull FragmentActivity activity) {
        this.mActivity = activity;
        mActivityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        mPm = activity.getPackageManager();
        mDisplay = getDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            patchLevel = getSecurityPatch();
        } else patchLevel = null;
    }

    @WorkerThread
    public void loadInfo() {
        hasRoot = RunnerUtils.isRootAvailable();
        selinux = getSelinuxStatus();
        encryptionStatus = getEncryptionStatus();
        cpuHardware = getCpuHardware();
        availableProcessors = Runtime.getRuntime().availableProcessors();
        glEsVersion = Utils.getGlEsVersion(mActivityManager.getDeviceConfigurationInfo().reqGlEsVersion);
        // TODO(19/12/20): Get vendor name
        mActivityManager.getMemoryInfo(memoryInfo);
        batteryCapacityMAh = getBatteryCapacity();
        // TODO(19/12/20): Get more battery info
        DisplayMetrics displayMetrics = new DisplayMetrics();
        // Actual size
        mDisplay.getRealMetrics(displayMetrics);
        scalingFactor = displayMetrics.density;
        actualWidthPx = displayMetrics.widthPixels;
        actualHeightPx = displayMetrics.heightPixels;
        // Window size
        mDisplay.getMetrics(displayMetrics);
        windowWidthPx = displayMetrics.widthPixels;
        windowHeightPx = displayMetrics.heightPixels;
        refreshRate = mDisplay.getRefreshRate();
        users = Users.getAllUsers();
        for (UserInfo info : users) {
            userPackages.put(info.id, getPackageStats(info.id));
        }
        features = mPm.getSystemAvailableFeatures();
    }

    @Override
    @NonNull
    public CharSequence toLocalizedString(@NonNull Context ctx) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        // Android platform info
        builder.append(getStyledKeyValue(ctx, R.string.os_version, osVersion)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.bootloader, bootloader)).append(", ")
                .append(getStyledKeyValue(ctx, "VM", vm)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.kernel, kernel)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.brand_name, brandName)).append(", ")
                .append(getStyledKeyValue(ctx, R.string.model, model)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.board_name, board)).append(", ")
                .append(getStyledKeyValue(ctx, R.string.manufacturer, manufacturer)).append("\n");
        // SDK
        builder.append("\n").append(getTitleText(ctx, R.string.sdk)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.sdk_max, String.format(Locale.getDefault(), "%d", maxSdk)));
        if (minSdk != 0) {
            builder.append(", ").append(getStyledKeyValue(ctx, R.string.sdk_min, String.format(Locale.getDefault(), "%d", minSdk)));
        }
        builder.append("\n");
        // Security
        builder.append("\n").append(getTitleText(ctx, R.string.security)).append("\n");
        builder.append(getStyledKeyValue(ctx, R.string.root, String.valueOf(hasRoot))).append("\n");
        if (selinux != 2) {
            builder.append(getStyledKeyValue(ctx, R.string.selinux, getString(selinux == 1 ?
                    R.string.enforcing : R.string.permissive))).append("\n");
        }
        builder.append(getStyledKeyValue(ctx, R.string.encryption, encryptionStatus)).append("\n");
        if (patchLevel != null) {
            builder.append(getStyledKeyValue(ctx, R.string.patch_level, patchLevel)).append("\n");
        }
        List<CharSequence> securityProviders = new ArrayList<>();
        for (Provider provider : this.securityProviders) {
            securityProviders.add(provider.getName() + " (v" + provider.getVersion() + ")");
        }
        builder.append(getStyledKeyValue(ctx, R.string.security_providers,
                TextUtilsCompat.joinSpannable(", ", securityProviders))).append("\n");
        // CPU info
        builder.append("\n").append(getTitleText(ctx, R.string.cpu)).append("\n");
        if (cpuHardware != null) {
            builder.append(getStyledKeyValue(ctx, R.string.hardware, cpuHardware)).append("\n");
        }
        builder.append(getStyledKeyValue(ctx, R.string.support_architectures,
                        TextUtils.join(", ", supportedAbis))).append("\n")
                .append(getStyledKeyValue(ctx, R.string.no_of_cores, String.format(Locale.getDefault(), "%d",
                        availableProcessors))).append("\n");
        // GPU info
        builder.append("\n").append(getTitleText(ctx, R.string.graphics)).append("\n");
        builder.append(getStyledKeyValue(ctx, R.string.gles_version, glEsVersion)).append("\n");
        // RAM info
        builder.append("\n").append(getTitleText(ctx, R.string.memory)).append("\n")
                .append(Formatter.formatFileSize(ctx, memoryInfo.totalMem)).append("\n");
        // Battery info
        builder.append("\n").append(getTitleText(ctx, R.string.battery)).append("\n");
        builder.append(getStyledKeyValue(ctx, R.string.battery_capacity, String.valueOf(batteryCapacityMAh)))
                .append("mAh").append("\n");
        // Screen resolution
        builder.append("\n").append(getTitleText(ctx, R.string.screen)).append("\n")
                .append(getStyledKeyValue(ctx, R.string.density, String.format(Locale.getDefault(), "%s (%d DPI)",
                        displayDensity, displayDensityDpi))).append("\n");
        // Actual size
        builder.append(getStyledKeyValue(ctx, R.string.scaling_factor, String.valueOf(scalingFactor))).append("\n")
                .append(getStyledKeyValue(ctx, R.string.size, actualWidthPx + "px × " + actualHeightPx + "px\n"));
        // Window size
        builder.append(getStyledKeyValue(ctx, R.string.window_size, windowWidthPx + "px × " + windowHeightPx
                + "px\n"));
        // Refresh rate
        builder.append(getStyledKeyValue(ctx, R.string.refresh_rate, String.format(Locale.getDefault(), "%.1f Hz",
                refreshRate))).append("\n");
        // List system locales
        List<String> localeStrings = new ArrayList<>(systemLocales.size());
        for (int i = 0; i < systemLocales.size(); ++i) {
            localeStrings.add(systemLocales.get(i).getDisplayName());
        }
        builder.append("\n").append(getTitleText(ctx, R.string.languages))
                .append("\n").append(TextUtilsCompat.joinSpannable(", ", localeStrings))
                .append("\n");
        if (users != null) {
            // Users
            builder.append("\n").append(getTitleText(ctx, R.string.users))
                    .append("\n");
            List<String> userNames = new ArrayList<>();
            for (UserInfo user : users) {
                userNames.add(user.name != null ? user.name : String.valueOf(user.id));
            }
            builder.append(String.format(Locale.getDefault(), "%d", users.size())).append(" (")
                    .append(TextUtilsCompat.joinSpannable(", ", userNames))
                    .append(")\n");
            // App stats per user
            builder.append("\n").append(getTitleText(ctx, R.string.apps)).append("\n");
            for (UserInfo user : users) {
                Pair<Integer, Integer> packageSizes = userPackages.get(user.id);
                if (packageSizes == null) continue;
                if (packageSizes.first + packageSizes.second == 0) continue;
                builder.append(getStyledKeyValue(ctx, R.string.user, user.toLocalizedString(ctx))).append("\n   ")
                        .append(getStyledKeyValue(ctx, R.string.total_size, String.format(Locale.getDefault(), "%d",
                                packageSizes.first + packageSizes.second))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.user, String.format(Locale.getDefault(), "%d",
                                packageSizes.first))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.system, String.format(Locale.getDefault(), "%d",
                                packageSizes.second)))
                        .append("\n");
            }
        } else {
            builder.append("\n").append(getTitleText(ctx, R.string.apps)).append("\n");
            Pair<Integer, Integer> packageSizes = userPackages.get(UserHandleHidden.myUserId());
            if (packageSizes != null) {
                builder.append(getStyledKeyValue(ctx, R.string.total_size, String.format(Locale.getDefault(), "%d",
                                packageSizes.first + packageSizes.second))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.user, String.format(Locale.getDefault(), "%d",
                                packageSizes.first))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.system, String.format(Locale.getDefault(), "%d",
                                packageSizes.second)))
                        .append("\n");
            }
        }
        // List available hardware/features
        builder.append("\n").append(getTitleText(ctx, R.string.features)).append("\n");
        List<CharSequence> featureStrings = new ArrayList<>(features.length);
        for (FeatureInfo info : features) {
            if (info.name != null) {
                // It's a feature
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.version != 0) {
                    featureStrings.add(info.name + " (v" + info.version + ")");
                } else featureStrings.add(info.name);
            }
        }
        Collections.sort(featureStrings, (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.toString(), o2.toString()));
        builder.append(TextUtilsCompat.joinSpannable("\n", featureStrings)).append("\n");
        return builder;
    }

    private Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return mActivity.getDisplay();
        } else {
            //noinspection deprecation
            return mActivity.getWindowManager().getDefaultDisplay();
        }
    }

    private String getDensity() {
        int dpi = StaticDataset.DEVICE_DENSITY;
        int smallestDiff = Integer.MAX_VALUE;
        String density = StaticDataset.XXXHDPI;
        // Find the smallest
        for (int i = 0; i < StaticDataset.DENSITY_NAME_TO_DENSITY.size(); ++i) {
            int diff = Math.abs(dpi - StaticDataset.DENSITY_NAME_TO_DENSITY.valueAt(i));
            if (diff < smallestDiff) {
                smallestDiff = diff;
                density = StaticDataset.DENSITY_NAME_TO_DENSITY.keyAt(i);
            }
        }
        return density;
    }

    @NonNull
    private String getVmVersion() {
        String vm = "Dalvik";
        String vmVersion = System.getProperty("java.vm.version");
        if (vmVersion != null && vmVersion.startsWith("2")) {
            vm = "ART";
        }
        return vm;
    }

    @NonNull
    private String getKernel() {
        String kernel = System.getProperty("os.version");
        if (kernel == null) return "";
        else return kernel;
    }

    @Nullable
    @RequiresApi(Build.VERSION_CODES.M)
    public static String getSecurityPatch() {
        String patch = Build.VERSION.SECURITY_PATCH;
        if (!"".equals(patch)) {
            try {
                SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                Date patchDate = template.parse(patch);
                String format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy");
                patch = DateFormat.format(format, patchDate).toString();
            } catch (ParseException e) {
                // broken parse; fall through and use the raw string
            }
            return patch;
        } else {
            return null;
        }
    }

    @WorkerThread
    private int getSelinuxStatus() {
        if (SELinux.isSELinuxEnabled()) {
            // if (SELinux.isSELinuxEnforced()) {
            //    return 1;
            // }
            Runner.Result result = Runner.runCommand("getenforce");
            if (result.isSuccessful() && result.getOutput().trim().equals("Permissive")) {
                return 0;
            }
            // SELinux enabled, but cannot access result means it is "Enforcing"
            return 1;
        }
        // Disabled
        return 2;
    }

    @NonNull
    private String getEncryptionStatus() {
        String state = SystemProperties.get("ro.crypto.state", "");
        if ("encrypted".equals(state)) {
            String encryptedMsg = getString(R.string.encrypted);
            String type = SystemProperties.get("ro.crypto.type", "");
            if ("file".equals(type)) return encryptedMsg + " (FBE)";
            else if ("block".equals(type)) return encryptedMsg + " (FDE)";
            else return encryptedMsg;
        } else if ("unencrypted".equals(state)) {
            return getString(R.string.unencrypted);
        } else return getString(R.string.state_unknown);
    }

    @Nullable
    private String getCpuHardware() {
        return ProcFs.getInstance().getCpuInfoHardware();
    }

    @SuppressLint("PrivateApi")
    private double getBatteryCapacity() {
        double capacity = -1.0;
        try {
            Object powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class).newInstance(mActivity.getApplication());
            //noinspection ConstantConditions
            capacity = (double) Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getAveragePower", String.class)
                    .invoke(powerProfile, "battery.capacity");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return capacity;
    }

    // User + System apps
    @NonNull
    private Pair<Integer, Integer> getPackageStats(int userHandle) {
        int systemApps = 0;
        int userApps = 0;
        try {
            List<ApplicationInfo> applicationInfoList = PackageManagerCompat.getInstalledApplications(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle);
            for (ApplicationInfo info : applicationInfoList) {
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                    ++systemApps;
                } else ++userApps;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return new Pair<>(userApps, systemApps);
    }

    private String getString(@StringRes int strRes) {
        return mActivity.getString(strRes);
    }
}
