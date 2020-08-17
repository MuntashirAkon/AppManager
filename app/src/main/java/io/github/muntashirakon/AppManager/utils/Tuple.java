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

package io.github.muntashirakon.AppManager.utils;

import java.util.Locale;

import androidx.annotation.NonNull;

public class Tuple<T, K> {

    private T mObjectOne;
    private K mObjectTwo;

    public Tuple(T param1, K param2) {
        mObjectOne = param1;
        mObjectTwo = param2;
    }

    public T getFirst() {
        return mObjectOne;
    }

    public K getSecond() {
        return mObjectTwo;
    }

    public void setFirst(T t) {
        mObjectOne = t;
    }

    public void setSecond(K k) {
        mObjectTwo = k;
    }

    public int compareTo(@NonNull Tuple<T, K> tt) {
        int i = mObjectOne.toString().toLowerCase(Locale.ROOT).compareTo(
                tt.getFirst().toString().toLowerCase(Locale.ROOT));
        if (i == 0) return mObjectTwo.toString().toLowerCase(Locale.ROOT).compareTo(
                tt.getSecond().toString().toLowerCase(Locale.ROOT));
        else if (i<0) return -1;
        else return 1;
    }

    @NonNull
    @Override
    public String toString() {
        return "Tuple(" + mObjectOne + ", " + mObjectTwo + ')';
    }
}
