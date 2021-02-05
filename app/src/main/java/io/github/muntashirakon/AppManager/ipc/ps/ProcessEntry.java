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

package io.github.muntashirakon.AppManager.ipc.ps;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class ProcessEntry implements Parcelable {
    public int pid;
    public int ppid;
    public int priority;
    public int niceness;
    public long instructionPointer;
    public long virtualMemorySize;
    public long residentSetSize;
    public int processGroupId;
    public int majorPageFaults;
    public int minorPageFaults;
    public int realTimePriority;
    public int schedulingPolicy;
    public int cpu;
    public int threadCount;
    public int tty;
    public String seLinuxPolicy;
    public String name;
    public ProcessUsers users;
    public long cpuTimeConsumed;
    public long elapsedTime;
    public String processState;
    public String processStatePlus;

    public ProcessEntry() {
    }

    protected ProcessEntry(@NonNull Parcel in) {
        pid = in.readInt();
        ppid = in.readInt();
        priority = in.readInt();
        niceness = in.readInt();
        instructionPointer = in.readLong();
        virtualMemorySize = in.readLong();
        residentSetSize = in.readLong();
        processGroupId = in.readInt();
        majorPageFaults = in.readInt();
        minorPageFaults = in.readInt();
        realTimePriority = in.readInt();
        schedulingPolicy = in.readInt();
        cpu = in.readInt();
        threadCount = in.readInt();
        tty = in.readInt();
        seLinuxPolicy = in.readString();
        name = in.readString();
        users = in.readParcelable(ProcessUsers.class.getClassLoader());
        cpuTimeConsumed = in.readLong();
        elapsedTime = in.readLong();
        processState = in.readString();
        processStatePlus = in.readString();
    }

    public static final Creator<ProcessEntry> CREATOR = new Creator<ProcessEntry>() {
        @Override
        @NonNull
        public ProcessEntry createFromParcel(Parcel in) {
            return new ProcessEntry(in);
        }

        @Override
        @NonNull
        public ProcessEntry[] newArray(int size) {
            return new ProcessEntry[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(pid);
        dest.writeInt(ppid);
        dest.writeInt(priority);
        dest.writeInt(niceness);
        dest.writeLong(instructionPointer);
        dest.writeLong(virtualMemorySize);
        dest.writeLong(residentSetSize);
        dest.writeInt(processGroupId);
        dest.writeInt(majorPageFaults);
        dest.writeInt(minorPageFaults);
        dest.writeInt(realTimePriority);
        dest.writeInt(schedulingPolicy);
        dest.writeInt(cpu);
        dest.writeInt(threadCount);
        dest.writeInt(tty);
        dest.writeString(seLinuxPolicy);
        dest.writeString(name);
        dest.writeParcelable(users, flags);
        dest.writeLong(cpuTimeConsumed);
        dest.writeLong(elapsedTime);
        dest.writeString(processState);
        dest.writeString(processStatePlus);
    }
}
