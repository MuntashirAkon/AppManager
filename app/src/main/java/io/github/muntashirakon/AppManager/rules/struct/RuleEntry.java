// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public abstract class RuleEntry {
    public static final String STUB = "STUB";

    /**
     * Name of the entry, unique for {@link RuleType#ACTIVITY}, {@link RuleType#PROVIDER}, {@link RuleType#RECEIVER},
     * {@link RuleType#SERVICE}, {@link RuleType#PERMISSION}, {@link RuleType#MAGISK_DENY_LIST} but not others.
     * In other cases, they can be {@link #STUB}.
     */
    @NonNull
    public final String name;
    /**
     * The package name this rule belong to.
     */
    @NonNull
    public final String packageName;
    /**
     * Type of the entry.
     */
    @NonNull
    public final RuleType type;

    public RuleEntry(@NonNull String packageName, @NonNull String name, @NonNull RuleType type) {
        this.packageName = packageName;
        this.name = name;
        this.type = type;
    }

    @NonNull
    @Override
    public String toString() {
        return "Entry{" +
                "name='" + name + '\'' +
                ", type=" + type +
                '}';
    }

    @NonNull
    public abstract String flattenToString(boolean isExternal);

    protected String addPackageWithTab(boolean isExternal) {
        return (isExternal ? packageName + "\t" : "");
    }

    @NonNull
    public static RuleEntry unflattenFromString(@Nullable String packageName, @NonNull String ruleLine,
                                                boolean isExternal) throws IllegalArgumentException {
        StringTokenizer tokenizer = new StringTokenizer(ruleLine, "\t");
        if (isExternal) {
            // External rules, the first part is the package name
            if (tokenizer.hasMoreElements()) {
                // Match package name
                String newPackageName = tokenizer.nextElement().toString();
                if (packageName == null) packageName = newPackageName;
                if (!packageName.equals(newPackageName)) {
                    throw new IllegalArgumentException("Invalid format: package names do not match.");
                }
            } else throw new IllegalArgumentException("Invalid format: packageName not found for external rule.");
        }
        if (packageName == null) {
            // packageName can't be empty
            throw new IllegalArgumentException("Package name cannot be empty.");
        }
        String name;
        RuleType type;
        if (tokenizer.hasMoreElements()) {
            name = tokenizer.nextElement().toString();
        } else throw new IllegalArgumentException("Invalid format: name not found");
        if (tokenizer.hasMoreElements()) {
            try {
                type = RuleType.valueOf(tokenizer.nextElement().toString());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid format: Invalid type");
            }
        } else throw new IllegalArgumentException("Invalid format: entryType not found");
        return getRuleEntry(packageName, name, type, tokenizer);
    }

    @NonNull
    private static RuleEntry getRuleEntry(@NonNull String packageName, @NonNull String name,
                                          @NonNull RuleType type, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        switch (type) {
            case ACTIVITY:
            case PROVIDER:
            case RECEIVER:
            case SERVICE:
                return new ComponentRule(packageName, name, type, tokenizer);
            case APP_OP:
                return new AppOpRule(packageName, name, tokenizer);
            case PERMISSION:
                return new PermissionRule(packageName, name, tokenizer);
            case MAGISK_HIDE:
                return new MagiskHideRule(packageName, name, tokenizer);
            case MAGISK_DENY_LIST:
                return new MagiskDenyListRule(packageName, name, tokenizer);
            case BATTERY_OPT:
                return new BatteryOptimizationRule(packageName, tokenizer);
            case NET_POLICY:
                return new NetPolicyRule(packageName, tokenizer);
            case NOTIFICATION:
                return new NotificationListenerRule(packageName, name, tokenizer);
            case URI_GRANT:
                return new UriGrantRule(packageName, tokenizer);
            case SSAID:
                return new SsaidRule(packageName, tokenizer);
            default:
                throw new IllegalArgumentException("Invalid type=" + type.name());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleEntry)) return false;
        RuleEntry ruleEntry = (RuleEntry) o;
        return name.equals(ruleEntry.name) && packageName.equals(ruleEntry.packageName) && type == ruleEntry.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, packageName, type);
    }
}
