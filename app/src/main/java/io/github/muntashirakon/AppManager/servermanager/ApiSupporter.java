/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.servermanager;

import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;

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
