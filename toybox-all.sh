#!/usr/bin/env bash

NDK=$1
API=$2

cd toybox
# Check if NDK is set
if [[ "${NDK}" == "" ]]; then
    echo "Environment variable NDK isn't set."
    exit 1
fi

# Check if API is set
if [[ "${API}" == "" ]]; then
    API=21
fi

# Check current architecture
hw_class=`uname -m`
if [[ "${hw_class}" != "x86_64" ]]; then
    echo "Unsupported architecture."
    exit 1
fi

# Check current OS, only macOS and Linux are supported
os=`uname -s`
if [[ "${os}" == "Darwin" ]]; then
    BUILD_TAG=darwin-x86_64
elif [[ "${os}" == "Linux" ]]; then
    BUILD_TAG=linux-x86_64
else
    echo "Unsupported OS."
    exit 1
fi

# Set targets, see https://developer.android.com/ndk/guides/other_build_systems
declare -a TARGETS
TARGETS=(armv7a-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android)
declare -a BIN_UTILS
BIN_UTILS=(arm-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android)
# Set JNI directories
declare -a JNI_DIRS
JNI_DIRS=(armeabi-v7a arm64-v8a x86 x86_64)
# Toolchain
export TOOLCHAIN=${NDK}/toolchains/llvm/prebuilt/${BUILD_TAG}
# Flags
export CFLAGS="--static -static -g0 -Os"
export CXXFLAGS="--static -static -g0 -Os"
export LDFLAGS="-s"
# Copy to JNI dir: This is a clever workaround as Android doesn't have
# any option to separate assets based on ABI
jni_dir=../app/src/main/jniLibs
target_name=libtoybox.so  # lib and .so is appended to fool Android into thinking that it's a JNI library
target_dir=
# Replace toybox with libtoybox in toys.h file
sed -i 's|#define TOYBOX_NAME .*|#define TOYBOX_NAME "libtoybox"|' toys.h
# Build and copy toybox to their respective directories
for (( i = 0; i < 4; ++i )); do
    export AR="${TOOLCHAIN}/bin/${BIN_UTILS[i]}-ar"
    export AS="${TOOLCHAIN}/bin/${BIN_UTILS[i]}-as"
    export CC="${TOOLCHAIN}/bin/${TARGETS[i]}${API}-clang"
    export CXX="${TOOLCHAIN}/bin/${TARGETS[i]}${API}-clang++"
    export LD="${TOOLCHAIN}/bin/${BIN_UTILS[i]}-ld"
    export RANLIB="${TOOLCHAIN}/bin/${BIN_UTILS[i]}-ranlib"
    export STRIP="${TOOLCHAIN}/bin/${BIN_UTILS[i]}-strip"
    # Run defconfig first
    make clean && make defconfig
    # create executable
    make
    if [[ $? -ne 0 ]]; then exit 1; fi
    chmod 755 toybox
    # move to jni dir
    target_dir=${jni_dir}/${JNI_DIRS[i]}
    mkdir -p ${target_dir}
    mv toybox ${target_dir}/${target_name}
done
