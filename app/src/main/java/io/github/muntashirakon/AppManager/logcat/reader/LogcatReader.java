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

package io.github.muntashirakon.AppManager.logcat.reader;

import java.io.IOException;
import java.util.List;

public interface LogcatReader {
    /**
     * Read a single log line, ala {@link java.io.BufferedReader#readLine()}.
     *
     * @return A single log line
     */
    String readLine() throws IOException;

    /**
     * Kill the reader and close all resources without throwing any exceptions.
     */
    void killQuietly();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean readyToRecord();

    List<Process> getProcesses();
}
