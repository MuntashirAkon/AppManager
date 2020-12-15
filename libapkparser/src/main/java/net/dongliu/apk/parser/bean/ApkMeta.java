package net.dongliu.apk.parser.bean;

import net.dongliu.apk.parser.AbstractApkFile;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * Apk meta info
 *
 * @author dongliu
 */
public class ApkMeta {

    private final String packageName;
    private final String label;
    private final String icon;
    private final String versionName;
    private final Long versionCode;
    private final Long revisionCode;
    private String sharedUserId;
    private String sharedUserLabel;
    private final String split;
    private final String configForSplit;
    private final boolean isFeatureSplit;
    private final boolean isSplitRequired;
    private final boolean isolatedSplits;
    private final String installLocation;
    private final String minSdkVersion;
    private final String targetSdkVersion;
    @Nullable
    private final String maxSdkVersion;
    @Nullable
    private final String compileSdkVersion;
    @Nullable
    private final String compileSdkVersionCodename;
    @Nullable
    private final String platformBuildVersionCode;
    @Nullable
    private final String platformBuildVersionName;
    private final GlEsVersion glEsVersion;
    private final boolean anyDensity;
    private final boolean smallScreens;
    private final boolean normalScreens;
    private final boolean largeScreens;
    private final boolean debuggable;

    private final List<String> usesPermissions;
    private final List<UseFeature> usesFeatures;
    private final List<Permission> permissions;

