package dalvik.system;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:libcore/libart/src/main/java/dalvik/system/VMRuntime.java
public final class VMRuntime {
    private static final Map<String, String> ABI_TO_INSTRUCTION_SET_MAP = new HashMap<>(16);

    static {
        ABI_TO_INSTRUCTION_SET_MAP.put("armeabi", "arm");
        ABI_TO_INSTRUCTION_SET_MAP.put("armeabi-v7a", "arm");
        ABI_TO_INSTRUCTION_SET_MAP.put("mips", "mips");
        ABI_TO_INSTRUCTION_SET_MAP.put("mips64", "mips64");
        ABI_TO_INSTRUCTION_SET_MAP.put("x86", "x86");
        ABI_TO_INSTRUCTION_SET_MAP.put("x86_64", "x86_64");
        ABI_TO_INSTRUCTION_SET_MAP.put("arm64-v8a", "arm64");
        ABI_TO_INSTRUCTION_SET_MAP.put("arm64-v8a-hwasan", "arm64");
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
        return "arm64".equals(instructionSet) ||
                "x86_64".equals(instructionSet) ||
                "mips64".equals(instructionSet);
    }

    public static boolean is64BitAbi(String abi) {
        return is64BitInstructionSet(getInstructionSet(abi));
    }
}
