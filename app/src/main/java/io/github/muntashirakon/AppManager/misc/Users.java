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

    @SuppressWarnings({"JavaReflectionMemberAccess", "rawtypes"})
    public static int getCurrentUser() {
        if (currentUserHandle == null) {
            currentUserHandle = 0;
            try {
                // using reflection to get id of calling user since method getCallingUserId of UserHandle is hidden
                // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/UserHandle.java#L123
                Class userHandle = Class.forName("android.os.UserHandle");
                boolean muEnabled = userHandle.getField("MU_ENABLED").getBoolean(null);
                int range = userHandle.getField("PER_USER_RANGE").getInt(null);
                if (muEnabled) currentUserHandle = android.os.Binder.getCallingUid() / range;
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignore) {
            }
            // Another way
//            try {
//                // FIXME: Get user id using root since this is only intended for root users
//                @SuppressWarnings("JavaReflectionMemberAccess")
//                Method myUserId = UserHandle.class.getMethod("myUserId");
//                currentUserHandle = (int) myUserId.invoke(null);
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
//                e.printStackTrace();
//            }
        }
        return currentUserHandle;
    }
}
