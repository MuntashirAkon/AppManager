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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public final class EmptyArray {
    private EmptyArray() {
    }

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
    public static final Type[] TYPE = new Type[0];
    public static final TypeVariable[] TYPE_VARIABLE = new TypeVariable[0];
}