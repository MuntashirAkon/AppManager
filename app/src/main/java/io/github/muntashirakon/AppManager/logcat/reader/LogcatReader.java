// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

// Copyright 2012 Nolan Lawson
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

    void killQuietly(ExecutorService executor);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean readyToRecord();

    List<Process> getProcesses();
}
