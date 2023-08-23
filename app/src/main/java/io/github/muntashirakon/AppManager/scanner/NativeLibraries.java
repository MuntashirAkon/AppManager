// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;
import android.text.format.Formatter;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.util.LocalizedString;

public class NativeLibraries {
    public static final String TAG = NativeLibraries.class.getSimpleName();

    private static final int ELF_MAGIC = 0x7f454c46; // 0x7f ELF

    public static abstract class NativeLib implements LocalizedString {
        @NonNull
        private final String mPath;
        @NonNull
        private final String mName;
        private final long mSize;
        private final byte[] mMagic;

        protected NativeLib(@NonNull String path, long size, byte[] magic) {
            mPath = path;
            mName = new File(path).getName();
            mSize = size;
            mMagic = magic;
        }

        @NonNull
        public String getPath() {
            return mPath;
        }

        @NonNull
        public String getName() {
            return mName;
        }

        public long getSize() {
            return mSize;
        }

        public byte[] getMagic() {
            return mMagic;
        }

        @NonNull
        public static NativeLib parse(@NonNull String path, long size, @NonNull InputStream is) throws IOException {
            byte[] header = new byte[20]; // First 20 bytes is enough
            is.read(header);
            ByteBuffer buffer = ByteBuffer.wrap(header);
            int magic = buffer.getInt();
            if (magic != ELF_MAGIC) {
                // Invalid library
                Log.w(TAG, "Invalid header magic 0x%x at path %s", magic, path);
                return new InvalidLib(path, size, header);
            }
            ElfLib elfLib = new ElfLib(path, size);
            elfLib.mArch = buffer.get(); // EI_CLASS
            elfLib.mEndianness = buffer.get(); // EI_DATA
            if (elfLib.mEndianness == ElfLib.ENDIANNESS_LITTLE_ENDIAN) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            buffer.position(16);
            elfLib.mType = buffer.getChar(); // e_type
            elfLib.mIsa = buffer.getChar(); // e_machine
            return elfLib;
        }
    }

    public static class InvalidLib extends NativeLib {
        protected InvalidLib(@NonNull String path, long size, byte[] magic) {
            super(path, size, magic);
        }

        @NonNull
        @Override
        public CharSequence toLocalizedString(@NonNull Context context) {
            StringBuilder sb = new StringBuilder();
            if (getSize() != -1) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ");
            }
            sb.append("Magic")
                    .append(LangUtils.getSeparatorString())
                    .append(HexEncoding.encodeToString(getMagic()))
                    .append("\n")
                    .append(getPath());
            return sb;
        }

