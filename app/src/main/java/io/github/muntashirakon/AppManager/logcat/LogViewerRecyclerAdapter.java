// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.Collections;
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
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogViewerRecyclerAdapter extends MultiSelectionView.Adapter<LogViewerRecyclerAdapter.ViewHolder>
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

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock. This lock is also
     * used by the filter (see {@link #getFilter()} to make a synchronized copy of
     * the original array of data.
     */
    private final Object mLock = new Object();
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    @GuardedBy("mLock")
    private List<LogLine> mObjects;

    private ViewHolder.OnSearchByClickListener mSearchByClickListener;

    private ArrayList<LogLine> mOriginalValues;
    private ArrayFilter mFilter;

    private int mLogLevelLimit = Prefs.LogViewer.getLogLevel();
    private final Set<LogLine> mSelectedLogLines = new LinkedHashSet<>();

    public LogViewerRecyclerAdapter() {
        mObjects = new ArrayList<>();
        setHasStableIds(true);
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    @GuardedBy("mLock")
    public void add(LogLine object, boolean notify) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            }
            mObjects.add(object);
            if (notify) {
                notifyItemInserted(mObjects.size() - 1);
            }
        }
    }

    @GuardedBy("mLock")
    public void readAll(LogLine object, boolean notify) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            }
            mObjects.add(object);
            if (notify) {
                notifyItemInserted(mObjects.size() - 1);
            }
        }
    }

    public void addWithFilter(@NonNull LogLine object, @Nullable SearchCriteria searchCriteria, boolean notify) {
        if (mOriginalValues != null) {
            List<LogLine> inputList = Collections.singletonList(object);
            if (mFilter == null) {
                mFilter = new ArrayFilter();
            }
            List<LogLine> filteredObjects = mFilter.performFilteringOnList(inputList, searchCriteria);
            synchronized (mLock) {
                mOriginalValues.add(object);
                mObjects.addAll(filteredObjects);
                if (!filteredObjects.isEmpty() && notify) {
                    notifyItemRangeInserted(mObjects.size() - filteredObjects.size(), filteredObjects.size());
                }
            }
        } else {
            synchronized (mLock) {
                mObjects.add(object);
                if (notify) {
                    notifyItemInserted(mObjects.size() - 1);
                }
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
            if (mOriginalValues != null) {
                mOriginalValues.add(index, object);
            } else {
                mObjects.add(index, object);
                notifyItemChanged(index);
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
            if (mOriginalValues != null) {
                mOriginalValues.remove(object);
            } else {
                int pos = mObjects.indexOf(object);
                if (pos >= 0) {
                    mObjects.remove(pos);
                    notifyItemRemoved(pos);
                }
            }
        }
    }

    public void removeFirst(int n) {
        StopWatch stopWatch = new StopWatch("removeFirst()");
        if (mOriginalValues != null) {
            synchronized (mLock) {
                List<LogLine> subList = mOriginalValues.subList(n, mOriginalValues.size());
                for (int i = 0; i < n; i++) {
                    int pos = mObjects.indexOf(mOriginalValues.get(i));
                    if (pos >= 0) {
                        mObjects.remove(pos);
                        notifyItemRemoved(pos);
                    }
                }
                mOriginalValues = new ArrayList<>(subList);
            }
        } else {
            synchronized (mLock) {
                mObjects = new ArrayList<>(mObjects.subList(n, mObjects.size()));
                notifyItemRangeRemoved(0, n);
            }
        }
        stopWatch.log();
    }

    /**
     * Remove all elements from the list.
     */
    @GuardedBy("mLock")
    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.clear();
            }
            int size = mObjects.size();
            mObjects.clear();
            notifyItemRangeRemoved(0, size);
        }
    }

    @GuardedBy("mLock")
    public LogLine getItem(int position) {
        synchronized (mLock) {
            return mObjects.get(position);
        }
    }

    @Nullable
    @GuardedBy("mLock")
    private LogLine getItemSafe(int position) {
        synchronized (mLock) {
            if (mObjects.size() > position) {
                return mObjects.get(position);
            }
            return null;
        }
    }

    @GuardedBy("mLock")
    public int getRealSize() {
        synchronized (mLock) {
            return (mOriginalValues != null ? mOriginalValues : mObjects).size();
        }
    }

    public Set<LogLine> getSelectedLogLines() {
        return mSelectedLogLines;
    }

    @GuardedBy("mLock")
    public void setCollapseMode(boolean isCollapsed) {
        synchronized (mLock) {
            List<LogLine> list = mOriginalValues != null ? mOriginalValues : mObjects;
            for (LogLine logLine : list) {
                logLine.setExpanded(!isCollapsed);
            }
        }
    }

    @Override
    protected void select(int position) {
        synchronized (mSelectedLogLines) {
            LogLine logLine = getItemSafe(position);
            if (logLine != null) {
                mSelectedLogLines.add(logLine);
            }
        }
    }

    @Override
    protected void deselect(int position) {
        synchronized (mSelectedLogLines) {
            LogLine logLine = getItemSafe(position);
            if (logLine != null) {
                mSelectedLogLines.remove(logLine);
            }
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
        holder.contentView.setBackgroundResource(position % 2 == 0 ? io.github.muntashirakon.ui.R.drawable.item_semi_transparent : io.github.muntashirakon.ui.R.drawable.item_transparent);

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
            if (isInSelectionMode()) {
                toggleSelection(position);
            } else {
                LogLine line = holder.logLine;
                line.setExpanded(!line.isExpanded());
                notifyItemChanged(position);
            }
        });
        // Long click on the item:
        // 1. If it is in selection mode, select range of item
        // 2. Open context menu
        holder.itemView.setOnLongClickListener(v -> {
            if (isInSelectionMode()) {
                int lastSelectedItemPosition = getLastSelectedItemPosition();
                if (lastSelectedItemPosition >= 0) {
                    // Select from last selection to this selection
                    selectRange(lastSelectedItemPosition, position);
                } else toggleSelection(position);
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
                        toggleSelection(position);
                        return true;
                    });
            popupMenu.show();
            return true;
        });
        super.onBindViewHolder(holder, position);
    }

    @GuardedBy("mLock")
    @Override
    public long getItemId(int position) {
        synchronized (mLock) {
            return mObjects.get(position).getOriginalLine().hashCode();
        }
    }

    @GuardedBy("mLock")
    @Override
    public int getItemCount() {
        synchronized (mLock) {
            return mObjects.size();
        }
    }

    public int getLogLevelLimit() {
        return mLogLevelLimit;
    }

    public void setLogLevelLimit(int logLevelLimit) {
        mLogLevelLimit = logLevelLimit;
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
            int i = 0;
            for (LogLine fmItem : mObjects) {
                if (fmItem.equals(lastItem)) {
                    return i;
                }
                ++i;
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

            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
            }

            SearchCriteria searchCriteria = new SearchCriteria(prefix != null ? prefix.toString() : null);
            ArrayList<LogLine> allValues = performFilteringOnList(mOriginalValues, searchCriteria);

            results.values = allValues;
            results.count = allValues.size();

            return results;
        }

        public ArrayList<LogLine> performFilteringOnList(List<LogLine> inputList, @Nullable SearchCriteria searchCriteria) {
            // search by log level
            ArrayList<LogLine> allValues = new ArrayList<>();

            ArrayList<LogLine> logLines;
            synchronized (mLock) {
                logLines = new ArrayList<>(inputList);
            }

            for (LogLine logLine : logLines) {
                if (logLine != null && logLine.getLogLevel() >= mLogLevelLimit) {
                    allValues.add(logLine);
                }
            }
            ArrayList<LogLine> finalValues = allValues;

            // search by criteria
            if (searchCriteria != null && !searchCriteria.isEmpty()) {
                final int count = allValues.size();
                final ArrayList<LogLine> newValues = new ArrayList<>(count);
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
                int previousCount = mObjects != null ? mObjects.size() : 0;
                mObjects = (List<LogLine>) results.values;
                AdapterUtils.notifyDataSetChanged(LogViewerRecyclerAdapter.this, previousCount, mObjects.size());
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
