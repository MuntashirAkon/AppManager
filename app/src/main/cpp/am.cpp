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

#include <dlfcn.h>
#include <sys/system_properties.h>
#include <cstdint>
#include <jni.h>

static void *libc;

static int (*property_foreach)(
        void (*callback)(const prop_info *pi, void *cookie),
        void *cookie);

static int (*property_read)(
        const prop_info *pi,
        char *name, char *value);

static void (*property_read_callback)(
        const prop_info *pi,
        void (*callback)(void *cookie, const char *name, const char *value, uint32_t serial),
        void *cookie);

static const prop_info *(*property_find_nth)(unsigned n);

static void get_from_libc(const char *fn_name, void *fn_ptr) {
    void **fn_ptr_ptr = static_cast<void **>(fn_ptr);
    if (*fn_ptr_ptr == nullptr) {
        if (!libc) {
            libc = dlopen("libc.so", RTLD_LAZY);
            if (libc == nullptr) return;
        }
        *fn_ptr_ptr = dlsym(libc, fn_name);
    }
}

static char gName[PROP_NAME_MAX];
static char gValue[PROP_VALUE_MAX];

struct JNICookie {
    JNIEnv *env;
    jobject thiz;
};

static void
handle_property(void *cookie, const char *name, const char *value, uint32_t  __unused serial) {
    auto *jniCookie = static_cast<struct JNICookie *>(cookie);
    JNIEnv *env = jniCookie->env;
    jclass clazz = jniCookie->env->GetObjectClass(jniCookie->thiz);
    jmethodID handleProperty = env->GetMethodID(clazz,
                                                "handleProperty",
                                                "(Ljava/lang/String;Ljava/lang/String;)V");
    env->CallVoidMethod(jniCookie->thiz,
                        handleProperty,
                        env->NewStringUTF(name),
                        env->NewStringUTF(value));
}

static void handle_property(const prop_info *propInfo, void *cookie) {
    if (propInfo == nullptr) return;
    get_from_libc("__system_property_read_callback", &property_read_callback);
    if (property_read_callback == nullptr) return;
    property_read_callback(propInfo, handle_property, cookie);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_muntashirakon_AppManager_misc_SystemProperties_readAndroidPropertiesPost26(
        JNIEnv *env, jobject thiz) {
    get_from_libc("__system_property_foreach", &property_foreach);
    if (property_foreach == nullptr) return;
    struct JNICookie jniCookie = {
            .env = env,
            .thiz = thiz,
    };
    while (property_foreach(handle_property, &jniCookie) == 1);
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_muntashirakon_AppManager_misc_SystemProperties_readAndroidPropertyPre26(
        JNIEnv *env, jobject __unused thiz, jint n, jobjectArray property) {
    get_from_libc("__system_property_find_nth", &property_find_nth);
    if (property_find_nth == nullptr) return 0;
    const prop_info *propInfo = property_find_nth(n);
    if (propInfo == nullptr) return 0;
    get_from_libc("__system_property_read", &property_read);
    if (property_read == nullptr) return 0;
    property_read(propInfo, gName, gValue);
    env->SetObjectArrayElement(property, 0, env->NewStringUTF(gName));
    env->SetObjectArrayElement(property, 1, env->NewStringUTF(gValue));
    return 1;  // true
}

