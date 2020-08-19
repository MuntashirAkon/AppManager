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

public abstract class ClassCallerProcessor {
    private Context mPackageContext;
    private Context mSystemContext;
    private ServerRunInfo mServerRunInfo;

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
     * @param args Arguments
     * @return Resultant bundle
     * @throws Throwable On failure
     */
    public abstract Bundle proxyInvoke(Bundle args) throws Throwable;
}
