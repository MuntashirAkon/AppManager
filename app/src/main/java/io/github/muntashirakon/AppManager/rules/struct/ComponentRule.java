// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class ComponentRule extends RuleEntry {
    @StringDef(value = {
            COMPONENT_BLOCKED,
            COMPONENT_TO_BE_BLOCKED,
            COMPONENT_TO_BE_UNBLOCKED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComponentStatus {
    }

    public static final String COMPONENT_BLOCKED = "true";  // To preserve compatibility
    public static final String COMPONENT_TO_BE_BLOCKED = "false";  // To preserve compatibility
    public static final String COMPONENT_TO_BE_UNBLOCKED = "unblocked";

    @NonNull
    @ComponentStatus
    private String componentStatus;

    public ComponentRule(@NonNull String packageName, @NonNull String name, RuleType componentType,
                         @NonNull @ComponentStatus String componentStatus) {
        super(packageName, name, componentType);
        this.componentStatus = componentStatus;
    }

    public ComponentRule(@NonNull String packageName, @NonNull String name, RuleType componentType,
                         @NonNull StringTokenizer tokenizer) throws IllegalArgumentException {
        super(packageName, name, componentType);
        if (tokenizer.hasMoreElements()) {
            componentStatus = tokenizer.nextElement().toString();
        } else throw new IllegalArgumentException("Invalid format: componentStatus not found");
    }

    @NonNull
    @ComponentStatus
    public String getComponentStatus() {
        return componentStatus;
    }

    public void setComponentStatus(@NonNull @ComponentStatus String componentStatus) {
        this.componentStatus = componentStatus;
    }

    @NonNull
    @Override
    public String toString() {
        return "ComponentRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type.name() +
                ", componentStatus='" + componentStatus + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + componentStatus;
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