        @NonNull
        @Override
        public String toString() {
            return "InvalidLib{" +
                    "mPath='" + getPath() + '\'' +
                    ", mName='" + getName() + '\'' +
                    '}';
        }
    }

    public static class ElfLib extends NativeLib {
        public static final int ARCH_NONE = 0; // ELFCLASSNONE
        public static final int ARCH_32BIT = 1; // ELFCLASS32
        public static final int ARCH_64BIT = 2; // ELFCLASS64

        @IntDef({ARCH_NONE, ARCH_32BIT, ARCH_64BIT})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Arch {
        }

        public static final int ENDIANNESS_NONE = 0; // ELFDATANONE
        public static final int ENDIANNESS_LITTLE_ENDIAN = 1; // ELFDATA2LSB
        public static final int ENDIANNESS_BIG_ENDIAN = 2; // ELFDATA2MSB

        @IntDef({ENDIANNESS_NONE, ENDIANNESS_LITTLE_ENDIAN, ENDIANNESS_BIG_ENDIAN})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Endianness {
        }

        public static final int TYPE_NONE = 0;
        public static final int TYPE_REL = 1;
        public static final int TYPE_EXEC = 2;
        public static final int TYPE_DYN = 3;
        public static final int TYPE_CORE = 4;

        @IntDef({TYPE_NONE, TYPE_REL, TYPE_EXEC, TYPE_DYN, TYPE_CORE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
        }

        @Arch
        private int mArch;
        @Endianness
        private int mEndianness;
        @Type
        private int mType;
        private int mIsa;

        private ElfLib(@NonNull String path, long size) {
            super(path, size, new byte[]{0x7f, 0x45, 0x4c, 0x46});
        }

        @Arch
        public int getArch() {
            return mArch;
        }

        @Endianness
        public int getEndianness() {
            return mEndianness;
        }

        @Type
        public int getType() {
            return mType;
        }

        public int getIsa() {
            return mIsa;
        }

        public String getIsaString() {
            // https://elixir.bootlin.com/linux/latest/source/include/uapi/linux/elf-em.h
            switch (mIsa) {
                case 0:
                    return "Unknown";
                case 3:
                    return "x86";
                case 8:
                    return "MIPS";
                case 40:
                    return "ARM";
                case 62:
                    return "x86_64";
                case 92:
                    return "OpenRISC";
                case 183:
                    return "AArch64";
                case 0xF3:
                    return "RISC-V";
                default:
                    // Not available in Android, but just in case
                    return String.format("Unknown(0x%x)", mIsa);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "ElfLib{" +
                    "mPath='" + getPath() + '\'' +
                    ", mName='" + getName() + '\'' +
                    ", mArch=" + mArch +
                    ", mEndianness=" + mEndianness +
                    ", mType=" + mType +
                    ", mIsa=" + getIsaString() +
                    '}';
        }

        @NonNull
        @Override
        public CharSequence toLocalizedString(@NonNull Context context) {
            StringBuilder sb = new StringBuilder();
            if (getSize() != -1) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ");
            }
            switch (mArch) {
                case ARCH_32BIT:
                    sb.append(context.getString(R.string.binary_32_bit)).append(", ");
                    break;
                case ARCH_64BIT:
                    sb.append(context.getString(R.string.binary_64_bit)).append(", ");
                    break;
                case ARCH_NONE:
                    break;
            }
            switch (mEndianness) {
                case ENDIANNESS_BIG_ENDIAN:
                    sb.append(context.getString(R.string.endianness_big_endian)).append(", ");
                    break;
                case ENDIANNESS_LITTLE_ENDIAN:
                    sb.append(context.getString(R.string.endianness_little_endian)).append(", ");
                    break;
                case ENDIANNESS_NONE:
                    break;
            }
            switch (mType) {
                case TYPE_NONE:
                case TYPE_CORE:
                case TYPE_REL:
                    // Not available in Android
                    break;
                case TYPE_DYN:
                    sb.append(context.getString(R.string.so_type_shared_library)).append(", ");
                    break;
                case TYPE_EXEC:
                    sb.append(context.getString(R.string.so_type_executable)).append(", ");
                    break;
            }
            sb.append(getIsaString()).append("\n").append(getPath());
            return sb;
        }
    }

    private final List<NativeLib> mLibs = new ArrayList<>();
    private final Set<String> mUniqueLibs = new HashSet<>();

    @WorkerThread
    public NativeLibraries(@NonNull File apkFile) throws IOException {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.getName().endsWith(".so")) {
                    try (InputStream is = zipFile.getInputStream(zipEntry)) {
                        NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), is);
                        mLibs.add(nativeLib);
                        mUniqueLibs.add(nativeLib.getName());
                    } catch (IOException e) {
                        Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                    }
                }
            }
        }
    }

    @WorkerThread
    public NativeLibraries(@NonNull InputStream apkInputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().endsWith(".so")) {
                    try {
                        NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), zipInputStream);
                        mLibs.add(nativeLib);
                        mUniqueLibs.add(nativeLib.getName());
                    } catch (IOException e) {
                        Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                    }
                }
            }
        }
    }

    @AnyThread
    public NativeLibraries(@NonNull ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".so")) {
                try (InputStream is = zipFile.getInputStream(zipEntry)) {
                    NativeLib nativeLib = NativeLib.parse(zipEntry.getName(), zipEntry.getSize(), is);
                    mLibs.add(nativeLib);
                    mUniqueLibs.add(nativeLib.getName());
                } catch (IOException e) {
                    Log.w(TAG, "Could not load native library %s", e, zipEntry.getName());
                }
            }
        }
    }

    @NonNull
    public List<NativeLib> getLibs() {
        return mLibs;
    }

    @NonNull
    public Collection<String> getUniqueLibs() {
        return mUniqueLibs;
    }
}
