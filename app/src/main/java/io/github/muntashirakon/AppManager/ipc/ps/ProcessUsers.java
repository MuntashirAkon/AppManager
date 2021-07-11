// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProcessUsers implements Parcelable {
    public final int realUid;
    public final int realGid;
    public final int effectiveUid;
    public final int effectiveGid;
    public final int savedSetUid;
    public final int savedSetGid;
    public final int fsUid;
    public final int fsGid;

    ProcessUsers(@Nullable String uidLine, @Nullable String gidLine) {
        if (uidLine == null || gidLine == null) {
            throw new IllegalArgumentException("UID/GID must be non null");
        }
        String[] uids = uidLine.split("\\s+");
        String[] gids = gidLine.split("\\s+");
        if (uids.length != gids.length && uids.length >= 4) {
            throw new IllegalArgumentException("Invalid UID/GID.\nUid: " + uidLine + "\nGid: " + gidLine);
        }
        // Set uids
        realUid = Integer.decode(uids[0].trim());
        effectiveUid = Integer.decode(uids[1].trim());
        savedSetUid = Integer.decode(uids[2].trim());
        fsUid = Integer.decode(uids[3].trim());
        // Set gids
        realGid = Integer.decode(gids[0].trim());
        effectiveGid = Integer.decode(gids[1].trim());
        savedSetGid = Integer.decode(gids[2].trim());
        fsGid = Integer.decode(gids[3].trim());
    }

    protected ProcessUsers(@NonNull Parcel in) {
        realUid = in.readInt();
        realGid = in.readInt();
        effectiveUid = in.readInt();
        effectiveGid = in.readInt();
        savedSetUid = in.readInt();
        savedSetGid = in.readInt();
        fsUid = in.readInt();
        fsGid = in.readInt();
    }

    public static final Creator<ProcessUsers> CREATOR = new Creator<ProcessUsers>() {
        @Override
        @NonNull
        public ProcessUsers createFromParcel(Parcel in) {
            return new ProcessUsers(in);
        }

        @Override
        @NonNull
        public ProcessUsers[] newArray(int size) {
            return new ProcessUsers[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(realUid);
        dest.writeInt(realGid);
        dest.writeInt(effectiveUid);
        dest.writeInt(effectiveGid);
        dest.writeInt(savedSetUid);
        dest.writeInt(savedSetGid);
        dest.writeInt(fsUid);
        dest.writeInt(fsGid);
    }
}
