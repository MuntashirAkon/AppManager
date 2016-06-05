package com.majeur.applicationsinfo;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.Loader;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Spannable;
import android.text.format.Formatter;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.majeur.applicationsinfo.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainListFragment extends ListFragment implements AdapterView.OnItemClickListener, SearchView.OnQueryTextListener,
        LoaderManager.LoaderCallbacks<List<ApplicationItem>> {

    private static Collator sCollator = Collator.getInstance();

    private static final int[] sSortMenuItemIdsMap = {R.id.action_sort_name,
            R.id.action_sort_pkg, R.id.action_sort_domain,
            R.id.action_sort_installation, R.id.action_sort_size};

    private static final int SORT_NAME = 0;
    private static final int SORT_PKG = 1;
    private static final int SORT_DOMAIN = 2;
    private static final int SORT_INSTALLATION = 3;
    private static final int SORT_SIZE = 4;
    private static final String INSTANCE_STATE_SORT_BY = "sort_by";

    private Adapter mAdapter;
    private List<ApplicationItem> mItemList = new ArrayList<>();
    private int mItemSizeRetrievedCount;
    private ProgressDialog mProgressDialog;
    private MainCallbacks mCallbacks;
    private Activity mActivity;

    private int mSortBy = SORT_NAME;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setTitle(R.string.loading_apps);
        mProgressDialog.setCancelable(false);

        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);

        SearchView searchView = new SearchView(actionBar.getThemedContext());
        searchView.setOnQueryTextListener(this);

        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        actionBar.setCustomView(searchView, layoutParams);

        if (savedInstanceState != null) {
            int sortBy = savedInstanceState.getInt(INSTANCE_STATE_SORT_BY, -1);
            if (sortBy != -1)
                setSortBy(sortBy);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(INSTANCE_STATE_SORT_BY, mSortBy);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemClickListener(this);
        getListView().setFastScrollEnabled(true);

        mAdapter = new Adapter(mActivity);
        setListAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<List<ApplicationItem>> onCreateLoader(int i, Bundle bundle) {
        mProgressDialog.show();
        return new MainLoader(mActivity);
    }

    @Override
    public void onLoadFinished(Loader<List<ApplicationItem>> loader, List<ApplicationItem> applicationItems) {
        mItemList = applicationItems;
        sortApplicationList();
        mAdapter.setDefaultList(mItemList);

        startRetrievingPackagesSize();

        mProgressDialog.dismiss();
    }

    @Override
    public void onLoaderReset(Loader<List<ApplicationItem>> loader) {
        mItemList = null;
        mAdapter.setDefaultList(null);

        mProgressDialog.dismiss();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = (MainCallbacks) activity;
        mActivity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        mActivity = null;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (mCallbacks != null)
            mCallbacks.onItemSelected(mAdapter.getItem(i).applicationInfo.packageName);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_main_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        switch (id) {
            case R.id.action_refresh:
                getLoaderManager().restartLoader(0, null, this);
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
            case R.id.action_sort_size:
                setSortBy(SORT_SIZE);
                item.setChecked(true);
            default:
                return super.onOptionsItemSelected(item);
        }
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

        if (getListView() != null)
            checkFastScroll();
    }

    private void checkFastScroll() {
        getListView().setFastScrollEnabled(mSortBy == SORT_NAME);
    }

    public void sortApplicationList() {
        Collections.sort(mItemList, new Comparator<ApplicationItem>() {
            @Override
            public int compare(ApplicationItem item1, ApplicationItem item2) {
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
                    default:
                        return 0;
                }
            }
        });
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mAdapter.getFilter().filter(s);
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

    private void getItemSize(final ApplicationItem item) {
        try {
            Method getPackageSizeInfo = PackageManager.class.getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(mActivity.getPackageManager(), item.applicationInfo.packageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, final boolean succeeded)
                        throws RemoteException {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (succeeded)
                                item.size = pStats.codeSize + pStats.cacheSize + pStats.dataSize
                                        + pStats.externalCodeSize + pStats.externalCacheSize + pStats.externalDataSize
                                        + pStats.externalMediaSize + pStats.externalObbSize;
                            else
                                item.size = -1L;

                            incrementItemSizeRetrievedCount();
                        }
                    });
                }
            });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            incrementItemSizeRetrievedCount();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            incrementItemSizeRetrievedCount();
        } catch (InvocationTargetException e) {
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
        static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

        static class ViewHolder {
            ImageView icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            IconAsyncTask iconLoader;
        }

        private Activity mActivity;
        private LayoutInflater mLayoutInflater;
        private PackageManager mPackageManager;
        private Filter mFilter;
        private String mConstraint;
        private List<ApplicationItem> mDefaultList;
        private List<ApplicationItem> mAdapterList;

        private int mColorGrey1;
        private int mColorGrey2;

        Adapter(Activity activity) {
            mActivity = activity;
            mLayoutInflater = activity.getLayoutInflater();
            mPackageManager = activity.getPackageManager();

            mColorGrey1 = activity.getResources().getColor(R.color.grey_1);
            mColorGrey2 = activity.getResources().getColor(R.color.grey_2);
        }

        void setDefaultList(List<ApplicationItem> list) {
            mDefaultList = list;
            mAdapterList = list;
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
                                    item.applicationInfo.packageName.contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null)
                            mAdapterList = mDefaultList;
                        else
                            mAdapterList = (List<ApplicationItem>) filterResults.values;

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
            ViewHolder holder;
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.main_list_item, viewGroup, false);
                holder = new ViewHolder();
                holder.icon = (ImageView) view.findViewById(R.id.icon);
                holder.label = (TextView) view.findViewById(R.id.label);
                holder.packageName = (TextView) view.findViewById(R.id.packageName);
                holder.version = (TextView) view.findViewById(R.id.version);
                holder.isSystemApp = (TextView) view.findViewById(R.id.isSystem);
                holder.date = (TextView) view.findViewById(R.id.date);
                holder.size = (TextView) view.findViewById(R.id.size);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
                holder.iconLoader.cancel(true);
            }

            view.setBackgroundColor(i % 2 == 0 ? mColorGrey2 : mColorGrey1);

            ApplicationItem item = mAdapterList.get(i);
            ApplicationInfo info = item.applicationInfo;

            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                holder.version.setText(packageInfo.versionName);
                Date date = new Date(packageInfo.firstInstallTime);
                holder.date.setText(sSimpleDateFormat.format(date));
            } catch (PackageManager.NameNotFoundException e) {
                //Do nothing
            }

            holder.iconLoader = new IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();

            if (mConstraint != null && item.label.toLowerCase().contains(mConstraint))
                holder.label.setText(getHighlightedText(item.label));
            else
                holder.label.setText(item.label);

            if (mConstraint != null && info.packageName.contains(mConstraint))
                holder.packageName.setText(getHighlightedText(info.packageName));
            else
                holder.packageName.setText(info.packageName);

            boolean isSystemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            holder.isSystemApp.setText(isSystemApp ? mActivity.getString(R.string.system) : mActivity.getString(R.string.user));

            if (item.size != -1L)
                holder.size.setText(Formatter.formatFileSize(mActivity, item.size));

            return view;
        }

        Spannable getHighlightedText(String s) {
            Spannable spannable = sSpannableFactory.newSpannable(s);
            int start = s.toLowerCase().indexOf(mConstraint);
            int end = start + mConstraint.length();
            spannable.setSpan(new BackgroundColorSpan(0xFFB7B7B7), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
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

        class IconAsyncTask extends AsyncTask<Void, Integer, Drawable> {

            ImageView imageView;
            ApplicationInfo info;

            IconAsyncTask(ImageView imageView, ApplicationInfo info) {
                this.imageView = imageView;
                this.info = info;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                imageView.setVisibility(View.INVISIBLE);
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
                imageView.setImageDrawable(drawable);
                imageView.setVisibility(View.VISIBLE);
            }
        }
    }
}
