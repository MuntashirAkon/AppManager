// SPDX-License-Identifier: GPL-3.0-or-later

#include <jni.h>
#include <errno.h>
#include <pwd.h>
#include <grp.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "io_github_muntashirakon_compat_system_OsCompat.h"

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

static __thread gid_t getgrentGid = AID_APP_START;
static __thread uid_t getpwentUid = AID_APP_START;

void setgrent() {
    getgrentGid = 0;
}

struct group *getgrent() {
    while (getgrentGid < AID_APP_START) {
        struct group *group = getgrgid(getgrentGid);
        ++getgrentGid;
        errno = 0;
        if (group) {
            return group;
        }
    }
    return NULL;
}

void endgrent() {
    setgrent();
}


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

static jclass getStringClass(JNIEnv *env) {
    static jclass stringClass = NULL;
    if (!stringClass) {
        stringClass = findClass(env, "java/lang/String");
    }
    return stringClass;
}

static jclass getStructGroupClass(JNIEnv *env) {
    static jclass structGroupClass = NULL;
    if (!structGroupClass) {
        structGroupClass = findClass(env, "io/github/muntashirakon/compat/system/StructGroup");
    }
    return structGroupClass;
}

static jclass getStructPasswdClass(JNIEnv *env) {
    static jclass structPasswdClass = NULL;
    if (!structPasswdClass) {
        structPasswdClass = findClass(env, "android/system/StructPasswd");
    }
    return structPasswdClass;
}

static jclass getStructTimespecClass(JNIEnv *env) {
    static jclass structTimespecClass = NULL;
    if (!structTimespecClass) {
        structTimespecClass = findClass(env, "io/github/muntashirakon/compat/system/StructTimespec");
    }
    return structTimespecClass;
}

static jclass getOsCompatClass(JNIEnv *env) {
    static jclass osCompatClass = NULL;
    if (!osCompatClass) {
        osCompatClass = findClass(env, "io/github/muntashirakon/compat/system/OsCompat");
    }
    return osCompatClass;
}


static jobject newStructGroup(JNIEnv *env, const struct group *group) {
    static jmethodID constructor = NULL;
    if (!constructor) {
        constructor = env->GetMethodID(getStructGroupClass(env), "<init>",
                "(Ljava/lang/String;Ljava/lang/String;I[Ljava/lang/String;)V");
    }
    jstring gr_name = env->NewStringUTF(group->gr_name);
    jstring gr_passwd = env->NewStringUTF(group->gr_passwd);
    jobjectArray gr_mem;
    if (group->gr_mem) {
        jsize gr_memLength = 0;
        for (char **gr_memIterator = group->gr_mem; *gr_memIterator; ++gr_memIterator) {
            ++gr_memLength;
        }
        gr_mem = env->NewObjectArray(gr_memLength, getStringClass(env), NULL);
        if (!gr_mem) {
            return NULL;
        }
        jsize gr_memIndex = 0;
        for (char **gr_memIterator = group->gr_mem; *gr_memIterator; ++gr_memIterator,
                ++gr_memIndex) {
            jstring gr_memElement = env->NewStringUTF(*gr_memIterator);
            if (!gr_memElement) {
                return NULL;
            }
            env->SetObjectArrayElement(gr_mem, gr_memIndex, gr_memElement);
            env->DeleteLocalRef(gr_memElement);
        }
    } else {
        gr_mem = NULL;
    }
    jobject struct_passwd = env->NewObject(getStructGroupClass(env), constructor, gr_name, gr_passwd, group->gr_gid,
            gr_mem);
    if (gr_name) {
        env->DeleteLocalRef(gr_name);
    }
    if (gr_passwd) {
        env->DeleteLocalRef(gr_passwd);
    }
    return struct_passwd;
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
    return struct_passwd;
}

