/*
 * Copyright (C) 2020 Muntashir Al-Islam
 * Copyright (C) 2017 The Android Open Source Project
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

package android.os;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * Wrapper class that offers to transport typical {@link Throwable} across a
 * {@link Binder} call. This class is typically used to transport exceptions
 * that cannot be modified to add {@link Parcelable} behavior, such as
 * {@link IOException}.
 * <ul>
 * <li>The wrapped throwable must be defined as system class (that is, it must
 * be in the same {@link ClassLoader} as {@link Parcelable}).
 * <li>The wrapped throwable must support the
 * {@link Throwable#Throwable(String)} constructor.
 * <li>The receiver side must catch any thrown {@link ParcelableException} and
 * call {@link #maybeRethrow(Class)} for all expected exception types.
 * </ul>
 */
public final class ParcelableException extends RuntimeException implements Parcelable {
    public ParcelableException(Throwable t) {
        super(t);
    }

    @SuppressWarnings("unchecked")
    public <T extends Throwable> void maybeRethrow(Class<T> clazz) throws T {
        if (clazz.isAssignableFrom(getCause().getClass())) {
            throw (T) getCause();
        }
    }

    public static Throwable readFromParcel(Parcel in) {
        final String name = in.readString();
        final String msg = in.readString();
        try {
            final Class<?> clazz = Class.forName(name, true, Parcelable.class.getClassLoader());
            if (Throwable.class.isAssignableFrom(clazz)) {
                return (Throwable) clazz.getConstructor(String.class).newInstance(msg);
            }
        } catch (ReflectiveOperationException ignore) {
        }
        return new RuntimeException(name + ": " + msg);
    }

    public static void writeToParcel(Parcel out, Throwable t) {
        out.writeString(t.getClass().getName());
        out.writeString(t.getMessage());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcel(dest, getCause());
    }

    public static final @NonNull Creator<ParcelableException> CREATOR = new Creator<ParcelableException>() {
        @Override
        public ParcelableException createFromParcel(Parcel source) {
            return new ParcelableException(readFromParcel(source));
        }

        @Override
        public ParcelableException[] newArray(int size) {
            return new ParcelableException[size];
        }
    };
}
