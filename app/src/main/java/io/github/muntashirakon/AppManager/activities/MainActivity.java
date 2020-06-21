package io.github.muntashirakon.AppManager.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.types.ApplicationItem;
import io.github.muntashirakon.AppManager.MainLoader;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.Utils;

import static androidx.appcompat.app.ActionBar.LayoutParams;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        SearchView.OnQueryTextListener, LoaderManager.LoaderCallbacks<List<ApplicationItem>>,
        SwipeRefreshLayout.OnRefreshListener {
    public static final String EXTRA_PACKAGE_LIST = "EXTRA_PACKAGE_LIST";
    public static final String EXTRA_LIST_NAME = "EXTRA_LIST_NAME";
    /**
     * A list of packages separated by \r\n. Debug apps should have a * after their package names.
     */
    public static String packageList;
    /**
     * The name of this particular package list
     */
    public static String listName;

    private static Collator sCollator = Collator.getInstance();

    private static final int[] sSortMenuItemIdsMap = {R.id.action_sort_domain,
            R.id.action_sort_name,R.id.action_sort_pkg,
            R.id.action_sort_installation,R.id.action_sort_sharedid,
            R.id.action_sort_size,R.id.action_sort_sha};

    private static final int SORT_DOMAIN = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_PKG = 2;
    private static final int SORT_INSTALLATION = 3;
    private static final int SORT_SHARED_ID = 4;
    private static final int SORT_SIZE = 5;
    private static final int SORT_SHA = 6;
    private static final String INSTANCE_STATE_SORT_BY = "sort_by";

    private MainActivity.Adapter mAdapter;
    private List<ApplicationItem> mItemList = new ArrayList<>();
    private int mItemSizeRetrievedCount;
    private ListView mListView;
    private ProgressBar mProgressBar;
    private LoaderManager mLoaderManager;
    private SwipeRefreshLayout mSwipeRefresh;
    private static String mConstraint;

    private int mSortBy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        packageList = getIntent().getStringExtra(EXTRA_PACKAGE_LIST);
        listName = getIntent().getStringExtra(EXTRA_LIST_NAME);
        if (listName == null) listName = "Onboard.packages";

        mProgressBar = findViewById(R.id.progress_horizontal);
        mListView = findViewById(R.id.item_list);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(this, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(this, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(null);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setTitle(getString(R.string.loading));

            SearchView searchView = new SearchView(actionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            actionBar.setCustomView(searchView, layoutParams);
        }
        if (savedInstanceState != null) {
            mSortBy = savedInstanceState.getInt(INSTANCE_STATE_SORT_BY);
        } else mSortBy = SORT_NAME;

        mListView.setOnItemClickListener(this);
        mListView.setFastScrollEnabled(true);
        mListView.setDividerHeight(0);

        mListView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            Intent appDetailsIntent = new Intent(MainActivity.this, AppDetailsActivity.class);
            appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME,
                    mAdapter.getItem(i).applicationInfo.packageName);
            MainActivity.this.startActivity(appDetailsIntent);
            return true;
        });

        mAdapter = new MainActivity.Adapter(MainActivity.this);
        mListView.setAdapter(mAdapter);

        mLoaderManager = LoaderManager.getInstance(this);
        mLoaderManager.initLoader(0, null, this);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(INSTANCE_STATE_SORT_BY, mSortBy);
        super.onSaveInstanceState(outState);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
        return true;
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                new AlertDialog.Builder(this, R.style.CustomDialog)
                        .setView(getLayoutInflater().inflate(R.layout.dialog_about, null))
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
                return true;
            case R.id.action_refresh:
                if (mSortBy == SORT_SIZE && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                    Toast t = Toast.makeText(this, getString(R.string.refresh) + " & " + getString(R.string.sort) + "/" + getString(R.string.size)
                            + "\n" + getString(R.string.unsupported), Toast.LENGTH_LONG);
                    t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
                    t.show();
                    return true;
                }
                mLoaderManager.restartLoader(0, null, this);
                return true;
            case R.id.action_sort_name:
                setSortBy(SORT_NAME);
                item.setChecked(true);
                return true;
            case R.id.action_sort_pkg:
                setSortBy(SORT_PKG);
                item.setChecked(true);
                return true;
            case R.id.action_sort_domain:
                setSortBy(SORT_DOMAIN);
                item.setChecked(true);
                return true;
            case R.id.action_sort_installation:
                setSortBy(SORT_INSTALLATION);
                item.setChecked(true);
                return true;
            case R.id.action_sort_sharedid:
                setSortBy(SORT_SHARED_ID);
                item.setChecked(true);
                return true;
            case R.id.action_sort_sha:
                setSortBy(SORT_SHA);
                item.setChecked(true);
                return true;
            case R.id.action_sort_size:
                setSortBy(SORT_SIZE);
                item.setChecked(true);
                return true;
            case R.id.action_app_usage:
                Intent intent = new Intent(this, AppUsageActivity.class);
                startActivity(intent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @NonNull
    @Override
    public Loader<List<ApplicationItem>> onCreateLoader(int id, @Nullable Bundle args) {
        showProgressBar(true);
        return new MainLoader(this);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<ApplicationItem>> loader, List<ApplicationItem> applicationItems) {
        mItemList = applicationItems;
        sortApplicationList();
        mAdapter.setDefaultList(mItemList);
        // Set title and subtitle
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(MainActivity.listName.substring(0,
                    MainActivity.listName.lastIndexOf(".")));
            actionBar.setSubtitle(MainActivity.listName.substring(
                    MainActivity.listName.lastIndexOf(".") + 1).toLowerCase());
        }
        if (Build.VERSION.SDK_INT < 26) {
            startRetrievingPackagesSize();
        }
        showProgressBar(false);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<ApplicationItem>> loader) {
        mItemList = null;
        mAdapter.setDefaultList(null);
        showProgressBar(false);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(this, AppInfoActivity.class);
        intent.putExtra(AppInfoActivity.EXTRA_PACKAGE_NAME, mAdapter.getItem(i).applicationInfo.packageName);
        startActivity(intent);
    }

    @Override
    public void onRefresh() {
        if (mSortBy == SORT_SIZE && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            Toast t = Toast.makeText(this, getString(R.string.refresh) + " & " + getString(R.string.sort) + "/" + getString(R.string.size)
                    + "\n" + getString(R.string.unsupported), Toast.LENGTH_LONG);
            t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
            t.show();
        } else {
            mLoaderManager.restartLoader(0, null, this);
        }
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null && mConstraint != null && !mConstraint.equals("")) {
            mAdapter.getFilter().filter(mConstraint);
        }
    }

    private void showProgressBar(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Sort main list if provided value is valid.
     *
     * @param sort Must be one of SORT_*
     */
    private void setSortBy(int sort) {
        mSortBy = sort;
        sortApplicationList();

        if (mAdapter != null)
            mAdapter.notifyDataSetChanged();

        if (mListView != null)
            checkFastScroll();
    }

    private void checkFastScroll() {
        mListView.setFastScrollEnabled(mSortBy == SORT_NAME);
    }

    private void sortApplicationList() {
        Collections.sort(mItemList, (item1, item2) -> {
            switch (mSortBy) {
                case SORT_NAME:
                    return sCollator.compare(item1.label, item2.label);
                case SORT_PKG:
                    return item1.applicationInfo.packageName.compareTo(item2.applicationInfo.packageName);
                case SORT_DOMAIN:
                    boolean isSystem1 = (item1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean isSystem2 = (item2.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    return Utils.compareBooleans(isSystem1, isSystem2);
                case SORT_INSTALLATION:
                    //Sort in decreasing order
                    return -item1.date.compareTo(item2.date);
                case SORT_SIZE:
                    return -item1.size.compareTo(item2.size);
                case SORT_SHARED_ID:
                    return item2.applicationInfo.uid - item1.applicationInfo.uid;
                case SORT_SHA:
                    try {
                        return item1.sha.compareTo(item2.sha);
                    } catch (NullPointerException ignored) {}
                default:
                    return 0;
            }
        });
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mConstraint = s;
        if (mAdapter != null)
            mAdapter.getFilter().filter(mConstraint);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    private void startRetrievingPackagesSize() {
        for (ApplicationItem item : mItemList)
            getItemSize(item);
    }

    private void getItemSize(@NonNull final ApplicationItem item) {
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method getPackageSizeInfo = PackageManager.class.getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(this.getPackageManager(), item.applicationInfo.packageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, final boolean succeeded) {
                    MainActivity.this.runOnUiThread(() -> {
                        if (succeeded)
                            item.size = pStats.codeSize + pStats.cacheSize + pStats.dataSize
                                    + pStats.externalCodeSize + pStats.externalCacheSize + pStats.externalDataSize
                                    + pStats.externalMediaSize + pStats.externalObbSize;
                        else
                            item.size = -1L;

                        incrementItemSizeRetrievedCount();
                    });
                }
            });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            incrementItemSizeRetrievedCount();
        }
    }

    private void incrementItemSizeRetrievedCount() {
        mItemSizeRetrievedCount++;

        if (mItemSizeRetrievedCount == mItemList.size())
            mAdapter.notifyDataSetChanged();
    }

    static class Adapter extends BaseAdapter implements SectionIndexer, Filterable {

        static final String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        @SuppressLint("SimpleDateFormat")
        static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");// hh:mm:ss");

        static class ViewHolder {
            ImageView icon;
            ImageView favorite_icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            TextView shared_id;
            TextView issuer;
            TextView sha;
            MainActivity.Adapter.IconAsyncTask iconLoader;
        }

        private Activity mActivity;
        private LayoutInflater mLayoutInflater;
        private static PackageManager mPackageManager;
        private Filter mFilter;
        private String mConstraint;
        private List<ApplicationItem> mDefaultList;
        private List<ApplicationItem> mAdapterList;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorOrange;
        private int mColorPrimary;
        private int mColorSecondary;
        private int mColorRed;

        Adapter(@NonNull Activity activity) {
            mActivity = activity;
            mLayoutInflater = activity.getLayoutInflater();
            mPackageManager = activity.getPackageManager();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = activity.getResources().getColor(R.color.SEMI_TRANSPARENT);
            mColorOrange = activity.getResources().getColor(R.color.orange);
            mColorPrimary = Utils.getThemeColor(mActivity, android.R.attr.textColorPrimary);
            mColorSecondary = Utils.getThemeColor(mActivity, android.R.attr.textColorSecondary);
            mColorRed = activity.getResources().getColor(R.color.red);
        }

        void setDefaultList(List<ApplicationItem> list) {
            mDefaultList = list;
            mAdapterList = list;
            if(MainActivity.mConstraint != null
                    && !MainActivity.mConstraint.equals("")) {
                getFilter().filter(MainActivity.mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase();
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<ApplicationItem> list = new ArrayList<>(mDefaultList.size());
                        for (ApplicationItem item : mDefaultList) {
                            if (item.label.toLowerCase().contains(constraint) ||
                                    item.applicationInfo.packageName.toLowerCase().contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<ApplicationItem>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public ApplicationItem getItem(int i) {
            return mAdapterList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            MainActivity.Adapter.ViewHolder holder;
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.item_main, viewGroup, false);
                holder = new MainActivity.Adapter.ViewHolder();
                holder.icon = view.findViewById(R.id.icon);
                holder.favorite_icon = view.findViewById(R.id.favorite_icon);
                holder.label = view.findViewById(R.id.label);
                holder.packageName = view.findViewById(R.id.packageName);
                holder.version = view.findViewById(R.id.version);
                holder.isSystemApp = view.findViewById(R.id.isSystem);
                holder.date = view.findViewById(R.id.date);
                holder.size = view.findViewById(R.id.size);
                holder.shared_id = view.findViewById(R.id.shareid);
                holder.issuer = view.findViewById(R.id.issuer);
                holder.sha = view.findViewById(R.id.sha);
                view.setTag(holder);
            } else {
                holder = (MainActivity.Adapter.ViewHolder) view.getTag();
                holder.iconLoader.cancel(true);
            }

            // Alternate background colors
            view.setBackgroundColor(i % 2 == 0 ? mColorSemiTransparent : mColorTransparent);

            ApplicationItem item = mAdapterList.get(i);
            ApplicationInfo info = item.applicationInfo;

            // If the app is disabled, add an ocean blue background
            if (!info.enabled) {
                view.setBackgroundColor(mActivity.getResources().getColor(R.color.disabled_app));
            }
            // Add yellow star if the app is in debug mode
            holder.favorite_icon.setVisibility(item.star ? View.VISIBLE : View.INVISIBLE);
            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                // Set version name
                holder.version.setText(packageInfo.versionName);
                // Set date and (if available,) days
                String lastUpdateDate = sSimpleDateFormat.format(new Date(packageInfo.lastUpdateTime));
                if (packageInfo.firstInstallTime == packageInfo.lastUpdateTime)
                    holder.date.setText(lastUpdateDate);
                else {
                    long days = TimeUnit.DAYS.convert(packageInfo.lastUpdateTime
                            - packageInfo.firstInstallTime, TimeUnit.MILLISECONDS);
                    SpannableString ssDate = new SpannableString(
                            String.format(mActivity.getString(R.string.main_list_date_days),
                                    lastUpdateDate, days));
                    ssDate.setSpan(new RelativeSizeSpan(.8f), 10, ssDate.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.date.setText(ssDate);
                }
                // Set date color to orange if app can read logs (and accepted)
                if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS,info.packageName)
                        == PackageManager.PERMISSION_GRANTED)
                    holder.date.setTextColor(mColorOrange);
                else holder.date.setTextColor(mColorSecondary);
                // Set kernel user ID
                holder.shared_id.setText(String.format(Locale.getDefault(),"%d", info.uid));
                // Set kernel user ID text color to orange if the package is shared
                if (packageInfo.sharedUserId != null) holder.shared_id.setTextColor(mColorOrange);
                else holder.shared_id.setTextColor(mColorSecondary);
                // Set issuer
                String issuer;
                try {
                    issuer = "CN=" + (item.sha.getFirst()).split("CN=", 2)[1];
                } catch (ArrayIndexOutOfBoundsException e){
                    issuer = item.sha.getFirst();
                }
                holder.issuer.setText(issuer);
                // Set signature type
                holder.sha.setText(item.sha.getSecond());
            } catch (PackageManager.NameNotFoundException | NullPointerException ignored) {}
            // Load app icon
            holder.iconLoader = new MainActivity.Adapter.IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();
            // Set app label
            if (mConstraint != null && item.label.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                holder.label.setText(Utils.getHighlightedText(item.label, mConstraint, mColorRed));
            } else {
                holder.label.setText(item.label);
            }
            // Set app label color to red if clearing user data not allowed
            if ((info.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0)
                holder.label.setTextColor(Color.RED);
            else holder.label.setTextColor(mColorPrimary);
            // Set package name
            if (mConstraint != null && info.packageName.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                holder.packageName.setText(Utils.getHighlightedText(info.packageName, mConstraint, mColorRed));
            } else {
                holder.packageName.setText(info.packageName);
            }
            // Set package name color to blue if the app is in stopped/force closed state
            if ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0)
                holder.packageName.setTextColor(mActivity.getResources().getColor(R.color.blue_green));
            else holder.packageName.setTextColor(mColorSecondary);
            // Set version (along with HW accelerated, debug and test only flags)
            CharSequence version = holder.version.getText();
            if ((info.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0) version = "_" + version;
            if ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) version = "debug" + version;
            if ((info.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) version = "~" + version;
            holder.version.setText(version);
            // Set version color to green if the app is inactive
            if (Build.VERSION.SDK_INT >= 23) {
                UsageStatsManager mUsageStats;
                mUsageStats = mActivity.getSystemService(UsageStatsManager.class);
                if (mUsageStats != null && mUsageStats.isAppInactive(info.packageName))
                    holder.version.setTextColor(Color.GREEN);
                else holder.version.setTextColor(mColorSecondary);
            }
            // Set app type: system or user app (along with large heap, suspended, multi-arch,
            // has code, vm safe mode)
            String isSystemApp;
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) isSystemApp = mActivity.getString(R.string.system);
            else isSystemApp = mActivity.getString(R.string.user);
            if ((info.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) isSystemApp += "#";
            if ((info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) isSystemApp += "°";
            if ((info.flags & ApplicationInfo.FLAG_MULTIARCH) != 0) isSystemApp += "X";
            if ((info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) isSystemApp += "0";
            if ((info.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) isSystemApp += "?";
            holder.isSystemApp.setText(isSystemApp);
            // Set app type text color to magenta if the app is persistent
            if ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0)
                holder.isSystemApp.setTextColor(Color.MAGENTA);
            else holder.isSystemApp.setTextColor(mColorSecondary);
            // Set SDK
            if (Build.VERSION.SDK_INT >= 26) {
                holder.size.setText(String.format(Locale.getDefault(), "SDK %d", -item.size));
            } else if (item.size != -1L) {
                holder.size.setText(Formatter.formatFileSize(mActivity, item.size));
            }
            // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
            if ((info.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) !=0)
                holder.size.setTextColor(mColorOrange);
            else holder.size.setTextColor(mColorSecondary);

            return view;
        }

        @Override
        public int getPositionForSection(int section) {
            for (int i = 0; i < this.getCount(); i++) {
                String item = mAdapterList.get(i).label;
                if (item.length() > 0) {
                    if (item.charAt(0) == sections.charAt(section))
                        return i;
                }
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int i) {
            return 0;
        }

        @Override
        public Object[] getSections() {
            String[] sectionsArr = new String[sections.length()];
            for (int i = 0; i < sections.length(); i++)
                sectionsArr[i] = "" + sections.charAt(i);

            return sectionsArr;
        }

        private static class IconAsyncTask extends AsyncTask<Void, Integer, Drawable> {
            private WeakReference<ImageView> imageView = null;
            ApplicationInfo info;

            private IconAsyncTask(ImageView pImageViewWeakReference,ApplicationInfo info) {
                link(pImageViewWeakReference);
                this.info = info;
            }

            private void link(ImageView pImageViewWeakReference) {
                imageView = new WeakReference<>(pImageViewWeakReference);
            }


            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (imageView.get()!=null)
                    imageView.get().setVisibility(View.INVISIBLE);
            }

            @Override
            protected Drawable doInBackground(Void... voids) {
                if (!isCancelled())
                    return info.loadIcon(mPackageManager);
                return null;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                super.onPostExecute(drawable);
                if (imageView.get()!=null){
                    imageView.get().setImageDrawable(drawable);
                    imageView.get().setVisibility(View.VISIBLE);

                }
            }
        }
    }

}
