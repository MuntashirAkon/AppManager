package com.majeur.applicationsinfo;

import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.majeur.applicationsinfo.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainListFragment extends ListFragment implements AdapterView.OnItemClickListener, AdapterView.OnItemSelectedListener {

    private Adapter mAdapter;
    private List<Item> mItemList = new ArrayList<Item>();
    private PackageManager mPackageManager;
    private ProgressDialog mProgressDialog;
    private LayoutInflater mLayoutInflater;
    private MainCallbacks mCallbacks;
    private Context mContext;
    private Async mAsyncLoader;

    private SimpleDateFormat mSimpleDateFormat;

    private int mSortBy = 0;

    private final int SORT_NAME = 0;
    private final int SORT_PKG = 1;
    private final int SORT_DOMAIN = 2;
    private final int SORT_INSTALLATION = 3;

    class Item {
        ApplicationInfo applicationInfo;
        String label;
        Long date;
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
        mSimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy hh:mm:ss");
        mColorGrey1 = getResources().getColor(R.color.grey_1);
        mColorGrey2 = getResources().getColor(R.color.grey_2);
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

        if (retainedFragment.getList() != null)
            onTaskEnded(retainedFragment.getList());
        else
            loadList();
    }

    public void loadList() {
        mAsyncLoader = new Async();
        mAsyncLoader.execute();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAsyncLoader != null)
            mAsyncLoader.cancel(true);
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
        Spinner spinner = (Spinner) menu.findItem(R.id.spinner).getActionView();
        SpinnerAdapter mSpinnerAdapter = ArrayAdapter.createFromResource(getActivity().getActionBar().getThemedContext(),
                R.array.sort_spinner_items, android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(mSpinnerAdapter);
        spinner.setOnItemSelectedListener(this);
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
        mSortBy = i;
        checkFastScroll();

        sortApplicationList(mItemList, mSortBy);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void checkFastScroll() {
        getListView().setFastScrollEnabled(mSortBy == SORT_NAME);
    }

    class Adapter extends BaseAdapter implements SectionIndexer {

        class ViewHolder {
            ImageView icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
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
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
                holder.iconLoader.cancel();
            }

            view.setBackgroundColor(i % 2 == 0 ? mColorGrey2 : mColorGrey1);

            ApplicationInfo info = mItemList.get(i).applicationInfo;

            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                holder.version.setText(packageInfo.versionName);
                Date date = new Date(packageInfo.firstInstallTime);
                holder.date.setText(mSimpleDateFormat.format(date));
            } catch (PackageManager.NameNotFoundException e) {

            }

            holder.iconLoader = new IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();

            holder.label.setText(info.loadLabel(mPackageManager));

            holder.packageName.setText(info.packageName);

            boolean isSystemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            holder.isSystemApp.setText(isSystemApp ? getString(R.string.system) : getString(R.string.user));

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
            boolean cancel = false;

            IconAsyncTask(ImageView imageView, ApplicationInfo info) {
                this.imageView = imageView;
                this.info = info;
            }

            public void cancel() {
                cancel = true;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                imageView.setVisibility(View.INVISIBLE);
            }

            @Override
            protected Drawable doInBackground(Void... voids) {
                if (!cancel)
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

                progress.label = label;
                publishProgress(progress);
            }

            sortApplicationList(itemList, mSortBy);

            return itemList;
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
                    default:
                        return 0;
                }
            }
        });
    }
}
