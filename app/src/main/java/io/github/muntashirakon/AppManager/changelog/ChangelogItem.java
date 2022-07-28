// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class ChangelogItem {
    @IntDef({HEADER, NOTE, NEW, IMPROVE, FIX})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChangelogType {
    }

    public static final int HEADER = -1;
    public static final int NOTE = 0;
    public static final int NEW = 1;
    public static final int IMPROVE = 2;
    public static final int FIX = 3;

    @NonNull
    private final CharSequence changeText;
    @ChangelogType
    public final int type;

    private boolean bulletedList;
    private boolean subtext;
    @Nullable
    private String changeTitle;

    public ChangelogItem(@ChangelogType int type) {
        changeText = "";
        this.type = type;
    }

    public ChangelogItem(@NonNull CharSequence changeText, @ChangelogType int type) {
        this.changeText = changeText;
        this.type = type;
    }

    public ChangelogItem(@NonNull String changeText, @ChangelogType int type) {
        this.changeText = parseChangeText(changeText);
        this.type = type;
    }

    public boolean isBulletedList() {
        return bulletedList;
    }

    public void setBulletedList(boolean bulletedList) {
        this.bulletedList = bulletedList;
    }

    public boolean isSubtext() {
        return subtext;
    }

    public void setSubtext(boolean subtext) {
        this.subtext = subtext;
    }

    @NonNull
    public CharSequence getChangeText() {
        return changeText;
    }

    @Nullable
    public String getChangeTitle() {
        return changeTitle;
    }

    void setChangeTitle(@Nullable String changeTitle) {
        this.changeTitle = changeTitle;
    }

    @NonNull
    public static CharSequence parseChangeText(@NonNull String changeText) {
        // TODO: Supported markups **Bold**, __Italic__, `Monospace`, ~~Strikethrough~~, [Link](link_name)
        changeText = changeText.replaceAll("\\[", "<").replaceAll("\\]", ">");
        return HtmlCompat.fromHtml(changeText, HtmlCompat.FROM_HTML_MODE_COMPACT);
    }
}