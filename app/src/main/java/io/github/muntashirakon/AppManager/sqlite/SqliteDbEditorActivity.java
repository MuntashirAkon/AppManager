// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sqlite;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.evrencoskun.tableview.TableView;
import com.evrencoskun.tableview.listener.ITableViewListener;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;

public class SqliteDbEditorActivity extends BaseActivity implements ITableViewListener {
    private SqliteDbTableViewAdapter mTableViewAdapter;
    private AppCompatSpinner mTableSelectionSpinner;
    private ArrayAdapter<String> mTableSelectionSpinnerAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private SqliteDbEditorViewModel mViewModel;
    private final ActivityResultLauncher<String> mCsvExporter = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri == null) {
                    return;
                }
                mProgressIndicator.show();
                mViewModel.exportTableAsCsv(uri);
            }
    );

    private static List<RecordItemHeader> generateRowHeader(List<List<TableRecordItem>> tableData, long offset) {
        List<RecordItemHeader> rowHeader = new ArrayList<>();
        long localOffset = offset;
        for (long i = 0; i < tableData.size(); i++) {
            rowHeader.add(new RecordItemHeader(String.valueOf(i), String.valueOf(localOffset += 1)));
        }
        return rowHeader;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_sqlite_db_editor);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(SqliteDbEditorViewModel.class);
        Uri dbUri = IntentCompat.getDataUri(getIntent());
        if (dbUri == null) {
            UIUtils.displayLongToast(R.string.failed_to_open_database);
            finish();
            return;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(dbUri.getLastPathSegment());
            // TODO: 20/2/23 Add subtitle provided through extras
        }

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);

        mTableSelectionSpinner = findViewById(R.id.spinner);
        mTableSelectionSpinnerAdapter = new ArrayAdapter<>(this, R.layout.item_checked_text_view);
        mTableSelectionSpinner.setAdapter(mTableSelectionSpinnerAdapter);
        mTableSelectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == mTableSelectionSpinnerAdapter.getCount() - 1) {
                    // TODO: 21/2/23 Display custom query prompt
                    return;
                }
                String tableName = mTableSelectionSpinnerAdapter.getItem(position);
                if (tableName != null) {
                    mViewModel.loadTable(tableName, 0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        TableView tableView = findViewById(R.id.content);
        UiUtils.applyWindowInsetsAsPaddingNoTop(tableView);
        mTableViewAdapter = new SqliteDbTableViewAdapter(this);
        tableView.setAdapter(mTableViewAdapter);
        tableView.setTableViewListener(this);

        mViewModel.getTableNames().observe(this, tables -> {
            mTableSelectionSpinnerAdapter.setNotifyOnChange(false);
            mTableSelectionSpinnerAdapter.clear();
            mTableSelectionSpinnerAdapter.addAll(tables);
            // Last position = Custom query
            mTableSelectionSpinnerAdapter.add("Custom query"); // TODO: 21/2/23 Add localization
            mTableSelectionSpinnerAdapter.notifyDataSetChanged();
        });

        mViewModel.getQueryResult().observe(this, queryResultData -> {
            mProgressIndicator.hide();
            // Set spinner
            int selectedPosition;
            if (queryResultData.tableName != null) {
                selectedPosition = mTableSelectionSpinnerAdapter.getPosition(queryResultData.tableName);
            } else {
                // Custom query
                selectedPosition = mTableSelectionSpinnerAdapter.getCount() - 1;
            }
            mTableSelectionSpinner.setSelection(selectedPosition);
            // Set rows
            List<ColumnHeader> columnHeaders = new ArrayList<>();
            for (TableColumn field : queryResultData.resultColumns) {
                columnHeaders.add(new ColumnHeader("1", field.getHeaderName()));
            }
            List<RecordItemHeader> RowHeader = generateRowHeader(queryResultData.resultRows, queryResultData.startOffset);
            mTableViewAdapter.setAllItems(columnHeaders, RowHeader, queryResultData.resultRows);
        });

        mViewModel.openDb(dbUri);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_sqlite_db_editor_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.action_export) {
            QueryResultData data = mViewModel.getLastQueryResult();
            String filename;
            if (data != null && data.tableName != null) {
                filename = data.tableName + ".csv";
            } else filename = "custom_query.csv";
            mCsvExporter.launch(filename);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onCellClicked(@NonNull RecyclerView.ViewHolder cellView, int column, int row) {

    }

    @Override
    public void onCellDoubleClicked(@NonNull RecyclerView.ViewHolder cellView, int column, int row) {

    }

    @Override
    public void onCellLongPressed(@NonNull RecyclerView.ViewHolder cellView, int column, int row) {

    }

    @Override
    public void onColumnHeaderClicked(@NonNull RecyclerView.ViewHolder columnHeaderView, int column) {

    }

    @Override
    public void onColumnHeaderDoubleClicked(@NonNull RecyclerView.ViewHolder columnHeaderView, int column) {

    }

    @Override
    public void onColumnHeaderLongPressed(@NonNull RecyclerView.ViewHolder columnHeaderView, int column) {

    }

    @Override
    public void onRowHeaderClicked(@NonNull RecyclerView.ViewHolder rowHeaderView, int row) {

    }

    @Override
    public void onRowHeaderDoubleClicked(@NonNull RecyclerView.ViewHolder rowHeaderView, int row) {

    }

    @Override
    public void onRowHeaderLongPressed(@NonNull RecyclerView.ViewHolder rowHeaderView, int row) {

    }
}

