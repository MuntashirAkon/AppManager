package com.oF2pks.applicationsinfo.utils;

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

    public int compareTo(Tuple tt) {
        int i = mObjectOne.toString().toLowerCase().compareTo(tt.getFirst().toString().toLowerCase());
        if (i==0) return mObjectTwo.toString().toLowerCase().compareTo(tt.getSecond().toString().toLowerCase());
        else if (i<0) return-1;
        else return 1;
    }
}
