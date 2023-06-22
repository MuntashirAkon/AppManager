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

    @ChangelogType
    public final int type;

    @NonNull
    private final CharSequence mChangeText;

    private boolean mBulletedList;
    private boolean mSubtext;
    @Nullable
    private String mChangeTitle;
    @ChangeTextType
    private int mChangeTextType;

    public ChangelogItem(@ChangelogType int type) {
        mChangeText = "";
        this.type = type;
    }

    public ChangelogItem(@NonNull CharSequence changeText, @ChangelogType int type) {
        mChangeText = changeText;
        this.type = type;
    }

    public ChangelogItem(@NonNull String changeText, @ChangelogType int type) {
        mChangeText = parseChangeText(changeText);
        this.type = type;
    }

    public boolean isBulletedList() {
        return mBulletedList;
    }

    public void setBulletedList(boolean bulletedList) {
        mBulletedList = bulletedList;
    }

    public boolean isSubtext() {
        return mSubtext;
    }

    public void setSubtext(boolean subtext) {
        mSubtext = subtext;
        mChangeTextType = subtext ? TEXT_SMALL : TEXT_MEDIUM;
    }

    @NonNull
    public CharSequence getChangeText() {
        return mChangeText;
    }

    @Nullable
    public String getChangeTitle() {
        return mChangeTitle;
    }

    void setChangeTitle(@Nullable String changeTitle) {
        mChangeTitle = changeTitle;
    }

    @ChangeTextType
    public int getChangeTextType() {
        return mChangeTextType;
    }

    public void setChangeTextType(@ChangeTextType int changeTextType) {
        mChangeTextType = changeTextType;
    }

    @NonNull
    public static CharSequence parseChangeText(@NonNull String changeText) {
        // TODO: Supported markups **Bold**, __Italic__, `Monospace`, ~~Strikethrough~~, [Link](link_name)
        changeText = changeText.replaceAll("\\[", "<").replaceAll("\\]", ">");
        return HtmlCompat.fromHtml(changeText, HtmlCompat.FROM_HTML_MODE_COMPACT);
    }
}