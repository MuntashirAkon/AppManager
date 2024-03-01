// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.FilterableAppInfo;

public class ComponentsOption extends FilterOption {
    public static final int COMPONENT_TYPE_ACTIVITY = 1 << 0;
    public static final int COMPONENT_TYPE_SERVICE = 1 << 1;
    public static final int COMPONENT_TYPE_RECEIVER = 1 << 2;
    public static final int COMPONENT_TYPE_PROVIDER = 1 << 3;

    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("with_type", TYPE_INT_FLAGS);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
    }};

    private final Map<Integer, CharSequence> mComponentTypeFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(COMPONENT_TYPE_ACTIVITY, "Activities");
        put(COMPONENT_TYPE_SERVICE, "Services");
        put(COMPONENT_TYPE_RECEIVER, "Receivers");
        put(COMPONENT_TYPE_PROVIDER, "Providers");
    }};

    public ComponentsOption() {
        super("components");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("with_type")) {
            return mComponentTypeFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull FilterableAppInfo info, @NonNull TestResult result) {
        Map<ComponentInfo, Integer> components = result.getMatchedComponents() != null
                ? result.getMatchedComponents()
                : info.getAllComponents();
        switch (key) {
            default:
                return result.setMatched(true).setMatchedComponents(components);
            case "with_type": {
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    int type = Objects.requireNonNull(components.get(component));
                    if ((intValue & type) != 0) {
                        filteredComponents.put(component, type);
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
            case "eq": {
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    if (component.name.equals(value)) {
                        filteredComponents.put(component, components.get(component));
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
            case "contains": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    if (component.name.contains(value)) {
                        filteredComponents.put(component, components.get(component));
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
            case "starts_with": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    if (component.name.startsWith(value)) {
                        filteredComponents.put(component, components.get(component));
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
            case "ends_with": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    if (component.name.endsWith(value)) {
                        filteredComponents.put(component, components.get(component));
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
            case "regex": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filteredComponents = new LinkedHashMap<>();
                for (ComponentInfo component : components.keySet()) {
                    if (regexValue.matcher(component.name).matches()) {
                        filteredComponents.put(component, components.get(component));
                    }
                }
                return result.setMatched(!filteredComponents.isEmpty())
                        .setMatchedComponents(filteredComponents);
            }
        }
    }
}
