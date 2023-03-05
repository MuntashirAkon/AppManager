// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.util.LocalizedString;

public class MagiskModuleInfo implements LocalizedString {
    @NonNull
    public static MagiskModuleInfo fromModule(@NonNull Path modulePath) throws IOException {
        Properties prop = new Properties();
        try (InputStream is = modulePath.findFile(MagiskModule.MODULE_PROP).openInputStream()) {
            prop.load(is);
        }
        return new MagiskModuleInfo(
                prop.getProperty("id"),
                prop.getProperty("name"),
                prop.getProperty("version"),
                Long.decode(prop.getProperty("versionCode")),
                prop.getProperty("author"),
                prop.getProperty("description"),
                prop.getProperty("updateJson")
        );
    }

    @NonNull
    public final String id;
    @NonNull
    public final String name;
    @NonNull
    public final String version;
    public final long versionCode;
    @NonNull
    public final String author;
    @NonNull
    public final String description;
    @Nullable
    public final String updateJson;

    public MagiskModuleInfo(@NonNull String id, @NonNull String name, @NonNull String version, long versionCode,
                            @NonNull String author, @NonNull String description, @Nullable String updateJson) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.versionCode = versionCode;
        this.author = author;
        this.description = description;
        this.updateJson = updateJson;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(UIUtils.getStyledKeyValue(context, "ID", id)).append("\n")
                .append(UIUtils.getStyledKeyValue(context, "Name", id)).append("\n")
                .append(UIUtils.getStyledKeyValue(context, R.string.version, version + " (" + versionCode + ")"))
                .append("\n")
                .append(description);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagiskModuleInfo)) return false;
        MagiskModuleInfo that = (MagiskModuleInfo) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    @Override
    public String toString() {
        return "MagiskModuleInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", versionCode=" + versionCode +
                '}';
    }
}
