// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

/**
 * Special-purpose API for use with {@link IBinderHidden#shellCommand IBinder.shellCommand} for
 * performing operations back on the invoking shell.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class ShellCallback implements Parcelable {
    /**
     * Create a new ShellCallback to receive requests.
     */
    public ShellCallback() {
        HiddenUtil.throwUOE();
    }

    /**
     * Ask the shell to open a file for writing.  This will truncate the file if it
     * already exists.  It will create the file if it doesn't exist.
     *
     * @param path           Path of the file to be opened/created.
     * @param seLinuxContext Optional SELinux context that must be allowed to have
     *                       access to the file; if null, nothing is required.
     * @deprecated Replaced in Android 9 (Pie) by {@link #openFile(String, String, String)}
     */
    @Deprecated
    public ParcelFileDescriptor openOutputFile(String path, String seLinuxContext) {
        return HiddenUtil.throwUOE(path, seLinuxContext);
    }

    /**
     * @deprecated Replaced in Android 9 (Pie) by {@link #onOpenFile(String, String, String)}
     */
    @Deprecated
    public ParcelFileDescriptor onOpenOutputFile(String path, String seLinuxContext) {
        return HiddenUtil.throwUOE(path, seLinuxContext);
    }

    /**
     * Ask the shell to open a file.  If opening for writing, will truncate the file if it
     * already exists and will create the file if it doesn't exist.
     *
     * @param path           Path of the file to be opened/created.
     * @param seLinuxContext Optional SELinux context that must be allowed to have
     *                       access to the file; if null, nothing is required.
     * @param mode           Mode to open file in: "r" for input/reading an existing file,
     *                       "r+" for reading/writing an existing file, "w" for output/writing a new file (either
     *                       creating or truncating an existing one), "w+" for reading/writing a new file (either
     *                       creating or truncating an existing one).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    public ParcelFileDescriptor openFile(String path, String seLinuxContext, String mode) {
        return HiddenUtil.throwUOE(path, seLinuxContext, mode);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    public ParcelFileDescriptor onOpenFile(String path, String seLinuxContext, String mode) {
        return HiddenUtil.throwUOE(path, seLinuxContext, mode);
    }

    public static void writeToParcel(@Nullable ShellCallback callback, Parcel out) {
        HiddenUtil.throwUOE(callback, out);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(@NonNull Parcel out, int flags) {
        HiddenUtil.throwUOE(out, flags);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public IBinder getShellCallbackBinder() {
        return HiddenUtil.throwUOE();
    }

    public static final Parcelable.Creator<ShellCallback> CREATOR
            = new Parcelable.Creator<ShellCallback>() {
        public ShellCallback createFromParcel(Parcel in) {
            return HiddenUtil.throwUOE(in);
        }

        public ShellCallback[] newArray(int size) {
            return new ShellCallback[size];
        }
    };
}