    private ApkMeta(Builder builder) {
        packageName = builder.packageName;
        label = builder.label;
        icon = builder.icon;
        versionName = builder.versionName;
        versionCode = builder.versionCode;
        revisionCode = builder.revisionCode;
        sharedUserId = builder.sharedUserId;
        sharedUserLabel = builder.sharedUserLabel;
        split = builder.split;
        configForSplit = builder.configForSplit;
        isFeatureSplit = builder.isFeatureSplit;
        isSplitRequired = builder.isSplitRequired;
        isolatedSplits = builder.isolatedSplits;
        installLocation = builder.installLocation;
        minSdkVersion = builder.minSdkVersion;
        targetSdkVersion = builder.targetSdkVersion;
        maxSdkVersion = builder.maxSdkVersion;
        compileSdkVersion = builder.compileSdkVersion;
        compileSdkVersionCodename = builder.compileSdkVersionCodename;
        platformBuildVersionCode = builder.platformBuildVersionCode;
        platformBuildVersionName = builder.platformBuildVersionName;
        glEsVersion = builder.glEsVersion;
        anyDensity = builder.anyDensity;
        smallScreens = builder.smallScreens;
        normalScreens = builder.normalScreens;
        largeScreens = builder.largeScreens;
        debuggable = builder.debuggable;
        usesPermissions = builder.usesPermissions;
        usesFeatures = builder.usesFeatures;
        permissions = builder.permissions;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public Long getVersionCode() {
        return versionCode;
    }

    public Long getRevisionCode() {
        return revisionCode;
    }

    public String getSharedUserId() {
        return sharedUserId;
    }

    public String getSharedUserLabel() {
        return sharedUserLabel;
    }

    public String getSplit() {
        return split;
    }

    public String getConfigForSplit() {
        return configForSplit;
    }

    public boolean isFeatureSplit() {
        return isFeatureSplit;
    }

    public boolean isSplitRequired() {
        return isSplitRequired;
    }

    public boolean isIsolatedSplits() {
        return isolatedSplits;
    }

    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public String getTargetSdkVersion() {
        return targetSdkVersion;
    }

    @Nullable
    public String getMaxSdkVersion() {
        return maxSdkVersion;
    }

    @Nullable
    public String getCompileSdkVersion() {
        return compileSdkVersion;
    }

    @Nullable
    public String getCompileSdkVersionCodename() {
        return compileSdkVersionCodename;
    }

    @Nullable
    public String getPlatformBuildVersionCode() {
        return platformBuildVersionCode;
    }

    @Nullable
    public String getPlatformBuildVersionName() {
        return platformBuildVersionName;
    }

    public List<String> getUsesPermissions() {
        return usesPermissions;
    }

    public void addUsesPermission(String permission) {
        this.usesPermissions.add(permission);
    }

    /**
     * the icon file path in apk
     *
     * @return null if not found
     * @deprecated use {@link AbstractApkFile#getAllIcons()} instead.
     */
    @Deprecated
    public String getIcon() {
        return icon;
    }

    /**
     * alias for getLabel
     */
    public String getName() {
        return label;
    }

    /**
     * get the apk's title(name)
     */
    public String getLabel() {
        return label;
    }

    public boolean isAnyDensity() {
        return anyDensity;
    }

    public boolean isSmallScreens() {
        return smallScreens;
    }

    public boolean isNormalScreens() {
        return normalScreens;
    }

    public boolean isLargeScreens() {
        return largeScreens;
    }

    public boolean isDebuggable() {
        return debuggable;
    }

    public GlEsVersion getGlEsVersion() {
        return glEsVersion;
    }

    public List<UseFeature> getUsesFeatures() {
        return usesFeatures;
    }

    public void addUseFeatures(UseFeature useFeature) {
        this.usesFeatures.add(useFeature);
    }

    public String getInstallLocation() {
        return installLocation;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public List<Permission> getPermissions() {
        return this.permissions;
    }


    @Override
    public String toString() {
        return "packageName: \t" + packageName + "\n"
                + "label: \t" + label + "\n"
                + "icon: \t" + icon + "\n"
                + "versionName: \t" + versionName + "\n"
                + "versionCode: \t" + versionCode + "\n"
                + "minSdkVersion: \t" + minSdkVersion + "\n"
                + "targetSdkVersion: \t" + targetSdkVersion + "\n"
                + "maxSdkVersion: \t" + maxSdkVersion;
    }

    public static final class Builder {
        private String packageName;
        private String label;
        private String icon;
        private String versionName;
        private Long versionCode;
        private Long revisionCode;
        private String sharedUserId;
        private String sharedUserLabel;
        private String split;
        private String configForSplit;
        private boolean isFeatureSplit;
        private boolean isSplitRequired;
        private boolean isolatedSplits;
        private String installLocation;
        private String minSdkVersion;
        private String targetSdkVersion;
        private String maxSdkVersion;
        private String compileSdkVersion;
        private String compileSdkVersionCodename;
        private String platformBuildVersionCode;
        private String platformBuildVersionName;
        private GlEsVersion glEsVersion;
        private boolean anyDensity;
        private boolean smallScreens;
        private boolean normalScreens;
        private boolean largeScreens;
        private boolean debuggable;
        private List<String> usesPermissions = new ArrayList<>();
        private List<UseFeature> usesFeatures = new ArrayList<>();
        private List<Permission> permissions = new ArrayList<>();

        private Builder() {
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setLabel(String label) {
            this.label = label;
            return this;
        }

        public Builder setIcon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder setVersionName(String versionName) {
            this.versionName = versionName;
            return this;
        }

        public Builder setVersionCode(Long versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        public Builder setRevisionCode(Long revisionCode) {
            this.revisionCode = revisionCode;
            return this;
        }

        public Builder setSharedUserId(String sharedUserId) {
            this.sharedUserId = sharedUserId;
            return this;
        }

        public Builder setSharedUserLabel(String sharedUserLabel) {
            this.sharedUserLabel = sharedUserLabel;
            return this;
        }

        public Builder setSplit(String split) {
            this.split = split;
            return this;
        }

        public Builder setConfigForSplit(String configForSplit) {
            this.configForSplit = configForSplit;
            return this;
        }

        public Builder setIsFeatureSplit(boolean isFeatureSplit) {
            this.isFeatureSplit = isFeatureSplit;
            return this;
        }

        public Builder setIsSplitRequired(boolean isSplitRequired) {
            this.isSplitRequired = isSplitRequired;
            return this;
        }

        public Builder setIsolatedSplits(boolean isolatedSplits) {
            this.isolatedSplits = isolatedSplits;
            return this;
        }

        public Builder setInstallLocation(String installLocation) {
            this.installLocation = installLocation;
            return this;
        }

        public Builder setMinSdkVersion(String minSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            return this;
        }

        public Builder setTargetSdkVersion(String targetSdkVersion) {
            this.targetSdkVersion = targetSdkVersion;
            return this;
        }

        public Builder setMaxSdkVersion(String maxSdkVersion) {
            this.maxSdkVersion = maxSdkVersion;
            return this;
        }

        public Builder setCompileSdkVersion(String compileSdkVersion) {
            this.compileSdkVersion = compileSdkVersion;
            return this;
        }

        public Builder setCompileSdkVersionCodename(String compileSdkVersionCodename) {
            this.compileSdkVersionCodename = compileSdkVersionCodename;
            return this;
        }

        public Builder setPlatformBuildVersionCode(String platformBuildVersionCode) {
            this.platformBuildVersionCode = platformBuildVersionCode;
            return this;
        }

        public Builder setPlatformBuildVersionName(String platformBuildVersionName) {
            this.platformBuildVersionName = platformBuildVersionName;
            return this;
        }

        public Builder setGlEsVersion(GlEsVersion glEsVersion) {
            this.glEsVersion = glEsVersion;
            return this;
        }

        public Builder setAnyDensity(boolean anyDensity) {
            this.anyDensity = anyDensity;
            return this;
        }

        public Builder setSmallScreens(boolean smallScreens) {
            this.smallScreens = smallScreens;
            return this;
        }

        public Builder setNormalScreens(boolean normalScreens) {
            this.normalScreens = normalScreens;
            return this;
        }

        public Builder setLargeScreens(boolean largeScreens) {
            this.largeScreens = largeScreens;
            return this;
        }

        public Builder setDebuggable(boolean debuggable) {
            this.debuggable = debuggable;
            return this;
        }

        public Builder addUsesPermission(String usesPermission) {
            this.usesPermissions.add(usesPermission);
            return this;
        }

        public Builder addUsesFeature(UseFeature usesFeature) {
            this.usesFeatures.add(usesFeature);
            return this;
        }

        public Builder addPermissions(Permission permission) {
            this.permissions.add(permission);
            return this;
        }

        public ApkMeta build() {
            return new ApkMeta(this);
        }
    }
}
