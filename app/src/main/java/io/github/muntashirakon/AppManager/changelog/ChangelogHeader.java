// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import androidx.annotation.NonNull;

import java.util.Locale;

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class ChangelogHeader extends ChangelogItem {
    @NonNull
    private final String versionName;
    private final long versionCode;
    @NonNull
    private final String releaseType;
    @NonNull
    private final String releaseDate;

    public ChangelogHeader(@NonNull String versionName, long versionCode, @NonNull String releaseType, @NonNull String releaseDate) {
        super(parseHeaderText(versionName, versionCode), HEADER);
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.releaseType = releaseType;
        this.releaseDate = releaseDate;
        setBulletedList(false);
    }

    @NonNull
    public String getVersionName() {
        return versionName;
    }

    public long getVersionCode() {
        return versionCode;
    }

    @NonNull
    public String getReleaseType() {
        return releaseType;
    }

    @NonNull
    public String getReleaseDate() {
        return releaseDate;
    }

    @NonNull
    private static CharSequence parseHeaderText(@NonNull String versionName, long versionCode) {
        return String.format(Locale.getDefault(), "Version %s (%d)", versionName, versionCode);
    }
}