static struct timespec javaStructTimespecToTimespec(JNIEnv *env, jobject obj) {
    static jfieldID tv_sec = NULL;
    static jfieldID tv_nsec = NULL;
    if (!tv_sec) {
        tv_sec = env->GetFieldID(getStructTimespecClass(env), "tv_sec", "J");
    }
    if (!tv_nsec) {
        tv_nsec = env->GetFieldID(getStructTimespecClass(env), "tv_nsec", "J");
    }
    struct timespec time;
    time.tv_sec = (time_t) env->GetLongField(obj, tv_sec);
    time.tv_nsec = env->GetLongField(obj, tv_nsec);
    return time;
}

/** OsCompat **/

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_setgrent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(setgrent());
    if (errno) {
        throwErrnoException(env, "setgrent");
    }
}

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_setpwent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(setpwent());
    if (errno) {
        throwErrnoException(env, "setpwent");
    }
}

JNIEXPORT jobject JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_getgrent
  (JNIEnv *env, jclass clazz) {
    while (true) {
        struct group *group = TEMP_FAILURE_RETRY_N(getgrent());
        if (errno) {
            throwErrnoException(env, "getgrent");
            return NULL;
        }
        if (!group) {
            return NULL;
        }
        if (group->gr_name[0] == 'o' && group->gr_name[1] == 'e' && group->gr_name[2] == 'm'
            && group->gr_name[3] == '_') {
            continue;
        }
        if (group->gr_name[0] == 'u' && (group->gr_name[1] >= '0' && group->gr_name[1] <= '9')) {
            return NULL;
        }
        if (group->gr_name[0] == 'a' && group->gr_name[1] == 'l' && group->gr_name[2] == 'l'
            && group->gr_name[3] == '_' && group->gr_name[4] == 'a'
            && (group->gr_name[5] >= '0' && group->gr_name[5] <= '9')) {
            return NULL;
        }
        return newStructGroup(env, group);
    }
}

JNIEXPORT jobject JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_getpwent
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

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_endgrent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(endgrent());
    if (errno) {
        throwErrnoException(env, "endgrent");
    }
}

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_endpwent
  (JNIEnv *env, jclass clazz) {
    TEMP_FAILURE_RETRY_V(endpwent());
    if (errno) {
        throwErrnoException(env, "endpwent");
    }
}

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_utimensat
  (JNIEnv *env, jclass clazz, jint dirfd, jstring pathname, jobject atime, jobject mtime, jint flags) {
    const char *path = env->GetStringUTFChars(pathname, 0);
    struct timespec times[2];
    times[0] = javaStructTimespecToTimespec(env, atime);
    times[1] = javaStructTimespecToTimespec(env, mtime);
    TEMP_FAILURE_RETRY_V(utimensat(dirfd, path, times, flags));
    env->ReleaseStringUTFChars(pathname, path);
    if (errno) {
        throwErrnoException(env, "utimensat");
    }
}

JNIEXPORT void JNICALL Java_io_github_muntashirakon_compat_system_OsCompat_setNativeConstants
  (JNIEnv *env, jclass clazz) {
    jclass osCompatClass = getOsCompatClass(env);
    jfieldID utime_now = env->GetStaticFieldID(osCompatClass, "UTIME_NOW", "J");
    jfieldID utime_omit = env->GetStaticFieldID(osCompatClass, "UTIME_OMIT", "J");
    jfieldID at_fdcwd = env->GetStaticFieldID(osCompatClass, "AT_FDCWD", "I");
    jfieldID at_symlink_nofollow = env->GetStaticFieldID(osCompatClass, "AT_SYMLINK_NOFOLLOW", "I");
    env->SetStaticLongField(osCompatClass, utime_now, UTIME_NOW);
    env->SetStaticLongField(osCompatClass, utime_omit, UTIME_OMIT);
    env->SetStaticIntField(osCompatClass, at_fdcwd, AT_FDCWD);
    env->SetStaticIntField(osCompatClass, at_symlink_nofollow, AT_SYMLINK_NOFOLLOW);
}
