package com.majeur.applicationsinfo.utils;

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
}
