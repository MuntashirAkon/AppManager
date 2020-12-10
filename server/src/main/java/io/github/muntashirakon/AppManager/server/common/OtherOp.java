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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.util.SparseArray;

/**
 * Created by zl on 2017/2/24.
 */

public final class OtherOp {

    public static final int OP_ACCESS_PHONE_DATA = 51001;

    public static final int OP_ACCESS_WIFI_NETWORK = 52002;


    public static final String OP_NAME_ACCESS_PHONE_DATA = "ACCESS_PHONE_DATA";
    public static final String OP_NAME_ACCESS_WIFI_NETWORK = "ACCESS_WIFI_NETWORK";

    private static Boolean sSupportCount = null;

    private static Integer s_OP_WIFI_SCAN = null;

    private static final SparseArray<String> mData = new SparseArray<>();
    private static final SparseArray<String> mPerms = new SparseArray<>();

    static {
        mData.put(OP_ACCESS_PHONE_DATA, OP_NAME_ACCESS_PHONE_DATA);
        mData.put(OP_ACCESS_WIFI_NETWORK, OP_NAME_ACCESS_WIFI_NETWORK);

        mPerms.put(OP_ACCESS_PHONE_DATA, Manifest.permission.INTERNET);
        mPerms.put(OP_ACCESS_WIFI_NETWORK, Manifest.permission.INTERNET);
    }

    public static boolean isOtherOp(int op) {
        return op == OP_ACCESS_PHONE_DATA || op == OP_ACCESS_WIFI_NETWORK;
    }

    public static boolean isOtherOp(String opName) {
        return OP_NAME_ACCESS_PHONE_DATA.equals(opName) || OP_NAME_ACCESS_WIFI_NETWORK.equals(opName);
    }


    public static String getOpName(int op) {
        return mData.get(op);
    }

    public static String getOpPermName(int op) {
        return mPerms.get(op);
    }

    public static boolean isSupportCount() {
        if (sSupportCount == null) {
            try {
                @SuppressLint("PrivateApi")
                Class<?> aClass = Class.forName("android.app.AppOpsManager$OpEntry", false,
                        ClassLoader.getSystemClassLoader());
                sSupportCount = ReflectUtils.hasField(aClass, "mAllowedCount");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return sSupportCount;
    }

    public static int getWifiScanOp() {
        if (s_OP_WIFI_SCAN == null) {
            s_OP_WIFI_SCAN = (Integer) ReflectUtils.getFieldValue(AppOpsManager.class, "OP_WIFI_SCAN");
        }
        return s_OP_WIFI_SCAN != null ? s_OP_WIFI_SCAN : -1;
    }

}
