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

package io.github.muntashirakon.AppManager.runningapps;

public class ProcessItem {
    public int pid;
    public int ppid;
    public long rss;
    public long vsz;
    public String user;
    public int uid;
    public String state;
    public String state_extra;
    public String name;
    /**
     * SELinux context
     * TODO(25/9/20): Improve this by parsing the string
     */
    public String context;

    public boolean selected = false;
}
