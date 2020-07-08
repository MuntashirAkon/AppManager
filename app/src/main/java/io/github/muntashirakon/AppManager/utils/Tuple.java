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
