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

package io.github.muntashirakon.AppManager.server.common;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class FLog {

    public static boolean writeLog = false;
    private static FileOutputStream fos;
    private static final AtomicInteger sBufferSize = new AtomicInteger();
    private static final AtomicInteger sErrorCount = new AtomicInteger();

    private static void openFile() {
        try {
            if (writeLog && fos == null && sErrorCount.get() < 5) {
                File file = new File("/data/local/tmp/am.txt");
                fos = new FileOutputStream(file);

                fos.write("\n\n\n--------------------".getBytes());
                fos.write(new Date().toString().getBytes());
                fos.write("\n\n".getBytes());
                chown(file.getAbsolutePath(), 2000, 2000);
                chmod(file.getAbsolutePath(), 0755);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sErrorCount.incrementAndGet();
            fos = null;
        }
    }

    private static void chown(String path, int uid, int gid) {
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                Os.chown(path, uid, gid);
            } else {
                Runtime.getRuntime().exec("chown " + uid + ":" + gid + " " + path).destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void chmod(String path, int mode) {
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                Os.chmod(path, mode);
            } else {
                Runtime.getRuntime().exec("chmod " + mode + " " + path).destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(String log) {
        if (writeLog) {
            System.out.println(log);
        } else {
            Log.e("am", "Flog --> " + log);
        }

        try {
            if (writeLog) {
                openFile();
                if (fos != null) {
                    fos.write(log.getBytes());
                    fos.write("\n".getBytes());

                    if (sBufferSize.incrementAndGet() > 10) {
                        fos.getFD().sync();
                        fos.flush();
                        sBufferSize.set(0);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(Throwable e) {
        log(Log.getStackTraceString(e));
    }

    public static void close() {
        try {
            if (writeLog && fos != null) {
                fos.getFD().sync();
                fos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

