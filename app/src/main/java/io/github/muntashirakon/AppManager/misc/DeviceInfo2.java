// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Build;
import android.text.SpannableStringBuilder;
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

import com.android.internal.util.TextUtils;

import java.io.BufferedReader;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.ProxyFileReader;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public class DeviceInfo2 {
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
    public int SELinux;
    public String encryptionStatus;
    @RequiresApi(Build.VERSION_CODES.M)
    public final String patchLevel = Build.VERSION.SECURITY_PATCH;
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

    private final FragmentActivity activity;
    private final ActivityManager activityManager;
    private final PackageManager pm;
    private final Display display;

    public DeviceInfo2(@NonNull FragmentActivity activity) {
        this.activity = activity;
        activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        pm = activity.getPackageManager();
        display = getDisplay();
    }

    @WorkerThread
    public void loadInfo() {
        hasRoot = RunnerUtils.isRootAvailable();
        SELinux = getSelinuxStatus();
        encryptionStatus = getEncryptionStatus();
        cpuHardware = getCpuHardware();
        availableProcessors = Runtime.getRuntime().availableProcessors();
        glEsVersion = Utils.getGlEsVersion(activityManager.getDeviceConfigurationInfo().reqGlEsVersion);
        // TODO(19/12/20): Get vendor name
        activityManager.getMemoryInfo(memoryInfo);
        batteryCapacityMAh = getBatteryCapacity();
        // TODO(19/12/20): Get more battery info
        DisplayMetrics displayMetrics = new DisplayMetrics();
        // Actual size
        display.getRealMetrics(displayMetrics);
        scalingFactor = displayMetrics.density;
        actualWidthPx = displayMetrics.widthPixels;
        actualHeightPx = displayMetrics.heightPixels;
        // Window size
        display.getMetrics(displayMetrics);
        windowWidthPx = displayMetrics.widthPixels;
        windowHeightPx = displayMetrics.heightPixels;
        refreshRate = display.getRefreshRate();
        users = Users.getUsers();
        if (users != null) {
            for (UserInfo info : users) {
                userPackages.put(info.id, getPackageStats(info.id));
            }
        } else {
            int myId = Users.myUserId();
            userPackages.put(myId, getPackageStats(myId));
        }
        features = pm.getSystemAvailableFeatures();
    }

    public CharSequence toLocalisedString(@NonNull Context ctx) {
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
                .append(getStyledKeyValue(ctx, R.string.sdk_max, String.valueOf(maxSdk)));
        if (minSdk != 0) {
            builder.append(", ").append(getStyledKeyValue(ctx, R.string.sdk_min, String.valueOf(minSdk)));
        }
        builder.append("\n");
        // Security
        builder.append("\n").append(getTitleText(ctx, R.string.security)).append("\n");
        builder.append(getStyledKeyValue(ctx, R.string.root, String.valueOf(hasRoot))).append("\n");
        if (SELinux != 2) {
            builder.append(getStyledKeyValue(ctx, R.string.selinux, getString(SELinux == 1 ?
                    R.string.enforcing : R.string.permissive))).append("\n");
        }
        builder.append(getStyledKeyValue(ctx, R.string.encryption, encryptionStatus)).append("\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.append(getStyledKeyValue(ctx, R.string.patch_level, patchLevel)).append("\n");
        }
        List<CharSequence> securityProviders = new ArrayList<>();
        for (Provider provider : this.securityProviders) {
            securityProviders.add(provider.getName() + " (v" + provider.getVersion() + ")");
        }
        builder.append(getStyledKeyValue(ctx, R.string.security_providers,
                TextUtils.joinSpannable(", ", securityProviders))).append("\n");
        // CPU info
        builder.append("\n").append(getTitleText(ctx, R.string.cpu)).append("\n");
        if (cpuHardware != null) {
            builder.append(getStyledKeyValue(ctx, R.string.hardware, cpuHardware)).append("\n");
        }
        builder.append(getStyledKeyValue(ctx, R.string.support_architectures,
                TextUtils.join(", ", supportedAbis))).append("\n")
                .append(getStyledKeyValue(ctx, R.string.no_of_cores, String.valueOf(availableProcessors))).append("\n");
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
                .append(getStyledKeyValue(ctx, R.string.density, displayDensity + " (" + displayDensityDpi
                        + " DPI)")).append("\n");
        // Actual size
        builder.append(getStyledKeyValue(ctx, R.string.scaling_factor, String.valueOf(scalingFactor))).append("\n")
                .append(getStyledKeyValue(ctx, R.string.size, actualWidthPx + "px × " + actualHeightPx + "px\n"));
        // Window size
        builder.append(getStyledKeyValue(ctx, R.string.window_size, windowWidthPx + "px × " + windowHeightPx
                + "px\n"));
        // Refresh rate
        builder.append(getStyledKeyValue(ctx, R.string.refresh_rate, String.format(Locale.ROOT, "%.1f Hz",
                refreshRate))).append("\n");
        // List system locales
        List<String> localeStrings = new ArrayList<>(systemLocales.size());
        for (int i = 0; i < systemLocales.size(); ++i) {
            localeStrings.add(systemLocales.get(i).getDisplayName());
        }
        builder.append("\n").append(getTitleText(ctx, R.string.languages))
                .append("\n").append(TextUtils.joinSpannable(", ", localeStrings))
                .append("\n");
        if (users != null) {
            // Users
            builder.append("\n").append(getTitleText(ctx, R.string.users))
                    .append("\n");
            List<String> userNames = new ArrayList<>();
            for (UserInfo user : users) {
                userNames.add(user.name);
            }
            builder.append(String.valueOf(users.size())).append(" (")
                    .append(TextUtils.joinSpannable(", ", userNames))
                    .append(")\n");
            // App stats per user
            builder.append("\n").append(getTitleText(ctx, R.string.apps)).append("\n");
            for (UserInfo user : users) {
                Pair<Integer, Integer> packageSizes = userPackages.get(user.id);
                if (packageSizes == null) continue;
                if (packageSizes.first + packageSizes.second == 0) continue;
                builder.append(getStyledKeyValue(ctx, R.string.user, user.name + " (" + user.id + ")")).append("\n   ")
                        .append(getStyledKeyValue(ctx, R.string.total_size,
                                String.valueOf(packageSizes.first + packageSizes.second))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.user, String.valueOf(packageSizes.first))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.system, String.valueOf(packageSizes.second)))
                        .append("\n");
            }
        } else {
            builder.append("\n").append(getTitleText(ctx, R.string.apps)).append("\n");
            Pair<Integer, Integer> packageSizes = userPackages.get(Users.myUserId());
            if (packageSizes != null) {
                builder.append(getStyledKeyValue(ctx, R.string.total_size,
                        String.valueOf(packageSizes.first + packageSizes.second))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.user, String.valueOf(packageSizes.first))).append(", ")
                        .append(getStyledKeyValue(ctx, R.string.system, String.valueOf(packageSizes.second)))
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
        builder.append(TextUtils.joinSpannable("\n", featureStrings)).append("\n");
        return builder;
    }

    private Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return activity.getDisplay();
        } else {
            //noinspection deprecation
            return activity.getWindowManager().getDefaultDisplay();
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

    @WorkerThread
    private int getSelinuxStatus() {
        Runner.Result result = Runner.runCommand("getenforce");
        if (result.isSuccessful()) {
            if (result.getOutput().trim().equals("Enforcing")) return 1;
            return 0;
        }
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
        try (BufferedReader reader = new BufferedReader(new ProxyFileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Hardware")) {
                    int colonLoc = line.indexOf(':');
                    if (colonLoc == -1) continue;
                    colonLoc += 2;
                    return line.substring(colonLoc).trim();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressLint("PrivateApi")
    private double getBatteryCapacity() {
        double capacity = -1.0;
        try {
            Object powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class).newInstance(activity.getApplication());
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
        IPackageManager pm = AppManager.getIPackageManager();
        int systemApps = 0;
        int userApps = 0;
        try {
            List<ApplicationInfo> applicationInfoList = pm.getInstalledApplications(0, userHandle).getList();
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
        return activity.getString(strRes);
    }
}
