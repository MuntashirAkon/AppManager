package com.majeur.applicationsinfo;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.majeur.applicationsinfo.utils.Tuple;
import com.majeur.applicationsinfo.utils.Utils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DetailFragment extends Fragment {

    static final String FRAGMENT_TAG = "fragment_detail";
    static final String EXTRA_PACKAGE_NAME = "pkg";
    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TR = "tcp_rcv";
    private static final String UID_STATS_RC = "tcp_snd";

    private static final int HEADER = 0;
    private static final int ACTIVITIES = 1;
    private static final int SERVICES = 2;
    private static final int RECEIVERS = 3;
    private static final int PROVIDERS = 4;
    private static final int USES_PERMISSIONS = 5;
    private static final int PERMISSIONS = 6;
    private static final int FEATURES = 7;
    private static final int CONFIGURATION = 8;
    private static final int SIGNATURES = 9;

    private PackageManager mPackageManager;
    private String mPackageName;
    private LayoutInflater mLayoutInflater;
    private PackageInfo mPackageInfo;
    private PackageStats mPackageStats;
    private DetailOverflowMenu mDetailOverflowMenu;

    private int mColorGrey1;
    private int mColorGrey2;
    private TypedArray mGroupTitleIds;

    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("EE LLL dd yyyy kk:mm:ss");

    public static DetailFragment getInstance(String packageName) {
        DetailFragment detailFragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putString(DetailFragment.EXTRA_PACKAGE_NAME, packageName);
        detailFragment.setArguments(args);

        return detailFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackageManager = getActivity().getPackageManager();
        mPackageName = getArguments().getString(EXTRA_PACKAGE_NAME);
        mColorGrey1 = getResources().getColor(R.color.grey_1);
        mColorGrey2 = getResources().getColor(R.color.grey_2);
        mDetailOverflowMenu = new DetailOverflowMenu(getActivity(), mPackageName);

        mGroupTitleIds = getResources().obtainTypedArray(R.array.group_titles);

        mPackageInfo = getPackageInfo(mPackageName);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mLayoutInflater = inflater;

        ExpandableListView listView = new ExpandableListView(getActivity());
        listView.setGroupIndicator(null);

        if (mPackageInfo == null) {
            Toast.makeText(getActivity(), R.string.app_not_installed, Toast.LENGTH_LONG).show();
        } else {
            listView.setAdapter(new Adapter());
        }

        return listView;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDetailOverflowMenu = null;
    }

    private PackageInfo getPackageInfo(String packageName) {
        try {
            return mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Used to finish when user clicks on {@link android.app.ActionBar} arrow
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            //onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Create and populate header view
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private View getHeaderView(ViewGroup viewGroup) {
        View headerView = mLayoutInflater.inflate(R.layout.detail_header, viewGroup, false);

        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;

        TextView labelView = (TextView) headerView.findViewById(R.id.label);
        CharSequence label = applicationInfo.loadLabel(mPackageManager);
        if (getActivity() instanceof DetailActivity) {
            //Application is not in multi-pane mode, use ActionBar for label
            getActivity().setTitle(label);
            labelView.setVisibility(View.GONE);
        } else {
            //Application is in multi-pane mode, ActionBar is already used, use field in header
            //to display label
            labelView.setText(label);
        }

        TextView packageNameView = (TextView) headerView.findViewById(R.id.packageName);
        packageNameView.setText(mPackageName);

        ImageView iconView = (ImageView) headerView.findViewById(R.id.icon);
        iconView.setImageDrawable(applicationInfo.loadIcon(mPackageManager));

        if (Utils.isApi20()) {
            ImageView bannerView = (ImageView) headerView.findViewById(R.id.banner);
            bannerView.setImageDrawable(applicationInfo.loadBanner(mPackageManager));
        }

        TextView versionView = (TextView) headerView.findViewById(R.id.version);
        versionView.setText(mPackageInfo.versionName + " (" + mPackageInfo.versionCode + ")");

        TextView pathView = (TextView) headerView.findViewById(R.id.path);
        pathView.setText(applicationInfo.sourceDir);

        TextView isSystemAppView = (TextView) headerView.findViewById(R.id.isSystem);
        boolean isSystemApp = (mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        isSystemAppView.setText(isSystemApp ? R.string.system : R.string.user);

        TextView installDateView = (TextView) headerView.findViewById(R.id.installed_date);
        installDateView.setText(getString(R.string.installation) + ": " + getTime(mPackageInfo.firstInstallTime));

        TextView updateDateView = (TextView) headerView.findViewById(R.id.update_date);
        updateDateView.setText(getString(R.string.update) + ": " + getTime(mPackageInfo.lastUpdateTime));

        ImageButton overflowButton = (ImageButton) headerView.findViewById(R.id.detail_overflow);
        mDetailOverflowMenu.setView(overflowButton);

        TextView sharedUserId = (TextView) headerView.findViewById(R.id.sharedUserId);
        sharedUserId.setText(getString(R.string.shared_user_id) + ": " + mPackageInfo.sharedUserId);

        Tuple<String, String> uidNetStats = getNetStats(applicationInfo.uid);

        TextView netStatsTransmittedView = (TextView) headerView.findViewById(R.id.netstats_transmitted);
        netStatsTransmittedView.setText(getString(R.string.netstats_transmitted) + ": "
                + uidNetStats.getFirst());

        TextView netStatsReceivedView = (TextView) headerView.findViewById(R.id.netstats_received);
        netStatsReceivedView.setText(getString(R.string.netstats_received) + ": "
                + uidNetStats.getSecond());

        if (mPackageStats == null)
            getPackageSizeInfo(headerView);
        else
            onPackageStatsLoaded(headerView);

        return headerView;
    }

    private String getReadableSize(long size) {
        return Formatter.formatFileSize(getActivity(), size);
    }

    private Tuple<String, String> getNetStats(int uid) {
        Tuple<String, String> tuple = new Tuple<>(getReadableSize(0), getReadableSize(0));
        File uidStatsDir = new File(UID_STATS_PATH + uid);

        if (uidStatsDir.exists() && uidStatsDir.isDirectory()) {
            for (File child : uidStatsDir.listFiles()) {
                if (child.getName().equals(UID_STATS_TR))
                    tuple.setFirst(getReadableSize(Long.parseLong(Utils.getFileContent(child))));
                else if (child.getName().equals(UID_STATS_RC))
                    tuple.setSecond(getReadableSize(Long.parseLong(Utils.getFileContent(child))));
            }
        }
        return tuple;
    }

    public String getTime(long time) {
        Date date = new Date(time);
        return mDateFormatter.format(date);
    }

    /**
     * Load package sizes and update views if success
     *
     * @param view
     */
    private void getPackageSizeInfo(final View view) {
        try {
            Method getPackageSizeInfo = PackageManager.class.getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(mPackageManager, mPackageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded)
                        throws RemoteException {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPackageStats = pStats;
                            onPackageStatsLoaded(view);
                        }
                    });
                }
            });
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update size views
     *
     * @param headerView
     */
    private void onPackageStatsLoaded(View headerView) {
        if (mPackageStats == null)
            return;

        TextView sizeCodeView = (TextView) headerView.findViewById(R.id.size_code);
        sizeCodeView.setText(getReadableSize(mPackageStats.codeSize));

        TextView sizeCacheView = (TextView) headerView.findViewById(R.id.size_cache);
        sizeCacheView.setText(getReadableSize(mPackageStats.cacheSize));

        TextView sizeDataView = (TextView) headerView.findViewById(R.id.size_data);
        sizeDataView.setText(getReadableSize(mPackageStats.dataSize));

        TextView sizeExtCodeView = (TextView) headerView.findViewById(R.id.size_ext_code);
        sizeExtCodeView.setText(getReadableSize(mPackageStats.externalCodeSize));

        TextView sizeExtCacheView = (TextView) headerView.findViewById(R.id.size_ext_cache);
        sizeExtCacheView.setText(getReadableSize(mPackageStats.externalCacheSize));

        TextView sizeExtDataView = (TextView) headerView.findViewById(R.id.size_ext_data);
        sizeExtDataView.setText(getReadableSize(mPackageStats.externalDataSize));

        TextView sizeObb = (TextView) headerView.findViewById(R.id.size_ext_obb);
        sizeObb.setText(getReadableSize(mPackageStats.externalObbSize));

        TextView sizeMedia = (TextView) headerView.findViewById(R.id.size_ext_media);
        sizeMedia.setText(getReadableSize(mPackageStats.externalMediaSize));
    }

    private class Adapter extends BaseExpandableListAdapter {

        /**
         * Returning total group titles count plus one for the header
         */
        @Override
        public int getGroupCount() {
            return mGroupTitleIds.length() + 1;
        }

        /**
         * {@link com.majeur.applicationsinfo.utils.Utils} method is used to prevent {@link java.lang.NullPointerException} when arrays are null.
         * In this case, we make sure that returned length is zero.
         */
        @Override
        public int getChildrenCount(int parentIndex) {
            return Utils.getArrayLengthSafely(getNeededArray(parentIndex));
        }

        /**
         * Return corresponding section's array
         */
        private Object[] getNeededArray(int index) {
            switch (index) {
                case HEADER:
                    return null;
                case ACTIVITIES:
                    return mPackageInfo.activities;
                case SERVICES:
                    return mPackageInfo.services;
                case RECEIVERS:
                    return mPackageInfo.receivers;
                case PROVIDERS:
                    return mPackageInfo.providers;
                case USES_PERMISSIONS:
                    return mPackageInfo.requestedPermissions;
                case PERMISSIONS:
                    return mPackageInfo.permissions;
                case FEATURES:
                    return mPackageInfo.reqFeatures;
                case CONFIGURATION:
                    return mPackageInfo.configPreferences;
                case SIGNATURES:
                    return mPackageInfo.signatures;
                default:
                    return null;
            }
        }

        /**
         * For HEADER value we return header view. In other case, we return simple {@link android.widget.TextView} with group title,
         * note that index in {@link android.content.res.TypedArray} is shifted to adapter implementation.
         */
        @Override
        public View getGroupView(int groupIndex, boolean b, View view, ViewGroup viewGroup) {
            if (groupIndex == HEADER)
                return getHeaderView(viewGroup);

            TextView textView;
            if (view instanceof TextView)
                textView = (TextView) view;
            else
                textView = (TextView) mLayoutInflater.inflate(R.layout.group_title_view, null);

            textView.setText(mGroupTitleIds.getString(groupIndex - 1) + " (" + getChildrenCount(groupIndex) + ")");
            return textView;
        }

        /**
         * Child click is not used
         */
        @Override
        public boolean isChildSelectable(int i, int i2) {
            return false;
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder {
            int currentViewType = -1;
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            TextView textView6;
            ImageView imageView;
            Button button;
        }

        /**
         * We return corresponding view, recycled view implementation is done in each method
         */
        @Override
        public View getChildView(int groupIndex, int childIndex, boolean b, View view, ViewGroup viewGroup) {
            switch (groupIndex) {
                case ACTIVITIES:
                    return getActivityView(viewGroup, view, childIndex);
                case SERVICES:
                    return getServicesView(viewGroup, view, childIndex);
                case RECEIVERS:
                    return getReceiverView(viewGroup, view, childIndex);
                case PROVIDERS:
                    return getProviderView(viewGroup, view, childIndex);
                case USES_PERMISSIONS:
                    return getUsesPermissionsView(view, childIndex);
                case PERMISSIONS:
                    return getPermissionsView(viewGroup, view, childIndex);
                case FEATURES:
                    return getFeaturesView(viewGroup, view, childIndex);
                case CONFIGURATION:
                    return getConfigurationView(viewGroup, view, childIndex);
                case SIGNATURES:
                    return getSignatureView(view, childIndex);
                default:
                    return null;
            }
        }

        /**
         * See below checkIfConvertViewMatch method.
         * Bored view inflation / creation.
         */
        private View getActivityView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, ACTIVITIES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = ACTIVITIES;
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.icon);
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.label);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = (TextView) convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = (TextView) convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = (TextView) convertView.findViewById(R.id.softInput);
                viewHolder.button = (Button) convertView.findViewById(R.id.launch);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = mPackageInfo.activities[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Label
            viewHolder.textView1.setText(activityInfo.loadLabel(mPackageManager));

            //Name
            viewHolder.textView2.setText(activityInfo.name.replaceFirst(mPackageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));

            //TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);

            //LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode));

            //Orientation
            viewHolder.textView5.setText(getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));

            //SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode));


            Button launch = viewHolder.button;
            boolean isExported = activityInfo.exported;
            launch.setEnabled(isExported);
            if (isExported) {
                launch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent();
                        intent.setClassName(mPackageName, activityInfo.name);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(getActivity(), e.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getServicesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, SERVICES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = SERVICES;
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.icon);
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.label);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.launchMode);
                convertView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                convertView.findViewById(R.id.orientation).setVisibility(View.GONE);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ServiceInfo serviceInfo = mPackageInfo.services[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Label
            viewHolder.textView1.setText(serviceInfo.loadLabel(mPackageManager));

            //Name
            viewHolder.textView2.setText(serviceInfo.name.replaceFirst(mPackageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(serviceInfo.loadIcon(mPackageManager));

            //Flags
            viewHolder.textView3.setText(getString(R.string.flags) + ": " + Utils.getServiceFlagsString(serviceInfo.flags));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getReceiverView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, RECEIVERS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = RECEIVERS;
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.icon);
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.label);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = (TextView) convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = (TextView) convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = (TextView) convertView.findViewById(R.id.softInput);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = mPackageInfo.receivers[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Label
            viewHolder.textView1.setText(activityInfo.loadLabel(mPackageManager));

            //Name
            viewHolder.textView2.setText(activityInfo.name.replaceFirst(mPackageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));

            //TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);

            //LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode));

            //Orientation
            viewHolder.textView5.setText(getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));

            //SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getProviderView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, PROVIDERS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PROVIDERS;
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.icon);
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.label);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.launchMode);
                viewHolder.textView4 = (TextView) convertView.findViewById(R.id.orientation);
                viewHolder.textView5 = (TextView) convertView.findViewById(R.id.softInput);
                viewHolder.textView6 = (TextView) convertView.findViewById(R.id.taskAffinity);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ProviderInfo providerInfo = mPackageInfo.providers[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Label
            viewHolder.textView1.setText(providerInfo.loadLabel(mPackageManager));

            //Name
            viewHolder.textView2.setText(providerInfo.name.replaceFirst(mPackageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(providerInfo.loadIcon(mPackageManager));

            //Uri permission
            viewHolder.textView3.setText(getString(R.string.grant_uri_permission) + ": " + providerInfo.grantUriPermissions);

            //Path permissions
            PathPermission[] pathPermissions = providerInfo.pathPermissions;
            String finalString;
            if (pathPermissions != null) {
                StringBuilder builder = new StringBuilder();
                String read = getString(R.string.read);
                String write = getString(R.string.write);
                for (PathPermission permission : pathPermissions) {
                    builder.append(read + ": " + permission.getReadPermission());
                    builder.append("/");
                    builder.append(write + ": " + permission.getWritePermission());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString = builder.toString();
            } else
                finalString = "null";
            viewHolder.textView4.setText(getString(R.string.path_permissions) + ": " + finalString);

            //Pattern matchers
            PatternMatcher[] patternMatchers = providerInfo.uriPermissionPatterns;
            String finalString1;
            if (patternMatchers != null) {
                StringBuilder builder = new StringBuilder();
                for (PatternMatcher patternMatcher : patternMatchers) {
                    builder.append(patternMatcher.toString());
                    builder.append(", ");
                }
                Utils.checkStringBuilderEnd(builder);
                finalString1 = builder.toString();
            } else
                finalString1 = "null";
            viewHolder.textView5.setText(getString(R.string.patterns_allowed) + ": " + finalString1);

            //Authority
            viewHolder.textView6.setText(getString(R.string.authority) + ": " + providerInfo.authority);

            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private View getUsesPermissionsView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(getActivity());
            }

            TextView textView = (TextView) convertView;
            textView.setTextIsSelectable(true);
            textView.setText(mPackageInfo.requestedPermissions[index]);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            int size = getActivity().getResources().getDimensionPixelSize(R.dimen.header_text_margin);
            textView.setPadding(size, 0, size, 0);

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getPermissionsView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, PERMISSIONS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PERMISSIONS;
                viewHolder.imageView = (ImageView) convertView.findViewById(R.id.icon);
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.label);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = (TextView) convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = (TextView) convertView.findViewById(R.id.orientation);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final PermissionInfo permissionInfo = mPackageInfo.permissions[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Label
            viewHolder.textView1.setText(permissionInfo.loadLabel(mPackageManager));

            //Name
            viewHolder.textView2.setText(permissionInfo.name.replaceFirst(mPackageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(permissionInfo.loadIcon(mPackageManager));

            //Description
            viewHolder.textView3.setText(permissionInfo.loadDescription(mPackageManager));

            //LaunchMode
            viewHolder.textView4.setText(getString(R.string.group) + ": " + permissionInfo.group);

            //Protection level
            viewHolder.textView5.setText(getString(R.string.protection_level) + ": " + Utils.getProtectionLevelString(permissionInfo.protectionLevel));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getFeaturesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, FEATURES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_features, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = FEATURES;
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.flags);
                viewHolder.textView3 = (TextView) convertView.findViewById(R.id.gles_ver);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final FeatureInfo featureInfo = mPackageInfo.reqFeatures[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //Name
            viewHolder.textView1.setText(featureInfo.name);

            //Falgs
            viewHolder.textView2.setText(getString(R.string.flags) + ": " + Utils.getFeatureFlagsString(featureInfo.flags));

            //GLES ver
            viewHolder.textView3.setText(getString(R.string.gles_ver) + ": " + featureInfo.reqGlEsVersion);

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getConfigurationView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, CONFIGURATION)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_features, viewGroup);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = CONFIGURATION;
                viewHolder.textView1 = (TextView) convertView.findViewById(R.id.name);
                viewHolder.textView2 = (TextView) convertView.findViewById(R.id.flags);
                convertView.findViewById(R.id.gles_ver).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ConfigurationInfo configurationInfo = mPackageInfo.configPreferences[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            //GLES ver
            viewHolder.textView1.setText(getString(R.string.gles_ver) + ": " + configurationInfo.reqGlEsVersion);

            //Falg
            viewHolder.textView2.setText(getString(R.string.input_features) + ": " +
                    Utils.getInputFeaturesString(configurationInfo.reqInputFeatures));

            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private View getSignatureView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(getActivity());
            }

            TextView textView = (TextView) convertView;
            textView.setText(mPackageInfo.signatures[index].toCharsString());
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setTextIsSelectable(true);
            int size = getActivity().getResources().getDimensionPixelSize(R.dimen.header_text_margin);
            textView.setPadding(size, 0, size, 0);

            return convertView;
        }

        /**
         * Here we check if recycled view match requested type. Tag can be null if recycled view comes from
         * groups that doesn't implement {@link ViewHolder}, such as groups that use only a simple text view.
         */
        private boolean checkIfConvertViewMatch(View convertView, int requestedGroup) {
            return convertView != null && convertView.getTag() != null && ((ViewHolder) convertView.getTag()).currentViewType == requestedGroup;
        }

        /**
         * Unused methods
         */

        @Override
        public Object getGroup(int i) {
            return null;
        }

        @Override
        public Object getChild(int i, int i2) {
            return null;
        }

        @Override
        public long getGroupId(int i) {
            return 0;
        }

        @Override
        public long getChildId(int i, int i2) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
