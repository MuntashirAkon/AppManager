// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;

// Copyright 2018 Zheng Li
public final class ApiSupporter {

    private ApiSupporter() {
    }

    public static Shell.Result runCommand(String command) throws Exception {
        LocalServer localServer = LocalServer.getInstance();
        ShellCaller shellCaller = new ShellCaller(command);
        CallerResult callerResult = localServer.exec(shellCaller);
        return (Shell.Result) callerResult.getReplyObj();
    }
}
