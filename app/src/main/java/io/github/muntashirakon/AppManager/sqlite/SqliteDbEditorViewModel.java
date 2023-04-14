// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.siegmar.fastcsv.writer.CsvWriter;
import io.github.muntashirakon.io.Paths;

public class SqliteDbEditorViewModel extends AndroidViewModel {
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final MutableLiveData<QueryResultData> mQueryResult = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mTableNames = new MutableLiveData<>();

    @Nullable
    private Sqlite3Database db;
    @Nullable
    private ArrayList<String> tables;
    @Nullable
    private QueryResultData lastQueryResult;
    private final int maxRowsPerPage = 300; // TODO: 20/2/23 Load from settings

    public SqliteDbEditorViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public LiveData<QueryResultData> getQueryResult() {
        return mQueryResult;
    }

    public LiveData<List<String>> getTableNames() {
        return mTableNames;
    }

    @Nullable
    public QueryResultData getLastQueryResult() {
        return lastQueryResult;
    }

    public void openDb(@NonNull Uri dbUri) {
        mExecutor.submit(() -> {
            try {
                db = new Sqlite3Database(Paths.get(dbUri), false, true);
                tables = db.getTables();
                mTableNames.postValue(tables);
                // Load the first table
                String firstTable = !tables.isEmpty() ? tables.get(0) : null;
                if (firstTable != null) {
                    lastQueryResult = db.getTableData(firstTable, maxRowsPerPage, 0);
                    mQueryResult.postValue(lastQueryResult);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void loadTable(@NonNull String tableName, long startOffset) {
        mExecutor.submit(() -> {
            if (db != null) {
                lastQueryResult = db.getTableData(tableName, maxRowsPerPage, startOffset);
                mQueryResult.postValue(lastQueryResult);
            }
        });
    }

    public void loadCustomQuery(@NonNull String sql, long startOffset) {
        mExecutor.submit(() -> {
            if (db != null) {
                lastQueryResult = db.getQueryData(sql, maxRowsPerPage, startOffset);
                mQueryResult.postValue(lastQueryResult);
            }
        });
    }

    public void refreshLastQuery() {
        mExecutor.submit(() -> {
            if (db != null && lastQueryResult != null) {
                if (lastQueryResult.tableName != null) {
                    lastQueryResult = db.getTableData(lastQueryResult.tableName, lastQueryResult.limit, lastQueryResult.startOffset);
                } else if (lastQueryResult.query != null) {
                    lastQueryResult = db.getQueryData(lastQueryResult.query, lastQueryResult.limit, lastQueryResult.startOffset);
                } else {
                    throw new IllegalStateException("Neither table nor custom query was defined in the last query.");
                }
                mQueryResult.postValue(lastQueryResult);
            }
        });
    }

    public void exportTableAsCsv(@NonNull Uri csvUri) {
        mExecutor.submit(() -> {
            if (lastQueryResult == null) {
                return;
            }
            try (CsvWriter br = CsvWriter.builder().build(new PrintWriter(Paths.get(csvUri).openOutputStream(), true))) {
                // Write header
                List<String> columns = new ArrayList<>(lastQueryResult.resultColumns.size());
                for (TableColumn column : lastQueryResult.resultColumns) {
                    columns.add(column.name);
                }
                br.writeRow(columns);
                // Write data
                for (List<TableRecordItem> tableRecordItems : lastQueryResult.resultRows) {
                    // Write per row
                    List<String> row = new ArrayList<>(tableRecordItems.size());
                    for (TableRecordItem tableRecordItem : tableRecordItems) {
                        if (tableRecordItem.data == null) {
                            row.add(null);
                        } else {
                            row.add(tableRecordItem.data.toString());
                        }
                    }
                    br.writeRow(row);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
