// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm;

import android.os.BadParcelableException;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class ParcelUtils {
    private static final String TAG = ParcelUtils.class.getSimpleName();

    public static <T> void writeParcelableCreator(@NonNull T parcelable, @NonNull Parcel dest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dest.writeParcelableCreator((Parcelable) parcelable);
        } else {
            String name = parcelable.getClass().getName();
            dest.writeString(name);
        }
    }

    // Cache of previously looked up CREATOR.createFromParcel() methods for
    // particular classes.  Keys are the names of the classes, values are
    // Method objects.
    private static final HashMap<ClassLoader, HashMap<String, Parcelable.Creator<?>>> mCreators = new HashMap<>();

    @Nullable
    public static Parcelable.Creator<?> readParcelableCreator(@NonNull Parcel from, @Nullable ClassLoader loader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return from.readParcelableCreator(loader);
        }
        String name = from.readString();
        if (name == null) {
            return null;
        }
        Parcelable.Creator<?> creator;
        synchronized (mCreators) {
            HashMap<String, Parcelable.Creator<?>> map = mCreators.get(loader);
            if (map == null) {
                map = new HashMap<>();
                mCreators.put(loader, map);
            }
            creator = map.get(name);
            if (creator == null) {
                try {
                    // If loader == null, explicitly emulate Class.forName(String) "caller
                    // classloader" behavior.
                    ClassLoader parcelableClassLoader = (loader == null ? from.getClass().getClassLoader() : loader);
                    // Avoid initializing the Parcelable class until we know it implements
                    // Parcelable and has the necessary CREATOR field.
                    Class<?> parcelableClass = Class.forName(name, false /* initialize */,
                            parcelableClassLoader);
                    if (!Parcelable.class.isAssignableFrom(parcelableClass)) {
                        throw new BadParcelableException("Parcelable protocol requires that the "
                                + "class implements Parcelable");
                    }
                    Field f = parcelableClass.getField("CREATOR");
                    if ((f.getModifiers() & Modifier.STATIC) == 0) {
                        throw new BadParcelableException("Parcelable protocol requires "
                                + "the CREATOR object to be static on class " + name);
                    }
                    Class<?> creatorType = f.getType();
                    if (!Parcelable.Creator.class.isAssignableFrom(creatorType)) {
                        // Fail before calling Field.get(), not after, to avoid initializing
                        // parcelableClass unnecessarily.
                        throw new BadParcelableException("Parcelable protocol requires a "
                                + "Parcelable.Creator object called "
                                + "CREATOR on class " + name);
                    }
                    creator = (Parcelable.Creator<?>) f.get(null);
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Illegal access when unmarshalling: " + name, e);
                    throw new BadParcelableException(
                            "IllegalAccessException when unmarshalling: " + name);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "Class not found when unmarshalling: " + name, e);
                    throw new BadParcelableException("ClassNotFoundException when unmarshalling: " + name);
                } catch (NoSuchFieldException e) {
                    throw new BadParcelableException("Parcelable protocol requires a "
                            + "Parcelable.Creator object called "
                            + "CREATOR on class " + name);
                }
                if (creator == null) {
                    throw new BadParcelableException("Parcelable protocol requires a "
                            + "non-null Parcelable.Creator object called "
                            + "CREATOR on class " + name);
                }
                map.put(name, creator);
            }
        }

        return creator;
    }
}
