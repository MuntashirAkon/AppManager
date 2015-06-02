package com.majeur.applicationsinfo;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;

import com.majeur.applicationsinfo.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainListFragment extends ListFragment implements AdapterView.OnItemClickListener, AdapterView.OnItemSelectedListener {

    private static final int SORT_NAME = 0;
    private static final int SORT_PKG = 1;
    private static final int SORT_DOMAIN = 2;
    private static final int SORT_INSTALLATION = 3;
    private static final int SORT_SIZE = 4;
    private static final String INSTANCE_STATE_SORT_BY = "sort_by";

    private Adapter mAdapter;
    private List<Item> mItemList = new ArrayList<Item>();
    private int mOnSizeFinishedItemCount;
    private PackageManager mPackageManager;
    private ProgressDialog mProgressDialog;
    private LayoutInflater mLayoutInflater;
    private MainCallbacks mCallbacks;
    private Context mContext;
    private Async mAsyncLoader;
    private Spinner mSpinner;
    private boolean mSpinnerListenerAuthorized;

    private SimpleDateFormat mSimpleDateFormat;

    private int mSortBy = 0;

    class Item {
        ApplicationInfo applicationInfo;
        String label;
        Long date;
        Long size = -1L;
    }

    private int mColorGrey1;
    private int mColorGrey2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.loading_apps);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        //Used to prevent message not showing later
        mProgressDialog.setMessage("");

        mPackageManager = mContext.getPackageManager();
        mSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        mColorGrey1 = getResources().getColor(R.color.grey_1);
        mColorGrey2 = getResources().getColor(R.color.grey_2);

        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);

        mSpinner = new Spinner(actionBar.getThemedContext());
        SpinnerAdapter spinnerAdapter = new SpinnerAdapter(actionBar.getThemedContext(),
                R.array.sort_spinner_items, android.R.layout.simple_list_item_1);
        mSpinner.setAdapter(spinnerAdapter);
        mSpinnerListenerAuthorized = false;
        mSpinner.setOnItemSelectedListener(this);

        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        actionBar.setCustomView(mSpinner, layoutParams);

        if (savedInstanceState != null)
            setSortBy(savedInstanceState.getInt(INSTANCE_STATE_SORT_BY, -1), false);
    }

    @Override
    public void onStart() {
        super.onStart();
        mSpinner.setSelection(mSortBy);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(INSTANCE_STATE_SORT_BY, mSortBy);
    }

    private void onTaskEnded(List<Item> list) {
        RetainedFragment retainedFragment = (RetainedFragment) getFragmentManager().findFragmentByTag(RetainedFragment.FRAGMENT_TAG);
        retainedFragment.setList(list);

        mItemList = list;
        mAdapter.notifyDataSetChanged();

        if (getListView().getAdapter() == null)
            setListAdapter(mAdapter);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemClickListener(this);
        getListView().setFastScrollEnabled(true);

        mAdapter = new Adapter();

        RetainedFragment retainedFragment = (RetainedFragment) getFragmentManager()
                .findFragmentByTag(RetainedFragment.FRAGMENT_TAG);

        if (retainedFragment == null) {
            retainedFragment = new RetainedFragment();
            getFragmentManager()
                    .beginTransaction()
                    .add(retainedFragment, RetainedFragment.FRAGMENT_TAG)
                    .commit();
        }

        if (retainedFragment.getList() != null) {
            onTaskEnded(retainedFragment.getList());

            mOnSizeFinishedItemCount = mItemList.size();
            //Notify spinner that size sort is available
            SpinnerAdapter adapter = (SpinnerAdapter) mSpinner.getAdapter();
            adapter.notifyDataSetChanged();
        } else
            loadList();
    }

    public void loadList() {
        mAsyncLoader = new Async();
        mAsyncLoader.execute();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = (MainCallbacks) activity;
        mContext = activity;
        mLayoutInflater = activity.getLayoutInflater();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mAsyncLoader != null)
            mAsyncLoader.cancel(true);
        mCallbacks = null;
        mContext = null;
        mLayoutInflater = null;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (mCallbacks != null)
            mCallbacks.onItemSelected(mItemList.get(i).applicationInfo.packageName);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_main_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                loadList();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (mSpinnerListenerAuthorized)
            setSortBy(i, true);

        mSpinnerListenerAuthorized = true;
    }

    /**
     * Sort main list if provided value is valid.
     * @param sort Must be one of SORT_*
     * @param checkViews Set if views have to be updated, eg. when restoring state, views aren't
     *                   created yet, so value must be false
     */
    public void setSortBy(int sort, boolean checkViews) {
        if (sort >= SORT_NAME && sort <= SORT_SIZE) {
            mSortBy = sort;

            if (checkViews) {
                checkFastScroll();

                sortApplicationList(mItemList, mSortBy);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void checkFastScroll() {
        getListView().setFastScrollEnabled(mSortBy == SORT_NAME);
    }

    public void sortApplicationList(List<Item> list, final int sortBy) {
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item item1, Item item2) {
                switch (sortBy) {
                    case SORT_NAME:
                        return item1.label.compareTo(item2.label);
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

    /**
     * This method is called by each item when it has finished retrieving its size
     * When all items have finished, we set size sort available in spinner, and invalidate
     * main list to display sizes in UI.
     */
    private void onItemFinishedSizeProcess() {
        mOnSizeFinishedItemCount ++;

        if (mOnSizeFinishedItemCount == mItemList.size()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SpinnerAdapter adapter = (SpinnerAdapter) mSpinner.getAdapter();
                    adapter.notifyDataSetChanged();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    class Adapter extends BaseAdapter implements SectionIndexer {

        class ViewHolder {
            ImageView icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            IconAsyncTask iconLoader;
        }

        String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        @Override
        public int getCount() {
            return mItemList.size();
        }

        @Override
        public Object getItem(int i) {
            return mItemList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder;
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.main_list_item, null);
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

            Item item = mItemList.get(i);
            ApplicationInfo info = item.applicationInfo;

            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                holder.version.setText(packageInfo.versionName);
                Date date = new Date(packageInfo.firstInstallTime);
                holder.date.setText(mSimpleDateFormat.format(date));
            } catch (PackageManager.NameNotFoundException e) {
                //Do nothing
            }

            holder.iconLoader = new IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();

            holder.label.setText(info.loadLabel(mPackageManager));

            holder.packageName.setText(info.packageName);

            boolean isSystemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            holder.isSystemApp.setText(isSystemApp ? getString(R.string.system) : getString(R.string.user));

            if (item.size != -1L)
                holder.size.setText(Formatter.formatFileSize(getActivity(), item.size));

            return view;
        }

        @Override
        public int getPositionForSection(int section) {
            for (int i = 0; i < this.getCount(); i++) {
                String item = mItemList.get(i).label;
                if (item.charAt(0) == sections.charAt(section))
                    return i;
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

    class SpinnerAdapter extends BaseAdapter {

        private Context mContext;
        private int mLayoutResId;
        private String[] mItems;

        public SpinnerAdapter(Context themedContext, int arrayResId, int layoutResId) {
            mContext = themedContext;
            mItems = themedContext.getResources().getStringArray(arrayResId);
            mLayoutResId = layoutResId;
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        //It make no sense to implement recycled view system because there is only 5 items in list
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            view = View.inflate(mContext, mLayoutResId, null);

            if (view instanceof TextView)
                ((TextView) view).setText(mItems[i]);

            return view;
        }

        /**
         * Set sort_by_size item disabled if all items haven't retrieved them size.
         */
        @Override
        public boolean isEnabled(int position) {
            return position != SORT_SIZE || mItemList != null && mOnSizeFinishedItemCount == mItemList.size();
        }
    }

    class Async extends AsyncTask<Void, Async.Progress, List<Item>> {

        class Progress {
            String label;
            int totalSize;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected List<Item> doInBackground(Void... voids) {
            List<ApplicationInfo> applicationInfos = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            Progress progress = new Progress();
            progress.totalSize = applicationInfos.size();

            List<Item> itemList = new ArrayList<Item>(applicationInfos.size());
            mOnSizeFinishedItemCount = 0;

            for (ApplicationInfo applicationInfo : applicationInfos) {
                if (isCancelled())
                    break;
                Item item = new Item();
                item.applicationInfo = applicationInfo;
                String label = applicationInfo.loadLabel(mPackageManager).toString();
                item.label = label;
                try {
                    item.date = mPackageManager.getPackageInfo(applicationInfo.packageName, 0).firstInstallTime;
                } catch (PackageManager.NameNotFoundException e) {
                    item.date = 0L;
                }
                itemList.add(item);

                getItemSize(item);

                progress.label = label;
                publishProgress(progress);
            }

            sortApplicationList(itemList, mSortBy);

            return itemList;
        }

        private void getItemSize(final Item item) {
            try {
                Method getPackageSizeInfo = mPackageManager.getClass().getMethod(
                        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

                getPackageSizeInfo.invoke(mPackageManager, item.applicationInfo.packageName, new IPackageStatsObserver.Stub() {
                    @Override
                    public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded)
                            throws RemoteException {
                        if (succeeded)
                            item.size = pStats.codeSize + pStats.cacheSize + pStats.dataSize
                                    + pStats.externalCodeSize + pStats.externalCacheSize + pStats.externalDataSize
                                    + pStats.externalMediaSize + pStats.externalObbSize;
                        else
                            item.size = -1L;

                        onItemFinishedSizeProcess();
                    }
                });
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                onItemFinishedSizeProcess();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                onItemFinishedSizeProcess();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
                onItemFinishedSizeProcess();
            }
        }

        @Override
        protected void onProgressUpdate(Progress... values) {
            super.onProgressUpdate(values);
            Progress progress = values[0];

            mProgressDialog.setMessage(progress.label);
            if (mProgressDialog.getMax() == 100)
                mProgressDialog.setMax(progress.totalSize);
            mProgressDialog.incrementProgressBy(1);
        }

        @Override
        protected void onPostExecute(List<Item> list) {
            super.onPostExecute(list);
            mProgressDialog.hide();
            onTaskEnded(list);
        }

        @Override
        protected void onCancelled(List<Item> list) {
            super.onCancelled(list);
            mProgressDialog.hide();
        }
    }
}
