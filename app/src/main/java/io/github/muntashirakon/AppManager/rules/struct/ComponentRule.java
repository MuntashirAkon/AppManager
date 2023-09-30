// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class ComponentRule extends RuleEntry {
    @StringDef({
            COMPONENT_BLOCKED_IFW_DISABLE,
            COMPONENT_BLOCKED_IFW,
            COMPONENT_DISABLED,
            COMPONENT_ENABLED,

            COMPONENT_TO_BE_BLOCKED_IFW_DISABLE,
            COMPONENT_TO_BE_BLOCKED_IFW,
            COMPONENT_TO_BE_DISABLED,
            COMPONENT_TO_BE_ENABLED,
            COMPONENT_TO_BE_DEFAULTED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComponentStatus {
    }

    // One would want to use flags but couldn't in order to preserve compatibility
    /**
     * Component has been blocked with both IFW and PM.
     */
    public static final String COMPONENT_BLOCKED_IFW_DISABLE = "true";  // To preserve compatibility
    /**
     * Component has been blocked with IFW.
     */
    public static final String COMPONENT_BLOCKED_IFW = "ifw_true";
    /**
     * Component has been disabled.
     */
    public static final String COMPONENT_DISABLED = "dis_true";
    /**
     * Component has been enabled.
     */
    public static final String COMPONENT_ENABLED = "en_true";

    /**
     * Component will be blocked with both IFW and PM.
     */
    public static final String COMPONENT_TO_BE_BLOCKED_IFW_DISABLE = "false";  // To preserve compatibility
    /**
     * Component will be blocked with IFW.
     */
    public static final String COMPONENT_TO_BE_BLOCKED_IFW = "ifw_false";
    /**
     * Component will be disabled.
     */
    public static final String COMPONENT_TO_BE_DISABLED = "dis_false";
    /**
     * Component will be enabled.
     */
    public static final String COMPONENT_TO_BE_ENABLED = "en_false";
    /**
     * Component will be set to the default state, removed from IFW rules if exists and cleared from DB.
     */
    public static final String COMPONENT_TO_BE_DEFAULTED = "unblocked";

    @NonNull
    @ComponentStatus
    private final String mComponentStatus;

    public ComponentRule(@NonNull String packageName, @NonNull String name, RuleType componentType,
                         @NonNull @ComponentStatus String componentStatus) {
        super(packageName, name, componentType);
        mComponentStatus = fixComponentStatus(componentStatus);
    }

    public ComponentRule(@NonNull String packageName, @NonNull String name, RuleType componentType,
                         @NonNull StringTokenizer tokenizer) throws IllegalArgumentException {
        super(packageName, name, componentType);
        if (tokenizer.hasMoreElements()) {
            mComponentStatus = fixComponentStatus(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: componentStatus not found");
    }

    public ComponentName getComponentName() {
        return new ComponentName(packageName, name);
    }

    @NonNull
    @ComponentStatus
    public String getComponentStatus() {
        return mComponentStatus;
    }

    public boolean toBeRemoved() {
        return mComponentStatus.equals(COMPONENT_TO_BE_DEFAULTED);
    }

    public boolean isBlocked() {
        return mComponentStatus.equals(COMPONENT_BLOCKED_IFW_DISABLE)
                || mComponentStatus.equals(COMPONENT_BLOCKED_IFW)
                || mComponentStatus.equals(COMPONENT_DISABLED);
    }

    public boolean isIfw() {
        return mComponentStatus.equals(COMPONENT_TO_BE_BLOCKED_IFW)
                || mComponentStatus.equals(COMPONENT_TO_BE_BLOCKED_IFW_DISABLE)
                || mComponentStatus.equals(COMPONENT_BLOCKED_IFW)
                || mComponentStatus.equals(COMPONENT_BLOCKED_IFW_DISABLE);
    }

    public boolean isApplied() {
        return !(mComponentStatus.equals(COMPONENT_TO_BE_BLOCKED_IFW_DISABLE)
                || mComponentStatus.equals(COMPONENT_TO_BE_BLOCKED_IFW)
                || mComponentStatus.equals(COMPONENT_TO_BE_DISABLED)
                || mComponentStatus.equals(COMPONENT_TO_BE_ENABLED)
                || mComponentStatus.equals(COMPONENT_TO_BE_DEFAULTED));
    }

    @ComponentStatus
    public String getCounterpartOfToBe() {
        switch (mComponentStatus) {
            case COMPONENT_TO_BE_BLOCKED_IFW_DISABLE:
                return COMPONENT_BLOCKED_IFW_DISABLE;
            case COMPONENT_TO_BE_BLOCKED_IFW:
                return COMPONENT_BLOCKED_IFW;
            case COMPONENT_TO_BE_DISABLED:
                return COMPONENT_DISABLED;
            case COMPONENT_TO_BE_ENABLED:
                return COMPONENT_ENABLED;
            default:
                return mComponentStatus;
        }
    }

    @ComponentStatus
    public String getToBe() {
        switch (mComponentStatus) {
            case COMPONENT_BLOCKED_IFW_DISABLE:
                return COMPONENT_TO_BE_BLOCKED_IFW_DISABLE;
            case COMPONENT_BLOCKED_IFW:
                return COMPONENT_TO_BE_BLOCKED_IFW;
            case COMPONENT_DISABLED:
                return COMPONENT_TO_BE_DISABLED;
            case COMPONENT_ENABLED:
                return COMPONENT_TO_BE_ENABLED;
            default:
                return mComponentStatus;
        }
    }

    private String fixComponentStatus(@ComponentStatus String componentStatus) {
        if (type != RuleType.PROVIDER) {
            return componentStatus;
        }
        // Providers do not support IFW
        switch (componentStatus) {
            case COMPONENT_BLOCKED_IFW_DISABLE:
                return COMPONENT_DISABLED;
            case COMPONENT_BLOCKED_IFW:
                return COMPONENT_ENABLED;
            case COMPONENT_TO_BE_BLOCKED_IFW:
            case COMPONENT_TO_BE_BLOCKED_IFW_DISABLE:
                return COMPONENT_TO_BE_DISABLED;
            default:
                return componentStatus;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ComponentRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type.name() +
                ", componentStatus='" + mComponentStatus + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mComponentStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentRule)) return false;
        if (!super.equals(o)) return false;
        ComponentRule rule = (ComponentRule) o;
        return getComponentStatus().equals(rule.getComponentStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getComponentStatus());
    }
}
