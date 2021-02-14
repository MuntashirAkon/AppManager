/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

import android.os.RemoteException;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class AppOpsUtils {
    @NonNull
    public static List<OpEntry> getChangedAppOps(@NonNull AppOpsService service, @NonNull String packageName, int uid)
            throws RemoteException {
        List<PackageOps> packageOpsList = service.getOpsForPackage(uid, packageName, null);
        List<OpEntry> opEntries = new ArrayList<>();
        if (packageOpsList.size() == 1) {
            opEntries.addAll(packageOpsList.get(0).getOps());
        }
        return opEntries;
    }
}
