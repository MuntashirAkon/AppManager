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

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dalvik.system.VMRuntime;
import io.github.muntashirakon.AppManager.BuildConfig;

@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint("SetWorldReadable")
class AssetsUtils {
    private static final char[] DIGITS_LOWER =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static void copyFile(@NonNull Context context, String fileName, File destFile, boolean force) {
        InputStream open = null;
        FileOutputStream fos = null;
        try {
            AssetFileDescriptor openFd = context.getAssets().openFd(fileName);

            if (force) {
                destFile.delete();
            } else {
                if (destFile.exists()) {
                    if (destFile.length() != openFd.getLength()) {
                        destFile.delete();
                    } else {
                        return;
                    }
                }
            }

            if (!destFile.exists()) {
                destFile.createNewFile();
                destFile.setReadable(true, false);
                destFile.setExecutable(true, false);
            }

            fos = new FileOutputStream(destFile);
            byte[] buff = new byte[1024 * 16];
            int len = -1;
            open = openFd.createInputStream();

            while ((len = open.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
            fos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (open != null) {
                    open.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void copyFile(String src, File destFile, boolean force) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            File srcFile = new File(src);
            if (force) {
                destFile.delete();
            } else {
                if (destFile.exists()) {
                    if (destFile.length() != srcFile.length()) {
                        destFile.delete();
                    } else {
                        return;
                    }
                }
            }

            if (!destFile.exists()) {
                destFile.createNewFile();
            }
            destFile.setReadable(true, false);
            destFile.setExecutable(true, false);


            fos = new FileOutputStream(destFile);
            byte[] buff = new byte[1024 * 16];
            int len = -1;
            fis = new FileInputStream(srcFile);

            while ((len = fis.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
            fos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    static void writeScript(@NonNull AppOps.Config config) {
        BufferedWriter bw = null;
        FileInputStream fis = null;
        try {
            AssetFileDescriptor openFd = config.context.getAssets().openFd("run_server.sh");
            File destFile = new File(config.context.getExternalFilesDir(null), "run_server.sh");
            if (destFile.exists()) {
                destFile.delete();
            }


            StringBuilder sb = new StringBuilder();

            sb.append("path:").append(ServerConfig.getPort());
            sb.append(",token:").append(ServerConfig.getLocalToken());


            if (config.allowBgRunning) {
                sb.append(",bgrun:1");
            }

            if (BuildConfig.DEBUG) {
                sb.append(",debug:1");
            }

            String classpath = ServerConfig.getClassPath();
            String args = sb.toString();

            fis = openFd.createInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            bw = new BufferedWriter(new FileWriter(destFile, false));

            String line = br.readLine();
            while (line != null) {
                String wl = line;
                if (classpath != null) {
                    if ("jar_path=%s".equals(line.trim())) {
                        wl = "jar_path=" + classpath;
                    } else if ("args=%s".equals(line.trim())) {
                        wl = "args=" + args;
                    }
                }
                bw.write(wl);
                bw.newLine();
                line = br.readLine();
            }
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    static boolean is64Bit() {
        return VMRuntime.is64BitAbi(Build.SUPPORTED_ABIS[0]);
    }

    @NonNull
    static String generateToken(int len) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[len];
        secureRandom.nextBytes(bytes);
        return new String(encodeHex(bytes, DIGITS_LOWER));
    }

    @NonNull
    private static char[] encodeHex(@NonNull final byte[] data, final char[] toDigits) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return out;
    }


    static boolean isEnableSELinux() {
        File f = new File("/sys/fs/selinux/enforce");
        String s;
        if (f.exists() && !TextUtils.isEmpty((s = readProc(f)))) {
            return "1".equals(s.trim());
        } else {
            String getenforce = readCommand("getenforce");
            if (!TextUtils.isEmpty(getenforce) && getenforce.contains("Enforcing")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String readProc(File file) {
        FileInputStream fis = null;
        try {
            byte[] buff = new byte[512];
            fis = new FileInputStream(file);
            int len = fis.read(buff);
            if (len > 0) {
                int i;
                for (i = 0; i < len; i++) {
                    if (buff[i] == '\0') {
                        break;
                    }
                }
                return new String(buff, 0, i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Nullable
    private static String readCommand(String cmd) {
        Process exec = null;
        InputStream inputStream = null;
        try {
            exec = Runtime.getRuntime().exec(cmd);
            inputStream = exec.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int len = -1;
            while ((len = inputStream.read(buff, 0, buff.length)) != -1) {
                baos.write(buff, 0, len);
                if (baos.size() >= 128 * 1024) {
                    break;
                }
            }
            return baos.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (exec != null) {
                exec.destroy();
            }
        }
        return null;
    }
}
