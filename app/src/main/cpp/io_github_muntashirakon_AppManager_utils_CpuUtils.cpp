// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <unistd.h>

#include "io_github_muntashirakon_AppManager_utils_CpuUtils.h"

JNIEXPORT jlong JNICALL Java_io_github_muntashirakon_AppManager_utils_CpuUtils_getClockTicksPerSecond
  (JNIEnv *, jclass) {
    return sysconf(_SC_CLK_TCK);
}
