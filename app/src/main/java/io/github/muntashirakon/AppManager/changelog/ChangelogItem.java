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
    @IntDef({HEADER, TITLE, NOTE, NEW, IMPROVE, FIX})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChangelogType {
    }

    public static final int HEADER = -1;
    public static final int TITLE = 0;
    public static final int NOTE = 1;
    public static final int NEW = 2;
    public static final int IMPROVE = 3;
    public static final int FIX = 4;

    @IntDef({TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChangeTextType {
    }

    public static final int TEXT_SMALL = 0;
    public static final int TEXT_MEDIUM = 1;
    public static final int TEXT_LARGE = 2;

    @NonNull
    private final CharSequence changeText;
    @ChangelogType
    public final int type;

    private boolean bulletedList;
    private boolean subtext;
    @Nullable
    private String changeTitle;
    @ChangeTextType
    private int changeTextType;

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
        changeTextType = subtext ? TEXT_SMALL : TEXT_MEDIUM;
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

    @ChangeTextType
    public int getChangeTextType() {
        return changeTextType;
    }

    public void setChangeTextType(@ChangeTextType int changeTextType) {
        this.changeTextType = changeTextType;
    }

    @NonNull
    public static CharSequence parseChangeText(@NonNull String changeText) {
        // TODO: Supported markups **Bold**, __Italic__, `Monospace`, ~~Strikethrough~~, [Link](link_name)
        changeText = changeText.replaceAll("\\[", "<").replaceAll("\\]", ">");
        return HtmlCompat.fromHtml(changeText, HtmlCompat.FROM_HTML_MODE_COMPACT);
    }
}