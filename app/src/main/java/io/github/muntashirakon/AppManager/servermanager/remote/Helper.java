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

package io.github.muntashirakon.AppManager.servermanager.remote;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.server.common.FixCompat;

class Helper {
    private static Map<String, Integer> sRuntimePermToOp = null;

    static int permissionToCode(String permission) {
        if (sRuntimePermToOp == null) {
            sRuntimePermToOp = new HashMap<>();
            String[] opPerms = FixCompat.sOpPerms();
            int[] opToSwitch = FixCompat.sOpToSwitch();

            if (opPerms != null && opToSwitch != null && opPerms.length == opToSwitch.length) {
                for (int i = 0; i < opToSwitch.length; i++) {
                    if (opPerms[i] != null) {
                        sRuntimePermToOp.put(opPerms[i], opToSwitch[i]);
                    }
                }
            }
        }
        Integer code = sRuntimePermToOp.get(permission);
        if (code != null) {
            return code;
        }
        return -1;
    }

}
