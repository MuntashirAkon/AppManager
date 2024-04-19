// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import androidx.annotation.NonNull;

public final class ConfigParams {
    public static final String PARAM_DEBUG = "debug";
    public static final String PARAM_APP = "app";
    public static final String PARAM_PATH = "path";
    public static final String PARAM_RUN_IN_BACKGROUND = "bgrun";
    public static final String PARAM_TOKEN = "token";
    public static final String PARAM_UID = "uid";

    private boolean mIsDebug;
    private String mAppName;
    private String mPath;
    private boolean mRunInBackground;
    private String mToken;
    private String mUid;

    public ConfigParams() {
    }

    public void put(@NonNull String key, @NonNull String value) {
        switch (key) {
            case PARAM_DEBUG:
                mIsDebug = "1".equals(value);
                break;
            case PARAM_APP:
                mAppName = value;
                break;
            case PARAM_PATH:
                mPath = value;
                break;
            case PARAM_RUN_IN_BACKGROUND:
                mRunInBackground = "1".equals(value);
                break;
            case PARAM_TOKEN:
                mToken = value;
                break;
            case PARAM_UID:
                mUid = value;
        }
    }

    public boolean isIsDebug() {
        return mIsDebug;
    }

    public String getAppName() {
        return mAppName;
    }

    public String getPath() {
        return mPath;
    }

    public boolean isRunInBackground() {
        return mRunInBackground;
    }

    public String getToken() {
        return mToken;
    }

    public String getUid() {
        return mUid;
    }

    @NonNull
    @Override
    public String toString() {
        return "ConfigParam{" +
                "mIsDebug=" + mIsDebug +
                ", mPath='" + mPath + '\'' +
                ", mRunInBackground=" + mRunInBackground +
                ", mToken='" + mToken + '\'' +
                ", mUid='" + mUid + '\'' +
                '}';
    }
}
