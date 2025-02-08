// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SELinux;
import android.os.UserHandleHidden;
import android.text.Spannable;
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

import com.android.internal.os.PowerProfile;

import java.security.Provider;
import java.security.Security;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.misc.gles.EglCore;
import io.github.muntashirakon.AppManager.misc.gles.OffscreenSurface;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
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
    public String dmVerity; // enforcing, disabled, eio, ...
    @Nullable
    public String verifiedBootState; // green (verified), yellow (self-signed), orange (unverified), red (failed) ?: orange (unverified)
    public String verifiedBootStateString;
    public String avbVersion;
    public String bootloaderState;
    public boolean debuggable;
    public final String patchLevel;
    public final Provider[] securityProviders = Security.getProviders();
    public final String hardwareBackedFeatures;
    public final String strongBoxBackedFeatures;
    // CPU Info
    @Nullable
    public String cpuHardware;
    public final String[] supportedAbis = Build.SUPPORTED_ABIS;
    public int availableProcessors;
    public String openGlEsVersion;
    @Nullable
    public String vulkanVersion;
    // Memory
    public final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    // Battery
    public boolean batteryPresent;
    public double batteryCapacityMAh;
    public double batteryCapacityMAhAlt;
    @Nullable
    public String batteryTechnology;
    public int batteryCycleCount;
    public String batteryHealth;
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
        features = mPm.getSystemAvailableFeatures();
        hardwareBackedFeatures = getHardwareBackedFeatures();
        strongBoxBackedFeatures = getStrongBoxBackedFeatures();
    }

    @WorkerThread
    public void loadInfo() {
        hasRoot = RunnerUtils.isRootAvailable();
        selinux = getSelinuxStatus();
        encryptionStatus = getEncryptionStatus();
        if (mPm.hasSystemFeature(PackageManager.FEATURE_VERIFIED_BOOT)) {
            verifiedBootState = SystemProperties.get("ro.boot.verifiedbootstate", "");
            verifiedBootStateString = getVerifiedBootStateString(verifiedBootState);
            dmVerity = SystemProperties.get("ro.boot.veritymode", "");
            avbVersion = SystemProperties.get("ro.boot.avb_version", "");
            bootloaderState = SystemProperties.get("ro.boot.vbmeta.device_state", "");
        } else verifiedBootState = null;
        debuggable = "1".equals(SystemProperties.get("ro.debuggable", "0"));
        cpuHardware = getCpuHardware();
        availableProcessors = Runtime.getRuntime().availableProcessors();
        openGlEsVersion = Utils.getGlEsVersion(mActivityManager.getDeviceConfigurationInfo().reqGlEsVersion);
        vulkanVersion = Utils.getVulkanVersion(mPm);
        mActivityManager.getMemoryInfo(memoryInfo);
        getBatteryStats(mActivity);
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
    }

    @Override
    @NonNull
    public CharSequence toLocalizedString(@NonNull Context ctx) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        // Android platform info
        builder.append(getStyledKeyValue(ctx, R.string.os_version, osVersion)).append(", ")
                .append(getStyledKeyValue(ctx, "Build", Build.DISPLAY)).append("\n")
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
        if (patchLevel != null) {
            builder.append(getStyledKeyValue(ctx, R.string.patch_level, patchLevel)).append("\n");
        }
        builder.append(getStyledKeyValue(ctx, R.string.root, String.valueOf(hasRoot))).append(", ")
                .append(getStyledKeyValue(ctx, R.string.debuggable, String.valueOf(debuggable)))
                .append("\n");
        if (selinux != 2) {
            builder.append(getStyledKeyValue(ctx, R.string.selinux, getString(selinux == 1 ?
                    R.string.enforcing : R.string.permissive))).append(", ");
        }
        builder.append(getStyledKeyValue(ctx, R.string.encryption, encryptionStatus)).append("\n");
        boolean verifiedBoot = false;
        if (!TextUtils.isEmpty(verifiedBootState)) {
            verifiedBoot = true;
            builder.append(getStyledKeyValue(ctx, R.string.verified_boot, verifiedBootState))
                    .append(" (").append(verifiedBootStateString).append(")");
        }
        if (!TextUtils.isEmpty(avbVersion)) {
            if (verifiedBoot) {
                builder.append(", ");
            }
            builder.append(getStyledKeyValue(ctx, R.string.android_verified_bootloader_version, avbVersion)).append("\n");
        } else if (verifiedBoot) {
            builder.append("\n");
        }
        boolean isDmVerity = false;
        if (!TextUtils.isEmpty(dmVerity)) {
            isDmVerity = true;
            builder.append(getStyledKeyValue(ctx, "dm-verity", dmVerity));
        }
        if (!TextUtils.isEmpty(bootloaderState)) {
            if (isDmVerity) {
                builder.append(", ");
            }
            builder.append(getStyledKeyValue(ctx, R.string.bootloader, bootloaderState)).append("\n");
        } else if (isDmVerity) {
            builder.append("\n");
        }
        List<CharSequence> securityProviders = new ArrayList<>();
        boolean hasAndroidKeyStore = false;
        for (Provider provider : this.securityProviders) {
            if ("AndroidKeyStore".equals(provider.getName())) {
                hasAndroidKeyStore = true;
            }
            securityProviders.add(provider.getName() + " (v" + provider.getVersion() + ")");
        }
        builder.append(getStyledKeyValue(ctx, R.string.security_providers,
                TextUtilsCompat.joinSpannable(", ", securityProviders))).append(".\n");
        // Android KeyStore
        if (hasAndroidKeyStore) {
            builder.append("\n").append(getTitleText(ctx, "Android KeyStore")).append("\n");
        }
        StringBuilder sb = new StringBuilder("Software");
        if (hardwareBackedFeatures != null) {
            sb.append(", Hardware");
        }
        if (strongBoxBackedFeatures != null) {
            sb.append(", StrongBox");
        }
        builder.append(getStyledKeyValue(ctx, R.string.features, sb)).append("\n");
        if (hardwareBackedFeatures != null) {
            builder.append("   ").append(getStyledKeyValue(ctx, "Hardware", hardwareBackedFeatures)).append("\n");
        }
        if (strongBoxBackedFeatures != null) {
            builder.append("   ").append(getStyledKeyValue(ctx, "StrongBox", strongBoxBackedFeatures)).append("\n");
        }
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
        builder.append("\n").append(getTitleText(ctx, R.string.graphics)).append("\n")
                .append(getGlInfo(ctx))
                .append(getStyledKeyValue(ctx, R.string.gles_version, openGlEsVersion)).append("\n");
        if (vulkanVersion != null) {
            builder.append(getStyledKeyValue(ctx, R.string.vulkan_version, vulkanVersion)).append("\n");
        }
        // RAM info
        builder.append("\n").append(getTitleText(ctx, R.string.memory)).append("\n")
                .append(Formatter.formatFileSize(ctx, memoryInfo.totalMem)).append("\n");
        // Battery info
        if (batteryPresent || batteryCapacityMAh > 0) {
            builder.append("\n").append(getTitleText(ctx, R.string.battery)).append("\n");
            if (batteryTechnology != null) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_technology, batteryTechnology))
                        .append("\n");
            }
            if (batteryCapacityMAh > 0) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_capacity, String.valueOf(batteryCapacityMAh)))
                        .append(" mAh");
                if (batteryCapacityMAhAlt > 0) {
                    builder.append(" (est. ")
                            .append(String.format(Locale.ROOT, "%.1f", batteryCapacityMAhAlt))
                            .append(" mAh)");
                }
                builder.append("\n");
            } else if (batteryCapacityMAhAlt > 0) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_capacity, String.format(Locale.ROOT, "%.1f", batteryCapacityMAhAlt)))
                        .append(" mAh (est.)").append("\n");
            }
            if (batteryHealth != null) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_health, batteryHealth));
                if (batteryCycleCount > 0) {
                    builder.append(" (").append(String.valueOf(batteryCycleCount)).append(" cycles)");
                }
                builder.append("\n");
            }
        }
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
            localeStrings.add(Objects.requireNonNull(systemLocales.get(i)).getDisplayName());
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
        Collections.sort(featureStrings, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        builder.append(TextUtilsCompat.joinSpannable("\n", featureStrings)).append("\n");
        return builder;
    }

    /**
     * Queries EGL/GL for information, then formats it all into one giant string.
     */
    @NonNull
    private Spannable getGlInfo(@NonNull Context ctx) {
        // We need a GL context to examine, which means we need an EGL surface. Create a 1x1 pbuffer.
        EglCore eglCore = new EglCore();
        OffscreenSurface surface = new OffscreenSurface(eglCore, 1, 1);
        surface.makeCurrent();

        String gpu = GLES20.glGetString(GLES20.GL_VENDOR) + " " + GLES20.glGetString(GLES20.GL_RENDERER);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(getStyledKeyValue(ctx, "GPU", gpu)).append("\n");
        // sb.append(formatExtensions(GLES20.glGetString(GLES20.GL_EXTENSIONS)));

        surface.release();
        eglCore.release();

        return sb;
    }

