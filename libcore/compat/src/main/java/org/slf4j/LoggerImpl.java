// SPDX-License-Identifier: GPL-3.0-or-later

package org.slf4j;

import android.util.Log;

import java.util.Locale;

import io.github.muntashirakon.AppManager.compat.BuildConfig;

class LoggerImpl implements Logger {
    private final String mTag;

    protected LoggerImpl(String tag) {
        mTag = tag;
    }

    public String getName() {
        return mTag;
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public void trace(String msg) {
    }

    public void trace(String format, Object arg) {
    }

    public void trace(String format, Object arg1, Object arg2) {
    }

    public void trace(String format, Object... arguments) {
    }

    public void trace(String msg, Throwable t) {
    }

    public boolean isDebugEnabled() {
        return BuildConfig.DEBUG;
    }

    public void debug(String msg) {
        Log.d(mTag, msg);
    }

    public void debug(String format, Object arg) {
        Log.d(mTag, String.format(format, arg));
    }

    public void debug(String format, Object arg1, Object arg2) {
        Log.d(mTag, String.format(format, arg1, arg2));
    }

    public void debug(String format, Object... arguments) {
        Log.d(mTag, String.format(format, arguments));
    }

    public void debug(String msg, Throwable t) {
        Log.d(mTag, msg, t);
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public void info(String msg) {
        Log.i(mTag, msg);
    }

    public void info(String format, Object arg) {
        Log.i(mTag, String.format(format, arg));
    }

    public void info(String format, Object arg1, Object arg2) {
        Log.i(mTag, String.format(format, arg1, arg2));
    }

    public void info(String format, Object... arguments) {
        Log.i(mTag, String.format(format, arguments));
    }

    public void info(String msg, Throwable t) {
        Log.i(mTag, msg, t);
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void warn(String msg) {
        Log.w(mTag, msg);
    }

    public void warn(String format, Object arg) {
        Log.w(mTag, String.format(Locale.ROOT, format, arg));
    }

    public void warn(String format, Object... arguments) {
        Log.w(mTag, String.format(Locale.ROOT, format, arguments));
    }

    public void warn(String format, Object arg1, Object arg2) {
        Log.w(mTag, String.format(Locale.ROOT, format, arg1, arg2));
    }

    public void warn(String msg, Throwable t) {
        Log.w(mTag, msg, t);
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public void error(String msg) {
        Log.e(mTag, msg);
    }

    public void error(String format, Object arg) {
        Log.e(mTag, String.format(Locale.ROOT, format, arg));
    }

    public void error(String format, Object arg1, Object arg2) {
        Log.e(mTag, String.format(Locale.ROOT, format, arg1, arg2));
    }

    public void error(String format, Object... arguments) {
        Log.e(mTag, String.format(Locale.ROOT, format, arguments));
    }

    public void error(String msg, Throwable t) {
        Log.e(mTag, msg, t);
    }
}