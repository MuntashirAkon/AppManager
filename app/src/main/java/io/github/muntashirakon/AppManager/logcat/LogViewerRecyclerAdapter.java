// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.collection.SparseArrayCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.util.AccessibilityUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogViewerRecyclerAdapter extends MultiSelectionView.Adapter<LogLine, LogViewerRecyclerAdapter.ViewHolder>
        implements Filterable {
    public static final String TAG = LogViewerRecyclerAdapter.class.getSimpleName();

    private static final SparseArrayCompat<Integer> BACKGROUND_COLORS = new SparseArrayCompat<Integer>(7) {
        {
            put(android.util.Log.VERBOSE, io.github.muntashirakon.ui.R.color.the_brown_shirts);
            put(android.util.Log.DEBUG, io.github.muntashirakon.ui.R.color.night_blue_shadow);
            put(android.util.Log.INFO, io.github.muntashirakon.ui.R.color.blue_popsicle);
            put(android.util.Log.WARN, io.github.muntashirakon.ui.R.color.red_orange);
            put(android.util.Log.ERROR, io.github.muntashirakon.ui.R.color.pure_red);
            put(android.util.Log.ASSERT, io.github.muntashirakon.ui.R.color.pure_red);
            put(LogLine.LOG_FATAL, io.github.muntashirakon.ui.R.color.electric_red);
        }
    };

    private static final SparseArrayCompat<Integer> FOREGROUND_COLORS = new SparseArrayCompat<Integer>(7) {
        {
            put(android.util.Log.VERBOSE, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(android.util.Log.DEBUG, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(android.util.Log.INFO, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(android.util.Log.WARN, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(android.util.Log.ERROR, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(android.util.Log.ASSERT, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
            put(LogLine.LOG_FATAL, io.github.muntashirakon.ui.R.color.brian_wrinkle_white);
        }
    };

    private static int[] sTagColors;

    @ColorInt
    private static int getBackgroundColorForLogLevel(Context context, int logLevel) {
        Integer result = BACKGROUND_COLORS.get(logLevel);
        if (result == null) {
            throw new IllegalArgumentException("Invalid log level: " + logLevel);
        }
        return ContextCompat.getColor(context, result);
    }

    @ColorInt
    private static int getForegroundColorForLogLevel(Context context, int logLevel) {
        Integer result = FOREGROUND_COLORS.get(logLevel);
        if (result == null) {
            throw new IllegalArgumentException("Invalid log level: " + logLevel);
        }
        return ContextCompat.getColor(context, result);
    }

    private static synchronized int getOrCreateTagColor(Context context, String tag) {
        if (sTagColors == null) {
            sTagColors = context.getResources().getIntArray(R.array.random_colors);
        }
        // Ensure consistency
        int hashCode = (tag == null) ? 0 : tag.hashCode();
        int smear = Math.abs(hashCode) % sTagColors.length;
        return sTagColors[smear];
    }

    private static final DiffUtil.ItemCallback<LogLine> DIFF_CALLBACK = new DiffUtil.ItemCallback<LogLine>() {
        @Override
        public boolean areItemsTheSame(@NonNull LogLine oldItem, @NonNull LogLine newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogLine oldItem, @NonNull LogLine newItem) {
            return true;
        }
    };

    /**
     * Lock used to modify the content of {@link #mMasterList}. Any write operation
     * performed on the array should be synchronized on this lock.
     */
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final List<LogLine> mMasterList = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<LogLine> mVisibleList = new ArrayList<>();
    @GuardedBy("mLock")
    private SearchCriteria mCurrentSearchCriteria = null;
    @GuardedBy("mLock")
    private boolean mIsUpdateScheduled = false;
    @GuardedBy("mLock")
    private boolean mHasPendingUpdate = false;

    private ViewHolder.OnSearchByClickListener mSearchByClickListener;
    private ArrayFilter mFilter;

    private int mLogLevelLimit = Prefs.LogViewer.getLogLevel();
    private final Set<LogLine> mSelectedLogLines = new LinkedHashSet<>();

    private RecyclerView mAttachedRecyclerView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mAutoScroll = true;
    private final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mAutoScroll = isUserAtBottom();
            }
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy < 0) {
                // User manually scrolled up
                mAutoScroll = false;
            } else if (dy > 0 && isUserAtBottom()) {
                // User scrolled down to the bottom
                mAutoScroll = true;
            }
        }
    };

    public LogViewerRecyclerAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mAttachedRecyclerView = recyclerView;
        mAttachedRecyclerView.addOnScrollListener(mScrollListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (mAttachedRecyclerView != null) {
            mAttachedRecyclerView.removeOnScrollListener(mScrollListener);
        }
        mAttachedRecyclerView = null;
    }

    private boolean isUserAtBottom() {
        if (mAttachedRecyclerView == null || mAttachedRecyclerView.getLayoutManager() == null) {
            return false;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) mAttachedRecyclerView.getLayoutManager();
        int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
        int totalItems = layoutManager.getItemCount();
        return totalItems == 0 || lastVisibleItem >= totalItems - 2;
    }

    private void scheduleMainThreadUpdate() {
        synchronized (mLock) {
            if (mIsUpdateScheduled) {
                // A diff is currently running. Flag that we need another update
                // once the current one finishes, then bail out.
                mHasPendingUpdate = true;
                return;
            }
            mIsUpdateScheduled = true;
            mHasPendingUpdate = false;
        }

        Runnable updateTask = () -> {
            List<LogLine> snapshot;
            synchronized (mLock) {
                snapshot = new ArrayList<>(mVisibleList);
            }
            // DiffUtil processes this snapshot on a background thread
            submitList(snapshot, () -> {
                boolean shouldRunAgain = false;
                synchronized (mLock) {
                    mIsUpdateScheduled = false; // Only release the lock after rendering completes
                    if (mHasPendingUpdate) {
                        shouldRunAgain = true;
                    }
                }
                if (mAutoScroll && mAttachedRecyclerView != null) {
                    int newCount = getItemCount();
                    if (newCount > 0) {
                        mAttachedRecyclerView.scrollToPosition(newCount - 1);
                    }
                }
                // If logs arrived while we were diffing, trigger the next batched update immediately
                if (shouldRunAgain) {
                    scheduleMainThreadUpdate();
                }
            });
        };
        if (mAttachedRecyclerView != null) {
            mAttachedRecyclerView.post(updateTask);
        } else {
            mMainHandler.post(updateTask);
        }
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    @GuardedBy("mLock")
    public void add(LogLine object, boolean notify) {
        addWithFilter(object, null, notify);
    }

    @GuardedBy("mLock")
    public void readAll(LogLine object, boolean notify) {
        addWithFilter(object, null, notify);
    }

    public void addWithFilter(@NonNull LogLine object, @Nullable SearchCriteria ignoredCriteria, boolean notify) {
        synchronized (mLock) {
            mMasterList.add(object);
            boolean matches = false;
            if (object.getLogLevel() >= mLogLevelLimit) {
                if (mCurrentSearchCriteria == null || mCurrentSearchCriteria.isEmpty()) {
                    matches = true;
                } else if (mCurrentSearchCriteria.matches(object)) {
                    matches = true;
                }
            }
            if (matches) {
                mVisibleList.add(object);
            }
            if (notify) {
                scheduleMainThreadUpdate();
            }
        }
    }

    /**
     * Inserts the specified object at the specified index in the array.
     *
     * @param object The object to insert into the array.
     * @param index  The index at which the object must be inserted.
     */
    @GuardedBy("mLock")
    public void insert(LogLine object, int index) {
        synchronized (mLock) {
            if (index >= 0 && index <= mMasterList.size()) {
                mMasterList.add(index, object);
                if (mFilter == null) {
                    mFilter = new ArrayFilter();
                }
                mFilter.filter(mCurrentSearchCriteria != null ? mCurrentSearchCriteria.query : null);
            }
        }
    }

    /**
     * Removes the specified object from the array.
     *
     * @param object The object to remove.
     */
    @GuardedBy("mLock")
    public void remove(LogLine object) {
        synchronized (mLock) {
            mMasterList.remove(object);
            mVisibleList.remove(object);
            scheduleMainThreadUpdate();
        }
    }

    public void removeFirst(int n) {
        StopWatch stopWatch = new StopWatch("removeFirst()");
        synchronized (mLock) {
            if (mMasterList.size() >= n) {
                List<LogLine> toRemove = mMasterList.subList(0, n);
                mVisibleList.removeAll(new HashSet<>(toRemove));
                toRemove.clear();
                scheduleMainThreadUpdate();
            }
        }
        stopWatch.log();
    }

    @GuardedBy("mLock")
    public void clear() {
        synchronized (mLock) {
            mMasterList.clear();
            mVisibleList.clear();
            scheduleMainThreadUpdate();
        }
    }

    @Nullable
    @GuardedBy("mLock")
    private LogLine getItemSafe(int position) {
        List<LogLine> current = getCurrentList();
        if (position >= 0 && position < current.size()) {
            return current.get(position);
        }
        return null;
    }

    @GuardedBy("mLock")
    public int getRealSize() {
        synchronized (mLock) {
            return mMasterList.size();
        }
    }

    public Set<LogLine> getSelectedLogLines() {
        return mSelectedLogLines;
    }

    @GuardedBy("mLock")
    public void setCollapseMode(boolean isCollapsed) {
        synchronized (mLock) {
            for (LogLine logLine : mMasterList) {
                logLine.setExpanded(!isCollapsed);
            }
            scheduleMainThreadUpdate();
        }
    }

    @Override
    protected boolean select(int position) {
        synchronized (mSelectedLogLines) {
            LogLine logLine = getItemSafe(position);
            if (logLine != null) {
                mSelectedLogLines.add(logLine);
            }
            return logLine != null;
        }
    }

    @Override
    protected boolean deselect(int position) {
        synchronized (mSelectedLogLines) {
            LogLine logLine = getItemSafe(position);
            if (logLine != null) {
                return mSelectedLogLines.remove(logLine);
            }
            return false;
        }
    }

    @Override
    protected boolean isSelected(int position) {
        synchronized (mSelectedLogLines) {
            LogLine logLine = getItemSafe(position);
            if (logLine != null) {
                return mSelectedLogLines.contains(logLine);
            }
            return false;
        }
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        synchronized (mSelectedLogLines) {
            mSelectedLogLines.clear();
        }
    }

    @Override
    protected int getSelectedItemCount() {
        synchronized (mSelectedLogLines) {
            return mSelectedLogLines.size();
        }
    }

    @Override
    protected int getTotalItemCount() {
        return getItemCount();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_logcat, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        LogLine logLine = getItem(position);
        holder.logLine = logLine;

        int levelColor = getBackgroundColorForLogLevel(context, logLine.getLogLevel());
        TextView t = holder.logLevel;
        t.setText(logLine.getProcessIdText());
        t.setBackgroundColor(levelColor);
        t.setTextColor(getForegroundColorForLogLevel(context, logLine.getLogLevel()));
        t.setVisibility(logLine.getLogLevel() == -1 ? View.GONE : View.VISIBLE);

        holder.itemView.setBackgroundResource(0);
        holder.contentView.setBackgroundResource(position % 2 == 0
                ? io.github.muntashirakon.ui.R.drawable.item_semi_transparent
                : io.github.muntashirakon.ui.R.drawable.item_transparent);

        // Display message
        TextView output = holder.output;
        output.setSingleLine(!logLine.isExpanded());
        output.setText(logLine.getLogOutput());

        //TAG TEXT VIEW
        TextView tag = holder.tag;
        tag.setSingleLine(!logLine.isExpanded());
        tag.setText(logLine.getTagName());
        tag.setVisibility(logLine.getLogLevel() == -1 ? View.GONE : View.VISIBLE);

        //EXPANDED INFO
        boolean extraInfoIsVisible = logLine.isExpanded() && logLine.getPid() != -1 // -1 marks lines like 'beginning of /dev/log...'
                && Prefs.LogViewer.showPidTidTimestamp();

        TextView infoText = holder.info;
        infoText.setVisibility(extraInfoIsVisible ? View.VISIBLE : View.GONE);

        if (extraInfoIsVisible) {
            StringBuilder sb = new StringBuilder(logLine.getTimestamp());
            if (logLine.getPid() >= 0) {
                sb.append(" • ").append(logLine.getPid());
            }
            if (logLine.getUidOwner() != null) {
                sb.append(" • ").append(logLine.getUidOwner());
            }
            if (logLine.getPackageName() != null) {
                sb.append(" • ").append(logLine.getPackageName());
            }
            infoText.setText(sb);
        }

        tag.setTextColor(getOrCreateTagColor(context, logLine.getTagName()));
        // Single click on the item:
        // 1. If it is in selection mode, select the item
        // 2. Otherwise, expand the item
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            if (isInSelectionMode()) {
                toggleSelection(currentPos);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
            } else {
                LogLine line = holder.logLine;
                line.setExpanded(!line.isExpanded());
                notifyItemChanged(currentPos, AdapterUtils.STUB);
            }
        });
        // Long click on the item:
        // 1. If it is in selection mode, select range of item
        // 2. Open context menu
        holder.itemView.setOnLongClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return false;
            if (isInSelectionMode()) {
                int lastSelectedItemPosition = getLastSelectedItemPosition();
                if (lastSelectedItemPosition >= 0) {
                    // Select from last selection to this selection
                    selectRange(lastSelectedItemPosition, currentPos);
                } else {
                    toggleSelection(currentPos);
                    AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
                }
                return true;
            }
            PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
            popupMenu.setForceShowIcon(true);
            Menu menu = popupMenu.getMenu();
            menu.add(R.string.filter_choice)
                    .setIcon(io.github.muntashirakon.ui.R.drawable.ic_search)
                    .setOnMenuItemClickListener(menuItem -> {
                        if (mSearchByClickListener != null) {
                            return mSearchByClickListener.onSearchByClick(menuItem, holder.logLine);
                        }
                        return true;
                    });
            menu.add(R.string.copy_to_clipboard)
                    .setIcon(R.drawable.ic_content_copy)
                    .setOnMenuItemClickListener(menuItem -> {
                        Utils.copyToClipboard(context, null, holder.logLine.getOriginalLine());
                        return true;
                    });
            menu.add(R.string.item_select)
                    .setIcon(R.drawable.ic_check_circle)
                    .setOnMenuItemClickListener(menuItem -> {
                        toggleSelection(currentPos);
                        AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
                        return true;
                    });
            popupMenu.show();
            return true;
        });
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        LogLine item = getItemSafe(position);
        // identityHashCode guarantees uniqueness even if log texts are identical
        return item != null ? System.identityHashCode(item) : RecyclerView.NO_ID;
    }

    public int getLogLevelLimit() {
        return mLogLevelLimit;
    }

    public void setLogLevelLimit(int logLevelLimit) {
        synchronized (mLock) {
            mLogLevelLimit = logLevelLimit;
        }
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        mFilter.filter(mCurrentSearchCriteria != null ? mCurrentSearchCriteria.query : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    public void setClickListener(ViewHolder.OnSearchByClickListener clickListener) {
        mSearchByClickListener = clickListener;
    }

    private int getLastSelectedItemPosition() {
        // Last selected item is the same as the last added item.
        Iterator<LogLine> it = mSelectedLogLines.iterator();
        LogLine lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        if (lastItem != null) {
            List<LogLine> visibleList = getCurrentList();
            for (int i = 0; i < visibleList.size(); i++) {
                if (visibleList.get(i) == lastItem) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter extends Filter {
        @NonNull
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            SearchCriteria searchCriteria = new SearchCriteria(prefix != null ? prefix.toString() : null);
            List<LogLine> filtered;
            synchronized (mLock) {
                mCurrentSearchCriteria = searchCriteria;
                filtered = performFilteringOnList(mMasterList, searchCriteria);
            }
            results.values = filtered;
            results.count = filtered.size();
            return results;
        }

        public ArrayList<LogLine> performFilteringOnList(List<LogLine> inputList, @Nullable SearchCriteria searchCriteria) {
            // search by log level
            ArrayList<LogLine> allValues = new ArrayList<>();
            for (LogLine logLine : inputList) {
                if (logLine != null && logLine.getLogLevel() >= mLogLevelLimit) {
                    allValues.add(logLine);
                }
            }
            ArrayList<LogLine> finalValues = allValues;

            // search by criteria
            if (searchCriteria != null && !searchCriteria.isEmpty()) {
                final ArrayList<LogLine> newValues = new ArrayList<>();
                for (final LogLine value : allValues) {
                    // search the logline based on the criteria
                    if (searchCriteria.matches(value)) {
                        newValues.add(value);
                    }
                }
                finalValues = newValues;
            }
            return finalValues;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            synchronized (mLock) {
                mVisibleList.clear();
                if (results.values != null) {
                    mVisibleList.addAll((List<LogLine>) results.values);
                }
                scheduleMainThreadUpdate();
            }
        }
    }

    private static class StopWatch {
        private long mStartTime;
        private String mName;

        public StopWatch(String name) {
            if (BuildConfig.DEBUG) {
                mName = name;
                mStartTime = System.currentTimeMillis();
            }
        }

        public void log() {
            Log.d(TAG, "%s took %d ms", mName, (System.currentTimeMillis() - mStartTime));
        }
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        LogLine logLine;
        View contentView;
        TextView logLevel;
        TextView tag;
        TextView output;
        TextView info;

        public ViewHolder(View itemView) {
            super(itemView);
            contentView = itemView.findViewById(R.id.log_content);
            logLevel = itemView.findViewById(R.id.log_level_text);
            tag = itemView.findViewById(R.id.tag_text);
            output = itemView.findViewById(R.id.log_output_text);
            info = itemView.findViewById(R.id.info);
        }

        public interface OnSearchByClickListener {
            boolean onSearchByClick(MenuItem item, LogLine logLine);
        }
    }
}
