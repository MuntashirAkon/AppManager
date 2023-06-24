// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.server.common;

// Copyright 2020 John "topjohnwu" Wu
interface IRootServiceManager {
    oneway void broadcast(int uid);
    oneway void stop(in ComponentName name, int uid);
    void connect(in IBinder binder);
    IBinder bind(in Intent intent);
    oneway void unbind(in ComponentName name);
}
