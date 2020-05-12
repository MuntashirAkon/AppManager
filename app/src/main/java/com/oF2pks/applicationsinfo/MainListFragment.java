package com.oF2pks.applicationsinfo;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.Loader;
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
import android.os.RemoteException;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.Formatter;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
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
import android.widget.Toast;

import com.oF2pks.applicationsinfo.utils.Utils;

import java.lang.ref.WeakReference;
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
import java.util.concurrent.TimeUnit;


public class MainListFragment extends ListFragment implements AdapterView.OnItemClickListener, SearchView.OnQueryTextListener,
        LoaderManager.LoaderCallbacks<List<ApplicationItem>> {

    private static Collator sCollator = Collator.getInstance();

    private static final int[] sSortMenuItemIdsMap = {R.id.action_sort_domain,
            R.id.action_sort_name,R.id.action_sort_pkg,
            R.id.action_sort_installation,R.id.action_sort_sharedid,
            R.id.action_sort_size,R.id.action_sort_sha};

    private static final int SORT_DOMAIN = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_PKG = 2;
    private static final int SORT_INSTALLATION = 3;
    private static final int SORT_SHAREDID =4;
    private static final int SORT_SIZE = 5;
    private static final int SORT_SHA = 6;
    public static final String INSTANCE_STATE_SORT_BY = "sort_by";

    private Adapter mAdapter;
    private List<ApplicationItem> mItemList = new ArrayList<>();
    private int mItemSizeRetrievedCount;
    private ProgressDialog mProgressDialog;
    private MainCallbacks mCallbacks;
    private Activity mActivity;

    private int mSortBy;
    private String mLastClick;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setTitle(R.string.loading_apps);
        mProgressDialog.setCancelable(false);

        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setTitle(getString(R.string.loading));

        SearchView searchView = new SearchView(actionBar.getThemedContext());
        searchView.setOnQueryTextListener(this);

        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionBar.setCustomView(searchView, layoutParams);

        if (savedInstanceState != null) {
            mSortBy = savedInstanceState.getInt(INSTANCE_STATE_SORT_BY);
        }else mSortBy=SORT_NAME;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(INSTANCE_STATE_SORT_BY, mSortBy);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setOnItemClickListener(this);
        getListView().setFastScrollEnabled(true);

        // Longclick : replace by any ...
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mAdapter.getItem(i).applicationInfo.packageName.equals("android")
                        || mAdapter.getItem(i).applicationInfo.packageName.equals(mActivity.getPackageName())){
                    Intent viewManifestIntent = new Intent(getActivity(), ViewManifestActivity.class);
                    viewManifestIntent.putExtra(ViewManifestActivity.EXTRA_PACKAGE_NAME, mAdapter.getItem(i).applicationInfo.packageName);
                    getActivity().startActivity(viewManifestIntent);
                }else {
                    Intent viewManifestIntent = new Intent(getActivity(), View2ManifestActivity.class);
                    viewManifestIntent.putExtra(View2ManifestActivity.EXTRA_PACKAGE_NAME, mAdapter.getItem(i).applicationInfo.packageName);
                    getActivity().startActivity(viewManifestIntent);
                }
                return true;
            }
        });
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
        getActivity().getActionBar().setTitle(MainActivity.permName.substring(0,MainActivity.permName.lastIndexOf(".")));
        getActivity().getActionBar().setSubtitle(
                MainActivity.permName.substring(MainActivity.permName.lastIndexOf(".")+1).toLowerCase());
        if (Build.VERSION.SDK_INT <26) {
            startRetrievingPackagesSize();
        }

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
        if (mCallbacks != null) {
            mLastClick = mAdapter.getItem(i).applicationInfo.packageName;
            mCallbacks.onItemSelected(mLastClick);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.appinfos_fragment_main_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (mSortBy == SORT_SIZE && Build.VERSION.SDK_INT <= 26) {
                Toast t = Toast.makeText(getActivity(), getString(R.string.refresh)
                                + " & " + getString(R.string.sort)
                                + "/" + getString(R.string.size)
                                + "\n" + getString(R.string.unsupported)
                        , Toast.LENGTH_LONG);
                t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
                t.show();
                return true;
            }
            getLoaderManager().restartLoader(0, null, this);
            if (mCallbacks != null && mLastClick != null && getActivity().findViewById(R.id.item_detail_container) != null){
                // && mItemList..contains(mLastClick))
                mCallbacks.onItemSelected(mLastClick);
                return true;
            }
        } else if (id == R.id.action_sort_name) {
            setSortBy(SORT_NAME);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_pkg) {
            setSortBy(SORT_PKG);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_domain) {
            setSortBy(SORT_DOMAIN);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_installation) {
            setSortBy(SORT_INSTALLATION);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_sharedid) {
            setSortBy(SORT_SHAREDID);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_sha) {
            setSortBy(SORT_SHA);
            item.setChecked(true);
            return true;
        } else if (id == R.id.action_sort_size) {
            setSortBy(SORT_SIZE);
            item.setChecked(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                    case SORT_SHAREDID:
                        return item2.applicationInfo.uid - item1.applicationInfo.uid;
                    case SORT_SHA:
                        try {
                            return item1.sha.compareTo(item2.sha);
                        } catch (NullPointerException e) {

                        }
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
        static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");// hh:mm:ss");
        static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

        static class ViewHolder {
            ImageView icon;
            ImageView favorite_icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            TextView sharedid;
            TextView issuer;
            TextView sha;
            IconAsyncTask iconLoader;
        }

        private Activity mActivity;
        private LayoutInflater mLayoutInflater;
        private static PackageManager mPackageManager;
        private Filter mFilter;
        private String mConstraint;
        private List<ApplicationItem> mDefaultList;
        private List<ApplicationItem> mAdapterList;

        private int mColorGrey1;
        private int mColorGrey2;
        private int mOrange1;

        Adapter(Activity activity) {
            mActivity = activity;
            mLayoutInflater = activity.getLayoutInflater();
            mPackageManager = activity.getPackageManager();

            mColorGrey1 = activity.getResources().getColor(R.color.grey_1);
            mColorGrey2 = activity.getResources().getColor(R.color.grey_2);
            mOrange1 = activity.getResources().getColor(R.color.orange_1);
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
                view = mLayoutInflater.inflate(R.layout.appinfos_main_list_item, viewGroup, false);
                holder = new ViewHolder();
                holder.icon = (ImageView) view.findViewById(R.id.icon);
                holder.favorite_icon = (ImageView) view.findViewById(R.id.favorite_icon);
                holder.label = (TextView) view.findViewById(R.id.label);
                holder.packageName = (TextView) view.findViewById(R.id.packageName);
                holder.version = (TextView) view.findViewById(R.id.version);
                holder.isSystemApp = (TextView) view.findViewById(R.id.isSystem);
                holder.date = (TextView) view.findViewById(R.id.date);
                holder.size = (TextView) view.findViewById(R.id.size);
                holder.sharedid=(TextView) view.findViewById(R.id.shareid);
                holder.issuer=(TextView) view.findViewById(R.id.issuer);
                holder.sha=(TextView) view.findViewById(R.id.sha);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
                holder.iconLoader.cancel(true);
            }

            view.setBackgroundColor(i % 2 == 0 ? mColorGrey2 : mColorGrey1);

            ApplicationItem item = mAdapterList.get(i);
            ApplicationInfo info = item.applicationInfo;
            if (!info.enabled) view.setBackgroundColor(Color.LTGRAY);//holder.icon.setImageAlpha(50);//view.setBackgroundColor(Color.LTGRAY);
            holder.favorite_icon.setVisibility(item.star ? View.VISIBLE : View.INVISIBLE);

            holder.sharedid.setText(Integer.toString(info.uid));
            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                holder.version.setText(packageInfo.versionName);
                String sDate = sSimpleDateFormat.format(new Date(packageInfo.lastUpdateTime));
                if (packageInfo.firstInstallTime == packageInfo.lastUpdateTime) holder.date.setText(sDate);
                else {
                    SpannableString ssDate = new SpannableString(sDate + "-" + TimeUnit.DAYS.convert(packageInfo.lastUpdateTime-packageInfo.firstInstallTime,TimeUnit.MILLISECONDS));
                    ssDate.setSpan(new RelativeSizeSpan(.8f), 10, ssDate.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.date.setText(ssDate);
                }
                if (packageInfo.sharedUserId != null) holder.sharedid.setTextColor(mOrange1);
                else holder.sharedid.setTextColor(Color.GRAY);
                holder.issuer.setText((String)item.sha.getFirst());
                holder.sha.setText((String)item.sha.getSecond());
            } catch (PackageManager.NameNotFoundException | NullPointerException e) {
                //Do nothing
            }

            holder.iconLoader = new IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();
            //KISS:? holder.icon.setImageDrawable(info.loadIcon(mPackageManager));

            if (mConstraint != null && item.label.toLowerCase().contains(mConstraint))
                holder.label.setText(getHighlightedText(item.label));
            else
                holder.label.setText(item.label);

            if (mConstraint != null && info.packageName.contains(mConstraint))
                holder.packageName.setText(getHighlightedText(info.packageName));
            else
                holder.packageName.setText(info.packageName);
            if ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0) holder.packageName.setTextColor(Color.BLUE);
            else holder.packageName.setTextColor(Color.GRAY);

            if ((info.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0) holder.version.setText("_"+holder.version.getText());
            if ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) holder.version.setText("debug"+holder.version.getText());
            if ((info.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) holder.version.setText("~"+holder.version.getText());


            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) holder.isSystemApp.setText(mActivity.getString(R.string.system));
            else holder.isSystemApp.setText(mActivity.getString(R.string.user));

            if (Build.VERSION.SDK_INT >= 23) {
                UsageStatsManager mUsageStats;
                mUsageStats = mActivity.getSystemService(UsageStatsManager.class);
                if (mUsageStats.isAppInactive(info.packageName))holder.version.setTextColor(Color.GREEN);
                else holder.version.setTextColor(Color.GRAY);
            }

            //holder.isSystemApp.setText(holder.isSystemApp.getText()+ getCategory(info.category, (char) 'c'));
            if ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) holder.isSystemApp.setTextColor(Color.MAGENTA);
            else holder.isSystemApp.setTextColor(Color.BLACK);
            if ((info.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"#");
            if ((info.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0) holder.label.setTextColor(Color.RED);
            else holder.label.setTextColor(Color.BLACK);
            if ((info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"°");
            if ((info.flags & ApplicationInfo.FLAG_MULTIARCH) != 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"X");
            if ((info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"0");
            if ((info.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"?");
            //if ((info.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) == 0) holder.isSystemApp.setText(holder.isSystemApp.getText()+"0");
            if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS,info.packageName)== PackageManager.PERMISSION_GRANTED) holder.date.setTextColor(mOrange1);
            else holder.date.setTextColor(Color.GRAY);

            if (Build.VERSION.SDK_INT >=26)  {
                holder.size.setText(item.size+"sdk");
                if ((info.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) !=0) holder.size.setTextColor(mOrange1);
                else holder.size.setTextColor(Color.GRAY);
            }
            else if (item.size != -1L)
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

        private static class IconAsyncTask extends AsyncTask<Void, Integer, Drawable> {
            private WeakReference<ImageView> imageView = null;
            ApplicationInfo info;

            private IconAsyncTask (ImageView pImageViewWeakReference,ApplicationInfo info) {
                link(pImageViewWeakReference);
                this.info = info;
            }

            private void link (ImageView pImageViewWeakReference) {
                imageView = new WeakReference<ImageView>(pImageViewWeakReference);
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
