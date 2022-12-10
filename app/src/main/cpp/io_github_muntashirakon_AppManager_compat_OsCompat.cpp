// SPDX-License-Identifier: GPL-3.0-or-later

#include <jni.h>
#include <errno.h>
#include <pwd.h>
#include <unistd.h>
#include <stdlib.h>

#include "io_github_muntashirakon_AppManager_compat_OsCompat.h"

// Converted from https://github.com/zhanghai/MaterialFiles/blob/faf6c1fe526e0bae3048070a8d1b742ff62c8e6f/app/src/main/jni/syscalls.c
// Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>

// Checks errno when return value is NULL.
#define TEMP_FAILURE_RETRY_N(exp) ({ \
    __typeof__(exp) _rc; \
    do { \
        errno = 0; \
        _rc = (exp); \
    } while (!_rc && errno == EINTR); \
    if (_rc) { \
        errno = 0; \
    } \
    _rc; })

// Always checks errno and ignores return value.
#define TEMP_FAILURE_RETRY_V(exp) ({ \
    do { \
        errno = 0; \
        (exp); \
    } while (errno == EINTR); })

#define AID_APP_START 10000

// API < 26 does not have the functions
#if __ANDROID_API__ < __ANDROID_API_O__

static __thread uid_t getpwentUid = AID_APP_START;

void setpwent() {
    getpwentUid = 0;
}

struct passwd *getpwent() {
    while (getpwentUid < AID_APP_START) {
        struct passwd *passwd = getpwuid(getpwentUid);
        ++getpwentUid;
        errno = 0;
        if (passwd) {
            return passwd;
        }
    }
    return NULL;
}

void endpwent() {
    setpwent();
}

#endif

static jclass findClass(JNIEnv *env, const char *name) {
    jclass localClass = env->FindClass(name);
    if (!localClass) {
        abort();
    }
    jclass globalClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);
    if (!globalClass) {
        abort();
    }
    return globalClass;
}

static jclass getErrnoExceptionClass(JNIEnv *env) {
    static jclass errnoExceptionClass = NULL;
    if (!errnoExceptionClass) {
        errnoExceptionClass = findClass(env, "android/system/ErrnoException");
    }
    return errnoExceptionClass;
}

static void throwException(JNIEnv *env, jclass exceptionClass, jmethodID constructor3,
                           jmethodID constructor2, const char *functionName, int error) {
    jthrowable cause = NULL;
    if (env->ExceptionCheck()) {
        cause = env->ExceptionOccurred();
        env->ExceptionClear();
    }
    jstring detailMessage = env->NewStringUTF(functionName);
    if (!detailMessage) {
        env->ExceptionClear();
    }
    jobject exception;
    if (cause) {
        exception = env->NewObject(exceptionClass, constructor3, detailMessage, error,
                cause);
    } else {
        exception = env->NewObject(exceptionClass, constructor2, detailMessage, error);
    }
    env->Throw((jthrowable) exception);
    if (detailMessage) {
        env->DeleteLocalRef(detailMessage);
    }
}

static void throwErrnoException(JNIEnv* env, const char* functionName) {
    int error = errno;
    static jmethodID constructor3 = NULL;
    if (!constructor3) {
        constructor3 = env->GetMethodID(getErrnoExceptionClass(env), "<init>",
                "(Ljava/lang/String;ILjava/lang/Throwable;)V");
    }
    static jmethodID constructor2 = NULL;
    if (!constructor2) {
        constructor2 = env->GetMethodID(getErrnoExceptionClass(env), "<init>", "(Ljava/lang/String;I)V");
    }
    throwException(env, getErrnoExceptionClass(env), constructor3, constructor2, functionName, error);
}

static jclass getStructPasswdClass(JNIEnv *env) {
    static jclass structPasswdClass = NULL;
    if (!structPasswdClass) {
        structPasswdClass = findClass(env, "android/system/StructPasswd");
    }
    return structPasswdClass;
}

static jobject newStructPasswd(JNIEnv *env, const struct passwd *passwd) {
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = env->GetMethodID(getStructPasswdClass(env), "<init>",
                "(Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;)V");
    }
    jstring pw_name = env->NewStringUTF(passwd->pw_name);
    jstring pw_dir = env->NewStringUTF(passwd->pw_dir);
    jstring pw_shell = env->NewStringUTF(passwd->pw_shell);
    jobject struct_passwd = env->NewObject(getStructPasswdClass(env), constructor, pw_name, passwd->pw_uid,
            passwd->pw_gid, pw_dir, pw_shell);
    if (pw_name) {
        env->DeleteLocalRef(pw_name);
    }
    if (pw_dir) {
        env->DeleteLocalRef(pw_dir);
    }
    if (pw_shell) {
        env->DeleteLocalRef(pw_shell);
    }
//    return NULL;
    return struct_passwd;
}


JNIEXPORT void JNICALL Java_io_github_muntashirakon_AppManager_compat_OsCompat_setpwent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(setpwent());
    if (errno) {
        throwErrnoException(env, "setpwent");
    }
}

JNIEXPORT jobject JNICALL Java_io_github_muntashirakon_AppManager_compat_OsCompat_getpwent
  (JNIEnv *env, jclass clazz) {
    while (true) {
        struct passwd *passwd = TEMP_FAILURE_RETRY_N(getpwent());
        if (errno) {
            throwErrnoException(env, "getpwent");
            return NULL;
        }
        if (!passwd) {
            return NULL;
        }
        if (passwd->pw_name[0] == 'o' && passwd->pw_name[1] == 'e' && passwd->pw_name[2] == 'm'
            && passwd->pw_name[3] == '_') {
            continue;
        }
        if (passwd->pw_name[0] == 'u' && passwd->pw_name[1] >= '0' && passwd->pw_name[1] <= '9') {
            return NULL;
        }
        return newStructPasswd(env, passwd);
    }
}

JNIEXPORT void JNICALL Java_io_github_muntashirakon_AppManager_compat_OsCompat_endpwent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(endpwent());
    if (errno) {
        throwErrnoException(env, "endpwent");
    }
}
