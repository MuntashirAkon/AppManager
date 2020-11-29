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

package com.android.internal.util;

import androidx.annotation.NonNull;
import android.os.RemoteException;
import android.util.ExceptionUtils;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilities specific to functional programming
 */
public class FunctionalUtils {
    private FunctionalUtils() {}

    /**
     * Converts a lambda expression that throws a checked exception(s) into a regular
     * {@link Consumer} by propagating any checked exceptions as {@link RuntimeException}
     */
    public static <T> Consumer<T> uncheckExceptions(ThrowingConsumer<T> action) {
        return action;
    }

    /**
     * @see #uncheckExceptions(ThrowingConsumer)
     */
    public static <I, O> Function<I, O> uncheckExceptions(ThrowingFunction<I, O> action) {
        return action;
    }

    /**
     * @see #uncheckExceptions(ThrowingConsumer)
     */
    public static Runnable uncheckExceptions(ThrowingRunnable action) {
        return action;
    }

    /**
     * @see #uncheckExceptions(ThrowingConsumer)
     */
    public static <A, B> BiConsumer<A, B> uncheckExceptions(ThrowingBiConsumer<A, B> action) {
        return action;
    }

    /**
     * @see #uncheckExceptions(ThrowingConsumer)
     */
    public static <T> Supplier<T> uncheckExceptions(ThrowingSupplier<T> action) {
        return action;
    }

    /**
     * Wraps a given {@code action} into one that ignores any {@link RemoteException}s
     */
    public static <T> Consumer<T> ignoreRemoteException(RemoteExceptionIgnoringConsumer<T> action) {
        return action;
    }

    /**
     * Wraps the given {@link ThrowingRunnable} into one that handles any exceptions using the
     * provided {@code handler}
     */
    public static Runnable handleExceptions(ThrowingRunnable r, Consumer<Throwable> handler) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                handler.accept(t);
            }
        };
    }

    /**
     * An equivalent of {@link Runnable} that allows throwing checked exceptions
     *
     * This can be used to specify a lambda argument without forcing all the checked exceptions
     * to be handled within it
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingRunnable extends Runnable {
        void runOrThrow() throws Exception;

        @Override
        default void run() {
            try {
                runOrThrow();
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }

    /**
     * An equivalent of {@link Supplier} that allows throwing checked exceptions
     *
     * This can be used to specify a lambda argument without forcing all the checked exceptions
     * to be handled within it
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingSupplier<T> extends Supplier<T> {
        T getOrThrow() throws Exception;

        @Override
        default T get() {
            try {
                return getOrThrow();
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }
    /**
     * A {@link Consumer} that allows throwing checked exceptions from its single abstract method.
     *
     * Can be used together with {@link #uncheckExceptions} to effectively turn a lambda expression
     * that throws a checked exception into a regular {@link Consumer}
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingConsumer<T> extends Consumer<T> {
        void acceptOrThrow(T t) throws Exception;

        @Override
        default void accept(T t) {
            try {
                acceptOrThrow(t);
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }

    /**
     * A {@link Consumer} that automatically ignores any {@link RemoteException}s.
     *
     * Used by {@link #ignoreRemoteException}
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface RemoteExceptionIgnoringConsumer<T> extends Consumer<T> {
        void acceptOrThrow(T t) throws RemoteException;

        @Override
        default void accept(T t) {
            try {
                acceptOrThrow(t);
            } catch (RemoteException ex) {
                // ignore
            }
        }
    }

    /**
     * A {@link Function} that allows throwing checked exceptions from its single abstract method.
     *
     * Can be used together with {@link #uncheckExceptions} to effectively turn a lambda expression
     * that throws a checked exception into a regular {@link Function}
     *
     * @param <T> see {@link Function}
     * @param <R> see {@link Function}
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingFunction<T, R> extends Function<T, R> {
        /** @see ThrowingFunction */
        R applyOrThrow(T t) throws Exception;

        @Override
        default R apply(T t) {
            try {
                return applyOrThrow(t);
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }

    /**
     * A {@link BiFunction} that allows throwing checked exceptions from its single abstract method.
     *
     * Can be used together with {@link #uncheckExceptions} to effectively turn a lambda expression
     * that throws a checked exception into a regular {@link BiFunction}
     *
     * @param <T> see {@link BiFunction}
     * @param <U> see {@link BiFunction}
     * @param <R> see {@link BiFunction}
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
        /** @see ThrowingFunction */
        R applyOrThrow(T t, U u) throws Exception;

        @Override
        default R apply(T t, U u) {
            try {
                return applyOrThrow(t, u);
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }

    /**
     * A {@link BiConsumer} that allows throwing checked exceptions from its single abstract method.
     *
     * Can be used together with {@link #uncheckExceptions} to effectively turn a lambda expression
     * that throws a checked exception into a regular {@link Function}
     *
     * @param <A> see {@link BiConsumer}
     * @param <B> see {@link BiConsumer}
     */
    @FunctionalInterface
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    public interface ThrowingBiConsumer<A, B> extends BiConsumer<A, B> {
        /** @see ThrowingFunction */
        void acceptOrThrow(A a, B b) throws Exception;

        @Override
        default void accept(A a, B b) {
            try {
                acceptOrThrow(a, b);
            } catch (Exception ex) {
                throw ExceptionUtils.propagate(ex);
            }
        }
    }

    // TODO: add unit test
    /**
     * Gets a user-friendly name for a lambda function.
     */
    @NonNull
    public static String getLambdaName(@NonNull Object function) {
        // Full function has one of the following formats:
        //   package-$$Lambda$class$randomId
        //   package-$$Lambda$randomId
        //
        // We just want just package.class$Lambda (or package$Lambda) respectively

        final String fullFunction = function.toString();

        final int endPkgIdx = fullFunction.indexOf("-$$");
        if (endPkgIdx == -1) return fullFunction;

        // firstDollarIdx could be either beginning of class or beginning of the random id
        final int firstDollarIdx = fullFunction.indexOf('$', endPkgIdx + 3);
        if (firstDollarIdx == -1) return fullFunction;

        final int endClassIdx = fullFunction.indexOf('$', firstDollarIdx + 1);
        if (endClassIdx == -1) {
            // Just package
            return fullFunction.substring(0, endPkgIdx - 1) + "$Lambda";
        }

        // Package + class
        return fullFunction.substring(0, endPkgIdx)
                + fullFunction.substring(firstDollarIdx + 1, endClassIdx)
                + "$Lambda";
    }
}
