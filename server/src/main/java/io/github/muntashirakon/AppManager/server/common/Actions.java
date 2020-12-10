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

package io.github.muntashirakon.AppManager.server.common;

public final class Actions {
    public static final String PACKAGE_NAME = "io.github.muntashirakon.AppManager";
    public static final String ACTION_SERVER_STARTED = PACKAGE_NAME + ".action.SERVER_STARTED";
    public static final String ACTION_SERVER_CONNECTED = PACKAGE_NAME + ".action.SERVER_CONNECTED";
    public static final String ACTION_SERVER_DISCONNECTED = PACKAGE_NAME + ".action.SERVER_DISCONNECTED";
    public static final String ACTION_SERVER_STOPPED = PACKAGE_NAME + ".action.SERVER_STOPED";
}
