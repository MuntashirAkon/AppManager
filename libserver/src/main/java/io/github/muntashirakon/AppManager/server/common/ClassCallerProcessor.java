/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.server.common;

import android.content.Context;
import android.os.Bundle;

/**
 * Process class calls using reflection. You can extend this class to create new class handlers. All
 * you have to do is override the {@link #proxyInvoke(Bundle args)} method. Since the inherited
 * classes run in privileged environment, any system API calls are acceptable.
 *
 * <h3>How it works</h3>
 * The <code>args</code> parameter in the {@link #proxyInvoke(Bundle args)} can be used to send any
 * arguments in the class. This parameter is identical to the third parameter in
 * {@link ClassCaller#ClassCaller(String, String, Bundle)} and the associated class must be called
 * via this method (where the second parameter is the class name in string).
 */
public abstract class ClassCallerProcessor {
    private final Context mPackageContext;
    private final Context mSystemContext;
    private final ServerRunInfo mServerRunInfo;

    public ClassCallerProcessor(Context mPackageContext, Context mSystemContext, byte[] bytes) {
        this.mPackageContext = mPackageContext;
        this.mSystemContext = mSystemContext;
        this.mServerRunInfo = ParcelableUtil.unmarshall(bytes, ServerRunInfo.CREATOR);
    }

    protected ServerRunInfo getServerRunInfo() {
        return mServerRunInfo;
    }

    protected Context getSystemContext() {
        return mSystemContext;
    }

    /**
     * get current package context
     *
     * @return Package context
     */
    protected Context getPackageContext() {
        return mPackageContext;
    }

    /**
     * Processes a method invocation on a proxy instance and returns the result. This method will be
     * invoked on an invocation handler when a method is invoked on a proxy instance that it is
     * associated with.
     * <p>
     *     <strong>Note:</strong> This method should only be invoked on root process.
     * </p>
     *
     * Source: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/InvocationHandler.html
     *
     * @param args Arguments
     * @return Resultant bundle
     * @throws Throwable On failure
     */
    public abstract Bundle proxyInvoke(Bundle args) throws Throwable;
}
