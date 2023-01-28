// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

// Copyright 2020 Rikka
interface IRemoteProcess {
    ParcelFileDescriptor getOutputStream();
    void closeOutputStream();
    ParcelFileDescriptor getInputStream();
    ParcelFileDescriptor getErrorStream();
    int waitFor();
    int exitValue();
    void destroy();
    boolean alive();
    boolean waitForTimeout(long timeout, String unit);
}
