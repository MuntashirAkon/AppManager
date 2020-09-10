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

package io.github.muntashirakon.AppManager.misc;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public final class Users {
    public static final boolean MU_ENABLED;
    public static final int PER_USER_RANGE;

    static {
        boolean muEnabled = true;
        int perUserRange = 100000;
        try {
            // using reflection to get id of calling user since method getCallingUserId of UserHandle is hidden
            // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/UserHandle.java#L123
            @SuppressWarnings("rawtypes")
            Class userHandle = Class.forName("android.os.UserHandle");
            //noinspection JavaReflectionMemberAccess
            muEnabled = userHandle.getField("MU_ENABLED").getBoolean(null);
            //noinspection JavaReflectionMemberAccess
            perUserRange = userHandle.getField("PER_USER_RANGE").getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        MU_ENABLED = muEnabled;
        PER_USER_RANGE = perUserRange;
    }

    @NonNull
    public static int[] getUsers() {
        // FIXME: Use Pattern.compile() instead
        Runner.Result result = Runner.runCommand(RunnerUtils.CMD_PM + " list users | " + Runner.TOYBOX + " sed -nr 's/.*\\{([0-9]+):.*/\\1/p'");
        if (result.isSuccessful()) {
            List<String> output = result.getOutputAsList();
            List<Integer> users = new ArrayList<>();
            for (String user : output) {
                try {
                    users.add(Integer.parseInt(user));
                } catch (Exception ignore) {
                }
            }
            return ArrayUtils.convertToIntArray(users);
        } else {
            return new int[]{getCurrentUser()};
        }
    }

    private static Integer currentUserHandle = null;

    public static int getCurrentUser() {
        if (currentUserHandle == null) {
            if (MU_ENABLED) currentUserHandle = android.os.Binder.getCallingUid() / PER_USER_RANGE;
            else currentUserHandle = 0;
            // Another way
//            try {
//                @SuppressWarnings("JavaReflectionMemberAccess")
//                Method myUserId = UserHandle.class.getMethod("myUserId");
//                currentUserHandle = (int) myUserId.invoke(null);
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//                e.printStackTrace();
//            }
        }
        return currentUserHandle;
    }

    public static int getUser(int uid) {
        if (MU_ENABLED && uid >= PER_USER_RANGE) return uid / PER_USER_RANGE;
        return uid;
    }
}
