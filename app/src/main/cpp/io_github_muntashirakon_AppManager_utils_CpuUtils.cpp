// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <unistd.h>
#include <cstring>

#include "io_github_muntashirakon_AppManager_utils_CpuUtils.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_muntashirakon_AppManager_utils_CpuUtils_getClockTicksPerSecond
        (JNIEnv *, jclass) {
    return sysconf(_SC_CLK_TCK);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_github_muntashirakon_AppManager_utils_CpuUtils_getCpuModel(JNIEnv *env, jclass) {
#if defined(__x86_64__) || defined(__i386__)
    unsigned int eax, ebx, ecx, edx;
    char cpuModel[48];

    // Call CPUID with EAX=0x80000002, 0x80000003, 0x80000004 to get the CPU model
    for (int i = 0; i < 3; i++) {
        asm volatile("cpuid"
                : "=a" (eax), "=b" (ebx), "=c" (ecx), "=d" (edx)
                : "a" (0x80000002 + i));
        memcpy(cpuModel + i * 16, &eax, 4);
        memcpy(cpuModel + i * 16 + 4, &ebx, 4);
        memcpy(cpuModel + i * 16 + 8, &ecx, 4);
        memcpy(cpuModel + i * 16 + 12, &edx, 4);
    }
    return env->NewStringUTF(cpuModel);
#else
    return 0;
#endif
}