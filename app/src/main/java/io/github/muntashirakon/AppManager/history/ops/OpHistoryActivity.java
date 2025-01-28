// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
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
        listView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        listView.setEmptyView(findViewById(android.R.id.empty));
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new OpHistoryAdapter(this);
        listView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    mProgressIndicator.show();
                    mViewModel.clearHistory();
                })
                .show());
        mViewModel.getOpHistoriesLiveData().observe(this, opHistories -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(opHistories);
        });
        mViewModel.getClearHistoryLiveData().observe(this, cleared ->
                UIUtils.displayShortToast(cleared ? R.string.done : R.string.failed));
        mViewModel.getServiceLauncherIntentLiveData().observe(this, intent -> {
            if (intent != null) {
                ContextCompat.startForegroundService(this, intent);
            } else {
                UIUtils.displayShortToast(R.string.failed);
            }
        });
        OpHistoryManager.getHistoryAddedLiveData().observe(this, opHistory -> {
            // New history added
            mProgressIndicator.show();
            mViewModel.loadOpHistories();
        });
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
        private OpHistoryItem[] mAdapterList;
        private final OpHistoryActivity mActivity;
        private final int mColorSuccess;
        private final int mColorFailure;

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView type;
            TextView title;
            TextView execTime;
            Button execBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                type = itemView.findViewById(R.id.type);
                title = itemView.findViewById(android.R.id.title);
                execTime = itemView.findViewById(android.R.id.summary);
                execBtn = itemView.findViewById(R.id.item_action);
            }
        }

        OpHistoryAdapter(@NonNull OpHistoryActivity activity) {
            mActivity = activity;
            mColorSuccess = ColorCodes.getSuccessColor(activity);
            mColorFailure = ColorCodes.getFailureColor(activity);
        }

        void setDefaultList(@NonNull List<OpHistoryItem> list) {
            mAdapterList = list.toArray(new OpHistoryItem[0]);
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
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_op_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OpHistoryItem history = mAdapterList[position];
            holder.itemView.setStrokeColor(history.getStatus() ? mColorSuccess : mColorFailure);
            holder.type.setText(history.getLocalizedType(mActivity));
            holder.title.setText(history.getLabel(mActivity));
            holder.execTime.setText(DateUtils.formatLongDateTime(mActivity, history.getTimestamp()));
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
            holder.execBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.title_confirm_execution)
                    .setMessage(R.string.are_you_sure)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, (dialog, which) ->
                            mActivity.mViewModel.getServiceLauncherIntent(history))
                    .show());
        }
    }

    public static class OpHistoryViewModel extends AndroidViewModel {
        private final MutableLiveData<List<OpHistoryItem>> mOpHistoriesLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mClearHistoryLiveData = new MutableLiveData<>();
        private final MutableLiveData<Intent> mServiceLauncherIntentLiveData = new MutableLiveData<>();
        private Future<?> mOpHistoriesResult;

        public OpHistoryViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<List<OpHistoryItem>> getOpHistoriesLiveData() {
            return mOpHistoriesLiveData;
        }

        public LiveData<Boolean> getClearHistoryLiveData() {
            return mClearHistoryLiveData;
        }

        public MutableLiveData<Intent> getServiceLauncherIntentLiveData() {
            return mServiceLauncherIntentLiveData;
        }

        public void loadOpHistories() {
            if (mOpHistoriesResult != null) {
                mOpHistoriesResult.cancel(true);
            }
            mOpHistoriesResult = ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    List<OpHistory> opHistories = OpHistoryManager.getAllHistoryItems();
                    Collections.sort(opHistories, (o1, o2) -> -Long.compare(o1.execTime, o2.execTime));
                    List<OpHistoryItem> opHistoryItems = new ArrayList<>(opHistories.size());
                    for (OpHistory history : opHistories) {
                        try {
                            opHistoryItems.add(new OpHistoryItem(history));
                        } catch (JSONException e) {
                            Log.w(TAG, e.getMessage(), e);
                        }
                    }
                    mOpHistoriesLiveData.postValue(opHistoryItems);
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

        public void getServiceLauncherIntent(@NonNull OpHistoryItem opHistoryItem) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Intent intent = OpHistoryManager.getExecutableIntent(getApplication(), opHistoryItem);
                    mServiceLauncherIntentLiveData.postValue(intent);
                } catch (JSONException e) {
                    Log.w(TAG, e.getMessage(), e);
                    mServiceLauncherIntentLiveData.postValue(null);
                }
            });
        }
    }
}
