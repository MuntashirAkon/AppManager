/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.AppManager.server.common;

// Source: https://github.com/apache/commons-beanutils/blob/master/src/main/java/org/apache/commons/beanutils2/MethodUtils.java

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;

/**
 * <p> Utility reflection methods focused on methods in general rather than properties in particular. </p>
 *
 * <h3>Known Limitations</h3>
 * <h4>Accessing Public Methods In A Default Access Superclass</h4>
 * <p>There is an issue when invoking public methods contained in a default access superclass.
 * Reflection locates these methods fine and correctly assigns them as public.
 * However, an <code>IllegalAccessException</code> is thrown if the method is invoked.</p>
 *
 * <p><code>MethodUtils</code> contains a workaround for this situation.
 * It will attempt to call <code>setAccessible</code> on this method.
 * If this call succeeds, then the method can be invoked as normal.
 * This call will only succeed when the application has sufficient security privileges.
 * If this call fails then a warning will be logged and the method may fail.</p>
 *
 * @version $Id$
 */
public class MethodUtils {

    // --------------------------------------------------------- Private Methods

    /**
     * Only log warning about accessibility work around once.
     * <p>
     * Note that this is broken when this class is deployed via a shared
     * classloader in a container, as the warning message will be emitted
     * only once, not once per webapp. However making the warning appear
     * once per webapp means having a map keyed by context classloader
     * which introduces nasty memory-leak problems. As this warning is
     * really optional we can ignore this problem; only one of the webapps
     * will get the warning in its logs but that should be good enough.
     */
    private static boolean loggedAccessibleWarning = false;

    /**
     * Indicates whether methods should be cached for improved performance.
     * <p>
     * Note that when this class is deployed via a shared classloader in
     * a container, this will affect all webapps. However making this
     * configurable per webapp would mean having a map keyed by context classloader
     * which may introduce memory-leak problems.
     */
    private static boolean CACHE_METHODS = true;

    /**
     * An empty class array
     */
    private static final Class<?>[] EMPTY_CLASS_PARAMETERS = new Class[0];
    /**
     * An empty object array
     */
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /**
     * Stores a cache of MethodDescriptor -> Method in a WeakHashMap.
     * <p>
     * The keys into this map only ever exist as temporary variables within
     * methods of this class, and are never exposed to users of this class.
     * This means that the WeakHashMap is used only as a mechanism for
     * limiting the size of the cache, ie a way to tell the garbage collector
     * that the contents of the cache can be completely garbage-collected
     * whenever it needs the memory. Whether this is a good approach to
     * this problem is doubtful; something like the commons-collections
     * LRUMap may be more appropriate (though of course selecting an
     * appropriate size is an issue).
     * <p>
     * This static variable is safe even when this code is deployed via a
     * shared classloader because it is keyed via a MethodDescriptor object
     * which has a Class as one of its members and that member is used in
     * the MethodDescriptor.equals method. So two components that load the same
     * class via different classloaders will generate non-equal MethodDescriptor
     * objects and hence end up with different entries in the map.
     */
    private static final Map<MethodDescriptor, Reference<Method>> cache = Collections
            .synchronizedMap(new WeakHashMap<MethodDescriptor, Reference<Method>>());

    // --------------------------------------------------------- Public Methods

    /**
     * Set whether methods should be cached for greater performance or not,
     * default is <code>true</code>.
     *
     * @param cacheMethods <code>true</code> if methods should be
     *                     cached for greater performance, otherwise <code>false</code>
     * @since 1.8.0
     */
    public static synchronized void setCacheMethods(final boolean cacheMethods) {
        CACHE_METHODS = cacheMethods;
        if (!CACHE_METHODS) {
            clearCache();
        }
    }

    /**
     * Clear the method cache.
     *
     * @return the number of cached methods cleared
     * @since 1.8.0
     */
    public static synchronized int clearCache() {
        final int size = cache.size();
        cache.clear();
        return size;
    }

