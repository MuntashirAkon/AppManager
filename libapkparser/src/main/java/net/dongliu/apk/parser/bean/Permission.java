package net.dongliu.apk.parser.bean;

import androidx.annotation.Nullable;

/**
 * permission provided by the app
 *
 * @author Liu Dong
 */
public class Permission {
    private final String name;
    private final String label;
    private final String icon;
    private final String description;
    private final String group;
    private final String protectionLevel;

    public Permission(String name, String label, String icon, String description, String group,
                      String protectionLevel) {
        this.name = name;
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.group = group;
        this.protectionLevel = protectionLevel;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public String getGroup() {
        return group;
    }

    @Nullable
    public String getProtectionLevel() {
        return protectionLevel;
    }

}
