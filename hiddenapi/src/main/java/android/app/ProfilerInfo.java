// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

import misc.utils.HiddenUtil;

/**
 * System private API for passing profiler settings.
 */
public class ProfilerInfo implements Parcelable {

    private static final String TAG = "ProfilerInfo";

    /* Name of profile output file. */
    public final String profileFile;

    /* File descriptor for profile output file, can be null. */
    public ParcelFileDescriptor profileFd;

    /* Indicates sample profiling when nonzero, interval in microseconds. */
    public final int samplingInterval;

    /* Automatically stop the profiler when the app goes idle. */
    public final boolean autoStopProfiler;

    /*
     * Indicates whether to stream the profiling info to the out file continuously.
     */
    public final boolean streamingOutput;

    /**
     * Denotes an agent (and its parameters) to attach for profiling.
     */
    public final String agent;

    /**
     * Whether the {@link #agent} should be attached early (before bind-application) or during
     * bind-application. Agents attached prior to binding cannot be loaded from the app's APK
     * directly and must be given as an absolute path (or available in the default LD_LIBRARY_PATH).
     * Agents attached during bind-application will miss early setup (e.g., resource initialization
     * and classloader generation), but are searched in the app's library search path.
     */
    public final boolean attachAgentDuringBind;

    public ProfilerInfo(String filename, ParcelFileDescriptor fd, int interval, boolean autoStop,
            boolean streaming, String agent, boolean attachAgentDuringBind) {
        HiddenUtil.throwUOE(filename, fd, interval, autoStop, streaming, agent, attachAgentDuringBind);
        throw new UnsupportedOperationException();
    }

    /**
     * Return a new ProfilerInfo instance, with fields populated from this object,
     * and {@link #agent} and {@link #attachAgentDuringBind} as given.
     */
    public ProfilerInfo setAgent(String agent, boolean attachAgentDuringBind) {
        return HiddenUtil.throwUOE(agent, attachAgentDuringBind);
    }

    /**
     * Close profileFd, if it is open. The field will be null after a call to this function.
     */
    public void closeFd() {
        HiddenUtil.throwUOE();
    }

    public static final Creator<ProfilerInfo> CREATOR = HiddenUtil.creator();

    @Override
    public int describeContents() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }
}