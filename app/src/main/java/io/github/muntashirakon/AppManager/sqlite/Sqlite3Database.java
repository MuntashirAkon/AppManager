// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class Sqlite3Database implements Closeable {
    public static final String TAG = Sqlite3Database.class.getSimpleName();

    private final SQLiteDatabase db;
    private final Path realPath;
    @Nullable
    private final File cachedPath;
    private final FileCache fileCache = new FileCache();

    public Sqlite3Database(@NonNull Path path, boolean create, boolean readOnly) throws IOException {
        realPath = path;
        File realFile = path.getFile();
        cachedPath = getCachedPathIfRequired(create, readOnly);
        // At this point, if cachedPath is null, realPath must be non-null
        File neededFile = cachedPath != null ? cachedPath : Objects.requireNonNull(realFile);
        int flags = 0;
        if (create) flags |= SQLiteDatabase.CREATE_IF_NECESSARY;
        if (readOnly) flags |= SQLiteDatabase.OPEN_READONLY;
        db = SQLiteDatabase.openDatabase(neededFile.getAbsolutePath(), null, flags);
    }

    public SQLiteDatabase getDb() {
        return db;
    }

    @NonNull
    public ArrayList<String> getTables() {
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name";
        Cursor res = db.rawQuery(sql, null);
        ArrayList<String> tables = new ArrayList<>();
        while (res.moveToNext()) {
            tables.add(res.getString(0));
        }
        res.close();
        return tables;
    }

    public ArrayList<TableColumn> getColumns(@NonNull String table) {
        String sql = "PRAGMA table_info([" + table + "])";
        Cursor cursor = db.rawQuery(sql, null);

        ArrayList<TableColumn> columns = new ArrayList<>();
        while (cursor.moveToNext()) {
            String name = cursor.getString(1);
            String type = cursor.getString(2);
            int nonNull = cursor.getInt(3);
            String def = cursor.getString(4);
            int pk = cursor.getInt(5);
            TableColumn column = new TableColumn(name, type, nonNull, pk, def);
            columns.add(column);
        }
        cursor.close();
        return columns;
    }

    public int getColumnCount(@NonNull String table) {
        String sql = "SELECT * FROM [" + table + "] LIMIT 1";
        Cursor cursor = db.rawQuery(sql, null);
        int cols = cursor.getColumnCount();
        cursor.close();
        return cols;
    }

    public String[] getColumnNames(String table) {
        String sql = "PRAGMA table_info([" + table + "])";
        Cursor cursor = db.rawQuery(sql, null);
        String[] columns = new String[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            columns[i] = cursor.getString(1);
            i++;
        }
        cursor.close();
        return columns;
    }

    public long getRowCount(@NonNull String table) {
        return DatabaseUtils.queryNumEntries(db, table);
    }

    public long getQueryRowCount(@NonNull String customQuery) {
        Cursor cursor = db.rawQuery(customQuery, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    public QueryResultData getQueryData(@NonNull String query, int limit, long offsetFrom) {
        return new QueryResultData(
                runQuery(query, limit, offsetFrom),
                getQueryColumns(query),
                getQueryRowCount(query),
                limit, offsetFrom, query, null);
    }

    public QueryResultData getTableData(@NonNull String tableName, int limit, long offsetFrom) {
        return new QueryResultData(
                getTableRows(tableName, limit, offsetFrom),
                getColumns(tableName),
                getRowCount(tableName),
                limit, offsetFrom, null, tableName);
    }

    @NonNull
    public List<List<TableRecordItem>> getTableRows(@NonNull String tableName, int limit, long offsetFrom) {
        String[] cols = getColumnNames(tableName);

        StringBuilder sql = new StringBuilder().append("SELECT ");
        for (int i = 0; i < cols.length; i++) {
            sql.append(cols[i]);
            if (i < cols.length - 1)
                sql.append(", ");
        }
        sql.append(" FROM ").append(tableName)
                .append(" LIMIT ").append(limit)
                .append(" OFFSET ").append(offsetFrom);

        Log.d(TAG, "Query: " + sql);

        Cursor cursor = db.rawQuery(sql.toString(), null);
        List<List<TableRecordItem>> tableRows = new ArrayList<>();
        while (cursor.moveToNext()) {
            List<TableRecordItem> colData = new ArrayList<>();
            for (String col : cols) {
                int colIndex = cursor.getColumnIndex(col);
                String data = "";
                try {
                    data = cursor.getString(cursor.getColumnIndex(col));
                } catch (SQLException e) {
                    if (Objects.requireNonNull(e.getMessage()).contains("Unable to convert BLOB to string")) {
                        data = "(BLOB)";
                    }
                }
                colData.add(new TableRecordItem(String.valueOf(colIndex), data));
            }
            tableRows.add(colData);
        }
        cursor.close();
        return tableRows;
    }

    private int getLimitPosition(@NonNull String query) {
        return query.toLowerCase(Locale.ROOT).lastIndexOf(" limit ");
    }

    private String getLimitSubstr(@NonNull String query) {
        int pos = getLimitPosition(query);
        return pos >= 0 ?
                query.substring(query.toLowerCase().lastIndexOf("limit", query.length())) : query;
    }

    public ArrayList<TableColumn> getQueryColumns(String sql) {
        String subSubstr = getLimitSubstr(sql);
        int limitpos = getLimitPosition(sql);
        StringBuilder columnSQL = new StringBuilder();
        if (limitpos > 0 && !subSubstr.contains(")")) {
            columnSQL.append(sql.substring(0, limitpos)).append(" LIMIT 1");
        } else {
            columnSQL.append(sql).append(" LIMIT 1");
        }

        ArrayList<TableColumn> fields = new ArrayList<>();

        Cursor res = db.rawQuery(columnSQL.toString(), null);
        int colCount = res.getColumnCount();
        while (res.moveToNext()) {
            for (int i = 0; i < colCount; i++) {
                TableColumn field = new TableColumn(res.getColumnName(i), getColumnDataType(res.getType(i)), 0, 0, null);
                fields.add(field);
            }
        }

        res.close();
        return fields;
    }

    public List<List<TableRecordItem>> runQuery(String query, int limit, long offsetFrom) throws SQLiteException {
        String subSubstr = getLimitSubstr(query);
        int limitpos = getLimitPosition(query);
        StringBuilder customSQL = new StringBuilder();
        int customQueryLimit = -1; //Moved from global var
        if (limitpos > 0 && !subSubstr.contains(")")) {
            customQueryLimit = Integer.parseInt(
                    subSubstr.toLowerCase().split("limit")[1].trim()
            );
            customSQL.append(query.substring(0, limitpos));
        } else
            customSQL.append(query);

        customSQL.append(" LIMIT ");
        if (customQueryLimit != -1) {
            if (customQueryLimit < limit) {
                customSQL.append(customQueryLimit);
            } else if ((offsetFrom + offsetFrom) > customQueryLimit) {
                customSQL.append(customQueryLimit - offsetFrom).append(" OFFSET ").append(offsetFrom);
            } else {
                customSQL.append(limit).append(" OFFSET ").append(offsetFrom);
            }
        } else {
            customSQL.append(limit).append(" OFFSET ").append(offsetFrom);
        }

        Cursor cursor = db.rawQuery(customSQL.toString(), null);

        List<List<TableRecordItem>> Tabledata = new ArrayList<>();
        while (cursor.moveToNext()) {
            List<TableRecordItem> colData = new ArrayList<>();
            String data = "";
            int colCount = cursor.getColumnCount();
            for (int i = 0; i < colCount; i++) {
                try {
                    data = cursor.getString(i);
                } catch (SQLException e) {
                    if (Objects.requireNonNull(e.getMessage()).contains("Unable to convert BLOB to string"))
                        data = "(BLOB)";
                }
                colData.add(new TableRecordItem(String.valueOf(i), data));
            }
            Tabledata.add(colData);
        }
        cursor.close();
        return Tabledata;
    }

    @Override
    public void close() throws IOException {
        boolean isRw = !db.isReadOnly();
        db.close();
        if (isRw && cachedPath != null) {
            // Write the DB back to the real path
            try (InputStream is = new FileInputStream(cachedPath);
                 OutputStream os = realPath.openOutputStream()) {
                IoUtils.copy(is, os);
            }
        }
        fileCache.close();
    }

    @Nullable
    private File getCachedPathIfRequired(boolean create, boolean readOnly) throws IOException {
        File file = realPath.getFile();
        if (file != null) {
            // Backed by a file
            if (realPath.exists()) {
                if ((readOnly && FileUtils.canRead(file)) || (!readOnly && file.canWrite())) {
                    Log.i(TAG, "Opening DB without caching.");
                    return null;
                }
                // Requires caching
                return fileCache.getCachedFile(realPath);
            }
            // File does not exist
            if (create && !file.createNewFile()) {
                // Could not create new file, requires caching
                return fileCache.createCachedFile("db");
            }
            if (file.exists()) {
                // No caching required as the file was created
                Log.i(TAG, "Opening DB without caching.");
                return null;
            }
            // Caching required but the file didn't exist in first place
            throw new FileNotFoundException(file + " does not exist");
        }
        // Not backed by a file, requires caching
        if (realPath.exists()) {
            return fileCache.getCachedFile(realPath);
        }
        // Path does not exist, create if necessary
        if (create) {
            return fileCache.createCachedFile("db");
        }
        // Caching required but the file didn't exist in first place
        throw new FileNotFoundException(realPath + " does not exist");
    }

    public static String getColumnDataType(int type) {
        switch (type) {
            case 1:
                return "INTEGER";
            case 2:
                return "FLOAT";
            case 3:
                return "STRING";
            case 4:
                return "BLOB";
            default:
                return "null";
        }
    }
}
