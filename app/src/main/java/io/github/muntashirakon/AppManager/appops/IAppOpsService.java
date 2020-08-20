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

package io.github.muntashirakon.AppManager.appops;

import java.util.List;

import io.github.muntashirakon.AppManager.server.common.PackageOps;

interface IAppOpsService {
    int checkOperation(int op, int uid, String packageName) throws Exception;
    List<PackageOps> getOpsForPackage(int uid, String packageName, int[] ops) throws Exception;
    void setMode(int op, int uid, String packageName, int mode) throws Exception;
    void resetAllModes(int reqUserId, String reqPackageName) throws Exception;
}