    /**
     * <p>Invoke a named method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than <code>invokeExactMethod()</code>.
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeMethod(Object object, String methodName, Object[] args)}.
     * </p>
     *
     * @param object     invoke method on this object
     * @param methodName get method with this name
     * @param arg        use this argument. May be null (this will result in calling the
     *                   parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeMethod(
            final Object object,
            final String methodName,
            final Object arg)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        final Object[] args = toArray(arg);
        return invokeMethod(object, methodName, args);
    }


    /**
     * <p>Invoke a named method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than {@link #invokeExactMethod(Object object, String methodName, Object [] args)}.
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeMethod(Object, String, Object[], Class[])}.
     * </p>
     *
     * @param object     invoke method on this object
     * @param methodName get method with this name
     * @param args       use these arguments - treat null as empty array (passing null will
     *                   result in calling the parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeMethod(
            final Object object,
            final String methodName,
            Object[] args)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }
        final int arguments = args.length;
        final Class<?>[] parameterTypes = new Class[arguments];
        for (int i = 0; i < arguments; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        return invokeMethod(object, methodName, args, parameterTypes);
    }


    /**
     * <p>Invoke a named method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than {@link
     * #invokeExactMethod(Object, String, Object[], Class[])}.
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * @param object         invoke method on this object
     * @param methodName     get method with this name
     * @param args           use these arguments - treat null as empty array (passing null will
     *                       result in calling the parameterless method with name {@code methodName}).
     * @param parameterTypes match these parameters - treat null as empty array
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeMethod(
            final Object object,
            final String methodName,
            Object[] args,
            Class<?>[] parameterTypes)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (parameterTypes == null) {
            parameterTypes = EMPTY_CLASS_PARAMETERS;
        }
        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }

        final Method method = getMatchingAccessibleMethod(
                object.getClass(),
                methodName,
                parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: " +
                    methodName + "() on object: " + object.getClass().getName());
        }
        return method.invoke(object, args);
    }


    /**
     * <p>Invoke a method whose parameter type matches exactly the object
     * type.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeExactMethod(Object object, String methodName, Object[] args)}.
     * </p>
     *
     * @param object     invoke method on this object
     * @param methodName get method with this name
     * @param arg        use this argument. May be null (this will result in calling the
     *                   parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeExactMethod(
            final Object object,
            final String methodName,
            final Object arg)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        final Object[] args = toArray(arg);
        return invokeExactMethod(object, methodName, args);
    }


    /**
     * <p>Invoke a method whose parameter types match exactly the object
     * types.</p>
     *
     * <p> This uses reflection to invoke the method obtained from a call to
     * <code>getAccessibleMethod()</code>.</p>
     *
     * @param object     invoke method on this object
     * @param methodName get method with this name
     * @param args       use these arguments - treat null as empty array (passing null will
     *                   result in calling the parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeExactMethod(
            final Object object,
            final String methodName,
            Object[] args)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }
        final int arguments = args.length;
        final Class<?>[] parameterTypes = new Class[arguments];
        for (int i = 0; i < arguments; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        return invokeExactMethod(object, methodName, args, parameterTypes);
    }


    /**
     * <p>Invoke a method whose parameter types match exactly the parameter
     * types given.</p>
     *
     * <p>This uses reflection to invoke the method obtained from a call to
     * <code>getAccessibleMethod()</code>.</p>
     *
     * @param object         invoke method on this object
     * @param methodName     get method with this name
     * @param args           use these arguments - treat null as empty array (passing null will
     *                       result in calling the parameterless method with name {@code methodName}).
     * @param parameterTypes match these parameters - treat null as empty array
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     */
    public static Object invokeExactMethod(
            final Object object,
            final String methodName,
            Object[] args,
            Class<?>[] parameterTypes)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }

        if (parameterTypes == null) {
            parameterTypes = EMPTY_CLASS_PARAMETERS;
        }

        final Method method = getAccessibleMethod(
                object.getClass(),
                methodName,
                parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: " +
                    methodName + "() on object: " + object.getClass().getName());
        }
        return method.invoke(object, args);
    }

    /**
     * <p>Invoke a static method whose parameter types match exactly the parameter
     * types given.</p>
     *
     * <p>This uses reflection to invoke the method obtained from a call to
     * {@link #getAccessibleMethod(Class, String, Class[])}.</p>
     *
     * @param objectClass    invoke static method on this class
     * @param methodName     get method with this name
     * @param args           use these arguments - treat null as empty array (passing null will
     *                       result in calling the parameterless method with name {@code methodName}).
     * @param parameterTypes match these parameters - treat null as empty array
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeExactStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            Object[] args,
            Class<?>[] parameterTypes)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }

        if (parameterTypes == null) {
            parameterTypes = EMPTY_CLASS_PARAMETERS;
        }

        final Method method = getAccessibleMethod(
                objectClass,
                methodName,
                parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: " +
                    methodName + "() on class: " + objectClass.getName());
        }
        return method.invoke(null, args);
    }

    /**
     * <p>Invoke a named static method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than {@link #invokeExactMethod(Object, String, Object[], Class[])}.
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeStaticMethod(Class objectClass, String methodName, Object[] args)}.
     * </p>
     *
     * @param objectClass invoke static method on this class
     * @param methodName  get method with this name
     * @param arg         use this argument. May be null (this will result in calling the
     *                    parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            final Object arg)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        final Object[] args = toArray(arg);
        return invokeStaticMethod(objectClass, methodName, args);
    }


    /**
     * <p>Invoke a named static method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than {@link #invokeExactMethod(Object object, String methodName, Object[] args)}.
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeStaticMethod(Class, String, Object[], Class[])}.
     * </p>
     *
     * @param objectClass invoke static method on this class
     * @param methodName  get method with this name
     * @param args        use these arguments - treat null as empty array (passing null will
     *                    result in calling the parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            Object[] args)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }
        final int arguments = args.length;
        final Class<?>[] parameterTypes = new Class[arguments];
        for (int i = 0; i < arguments; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        return invokeStaticMethod(objectClass, methodName, args, parameterTypes);
    }


    /**
     * <p>Invoke a named static method whose parameter type matches the object type.</p>
     *
     * <p>The behaviour of this method is less deterministic
     * than {@link #invokeStaticMethod(Class, String, Object[], Class[])}
     * It loops through all methods with names that match
     * and then executes the first it finds with compatible parameters.</p>
     *
     * <p>This method supports calls to methods taking primitive parameters
     * via passing in wrapping classes. So, for example, a <code>Boolean</code> class
     * would match a <code>boolean</code> primitive.</p>
     *
     * @param objectClass    invoke static method on this class
     * @param methodName     get method with this name
     * @param args           use these arguments - treat null as empty array (passing null will
     *                       result in calling the parameterless method with name {@code methodName}).
     * @param parameterTypes match these parameters - treat null as empty array
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            Object[] args,
            Class<?>[] parameterTypes)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (parameterTypes == null) {
            parameterTypes = EMPTY_CLASS_PARAMETERS;
        }
        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }

        final Method method = getMatchingAccessibleMethod(
                objectClass,
                methodName,
                parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException("No such accessible method: " +
                    methodName + "() on class: " + objectClass.getName());
        }
        return method.invoke(null, args);
    }


    /**
     * <p>Invoke a static method whose parameter type matches exactly the object
     * type.</p>
     *
     * <p> This is a convenient wrapper for
     * {@link #invokeExactStaticMethod(Class objectClass, String methodName, Object[] args)}.
     * </p>
     *
     * @param objectClass invoke static method on this class
     * @param methodName  get method with this name
     * @param arg         use this argument. May be null (this will result in calling the
     *                    parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeExactStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            final Object arg)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        final Object[] args = toArray(arg);
        return invokeExactStaticMethod(objectClass, methodName, args);
    }

    /**
     * <p>Invoke a static method whose parameter types match exactly the object
     * types.</p>
     *
     * <p> This uses reflection to invoke the method obtained from a call to
     * {@link #getAccessibleMethod(Class, String, Class[])}.</p>
     *
     * @param objectClass invoke static method on this class
     * @param methodName  get method with this name
     * @param args        use these arguments - treat null as empty array (passing null will
     *                    result in calling the parameterless method with name {@code methodName}).
     * @return The value returned by the invoked method
     * @throws NoSuchMethodException     if there is no such accessible method
     * @throws InvocationTargetException wraps an exception thrown by the
     *                                   method invoked
     * @throws IllegalAccessException    if the requested method is not accessible
     *                                   via reflection
     * @since 1.8.0
     */
    public static Object invokeExactStaticMethod(
            final Class<?> objectClass,
            final String methodName,
            Object[] args)
            throws
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException {

        if (args == null) {
            args = EMPTY_OBJECT_ARRAY;
        }
        final int arguments = args.length;
        final Class<?>[] parameterTypes = new Class[arguments];
        for (int i = 0; i < arguments; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        return invokeExactStaticMethod(objectClass, methodName, args, parameterTypes);
    }


    private static Object[] toArray(final Object arg) {
        Object[] args = null;
        if (arg != null) {
            args = new Object[]{arg};
        }
        return args;
    }

    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) with given name and a single parameter.  If no such method
     * can be found, return <code>null</code>.
     * Basically, a convenience wrapper that constructs a <code>Class</code>
     * array for you.</p>
     *
     * @param clazz         get method from this class
     * @param methodName    get method with this name
     * @param parameterType taking this type of parameter
     * @return The accessible method
     */
    public static Method getAccessibleMethod(
            final Class<?> clazz,
            final String methodName,
            final Class<?> parameterType) {

        final Class<?>[] parameterTypes = {parameterType};
        return getAccessibleMethod(clazz, methodName, parameterTypes);
    }


    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) with given name and parameters.  If no such method
     * can be found, return <code>null</code>.
     * This is just a convenient wrapper for
     * {@link #getAccessibleMethod(Method method)}.</p>
     *
     * @param clazz          get method from this class
     * @param methodName     get method with this name
     * @param parameterTypes with these parameters types
     * @return The accessible method
     */
    public static Method getAccessibleMethod(
            final Class<?> clazz,
            final String methodName,
            final Class<?>[] parameterTypes) {

        try {
            final MethodDescriptor md = new MethodDescriptor(clazz, methodName, parameterTypes, true);
            // Check the cache first
            Method method = getCachedMethod(md);
            if (method != null) {
                return method;
            }

            method = getAccessibleMethod
                    (clazz, clazz.getMethod(methodName, parameterTypes));
            cacheMethod(md, method);
            return method;
        } catch (final NoSuchMethodException e) {
            return (null);
        }
    }

    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) that implements the specified Method.  If no such method
     * can be found, return <code>null</code>.</p>
     *
     * @param method The method that we wish to call
     * @return The accessible method
     */
    public static Method getAccessibleMethod(final Method method) {

        // Make sure we have a method to check
        if (method == null) {
            return (null);
        }

        return getAccessibleMethod(method.getDeclaringClass(), method);
    }


    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) that implements the specified Method.  If no such method
     * can be found, return <code>null</code>.</p>
     *
     * @param clazz  The class of the object
     * @param method The method that we wish to call
     * @return The accessible method
     * @since 1.8.0
     */
    public static Method getAccessibleMethod(Class<?> clazz, Method method) {

        // Make sure we have a method to check
        if (method == null) {
            return (null);
        }

        // If the requested method is not public we cannot call it
        if (!Modifier.isPublic(method.getModifiers())) {
            return (null);
        }

        boolean sameClass = true;
        if (clazz == null) {
            clazz = method.getDeclaringClass();
        } else {
            sameClass = clazz.equals(method.getDeclaringClass());
            if (!method.getDeclaringClass().isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(clazz.getName() +
                        " is not assignable from " + method.getDeclaringClass().getName());
            }
        }

        // If the class is public, we are done
        if (Modifier.isPublic(clazz.getModifiers())) {
            if (!sameClass && !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                setMethodAccessible(method); // Default access superclass workaround
            }
            return (method);
        }

        final String methodName = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();

        // Check the implemented interfaces and subinterfaces
        method =
                getAccessibleMethodFromInterfaceNest(clazz,
                        methodName,
                        parameterTypes);

        // Check the superclass chain
        if (method == null) {
            method = getAccessibleMethodFromSuperclass(clazz,
                    methodName,
                    parameterTypes);
        }

        return (method);
    }


    // -------------------------------------------------------- Private Methods

    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) by scanning through the superclasses. If no such method
     * can be found, return <code>null</code>.</p>
     *
     * @param clazz          Class to be checked
     * @param methodName     Method name of the method we wish to call
     * @param parameterTypes The parameter type signatures
     */
    private static Method getAccessibleMethodFromSuperclass
    (final Class<?> clazz, final String methodName, final Class<?>[] parameterTypes) {

        Class<?> parentClazz = clazz.getSuperclass();
        while (parentClazz != null) {
            if (Modifier.isPublic(parentClazz.getModifiers())) {
                try {
                    return parentClazz.getMethod(methodName, parameterTypes);
                } catch (final NoSuchMethodException e) {
                    return null;
                }
            }
            parentClazz = parentClazz.getSuperclass();
        }
        return null;
    }

    /**
     * <p>Return an accessible method (that is, one that can be invoked via
     * reflection) that implements the specified method, by scanning through
     * all implemented interfaces and subinterfaces.  If no such method
     * can be found, return <code>null</code>.</p>
     *
     * <p> There isn't any good reason why this method must be private.
     * It is because there doesn't seem any reason why other classes should
     * call this rather than the higher level methods.</p>
     *
     * @param clazz          Parent class for the interfaces to be checked
     * @param methodName     Method name of the method we wish to call
     * @param parameterTypes The parameter type signatures
     */
    private static Method getAccessibleMethodFromInterfaceNest
    (Class<?> clazz, final String methodName, final Class<?>[] parameterTypes) {

        Method method = null;

        // Search up the superclass chain
        for (; clazz != null; clazz = clazz.getSuperclass()) {

            // Check the implemented interfaces of the parent class
            final Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> anInterface : interfaces) {

                // Is this interface public?
                if (!Modifier.isPublic(anInterface.getModifiers())) {
                    continue;
                }

                // Does the method exist on this interface?
                try {
                    method = anInterface.getDeclaredMethod(methodName,
                            parameterTypes);
                } catch (final NoSuchMethodException e) {
                    /* Swallow, if no method is found after the loop then this
                     * method returns null.
                     */
                }
                if (method != null) {
                    return method;
                }

                // Recursively check our parent interfaces
                method =
                        getAccessibleMethodFromInterfaceNest(anInterface,
                                methodName,
                                parameterTypes);
                if (method != null) {
                    return method;
                }

            }

        }

        // We did not find anything
        return (null);
    }

    /**
     * <p>Find an accessible method that matches the given name and has compatible parameters.
     * Compatible parameters mean that every method parameter is assignable from
     * the given parameters.
     * In other words, it finds a method with the given name
     * that will take the parameters given.</p>
     *
     * <p>This method is slightly undeterministic since it loops
     * through methods names and return the first matching method.</p>
     *
     * <p>This method is used by
     * {@link
     * #invokeMethod(Object, String, Object[], Class[])}
     *
     * <p>This method can match primitive parameter by passing in wrapper classes.
     * For example, a <code>Boolean</code> will match a primitive <code>boolean</code>
     * parameter.
     *
     * @param clazz          find method in this class
     * @param methodName     find method with this name
     * @param parameterTypes find method with compatible parameters
     * @return The accessible method
     */
    public static Method getMatchingAccessibleMethod(
            final Class<?> clazz,
            final String methodName,
            final Class<?>[] parameterTypes) {

        final MethodDescriptor md = new MethodDescriptor(clazz, methodName, parameterTypes, false);

        // see if we can find the method directly
        // most of the time this works and it's much faster
        try {
            // Check the cache first
            Method method = getCachedMethod(md);
            if (method != null) {
                return method;
            }

            method = clazz.getMethod(methodName, parameterTypes);

            setMethodAccessible(method); // Default access superclass workaround

            cacheMethod(md, method);
            return method;

        } catch (final NoSuchMethodException e) { /* SWALLOW */ }

        // search through all methods
        final int paramSize = parameterTypes.length;
        Method bestMatch = null;
        final Method[] methods = clazz.getMethods();
        float bestMatchCost = Float.MAX_VALUE;
        float myCost;
        for (Method method2 : methods) {
            if (method2.getName().equals(methodName)) {


                // compare parameters
                final Class<?>[] methodsParams = method2.getParameterTypes();
                final int methodParamSize = methodsParams.length;
                if (methodParamSize == paramSize) {
                    boolean match = true;
                    for (int n = 0; n < methodParamSize; n++) {

                        if (!isAssignmentCompatible(methodsParams[n], parameterTypes[n])) {

                            match = false;
                            break;
                        }
                    }

                    if (match) {
                        // get accessible version of method
                        final Method method = getAccessibleMethod(clazz, method2);
                        if (method != null) {

                            setMethodAccessible(method); // Default access superclass workaround
                            myCost = getTotalTransformationCost(parameterTypes, method.getParameterTypes());
                            if (myCost < bestMatchCost) {
                                bestMatch = method;
                                bestMatchCost = myCost;
                            }
                        }

                    }
                }
            }
        }
        if (bestMatch != null) {
            cacheMethod(md, bestMatch);
        } // else didn't find a match

        return bestMatch;
    }

    /**
     * Try to make the method accessible
     *
     * @param method The source arguments
     */
    private static void setMethodAccessible(final Method method) {
        try {
            //
            // XXX Default access superclass workaround
            //
            // When a public class has a default access superclass
            // with public methods, these methods are accessible.
            // Calling them from compiled code works fine.
            //
            // Unfortunately, using reflection to invoke these methods
            // seems to (wrongly) to prevent access even when the method
            // modifer is public.
            //
            // The following workaround solves the problem but will only
            // work from sufficiently privilages code.
            //
            // Better workarounds would be greatfully accepted.
            //
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }

        } catch (final SecurityException se) {
            // log but continue just in case the method.invoke works anyway

            if (!loggedAccessibleWarning) {
//                boolean vulnerableJVM = false;
//                try {
//                    final String specVersion = System.getProperty("java.specification.version");
//                    if (specVersion.charAt(0) == '1' &&
//                            (specVersion.charAt(2) == '0' ||
//                                    specVersion.charAt(2) == '1' ||
//                                    specVersion.charAt(2) == '2' ||
//                                    specVersion.charAt(2) == '3')) {
//
//                        vulnerableJVM = true;
//                    }
//                } catch (final SecurityException e) {
//                    // don't know - so display warning
//                    vulnerableJVM = true;
//                }
//                if (vulnerableJVM) {}
                loggedAccessibleWarning = true;
            }

        }
    }

    /**
     * Returns the sum of the object transformation cost for each class in the source
     * argument list.
     *
     * @param srcArgs  The source arguments
     * @param destArgs The destination arguments
     * @return The total transformation cost
     */
    private static float getTotalTransformationCost(@NonNull final Class<?>[] srcArgs, final Class<?>[] destArgs) {

        float totalCost = 0.0f;
        for (int i = 0; i < srcArgs.length; i++) {
            Class<?> srcClass, destClass;
            srcClass = srcArgs[i];
            destClass = destArgs[i];
            totalCost += getObjectTransformationCost(srcClass, destClass);
        }

        return totalCost;
    }

    /**
     * Gets the number of steps required needed to turn the source class into the
     * destination class. This represents the number of steps in the object hierarchy
     * graph.
     *
     * @param srcClass  The source class
     * @param destClass The destination class
     * @return The cost of transforming an object
     */
    private static float getObjectTransformationCost(Class<?> srcClass, final Class<?> destClass) {
        float cost = 0.0f;
        while (srcClass != null && !destClass.equals(srcClass)) {
            if (destClass.isPrimitive()) {
                final Class<?> destClassWrapperClazz = getPrimitiveWrapper(destClass);
                if (destClassWrapperClazz != null && destClassWrapperClazz.equals(srcClass)) {
                    cost += 0.25f;
                    break;
                }
            }
            if (destClass.isInterface() && isAssignmentCompatible(destClass, srcClass)) {
                // slight penalty for interface match.
                // we still want an exact match to override an interface match, but
                // an interface match should override anything where we have to get a
                // superclass.
                cost += 0.25f;
                break;
            }
            cost++;
            srcClass = srcClass.getSuperclass();
        }

        /*
         * If the destination class is null, we've travelled all the way up to
         * an Object match. We'll penalize this by adding 1.5 to the cost.
         */
        if (srcClass == null) {
            cost += 1.5f;
        }

        return cost;
    }


    /**
     * <p>Determine whether a type can be used as a parameter in a method invocation.
     * This method handles primitive conversions correctly.</p>
     *
     * <p>In order words, it will match a <code>Boolean</code> to a <code>boolean</code>,
     * a <code>Long</code> to a <code>long</code>,
     * a <code>Float</code> to a <code>float</code>,
     * a <code>Integer</code> to a <code>int</code>,
     * and a <code>Double</code> to a <code>double</code>.
     * Now logic widening matches are allowed.
     * For example, a <code>Long</code> will not match a <code>int</code>.
     *
     * @param parameterType    the type of parameter accepted by the method
     * @param parameterization the type of parameter being tested
     * @return true if the assignment is compatible.
     */
    public static boolean isAssignmentCompatible(@NonNull final Class<?> parameterType, final Class<?> parameterization) {
        // try plain assignment
        if (parameterType.isAssignableFrom(parameterization)) {
            return true;
        }

        if (parameterType.isPrimitive()) {
            // this method does *not* do widening - you must specify exactly
            // is this the right behaviour?
            final Class<?> parameterWrapperClazz = getPrimitiveWrapper(parameterType);
            if (parameterWrapperClazz != null) {
                return parameterWrapperClazz.equals(parameterization);
            }
        }

        return false;
    }

    /**
     * Gets the wrapper object class for the given primitive type class.
     * For example, passing <code>boolean.class</code> returns <code>Boolean.class</code>
     *
     * @param primitiveType the primitive type class for which a match is to be found
     * @return the wrapper type associated with the given primitive
     * or null if no match is found
     */
    public static Class<?> getPrimitiveWrapper(final Class<?> primitiveType) {
        // does anyone know a better strategy than comparing names?
        if (boolean.class.equals(primitiveType)) {
            return Boolean.class;
        } else if (float.class.equals(primitiveType)) {
            return Float.class;
        } else if (long.class.equals(primitiveType)) {
            return Long.class;
        } else if (int.class.equals(primitiveType)) {
            return Integer.class;
        } else if (short.class.equals(primitiveType)) {
            return Short.class;
        } else if (byte.class.equals(primitiveType)) {
            return Byte.class;
        } else if (double.class.equals(primitiveType)) {
            return Double.class;
        } else if (char.class.equals(primitiveType)) {
            return Character.class;
        } else {

            return null;
        }
    }

    /**
     * Gets the class for the primitive type corresponding to the primitive wrapper class given.
     * For example, an instance of <code>Boolean.class</code> returns a <code>boolean.class</code>.
     *
     * @param wrapperType the
     * @return the primitive type class corresponding to the given wrapper class,
     * null if no match is found
     */
    public static Class<?> getPrimitiveType(final Class<?> wrapperType) {
        // does anyone know a better strategy than comparing names?
        if (Boolean.class.equals(wrapperType)) {
            return boolean.class;
        } else if (Float.class.equals(wrapperType)) {
            return float.class;
        } else if (Long.class.equals(wrapperType)) {
            return long.class;
        } else if (Integer.class.equals(wrapperType)) {
            return int.class;
        } else if (Short.class.equals(wrapperType)) {
            return short.class;
        } else if (Byte.class.equals(wrapperType)) {
            return byte.class;
        } else if (Double.class.equals(wrapperType)) {
            return double.class;
        } else if (Character.class.equals(wrapperType)) {
            return char.class;
        } else {

            return null;
        }
    }

    /**
     * Find a non primitive representation for given primitive class.
     *
     * @param clazz the class to find a representation for, not null
     * @return the original class if it not a primitive. Otherwise the wrapper class. Not null
     */
    public static Class<?> toNonPrimitiveClass(final Class<?> clazz) {
        if (clazz.isPrimitive()) {
            final Class<?> primitiveClazz = MethodUtils.getPrimitiveWrapper(clazz);
            // the above method returns
            if (primitiveClazz != null) {
                return primitiveClazz;
            } else {
                return clazz;
            }
        } else {
            return clazz;
        }
    }


    /**
     * Return the method from the cache, if present.
     *
     * @param md The method descriptor
     * @return The cached method
     */
    private static Method getCachedMethod(final MethodDescriptor md) {
        if (CACHE_METHODS) {
            final Reference<Method> methodRef = cache.get(md);
            if (methodRef != null) {
                return methodRef.get();
            }
        }
        return null;
    }

    /**
     * Add a method to the cache.
     *
     * @param md     The method descriptor
     * @param method The method to cache
     */
    private static void cacheMethod(final MethodDescriptor md, final Method method) {
        if (CACHE_METHODS) {
            if (method != null) {
                cache.put(md, new WeakReference<>(method));
            }
        }
    }

    /**
     * Represents the key to looking up a Method by reflection.
     */
    private static class MethodDescriptor {
        private final Class<?> cls;
        private final String methodName;
        private final Class<?>[] paramTypes;
        private final boolean exact;
        private final int hashCode;

        /**
         * The sole constructor.
         *
         * @param cls        the class to reflect, must not be null
         * @param methodName the method name to obtain
         * @param paramTypes the array of classes representing the parameter types
         * @param exact      whether the match has to be exact.
         */
        public MethodDescriptor(final Class<?> cls, final String methodName, Class<?>[] paramTypes, final boolean exact) {
            if (cls == null) {
                throw new IllegalArgumentException("Class cannot be null");
            }
            if (methodName == null) {
                throw new IllegalArgumentException("Method Name cannot be null");
            }
            if (paramTypes == null) {
                paramTypes = EMPTY_CLASS_PARAMETERS;
            }

            this.cls = cls;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
            this.exact = exact;

            this.hashCode = methodName.length();
        }

        /**
         * Checks for equality.
         *
         * @param obj object to be tested for equality
         * @return true, if the object describes the same Method.
         */
        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof MethodDescriptor)) {
                return false;
            }
            final MethodDescriptor md = (MethodDescriptor) obj;

            return (
                    exact == md.exact &&
                            methodName.equals(md.methodName) &&
                            cls.equals(md.cls) &&
                            java.util.Arrays.equals(paramTypes, md.paramTypes)
            );
        }

        /**
         * Returns the string length of method name. I.e. if the
         * hashcodes are different, the objects are different. If the
         * hashcodes are the same, need to use the equals method to
         * determine equality.
         *
         * @return the string length of method name.
         */
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}