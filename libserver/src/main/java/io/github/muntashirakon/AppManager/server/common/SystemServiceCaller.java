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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import static io.github.muntashirakon.AppManager.server.common.BaseCaller.TYPE_SYSTEM_SERVICE;

public class SystemServiceCaller extends Caller {
    private final String serviceName;
    private final String methodName;

    /**
     * Call a method from system service with parameters. The result is stored at
     * {@link CallerResult}.
     *
     * @param serviceName System service name, such as {@link android.content.Context#APP_OPS_SERVICE}
     * @param methodName  Method name from service aidl
     * @param paramsType  Parameter types
     * @param params      Parameter values
     */
    @SuppressWarnings("rawtypes")
    public SystemServiceCaller(String serviceName, String methodName, Class[] paramsType, Object[] params) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        initParams(paramsType, params);
    }


    /**
     * Call a method method from system service without parameters. The result is stored at
     * {@link CallerResult}.
     *
     * @param serviceName SystemService name, Like {@link android.content.Context#APP_OPS_SERVICE}
     * @param methodName  Method name from service aidl
     */
    public SystemServiceCaller(String serviceName, String methodName) {
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.serviceName);
        dest.writeString(this.methodName);
        dest.writeStringArray(this.sParamsType);
        dest.writeArray(this.params);
    }

    protected SystemServiceCaller(@NonNull Parcel in) {
        this.serviceName = in.readString();
        this.methodName = in.readString();
        this.sParamsType = in.createStringArray();
        this.params = in.readArray(Object[].class.getClassLoader());
    }

    public static final Parcelable.Creator<SystemServiceCaller> CREATOR = new Parcelable.Creator<SystemServiceCaller>() {
        @NonNull
        @Override
        public SystemServiceCaller createFromParcel(Parcel source) {
            return new SystemServiceCaller(source);
        }

        @NonNull
        @Override
        public SystemServiceCaller[] newArray(int size) {
            return new SystemServiceCaller[size];
        }
    };

    @Override
    public int getType() {
        return TYPE_SYSTEM_SERVICE;
    }
}
