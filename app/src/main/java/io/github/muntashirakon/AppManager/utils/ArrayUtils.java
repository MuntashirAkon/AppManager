// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils;

// Source: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/util/ArrayUtils.java

import android.os.Build;
import android.util.ArraySet;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import aosp.libcore.util.EmptyArray;

/**
 * ArrayUtils contains some methods that you can call to find out
 * the most efficient increments by which to grow arrays.
 */
@SuppressWarnings("unused")
public class ArrayUtils {
    private static final int CACHE_SIZE = 73;
    private static final Object[] sCache = new Object[CACHE_SIZE];

    public static final File[] EMPTY_FILE = new File[0];

    private ArrayUtils() { /* cannot be instantiated */ }

    /**
     * Throws {@link ArrayIndexOutOfBoundsException} if the range is out of bounds.
     *
     * @param len    length of the array. Must be non-negative
     * @param offset start index of the range. Must be non-negative
     * @param count  length of the range. Must be non-negative
     * @throws ArrayIndexOutOfBoundsException if the range from {@code offset} with length
     *                                        {@code count} is out of bounds of the array
     */
    public static void throwsIfOutOfBounds(int len, int offset, int count) {
        if (len < 0) {
            throw new ArrayIndexOutOfBoundsException("Negative length: " + len);
        }

        if ((offset | count) < 0 || offset > len - count) {
            throw new ArrayIndexOutOfBoundsException(
                    "length=" + len + "; regionStart=" + offset + "; regionLength=" + count);
        }
    }

    /**
     * Checks if the beginnings of two byte arrays are equal.
     *
     * @param array1 the first byte array
     * @param array2 the second byte array
     * @param length the number of bytes to check
     * @return true if they're equal, false otherwise
     */
    public static boolean equals(byte[] array1, byte[] array2, int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }

        if (array1 == array2) {
            return true;
        }
        if (array1 == null || array2 == null || array1.length < length || array2.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns an empty array of the specified type.  The intent is that
     * it will return the same empty array every time to avoid reallocation,
     * although this is not guaranteed.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] emptyArray(Class<T> kind) {
        if (kind == Object.class) {
            return (T[]) EmptyArray.OBJECT;
        }

        int bucket = (kind.hashCode() & 0x7FFFFFFF) % CACHE_SIZE;
        Object cache = sCache[bucket];

        if (cache == null || cache.getClass().getComponentType() != kind) {
            cache = Array.newInstance(kind, 0);
            sCache[bucket] = cache;

            // Log.e("cache", "new empty " + kind.getName() + " at " + bucket);
        }

        return (T[]) cache;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable Collection<?> array) {
        return array == null || array.isEmpty();
    }

    /**
     * Checks if given map is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static <T> boolean isEmpty(@Nullable T[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable long[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable byte[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable boolean[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Length of the given array or 0 if it's null.
     */
    public static int size(@Nullable Object[] array) {
        return array == null ? 0 : array.length;
    }

