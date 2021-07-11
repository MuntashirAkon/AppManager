// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Parcel;
import android.os.Parcelable;
import android.system.StructStat;

import androidx.annotation.NonNull;

public class FileStatus implements Parcelable {
    /** Device ID of device containing file. */
    public final long st_dev; /*dev_t*/

    /** File serial number (inode). */
    public final long st_ino; /*ino_t*/

    /** Mode (permissions) of file. */
    public final int st_mode; /*mode_t*/

    /** Number of hard links to the file. */
    public final long st_nlink; /*nlink_t*/

    /** User ID of file. */
    public final int st_uid; /*uid_t*/

    /** Group ID of file. */
    public final int st_gid; /*gid_t*/

    /** Device ID (if file is character or block special). */
    public final long st_rdev; /*dev_t*/

    /**
     * For regular files, the file size in bytes.
     * For symbolic links, the length in bytes of the pathname contained in the symbolic link.
     * For a shared memory object, the length in bytes.
     * For a typed memory object, the length in bytes.
     * For other file types, the use of this field is unspecified.
     */
    public final long st_size; /*off_t*/

    /** Seconds part of time of last access. */
    public final long st_atime; /*time_t*/

    /** Seconds part of time of last data modification. */
    public final long st_mtime; /*time_t*/

    /** Seconds part of time of last status change */
    public final long st_ctime; /*time_t*/

    /**
     * A file system-specific preferred I/O block size for this object.
     * For some file system types, this may vary from file to file.
     */
    public final long st_blksize; /*blksize_t*/

    /** Number of blocks allocated for this object. */
    public final long st_blocks; /*blkcnt_t*/

    public FileStatus(@NonNull StructStat stat) {
        this.st_dev = stat.st_dev;
        this.st_ino = stat.st_ino;
        this.st_mode = stat.st_mode;
        this.st_nlink = stat.st_nlink;
        this.st_uid = stat.st_uid;
        this.st_gid = stat.st_gid;
        this.st_rdev = stat.st_rdev;
        this.st_size = stat.st_size;
        this.st_atime = stat.st_atime;
        this.st_mtime = stat.st_mtime;
        this.st_ctime = stat.st_ctime;
        this.st_blksize = stat.st_blksize;
        this.st_blocks = stat.st_blocks;
    }

    protected FileStatus(@NonNull Parcel in) {
        this.st_dev = in.readLong();
        this.st_ino = in.readLong();
        this.st_mode = in.readInt();
        this.st_nlink = in.readLong();
        this.st_uid = in.readInt();
        this.st_gid = in.readInt();
        this.st_rdev = in.readLong();
        this.st_size = in.readLong();
        this.st_atime = in.readLong();
        this.st_mtime = in.readLong();
        this.st_ctime = in.readLong();
        this.st_blksize = in.readLong();
        this.st_blocks = in.readLong();
    }

    public static final Creator<FileStatus> CREATOR = new Creator<FileStatus>() {
        @NonNull
        @Override
        public FileStatus createFromParcel(Parcel in) {
            return new FileStatus(in);
        }

        @NonNull
        @Override
        public FileStatus[] newArray(int size) {
            return new FileStatus[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(this.st_dev);
        dest.writeLong(this.st_ino);
        dest.writeInt(this.st_mode);
        dest.writeLong(this.st_nlink);
        dest.writeInt(this.st_uid);
        dest.writeInt(this.st_gid);
        dest.writeLong(this.st_rdev);
        dest.writeLong(this.st_size);
        dest.writeLong(this.st_atime);
        dest.writeLong(this.st_mtime);
        dest.writeLong(this.st_ctime);
        dest.writeLong(this.st_blksize);
        dest.writeLong(this.st_blocks);
    }
}
