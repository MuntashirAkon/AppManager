// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class OpHistoryActivity extends BaseActivity {
    private OpHistoryViewModel mViewModel;
    private OpHistoryAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_op_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(OpHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setEmptyView(findViewById(android.R.id.empty));
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new OpHistoryAdapter(this);
        listView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> {
            mProgressIndicator.show();
            mViewModel.clearHistory();
        });
        mViewModel.getOpHistoriesLiveData().observe(this, opHistories -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(opHistories);
        });
        mViewModel.getClearHistoryLiveData().observe(this, cleared ->
                UIUtils.displayShortToast(cleared ? R.string.done : R.string.failed));
        mProgressIndicator.show();
        mViewModel.loadOpHistories();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    static class OpHistoryAdapter extends RecyclerView.Adapter<OpHistoryAdapter.ViewHolder> {
        private OpHistory[] mAdapterList;
        private final OpHistoryActivity mActivity;

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView summary;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.title);
                summary = itemView.findViewById(android.R.id.summary);
                itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
            }
        }

        OpHistoryAdapter(@NonNull OpHistoryActivity activity) {
            mActivity = activity;
        }

        void setDefaultList(@NonNull List<OpHistory> list) {
            mAdapterList= list.toArray(new OpHistory[0]);
            int previousCount = getItemCount();
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList.length);
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @Override
        public long getItemId(int position) {
            return mAdapterList[position].hashCode();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OpHistory history = mAdapterList[position];
            holder.title.setText(history.type);
            holder.summary.setText(DateUtils.formatLongDateTime(mActivity, history.execTime));
            holder.itemView.setOnClickListener(v -> {
                // TODO: 1/26/25 Display history info
            });
            holder.itemView.setOnLongClickListener(v -> {
                // TODO: 1/26/25 Possible long click options
                //  1. Apply
                //  2. Delete
                //  3. Add as a profile (for profile and batch op)
                //  4. Export (for profile)
                //  5. Create shortcut
                return true;
            });
        }
    }

    public static class OpHistoryViewModel extends AndroidViewModel {
        private final MutableLiveData<List<OpHistory>> mOpHistoriesLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mClearHistoryLiveData = new MutableLiveData<>();
        private Future<?> mOpHistoriesResult;

        public OpHistoryViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<List<OpHistory>> getOpHistoriesLiveData() {
            return mOpHistoriesLiveData;
        }

        public LiveData<Boolean> getClearHistoryLiveData() {
            return mClearHistoryLiveData;
        }

        public void loadOpHistories() {
            if (mOpHistoriesResult != null) {
                mOpHistoriesResult.cancel(true);
            }
            mOpHistoriesResult = ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    List<OpHistory> opHistories = OpHistoryManager.getAllHistoryItems();
                    Collections.sort(opHistories, (o1, o2) -> -Long.compare(o1.execTime, o2.execTime));
                    mOpHistoriesLiveData.postValue(opHistories);
                }
            });
        }

        public void clearHistory() {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    OpHistoryManager.clearAllHistory();
                    mClearHistoryLiveData.postValue(true);
                    mOpHistoriesLiveData.postValue(Collections.emptyList());
                }
            });
        }
    }
}
