package com.majeur.applicationsinfo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Date;

public class DetailActivity extends Activity {

    static final String EXTRA_PACKAGE_NAME = "pkg";

    private final int HEADER = 0;
    private final int ACTIVITIES = 1;
    private final int SERVICES = 2;
    private final int RECEIVERS = 3;
    private final int PROVIDERS = 4;
    private final int USES_PERMISSIONS = 5;
    private final int PERMISSIONS = 6;
    private final int FEATURES = 7;
    private final int CONFIGURATION = 8;
    private final int SIGNATURES = 9;


    private PackageManager packageManager;
    private String packageName;
    private LayoutInflater mLayoutInflater;
    private ExpandableListView mListView;
    private PackageInfo mPackageInfo;

    int mColorGrey1;
    int mColorGrey2;
    TypedArray mGroupTitleIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        packageManager = getPackageManager();
        mLayoutInflater = getLayoutInflater();
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        mColorGrey1 = getResources().getColor(R.color.grey_1);
        mColorGrey2 = getResources().getColor(R.color.grey_2);

        mGroupTitleIds = getResources().obtainTypedArray(R.array.group_titles);

        mListView = new ExpandableListView(this);
        mListView.setGroupIndicator(null);
        setContentView(mListView);

        new Async().execute();
    }

    private void onAsyncFinished(PackageInfo packageInfo) {
        if (packageInfo == null) {
            Toast.makeText(DetailActivity.this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mPackageInfo = packageInfo;
        mListView.setAdapter(new Adapter());
    }

    /**
     * Used to finish when user clicks on {@link android.app.ActionBar} arrow
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
         * {@link Utils} method is used to prevent {@link java.lang.NullPointerException} when arrays are null.
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
                return getHeaderView();

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
                    return getActivityView(view, childIndex);
                case SERVICES:
                    return getServicesView(view, childIndex);
                case RECEIVERS:
                    return getReceiverView(view, childIndex);
                case PROVIDERS:
                    return getProviderView(view, childIndex);
                case USES_PERMISSIONS:
                    return getUsesPermissionsView(view, childIndex);
                case PERMISSIONS:
                    return getPermissionsView(view, childIndex);
                case FEATURES:
                    return getFeaturesView(view, childIndex);
                case CONFIGURATION:
                    return getConfigurationView(view, childIndex);
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
        private View getActivityView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, ACTIVITIES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, null);

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
            viewHolder.textView1.setText(activityInfo.loadLabel(packageManager));

            //Name
            viewHolder.textView2.setText(activityInfo.name.replaceFirst(packageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(packageManager));

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
                        intent.setClassName(packageName, activityInfo.name);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(DetailActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getServicesView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, SERVICES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, null);

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
            viewHolder.textView1.setText(serviceInfo.loadLabel(packageManager));

            //Name
            viewHolder.textView2.setText(serviceInfo.name.replaceFirst(packageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(serviceInfo.loadIcon(packageManager));

            //Flags
            viewHolder.textView3.setText(getString(R.string.flags) + ": " + Utils.getServiceFlagsString(serviceInfo.flags));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getReceiverView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, RECEIVERS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, null);

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
            viewHolder.textView1.setText(activityInfo.loadLabel(packageManager));

            //Name
            viewHolder.textView2.setText(activityInfo.name.replaceFirst(packageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(packageManager));

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
        private View getProviderView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, PROVIDERS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, null);

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
            viewHolder.textView1.setText(providerInfo.loadLabel(packageManager));

            //Name
            viewHolder.textView2.setText(providerInfo.name.replaceFirst(packageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(providerInfo.loadIcon(packageManager));

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
                convertView = new TextView(DetailActivity.this);
            }

            TextView textView = (TextView) convertView;
            textView.setText(mPackageInfo.requestedPermissions[index]);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setPadding(Utils.dpToPx(DetailActivity.this, 15), 0, 0, 0);

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getPermissionsView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, PERMISSIONS)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_activities, null);

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
            viewHolder.textView1.setText(permissionInfo.loadLabel(packageManager));

            //Name
            viewHolder.textView2.setText(permissionInfo.name.replaceFirst(packageName, ""));

            //Icon
            viewHolder.imageView.setImageDrawable(permissionInfo.loadIcon(packageManager));

            //Description
            viewHolder.textView3.setText(permissionInfo.loadDescription(packageManager));

            //LaunchMode
            viewHolder.textView4.setText(getString(R.string.group) + ": " + permissionInfo.group);

            //Protection level
            viewHolder.textView5.setText(getString(R.string.protection_level) + ": " + Utils.getProtectionLevelString(permissionInfo.protectionLevel));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        private View getFeaturesView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, FEATURES)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_features, null);

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
        private View getConfigurationView(View convertView, int index) {
            ViewHolder viewHolder;
            if (!checkIfConvertViewMatch(convertView, CONFIGURATION)) {
                convertView = mLayoutInflater.inflate(R.layout.detail_features, null);

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
                convertView = new TextView(DetailActivity.this);
            }

            TextView textView = (TextView) convertView;
            textView.setText(mPackageInfo.signatures[index].toCharsString());
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

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
         * Create and populate header view
         */
        private View getHeaderView() {
            View headerView = mLayoutInflater.inflate(R.layout.detail_header, null);

            ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;

            setTitle(applicationInfo.loadLabel(packageManager));

            TextView packageNameView = (TextView) headerView.findViewById(R.id.packageName);
            packageNameView.setText(packageName);

            ImageView iconView = (ImageView) headerView.findViewById(R.id.icon);
            iconView.setImageDrawable(applicationInfo.loadIcon(packageManager));

            TextView versionView = (TextView) headerView.findViewById(R.id.version);
            versionView.setText(mPackageInfo.versionName + " (" + mPackageInfo.versionCode + ")");

            TextView pathView = (TextView) headerView.findViewById(R.id.path);
            pathView.setText(applicationInfo.sourceDir);

            TextView sizeView = (TextView) headerView.findViewById(R.id.size);
            long size = new File(applicationInfo.sourceDir).length();
            float sizeMb = size / 1048576f;
            sizeView.setText(getString(R.string.file_size) + ": " + String.format("%.2f", sizeMb) + " Mo");

            TextView isSystemAppView = (TextView) headerView.findViewById(R.id.isSystem);
            boolean isSystemApp = (mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            isSystemAppView.setText(isSystemApp ? "System" : "User");

            TextView installDateView = (TextView) headerView.findViewById(R.id.installed_date);
            Date installDate = new Date(mPackageInfo.firstInstallTime);
            installDateView.setText(getString(R.string.installation) + ": " + installDate.toString());

            TextView updateDateView = (TextView) headerView.findViewById(R.id.update_date);
            Date updateDate = new Date(mPackageInfo.lastUpdateTime);
            updateDateView.setText(getString(R.string.update) + ": " + updateDate.toString());

            Button uninstallButton = (Button) headerView.findViewById(R.id.uninstall);
            uninstallButton.setEnabled(!isSystemApp && !packageName.equals(getPackageName()));
            uninstallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                }
            });


            Button appInfoButton = (Button) headerView.findViewById(R.id.appInfo);
            appInfoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                }
            });

            TextView sharedUserId = (TextView) headerView.findViewById(R.id.sharedUserId);
            sharedUserId.setText(getString(R.string.shared_user_id) + ": " + mPackageInfo.sharedUserId);

            return headerView;
        }

        /** Unused methods */

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

    /**
     * Simple {@link android.os.AsyncTask} to load {@link android.content.pm.PackageInfo} without stopping Ui thread.
     */
    private class Async extends AsyncTask<Void, Integer, PackageInfo> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected PackageInfo doInBackground(Void... voids) {
            try {
                return packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                        | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                        | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(PackageInfo packageInfo) {
            super.onPostExecute(packageInfo);
            setProgressBarIndeterminateVisibility(false);
            onAsyncFinished(packageInfo);
        }
    }
}
