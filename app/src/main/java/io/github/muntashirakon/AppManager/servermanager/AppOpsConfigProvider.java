// SPDX-License-Identifier: MIT

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

import java.io.IOException;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

// Copyright 2017 Zheng Li
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

        try {
            ServerConfig.init(ContextUtils.getDeContext(getContext()), Users.myUserId());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
