// SPDX-License-Identifier: Apache-2.0

package aosp.libcore.util;

// Copyright 2006 The Android Open Source Project
public final class EmptyArray {
    private EmptyArray() {}

    public static final boolean[] BOOLEAN = new boolean[0];
    public static final byte[] BYTE = new byte[0];
    public static final char[] CHAR = new char[0];
    public static final double[] DOUBLE = new double[0];
    public static final float[] FLOAT = new float[0];
    public static final int[] INT = new int[0];
    public static final long[] LONG = new long[0];

    public static final Class<?>[] CLASS = new Class[0];
    public static final Object[] OBJECT = new Object[0];
    public static final String[] STRING = new String[0];
    public static final Throwable[] THROWABLE = new Throwable[0];
    public static final StackTraceElement[] STACK_TRACE_ELEMENT = new StackTraceElement[0];
    public static final java.lang.reflect.Type[] TYPE = new java.lang.reflect.Type[0];
    @SuppressWarnings("rawtypes")
    public static final java.lang.reflect.TypeVariable[] TYPE_VARIABLE =
            new java.lang.reflect.TypeVariable[0];
}