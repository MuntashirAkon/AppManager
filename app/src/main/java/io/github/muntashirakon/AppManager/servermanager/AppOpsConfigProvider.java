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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public class AppOpsConfigProvider extends ContentProvider {
    private static final UriMatcher uriMatcher;
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.local";

    private static final int TYPE_TOKEN = 1;
    private static final int TYPE_PORT = 2;
    private static final int TYPE_CLASS_PATH = 3;
    private static final int TYPE_SOCKET_PATH = 4;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "token", TYPE_TOKEN);
        uriMatcher.addURI(AUTHORITY, "adbPort", TYPE_PORT);
        uriMatcher.addURI(AUTHORITY, "classpath", TYPE_CLASS_PATH);
        uriMatcher.addURI(AUTHORITY, "socketPath", TYPE_SOCKET_PATH);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        checkCalling();

        ServerConfig.init(ContextUtils.getDeContext(getContext()), Users.getCurrentUserHandle());
        MatrixCursor cursor = null;
        switch (uriMatcher.match(uri)) {
            case TYPE_TOKEN:
                cursor = new MatrixCursor(new String[]{"token"}, 1);
                cursor.addRow(new String[]{ServerConfig.getLocalToken()});
                break;
            case TYPE_PORT:
                cursor = new MatrixCursor(new String[]{"port"}, 1);
                cursor.addRow(new String[]{String.valueOf(ServerConfig.getLocalServerPort())});
                break;
            case TYPE_CLASS_PATH:
                cursor = new MatrixCursor(new String[]{"classpath"}, 1);
                cursor.addRow(new String[]{ServerConfig.getClassPath()});
                break;
            case TYPE_SOCKET_PATH:
                cursor = new MatrixCursor(new String[]{"socketPath"}, 1);
                cursor.addRow(new String[]{ServerConfig.SOCKET_PATH});
                break;
        }
        return cursor;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private void checkCalling() {
        int callingUid = Binder.getCallingUid();
        boolean allow = callingUid == Process.myUid() || callingUid == 0 || callingUid == 1000
                || callingUid == 2000;
        if (!allow) {
            throw new SecurityException("Illegal uid " + callingUid);
        }
    }
}
