// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

// Copyright 2017 Zheng Li
public final class ServerActions {
    // This hard coded value won't cause any issue because it's only used internally.
    public static final String PACKAGE_NAME = "io.github.muntashirakon.AppManager";

    public static final String ACTION_SERVER_STARTED = PACKAGE_NAME + ".action.SERVER_STARTED";
    public static final String ACTION_SERVER_CONNECTED = PACKAGE_NAME + ".action.SERVER_CONNECTED";
    public static final String ACTION_SERVER_DISCONNECTED = PACKAGE_NAME + ".action.SERVER_DISCONNECTED";
    public static final String ACTION_SERVER_STOPPED = PACKAGE_NAME + ".action.SERVER_STOPED";
}
