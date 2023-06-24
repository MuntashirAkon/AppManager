// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.system.ErrnoException;

import java.io.IOException;

// Copyright 2023 John "topjohnwu" Wu
class IOResult implements Parcelable {
    private static final String REMOTE_ERR_MSG = "Exception thrown on remote process";
    private static final ClassLoader cl = IOResult.class.getClassLoader();

    private final Object val;

    IOResult() {
        val = null;
    }

    IOResult(Object v) {
        val = v;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(val);
    }

    void checkException() throws IOException {
        if (val instanceof Throwable) {
            throw new IOException(REMOTE_ERR_MSG, (Throwable) val);
        }
    }

    void checkErrnoException() throws ErrnoException, RemoteException {
        if (val instanceof ErrnoException) {
            throw (ErrnoException) val;
        } else if (val instanceof Throwable) {
            Throwable th = (Throwable) val;
            throw (RemoteException) new RemoteException(th.getMessage()).initCause(th);
        }
    }

    @SuppressWarnings("unchecked")
    <T> T tryAndGet() throws IOException {
        checkException();
        return (T) val;
    }

    @SuppressWarnings("unchecked")
    <T> T tryAndGetErrnoException() throws ErrnoException, RemoteException {
        checkErrnoException();
        return (T) val;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private IOResult(Parcel in) {
        val = in.readValue(cl);
    }

    static final Creator<IOResult> CREATOR = new Creator<IOResult>() {
        @Override
        public IOResult createFromParcel(Parcel in) {
            return new IOResult(in);
        }

        @Override
        public IOResult[] newArray(int size) {
            return new IOResult[size];
        }
    };
}