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

package io.github.muntashirakon.AppManager.server;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.ParceledListSlice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.ClassCallerProcessor;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.MethodUtils;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.server.common.SystemServiceCaller;

import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_DEBUG;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_PATH;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_RUN_IN_BACKGROUND;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TOKEN;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE_ROOT;

@SuppressWarnings("rawtypes")
class ServerHandler implements DataTransmission.OnReceiveCallback, AutoCloseable {
    private static final int MSG_TIMEOUT = 1;
    private static final int DEFAULT_TIMEOUT = 1000 * 60; // 1 min
    private static final int BG_TIMEOUT = DEFAULT_TIMEOUT * 10; // 10 min

    private Server server;
    private Handler handler;
    private volatile boolean isDead = false;
    private int timeout = DEFAULT_TIMEOUT;
    private volatile boolean runInBackground;

    ServerHandler(@NonNull Map<String, String> configParams) throws IOException {
        // Set params
        System.out.println("Config params: " + configParams);
        boolean isRoot = TextUtils.equals(configParams.get(PARAM_TYPE), PARAM_TYPE_ROOT);
        String path = configParams.get(PARAM_PATH);
        int port = -1;
        try {
            if (path != null) port = Integer.parseInt(path);
        } catch (Exception ignore) {
        }
        String token = configParams.get(PARAM_TOKEN);
        runInBackground = TextUtils.equals(configParams.get(PARAM_RUN_IN_BACKGROUND), "1");
        boolean debug = TextUtils.equals(configParams.get(PARAM_DEBUG), "1");
        // Set server
        if (port == -1) {
            server = new Server(path, token, this);
        } else {
            server = new Server(port, token, this);
        }
        server.runInBackground = runInBackground;
        // If run in background not requested, stop server on time out
        if (!runInBackground) {
            HandlerThread handlerThread = new HandlerThread("watcher-ups");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper()) {
                @Override
                public void handleMessage(@NonNull Message message) {
                    super.handleMessage(message);
                    if (message.what == MSG_TIMEOUT) {
                        close();
                    }
                }
            };
            handler.sendEmptyMessageDelayed(MSG_TIMEOUT, timeout);
        }
    }

    void start() throws Exception {
        server.run();
    }

    @Override
    public void close() {
        FLog.log("ServerHandler: Destroying...");
        try {
            if (!runInBackground && handler != null) {
                handler.removeCallbacksAndMessages(null);
                handler.removeMessages(MSG_TIMEOUT);
                handler.getLooper().quit();
            }
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
        try {
            isDead = true;
            server.setStop();
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
    }

    private void sendOpResult(Parcelable result) {
        try {
            server.sendResult(ParcelableUtil.marshall(result));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler.removeMessages(MSG_TIMEOUT);
        }

        if (!isDead) {
            if (!runInBackground && handler != null) {
                handler.sendEmptyMessageDelayed(MSG_TIMEOUT, BG_TIMEOUT);
            }
            LifecycleAgent.serverRunInfo.rxBytes += bytes.length;
            CallerResult result = null;
            try {
                BaseCaller baseCaller = ParcelableUtil.unmarshall(bytes, BaseCaller.CREATOR);
                int type = baseCaller.getType();
                switch (type) {
                    case BaseCaller.TYPE_CLOSE:
                        close();
                        return;
                    case BaseCaller.TYPE_SYSTEM_SERVICE:
                        SystemServiceCaller callerMethod = ParcelableUtil.unmarshall(baseCaller.getRawBytes(), SystemServiceCaller.CREATOR);
                        callerMethod.unwrapParams();
                        result = callServiceMethod(callerMethod);
                        break;
                    case BaseCaller.TYPE_CLASS:
                        ClassCaller callerMethod1 = ParcelableUtil.unmarshall(baseCaller.getRawBytes(), ClassCaller.CREATOR);
                        callerMethod1.unwrapParams();
                        result = callClass(callerMethod1);
                        break;
                }
                LifecycleAgent.serverRunInfo.successCount++;
            } catch (Throwable e) {
                FLog.log(e);
                result = new CallerResult();
                result.setThrowable(e);
                LifecycleAgent.serverRunInfo.errorCount++;
            } finally {
                if (result == null) {
                    result = new CallerResult();
                }
                sendOpResult(result);
            }
        }
    }

    private static class FindValue {
        private Object receiver;
        private Method method;

        void put(Object receiver, Method method) {
            this.method = method;
            this.receiver = receiver;
        }

        boolean founded() {
            return method != null && receiver != null;
        }

        void recycle() {
            receiver = null;
            method = null;
        }
    }

    private static final FindValue sFindValue = new FindValue();

    private void findFromService(SystemServiceCaller caller) {
        try {
            IBinder service = ServiceManager.getService(caller.getServiceName());
            if (service == null)
                throw new RuntimeException("Service " + caller.getServiceName() + " doesn't exist.");
            String aidl = service.getInterfaceDescriptor();
            Class clazz = sClassCache.get(aidl);
            if (clazz == null) {
                clazz = Class.forName(aidl + "$Stub", false, null);
                sClassCache.put(aidl, clazz);
            }
            Object asInterface = MethodUtils.invokeStaticMethod(clazz, "asInterface", new Object[]{service}, new Class[]{IBinder.class});
            Method method = MethodUtils.getAccessibleMethod(clazz, caller.getMethodName(), caller.getParamsType());
            if (method != null && asInterface != null) {
                sFindValue.recycle();
                sFindValue.put(asInterface, method);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            FLog.log(e);
        }
    }

    @NonNull
    private CallerResult callServiceMethod(SystemServiceCaller caller) {
        CallerResult callerResult = new CallerResult();
        try {
            sFindValue.recycle();
            findFromService(caller);
            if (sFindValue.founded()) {
                callMethod(sFindValue.receiver, sFindValue.method, caller, callerResult);
                sFindValue.recycle();
            } else {
                throw new NoSuchMethodException("not found service " + caller.getServiceName() + "  method " + caller.getMethodName() + " params: " + Arrays
                        .toString(caller.getParamsType()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
            FLog.log(e);
            if (callerResult.getThrowable() != null) {
                callerResult.setThrowable(e);
            }
        }
        return callerResult;
    }

    private void callMethod(Object obj, Method method, Caller caller, CallerResult result) {
        try {
            result.setReturnType(method.getReturnType());
            Object ret = method.invoke(obj, caller.getParams());
            writeResult(result, ret);
        } catch (Throwable e) {
            e.printStackTrace();
            FLog.log("callMethod --> " + Log.getStackTraceString(e));
            result.setThrowable(e);
        }
    }

    private static final LruCache<String, Class> sClassCache = new LruCache<>(16);
    private static final LruCache<String, Constructor> sConstructorCache = new LruCache<>(16);
    private static final LruCache<String, WeakReference<Context>> sLocalContext = new LruCache<>(16);

    @NonNull
    private CallerResult callClass(ClassCaller caller) {
        CallerResult result = new CallerResult();
        try {
            ActivityThread activityThread = ActivityThread.currentActivityThread();
            Context context = Objects.requireNonNull(activityThread).getSystemContext();
            Context packageContext = null;
            // Get context from cache or create new
            WeakReference<Context> contextWeakReference = sLocalContext.get(caller.getPackageName());
            if (contextWeakReference != null && contextWeakReference.get() != null) {
                packageContext = contextWeakReference.get();
            }
            if (packageContext == null) {
                packageContext = context.createPackageContext(caller.getPackageName(), Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                sLocalContext.put(caller.getPackageName(), new WeakReference<>(packageContext));
            }
            // Load the class
            Class<?> clazz = sClassCache.get(caller.getClassName());
            Constructor<?> clazzConstructor = sConstructorCache.get(caller.getClassName());
            if (clazz == null || clazzConstructor == null) {
                clazz = Class.forName(caller.getClassName(), false, packageContext.getClassLoader());
                Class<?> callerProcessorClazz = Class.forName(ClassCallerProcessor.class.getName(), false, packageContext.getClassLoader());
                if (callerProcessorClazz.isAssignableFrom(clazz)) {
                    sClassCache.put(caller.getClassName(), clazz);
                    sConstructorCache.put(caller.getClassName(), clazz.getConstructor(Context.class, Context.class, byte[].class));
                    clazzConstructor = sConstructorCache.get(caller.getClassName());
                } else {
                    throw new ClassCastException("Class " + clazz.getName() + " did not extend ClassCallerProcessor.");
                }
            }
            // Check if class is successfully loaded
            if (clazzConstructor == null)
                throw new Exception("Class constructor cannot be null");
            // Class has been loaded
            // Get the object
            final Object callerProcessor = clazzConstructor.newInstance(packageContext, context,
                    ParcelableUtil.marshall(LifecycleAgent.serverRunInfo));
            // Change package context of the params
            Object[] params = caller.getParams();
            if (params != null) {
                for (Object param : params) {
                    if (param instanceof Bundle) {
                        ((Bundle) param).setClassLoader(packageContext.getClassLoader());
                    }
                }
            }
            // Log changes
            FLog.log("CallClass: Object: " + callerProcessor + ", params: " + Arrays.toString(params) + ", class: " + clazz.getName());
            //  Invoke proxyInvoke method
            Object ret = MethodUtils.invokeExactMethod(callerProcessor, "proxyInvoke", params, new Class[]{Bundle.class});
            if (ret instanceof Bundle) {
                writeResult(result, ret);
            } else {
                writeResult(result, Bundle.EMPTY);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            FLog.log(e);
            result.setThrowable(e);
        }
        return result;
    }

    private void writeResult(CallerResult result, Object object) {
        Parcel parcel = Parcel.obtain();
        if (object instanceof ParceledListSlice) {
            parcel.writeValue(((ParceledListSlice) object).getList());
        } else {
            parcel.writeValue(object);
        }
        result.setReply(parcel.marshall());

        parcel.recycle();
    }

}
