// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

// Copyright 2020 John "topjohnwu" Wu
interface IRootIPC {
    oneway void broadcast();
    IBinder bind(in Intent intent);
    oneway void unbind();
    oneway void stop();
}