//    private String formatExtensions(@NonNull String ext) {
//        String[] values = ext.split(" ");
//        Arrays.sort(values);
//        StringBuilder sb = new StringBuilder();
//        for (String value : values) {
//            sb.append("  ");
//            sb.append(value);
//            sb.append("\n");
//        }
//        return sb.toString();
//    }

    private CountDownLatch mBatteryStatusLock;
    @Nullable
    private Bundle mBatteryStatusBundle;
    private final BroadcastReceiver mBatteryStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mBatteryStatusLock != null) {
                mBatteryStatusLock.countDown();
            }
            mBatteryStatusBundle = intent.getExtras();
        }
    };

    @WorkerThread
    private void getBatteryStats(Context ctx) {
        batteryCapacityMAh = new PowerProfile(ContextUtils.getContext()).getBatteryCapacity();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent data = ctx.registerReceiver(mBatteryStatusReceiver, filter);
        if (data != null) {
            mBatteryStatusBundle = data.getExtras();
        }
        if (mBatteryStatusBundle == null) {
            // fallback to old method
            mBatteryStatusLock = new CountDownLatch(1);
            try {
                if (!mBatteryStatusLock.await(10, TimeUnit.SECONDS)) {
                    throw new InterruptedException();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        ctx.unregisterReceiver(mBatteryStatusReceiver);
        if (mBatteryStatusBundle != null) {
            batteryPresent = mBatteryStatusBundle.getBoolean(BatteryManager.EXTRA_PRESENT);
            batteryTechnology = mBatteryStatusBundle.getString(BatteryManager.EXTRA_TECHNOLOGY);
            int batteryCapacityUAh = mBatteryStatusBundle.getInt("charge_counter", 0);
            if (batteryCapacityUAh != 0) {
                batteryCapacityMAhAlt = batteryCapacityUAh / 1000.;
                // This is the current capacity, calculate the actual capacity using the battery
                // percentage
                int level = mBatteryStatusBundle.getInt(BatteryManager.EXTRA_LEVEL, 0);
                int scale = mBatteryStatusBundle.getInt(BatteryManager.EXTRA_SCALE, 0);
                double batteryPercent = scale > 0 ? (level * 100. / scale) : 0;
                if (batteryPercent > 0) {
                    batteryCapacityMAhAlt = (batteryCapacityMAhAlt * 100. / batteryPercent);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryCycleCount = mBatteryStatusBundle.getInt(BatteryManager.EXTRA_CYCLE_COUNT, 0);
            }
            int health = mBatteryStatusBundle.getInt(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            switch (health) {
                case BatteryManager.BATTERY_HEALTH_GOOD:
                    batteryHealth = "Good";
                    break;
                case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                    batteryHealth = "Overheat";
                    break;
                case BatteryManager.BATTERY_HEALTH_DEAD:
                    batteryHealth = "Dead";
                    break;
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                    batteryHealth = "Over voltage";
                    break;
                case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                    batteryHealth = "failure";
                    break;
                case BatteryManager.BATTERY_HEALTH_COLD:
                    batteryHealth = "Cold";
                    break;
                default:
                    batteryHealth = "Unknown";
            }
        }
    }

    @NonNull
    private Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Objects.requireNonNull(mActivity.getDisplay());
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

    @NonNull
    private String getVerifiedBootStateString(@NonNull String color) {
        switch (color) {
            case "green":
                return "verified";
            case "yellow":
                return "self-signed";
            case "red":
                return "failed";
            case "orange":
            default:
                return "unverified";
        }
    }

    @Nullable
    private String getHardwareBackedFeatures() {
        // We use string instead of PackageManager.FEATURE_HARDWARE_KEYSTORE because it may present
        // in older devices.
        FeatureInfo f = getFeature("android.hardware.hardware_keystore");
        if (f == null) {
            return null;
        }
        int version;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            version = f.version;
        } else version = 0;
        if (version < 40) {
            return getString(R.string.state_unknown);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AES, HMAC, ECDSA, RSA");
        if (version >= 100) {
            sb.append(", ECDH");
        }
        if (version >= 200) {
            sb.append(", Curve 25519");
        }
        return sb.toString();
    }

    @Nullable
    private String getStrongBoxBackedFeatures() {
        // We use string instead of PackageManager.FEATURE_STRONGBOX_KEYSTORE because it may present
        // in older devices.
        FeatureInfo f = getFeature("android.hardware.strongbox_keystore");
        if (f == null) {
            return null;
        }
        int version;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            version = f.version;
        } else version = 0;
        if (version < 40) {
            return getString(R.string.state_unknown);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AES, HMAC, ECDSA, RSA");
        if (version >= 100) {
            sb.append(", ECDH");
        }
        return sb.toString();
    }

    @Nullable
    private FeatureInfo getFeature(@NonNull String feature) {
        for (FeatureInfo info : features) {
            if (feature.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    @Nullable
    private String getCpuHardware() {
        String model = CpuUtils.getCpuModel();
        if (model == null) {
            // ARM: fallback to /proc/cpuinfo
            model = ProcFs.getInstance().getCpuInfoHardware();
        }
        if (model == null) {
            // fallback to Android properties
            String part1 = SystemProperties.get("ro.soc.manufacturer", "");
            String part2 = SystemProperties.get("ro.soc.model", "");
            if (!part2.isEmpty()) {
                return part1 + (!part1.isEmpty() ? " " : "") + part2;
            }
            model = SystemProperties.get("ro.board.platform", "");
        }
        return !model.isEmpty() ? model : null;
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
