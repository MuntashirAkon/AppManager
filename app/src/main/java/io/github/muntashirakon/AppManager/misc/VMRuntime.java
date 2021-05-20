// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:libcore/libart/src/main/java/dalvik/system/VMRuntime.java
public final class VMRuntime {
    public static final String ABI_ARMEABI = "armeabi";
    public static final String ABI_ARMEABI_V7A = "armeabi-v7a";
    public static final String ABI_MIPS = "mips";
    public static final String ABI_MIPS64 = "mips64";
    public static final String ABI_X86 = "x86";
    public static final String ABI_X86_64 = "x86_64";
    public static final String ABI_ARM64_V8A = "arm64-v8a";
    public static final String ABI_ARM64_V8A_HWASAN = "arm64-v8a-hwasan";

    public static final String INSTRUCTION_SET_ARM = "arm";
    public static final String INSTRUCTION_SET_MIPS = "mips";
    public static final String INSTRUCTION_SET_MIPS64 = "mips64";
    public static final String INSTRUCTION_SET_X86 = "x86";
    public static final String INSTRUCTION_SET_X86_64 = "x86_64";
    public static final String INSTRUCTION_SET_ARM64 = "arm64";

    private static final Map<String, String> ABI_TO_INSTRUCTION_SET_MAP = new HashMap<>(16);

    static {
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_ARMEABI, INSTRUCTION_SET_ARM);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_ARMEABI_V7A, INSTRUCTION_SET_ARM);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_MIPS, INSTRUCTION_SET_MIPS);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_MIPS64, INSTRUCTION_SET_MIPS64);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_X86, INSTRUCTION_SET_X86);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_X86_64, INSTRUCTION_SET_X86_64);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_ARM64_V8A, INSTRUCTION_SET_ARM64);
        ABI_TO_INSTRUCTION_SET_MAP.put(ABI_ARM64_V8A_HWASAN, INSTRUCTION_SET_ARM64);
    }

    @NonNull
    public static String getInstructionSet(String abi) {
        final String instructionSet = ABI_TO_INSTRUCTION_SET_MAP.get(abi);
        if (instructionSet == null) {
            throw new IllegalArgumentException("Unsupported ABI: " + abi);
        }

        return instructionSet;
    }

    public static boolean is64BitInstructionSet(String instructionSet) {
        return INSTRUCTION_SET_ARM64.equals(instructionSet) ||
                INSTRUCTION_SET_X86_64.equals(instructionSet) ||
                INSTRUCTION_SET_MIPS64.equals(instructionSet);
    }

    public static boolean is64BitAbi(String abi) {
        return is64BitInstructionSet(getInstructionSet(abi));
    }
}
