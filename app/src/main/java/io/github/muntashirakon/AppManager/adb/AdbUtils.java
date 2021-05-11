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

package io.github.muntashirakon.AppManager.adb;

import android.content.Context;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class AdbUtils {
    public static boolean isAdbAvailable(Context context, String host, int port) {
        try (AdbConnection ignored = AdbConnectionManager.connect(context, host, port)) {
            return true;
        } catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
            return false;
        }
    }
}