    /**
     * Length of the given collection or 0 if it's null.
     */
    public static int size(@Nullable Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Checks that value is present as at least one of the elements of the array.
     *
     * @param array the array to check in
     * @param value the value to check for
     * @return true if the value is present in the array
     */
    public static <T> boolean contains(@Nullable T[] array, T value) {
        return indexOf(array, value) != -1;
    }

    /**
     * Return first index of {@code value} in {@code array}, or {@code -1} if
     * not found.
     */
    public static <T> int indexOf(@Nullable T[] array, T value) {
        if (array == null) return -1;
        for (int i = 0; i < array.length; i++) {
            if (Objects.equals(array[i], value)) return i;
        }
        return -1;
    }

    /**
     * Test if all {@code check} items are contained in {@code array}.
     */
    public static <T> boolean containsAll(@Nullable T[] array, T[] check) {
        if (check == null) return true;
        for (T checkItem : check) {
            if (!contains(array, checkItem)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test if any {@code check} items are contained in {@code array}.
     */
    public static <T> boolean containsAny(@Nullable T[] array, T[] check) {
        if (check == null) return false;
        for (T checkItem : check) {
            if (contains(array, checkItem)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable int[] array, int value) {
        if (array == null) return false;
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable long[] array, long value) {
        if (array == null) return false;
        for (long element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(@Nullable char[] array, char value) {
        if (array == null) return false;
        for (char element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if all {@code check} items are contained in {@code array}.
     */
    public static <T> boolean containsAll(@Nullable char[] array, char[] check) {
        if (check == null) return true;
        for (char checkItem : check) {
            if (!contains(array, checkItem)) {
                return false;
            }
        }
        return true;
    }

    public static long total(@Nullable long[] array) {
        long total = 0;
        if (array != null) {
            for (long value : array) {
                total += value;
            }
        }
        return total;
    }

    @NonNull
    public static int[] convertToIntArray(@NonNull List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    @NonNull
    public static int[] convertToIntArray(@NonNull Set<Integer> set) {
        return convertToIntArray(new ArrayList<>(set));
    }

    @Nullable
    public static long[] convertToLongArray(@Nullable int[] intArray) {
        if (intArray == null) return null;
        long[] array = new long[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            array[i] = intArray[i];
        }
        return array;
    }

    @NonNull
    public static <T> ArrayList<CharSequence> toCharSequence(@NonNull ArrayList<T> list) {
        ArrayList<CharSequence> charSequenceList = new ArrayList<>(list.size());
        try {
            for (T item : list) charSequenceList.add((CharSequence) item);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
        return charSequenceList;
    }

    @SuppressWarnings("unchecked")
    @CheckResult
    @NonNull
    public static <T> T[] concatElements(Class<T> kind, @Nullable T[] a, @Nullable T[] b) {
        final int an = (a != null) ? a.length : 0;
        final int bn = (b != null) ? b.length : 0;
        if (an == 0 && bn == 0) {
            if (kind == String.class) {
                return (T[]) EmptyArray.STRING;
            } else if (kind == Object.class) {
                return (T[]) EmptyArray.OBJECT;
            }
        }
        final T[] res = (T[]) Array.newInstance(kind, an + bn);
        if (an > 0) System.arraycopy(a, 0, res, 0, an);
        if (bn > 0) System.arraycopy(b, 0, res, an, bn);
        return res;
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @CheckResult
    @NonNull
    public static <T> T[] appendElement(Class<T> kind, @Nullable T[] array, T element) {
        return appendElement(kind, array, element, false);
    }

    /**
     * Adds value to given array.
     */
    @SuppressWarnings("unchecked")
    @CheckResult
    @NonNull
    public static <T> T[] appendElement(Class<T> kind, @Nullable T[] array, T element,
                                        boolean allowDuplicates) {
        final T[] result;
        final int end;
        if (array != null) {
            if (!allowDuplicates && contains(array, element)) return array;
            end = array.length;
            result = (T[]) Array.newInstance(kind, end + 1);
            System.arraycopy(array, 0, result, 0, end);
        } else {
            end = 0;
            result = (T[]) Array.newInstance(kind, 1);
        }
        result[end] = element;
        return result;
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T[] removeElement(Class<T> kind, @Nullable T[] array, T element) {
        if (array != null) {
            if (!contains(array, element)) return array;
            final int length = array.length;
            for (int i = 0; i < length; i++) {
                if (Objects.equals(array[i], element)) {
                    if (length == 1) {
                        return null;
                    }
                    T[] result = (T[]) Array.newInstance(kind, length - 1);
                    System.arraycopy(array, 0, result, 0, i);
                    System.arraycopy(array, i + 1, result, i, length - i - 1);
                    return result;
                }
            }
        }
        return array;
    }

    /**
     * Adds value to given array.
     */
    @NonNull
    public static int[] appendInt(@Nullable int[] cur, int val,
                                  boolean allowDuplicates) {
        if (cur == null) {
            return new int[]{val};
        }
        final int N = cur.length;
        if (!allowDuplicates) {
            for (int value : cur) {
                if (value == val) {
                    return cur;
                }
            }
        }
        int[] ret = new int[N + 1];
        System.arraycopy(cur, 0, ret, 0, N);
        ret[N] = val;
        return ret;
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @NonNull
    public static int[] appendInt(@Nullable int[] cur, int val) {
        return appendInt(cur, val, false);
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @Nullable
    public static int[] removeInt(@Nullable int[] cur, int val) {
        if (cur == null) {
            return null;
        }
        final int N = cur.length;
        for (int i = 0; i < N; i++) {
            if (cur[i] == val) {
                int[] ret = new int[N - 1];
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i);
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1);
                }
                return ret;
            }
        }
        return cur;
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @Nullable
    public static String[] removeString(@Nullable String[] cur, String val) {
        if (cur == null) {
            return null;
        }
        final int N = cur.length;
        for (int i = 0; i < N; i++) {
            if (Objects.equals(cur[i], val)) {
                String[] ret = new String[N - 1];
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i);
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1);
                }
                return ret;
            }
        }
        return cur;
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @NonNull
    public static long[] appendLong(@Nullable long[] cur, long val,
                                    boolean allowDuplicates) {
        if (cur == null) {
            return new long[]{val};
        }
        final int N = cur.length;
        if (!allowDuplicates) {
            for (long l : cur) {
                if (l == val) {
                    return cur;
                }
            }
        }
        long[] ret = new long[N + 1];
        System.arraycopy(cur, 0, ret, 0, N);
        ret[N] = val;
        return ret;
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @NonNull
    public static long[] appendLong(@Nullable long[] cur, long val) {
        return appendLong(cur, val, false);
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @Nullable
    public static long[] removeLong(@Nullable long[] cur, long val) {
        if (cur == null) {
            return null;
        }
        final int N = cur.length;
        for (int i = 0; i < N; i++) {
            if (cur[i] == val) {
                long[] ret = new long[N - 1];
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i);
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1);
                }
                return ret;
            }
        }
        return cur;
    }

    @Nullable
    public static long[] cloneOrNull(@Nullable long[] array) {
        return (array != null) ? array.clone() : null;
    }

    /**
     * Clones an array or returns null if the array is null.
     */
    @Nullable
    public static <T> T[] cloneOrNull(@Nullable T[] array) {
        return (array != null) ? array.clone() : null;
    }

    @Nullable
    public static <T> HashSet<T> cloneOrNull(@Nullable HashSet<T> array) {
        return (array != null) ? new HashSet<>(array) : null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Nullable
    public static <T> ArraySet<T> cloneOrNull(@Nullable ArraySet<T> array) {
        return (array != null) ? new ArraySet<>(array) : null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @NonNull
    public static <T> ArraySet<T> add(@Nullable ArraySet<T> cur, T val) {
        if (cur == null) {
            cur = new ArraySet<>();
        }
        cur.add(val);
        return cur;
    }

    @Nullable
    public static <T> ArraySet<T> remove(@Nullable ArraySet<T> cur, T val) {
        if (cur == null) {
            return null;
        }
        cur.remove(val);
        if (cur.isEmpty()) {
            return null;
        } else {
            return cur;
        }
    }

    @NonNull
    public static <T> ArrayList<T> add(@Nullable ArrayList<T> cur, T val) {
        if (cur == null) {
            cur = new ArrayList<>();
        }
        cur.add(val);
        return cur;
    }

    @Nullable
    public static <T> ArrayList<T> remove(@Nullable ArrayList<T> cur, T val) {
        if (cur == null) {
            return null;
        }
        cur.remove(val);
        if (cur.isEmpty()) {
            return null;
        } else {
            return cur;
        }
    }

    public static <T> boolean contains(@Nullable Collection<T> cur, T val) {
        return cur != null && cur.contains(val);
    }

    @Nullable
    public static <T> T[] trimToSize(@Nullable T[] array, int size) {
        if (array == null || size == 0) {
            return null;
        } else if (array.length == size) {
            return array;
        } else {
            return Arrays.copyOf(array, size);
        }
    }

    /**
     * Returns true if the two ArrayLists are equal with respect to the objects they contain.
     * The objects must be in the same order and be reference equal (== not .equals()).
     */
    public static <T> boolean referenceEquals(ArrayList<T> a, ArrayList<T> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        final int sizeA = a.size();
        final int sizeB = b.size();
        if (sizeA != sizeB) {
            return false;
        }
        boolean diff = false;
        for (int i = 0; i < sizeA && !diff; i++) {
            diff |= a.get(i) != b.get(i);
        }
        return !diff;
    }

    /**
     * Removes elements that match the predicate in an efficient way that alters the order of
     * elements in the collection. This should only be used if order is not important.
     *
     * @param collection The ArrayList from which to remove elements.
     * @param predicate  The predicate that each element is tested against.
     * @return the number of elements removed.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static <T> int unstableRemoveIf(@Nullable ArrayList<T> collection,
                                           @NonNull Predicate<T> predicate) {
        if (collection == null) {
            return 0;
        }

        final int size = collection.size();
        int leftIdx = 0;
        int rightIdx = size - 1;
        while (leftIdx <= rightIdx) {
            // Find the next element to remove moving left to right.
            while (leftIdx < size && !predicate.test(collection.get(leftIdx))) {
                leftIdx++;
            }

            // Find the next element to keep moving right to left.
            while (rightIdx > leftIdx && predicate.test(collection.get(rightIdx))) {
                rightIdx--;
            }

            if (leftIdx >= rightIdx) {
                // Done.
                break;
            }

            Collections.swap(collection, leftIdx, rightIdx);
            leftIdx++;
            rightIdx--;
        }

        // leftIdx is now at the end.
        if (size > leftIdx) {
            collection.subList(leftIdx, size).clear();
        }
        return size - leftIdx;
    }

    @NonNull
    public static int[] defeatNullable(@Nullable int[] val) {
        return (val != null) ? val : EmptyArray.INT;
    }

    @NonNull
    public static String[] defeatNullable(@Nullable String[] val) {
        return (val != null) ? val : EmptyArray.STRING;
    }

    @NonNull
    public static File[] defeatNullable(@Nullable File[] val) {
        return (val != null) ? val : EMPTY_FILE;
    }

    @NonNull
    public static <T> T[] defeatNullable(Class<T> clazz, @Nullable T[] val) {
        return (val != null) ? val : emptyArray(clazz);
    }

    /**
     * Throws {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.
     *
     * @param len   length of the array. Must be non-negative
     * @param index the index to check
     * @throws ArrayIndexOutOfBoundsException if the {@code index} is out of bounds of the array
     */
    public static void checkBounds(int len, int index) {
        if (index < 0 || len <= index) {
            throw new ArrayIndexOutOfBoundsException("length=" + len + "; index=" + index);
        }
    }

    /**
     * Returns an array with values from {@code val} minus {@code null} values
     *
     * @param arrayConstructor typically {@code T[]::new} e.g. {@code String[]::new}
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static <T> T[] filterNotNull(T[] val, IntFunction<T[]> arrayConstructor) {
        int nullCount = 0;
        int size = size(val);
        for (int i = 0; i < size; i++) {
            if (val[i] == null) {
                nullCount++;
            }
        }
        if (nullCount == 0) {
            return val;
        }
        T[] result = arrayConstructor.apply(size - nullCount);
        int outIdx = 0;
        for (int i = 0; i < size; i++) {
            if (val[i] != null) {
                result[outIdx++] = val[i];
            }
        }
        return result;
    }

    public static boolean startsWith(byte[] cur, byte[] val) {
        if (cur == null || val == null) return false;
        if (cur.length < val.length) return false;
        for (int i = 0; i < val.length; i++) {
            if (cur[i] != val[i]) return false;
        }
        return true;
    }

    /**
     * Returns the first element from the array for which
     * condition {@code predicate} is true, or null if there is no such element
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Nullable
    public static <T> T find(@Nullable T[] items, @NonNull Predicate<T> predicate) {
        if (isEmpty(items)) return null;
        for (final T item : items) {
            if (predicate.test(item)) return item;
        }
        return null;
    }

    public static String deepToString(Object value) {
        if (value != null && value.getClass().isArray()) {
            if (value.getClass() == boolean[].class) {
                return Arrays.toString((boolean[]) value);
            } else if (value.getClass() == byte[].class) {
                return Arrays.toString((byte[]) value);
            } else if (value.getClass() == char[].class) {
                return Arrays.toString((char[]) value);
            } else if (value.getClass() == double[].class) {
                return Arrays.toString((double[]) value);
            } else if (value.getClass() == float[].class) {
                return Arrays.toString((float[]) value);
            } else if (value.getClass() == int[].class) {
                return Arrays.toString((int[]) value);
            } else if (value.getClass() == long[].class) {
                return Arrays.toString((long[]) value);
            } else if (value.getClass() == short[].class) {
                return Arrays.toString((short[]) value);
            } else {
                return Arrays.deepToString((Object[]) value);
            }
        } else {
            return String.valueOf(value);
        }
    }

    @Nullable
    public static <T> T firstOrNull(@NonNull T[] items) {
        return items.length > 0 ? items[0] : null;
    }
}