// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.RestartUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class BatchOpsResultsActivity extends BaseActivity {
    private RecyclerView mRecyclerView;
    private AppCompatEditText mLogViewer;

    @Nullable
    private BatchQueueItem mBatchQueueItem;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (getIntent() == null) {
            finish();
            return;
        }
        if (restartIfNeeded(getIntent())) {
            return;
        }
        setContentView(R.layout.activity_batch_ops_results);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        mRecyclerView = findViewById(R.id.list);
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        MaterialButton logToggler = findViewById(R.id.action_view_logs);
        mLogViewer = findViewById(R.id.text);
        mLogViewer.setKeyListener(null);
        logToggler.setOnClickListener(v -> mLogViewer.setVisibility(View.VISIBLE));
        handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (restartIfNeeded(getIntent())) {
            return;
        }
        handleIntent(intent);
    }

    private void handleIntent(@NonNull Intent intent) {
        mBatchQueueItem = IntentCompat.getParcelableExtra(intent, BatchOpsService.EXTRA_QUEUE_ITEM, BatchQueueItem.class);
        if (mBatchQueueItem == null) {
            finish();
            return;
        }
        setTitle(intent.getStringExtra(BatchOpsService.EXTRA_FAILURE_MESSAGE));
        ArrayList<CharSequence> packageLabels = PackageUtils.packagesToAppLabels(getPackageManager(),
                mBatchQueueItem.getPackages(), mBatchQueueItem.getUsers());
        RecyclerAdapter adapter = new RecyclerAdapter(packageLabels);
        mRecyclerView.setAdapter(adapter);
        if (packageLabels != null) {
            adapter.notifyItemRangeInserted(0, packageLabels.size());
        }
        mLogViewer.setText(getFormattedLogs(BatchOpsLogger.getAllLogs()));
        intent.removeExtra(BatchOpsService.EXTRA_QUEUE_ITEM);
    }

    private static boolean restartIfNeeded(@NonNull Intent intent) {
        if (intent.getBooleanExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, false)) {
            RestartUtils.restart(RestartUtils.RESTART_NORMAL);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_batch_ops_results_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_retry) {
            Intent BatchOpsIntent = new Intent(this, BatchOpsService.class);
            BatchOpsIntent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, mBatchQueueItem);
            ContextCompat.startForegroundService(this, BatchOpsIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        BatchOpsLogger.clearLogs();
        super.onDestroy();
    }

    public CharSequence getFormattedLogs(String logs) {
        SpannableString str = new SpannableString(logs);
        int fIndex = 0;
        while(true) {
            fIndex = logs.indexOf("====> ", fIndex);
            if (fIndex == -1) {
                return str;
            }
            int lIndex = logs.indexOf('\n', fIndex);
            str.setSpan(new StyleSpan(Typeface.BOLD), fIndex, lIndex, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            fIndex = lIndex;
        }
    }

    static class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> mAppLabels;

        public RecyclerAdapter(@Nullable List<CharSequence> appLabels) {
            mAppLabels = appLabels == null ? Collections.emptyList() : appLabels;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.itemView.setText(mAppLabels.get(position));
        }

        @Override
        public int getItemCount() {
            return mAppLabels.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (TextView) itemView;
            }
        }
    }
}
