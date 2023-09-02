// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.Objects;

public class ProcessEntry implements Parcelable {
    public int pid;
    public int ppid;
    public int priority;
    public int niceness;
    public long instructionPointer;
    public long virtualMemorySize;
    public long residentSetSize;
    public long sharedMemory;
    public int processGroupId;
    public int majorPageFaults;
    public int minorPageFaults;
    public int realTimePriority;
    public int schedulingPolicy;
    public int cpu;
    public int threadCount;
    public int tty;
    @Nullable
    public String seLinuxPolicy;
    public String name;
    public ProcessUsers users;
    public long cpuTimeConsumed;
    public long cCpuTimeConsumed;
    public long elapsedTime;
    public String processState;
    public String processStatePlus;

    ProcessEntry() {
    }

    protected ProcessEntry(@NonNull Parcel in) {
        pid = in.readInt();
        ppid = in.readInt();
        priority = in.readInt();
        niceness = in.readInt();
        instructionPointer = in.readLong();
        virtualMemorySize = in.readLong();
        residentSetSize = in.readLong();
        sharedMemory = in.readLong();
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
        users = Objects.requireNonNull(ParcelCompat.readParcelable(in, ProcessUsers.class.getClassLoader(), ProcessUsers.class));
        cpuTimeConsumed = in.readLong();
        cCpuTimeConsumed = in.readLong();
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
        dest.writeLong(sharedMemory);
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
        dest.writeLong(cCpuTimeConsumed);
        dest.writeLong(elapsedTime);
        dest.writeString(processState);
        dest.writeString(processStatePlus);
    }
}
