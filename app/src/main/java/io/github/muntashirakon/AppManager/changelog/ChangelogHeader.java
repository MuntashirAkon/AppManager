// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import androidx.annotation.NonNull;

import java.util.Locale;

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class ChangelogHeader extends ChangelogItem {
    @NonNull
    private final String mVersionName;
    private final long mVersionCode;
    @NonNull
    private final String mReleaseType;
    @NonNull
    private final String mReleaseDate;

    public ChangelogHeader(@NonNull String versionName, long versionCode, @NonNull String releaseType, @NonNull String releaseDate) {
        super(parseHeaderText(versionName, versionCode), HEADER);
        mVersionName = versionName;
        mVersionCode = versionCode;
        mReleaseType = releaseType;
        mReleaseDate = releaseDate;
        setBulletedList(false);
    }

    @NonNull
    public String getVersionName() {
        return mVersionName;
    }

    public long getVersionCode() {
        return mVersionCode;
    }

    @NonNull
    public String getReleaseType() {
        return mReleaseType;
    }

    @NonNull
    public String getReleaseDate() {
        return mReleaseDate;
    }

    @NonNull
    private static CharSequence parseHeaderText(@NonNull String versionName, long versionCode) {
        return String.format(Locale.getDefault(), "Version %s (%d)", versionName, versionCode);
    }
}