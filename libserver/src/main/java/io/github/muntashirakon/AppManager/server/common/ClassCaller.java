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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

import androidx.annotation.NonNull;

import static io.github.muntashirakon.AppManager.server.common.BaseCaller.TYPE_CLASS;

public class ClassCaller extends Caller {
    @SuppressWarnings("rawtypes")
    private static final Class[] paramsType = new Class[]{Bundle.class};
    private final String packageName;
    private final String className;

    /**
     * @param packageName Package name
     * @param className   Class name
     * @param bundle      Arguments
     */
    public ClassCaller(String packageName, String className, Bundle bundle) {
        this.packageName = packageName;
        this.className = className;
        initParams(paramsType, new Object[]{bundle});
    }

    public ClassCaller(String packageName, String className) {
        this(packageName, className, null);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.className);
        dest.writeStringArray(this.sParamsType);
        dest.writeArray(this.params);
    }

    protected ClassCaller(@NonNull Parcel in) {
        this.packageName = in.readString();
        this.className = in.readString();
        this.sParamsType = in.createStringArray();
        this.params = in.readArray(Object[].class.getClassLoader());
    }

    public static final Parcelable.Creator<ClassCaller> CREATOR = new Parcelable.Creator<ClassCaller>() {
        @NonNull
        @Override
        public ClassCaller createFromParcel(Parcel source) {
            return new ClassCaller(source);
        }

        @NonNull
        @Override
        public ClassCaller[] newArray(int size) {
            return new ClassCaller[size];
        }
    };

    @Override
    public int getType() {
        return TYPE_CLASS;
    }


    @NonNull
    @Override
    public String toString() {
        return "ClassCaller{" +
                "packageName='" + packageName + '\'' +
                ", className='" + className + '\'' +
                ", cParamsType=" + Arrays.toString(cParamsType) +
                ", sParamsType=" + Arrays.toString(sParamsType) +
                ", params=" + Arrays.toString(params) +
                '}';
    }
}
