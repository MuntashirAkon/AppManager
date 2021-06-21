// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import androidx.annotation.NonNull;

import java.util.Objects;

public class ProcessItem {
    public final int pid;
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

    public ProcessItem(int pid) {
        this.pid = pid;
    }

    @Override
    @NonNull
    public String toString() {
        return "ProcessItem{" +
                "pid=" + pid +
                ", ppid=" + ppid +
                ", rss=" + rss +
                ", vsz=" + vsz +
                ", user='" + user + '\'' +
                ", uid=" + uid +
                ", state='" + state + '\'' +
                ", state_extra='" + state_extra + '\'' +
                ", name='" + name + '\'' +
                ", context='" + context + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessItem)) return false;
        ProcessItem that = (ProcessItem) o;
        return pid == that.pid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid);
    }
}
