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

package android.content.pm;

import android.os.UserHandle;

public class UserInfo {
    public int id;
    public int serialNumber;
    public String name;
    public String iconPath;
    public int flags;

    public boolean isPrimary() {
        return false;
    }

    public boolean isAdmin() {
        return false;
    }

    public boolean isManagedProfile() {
        return false;
    }

    public UserHandle getUserHandle() {
        return null;
    }
}
