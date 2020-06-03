package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.activities.AppInfoActivity;
import io.github.muntashirakon.AppManager.utils.LauncherIconCreator;
import io.github.muntashirakon.AppManager.utils.Utils;


public class AppDetailsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String NEEDED_PROPERTY_INT = "neededProperty";

    private static final int ACTIVITIES = 0;
    private static final int SERVICES = 1;
    private static final int RECEIVERS = 2;
    private static final int PROVIDERS = 3;
    private static final int USES_PERMISSIONS = 4;
    private static final int PERMISSIONS = 5;
    private static final int FEATURES = 6;
    private static final int CONFIGURATION = 7;
    private static final int SIGNATURES = 8;
    private static final int SHARED_LIBRARY_FILES = 9;

    private int neededProperty;

    private LayoutInflater mLayoutInflater;
    private String mPackageName;
    private PackageManager mPackageManager;
    private PackageInfo mPackageInfo;
    private Activity mActivity;
    private ActivitiesListAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;

    private String[] aPermissionsUse;
    private boolean bFi;

    private int mColorGrey1;
    private int mColorGrey2;

    // Load from saved instance if empty constructor is called.
    private boolean isEmptyFragmentConstructCalled = false;
    public AppDetailsFragment() {
        isEmptyFragmentConstructCalled = true;
    }

    public AppDetailsFragment(int neededProperty) {
        this.neededProperty = neededProperty;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(NEEDED_PROPERTY_INT, neededProperty);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isEmptyFragmentConstructCalled && savedInstanceState != null){
            neededProperty = savedInstanceState.getInt(NEEDED_PROPERTY_INT);
        }
        try {
            mPackageName = Objects.requireNonNull(getActivity()).getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        } catch (NullPointerException e) {
            return;
        }
        mPackageManager = getActivity().getPackageManager();
        mLayoutInflater = getLayoutInflater();
        mActivity = getActivity();
        if (mActivity != null) {
            mColorGrey1 = Color.TRANSPARENT;
            mColorGrey2 = mActivity.getResources().getColor(R.color.SEMI_TRANSPARENT);
        }
        getPackageInfo(mPackageName);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pager_app_details, container, false);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        ListView listView = view.findViewById(android.R.id.list);
        TextView textView = view.findViewById(R.id.internalEmpty);
        textView.setText(getNeededString(neededProperty));
        mAdapter = new ActivitiesListAdapter();
        listView.setAdapter(mAdapter);
        if (mAdapter.count == 0) listView.setVisibility(View.GONE);
        return view;
    }

    @Override
    public void onRefresh() {
        if (mAdapter != null){
            getPackageInfo(mPackageName);
            mAdapter.notifyDataSetChanged();
        }
        mSwipeRefresh.setRefreshing(false);
    }

    /**
     * Get package info.
     *
     * @param packageName Package name (e.g. com.android.wallpaper)
     */
    private void getPackageInfo(String packageName) {
        try {
            final int signingCertFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                signingCertFlag = PackageManager.GET_SIGNING_CERTIFICATES;
            } else {
                signingCertFlag = PackageManager.GET_SIGNATURES;
            }
            mPackageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | signingCertFlag | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);

            if (mPackageInfo == null) return;

            switch (neededProperty){
                case SERVICES:
                    if (mPackageInfo.services != null) {
                        Arrays.sort(mPackageInfo.services, new Comparator<ServiceInfo>() {
                            public int compare(ServiceInfo o1, ServiceInfo o2) {
                                return o1.name.compareToIgnoreCase(o2.name);
                            }
                        });
                    }
                    break;
                case RECEIVERS:
                    if (mPackageInfo.receivers != null) {
                        Arrays.sort(mPackageInfo.receivers, new Comparator<ActivityInfo>() {
                            public int compare(ActivityInfo o1, ActivityInfo o2) {
                                return o1.name.compareToIgnoreCase(o2.name);
                            }
                        });
                    }
                    break;
                case PROVIDERS:
                    if (mPackageInfo.providers != null) {
                        Arrays.sort(mPackageInfo.providers, new Comparator<ProviderInfo>() {
                            public int compare(ProviderInfo o1, ProviderInfo o2) {
                                return o1.name.compareToIgnoreCase(o2.name);
                            }
                        });
                    }
                    break;
                case USES_PERMISSIONS:  // Requested Permissions
                    if (mPackageInfo.requestedPermissions == null) aPermissionsUse = null;
                    else {
                        aPermissionsUse = new String[mPackageInfo.requestedPermissions.length];
                        for (int i = 0; i < mPackageInfo.requestedPermissions.length; ++i) {
                            aPermissionsUse[i] = mPackageInfo.requestedPermissions[i] + " ";
                            try {
                                if (Utils.getProtectionLevelString(
                                        mPackageManager.getPermissionInfo(
                                                mPackageInfo.requestedPermissions[i],
                                                PackageManager.GET_META_DATA).protectionLevel)
                                        .contains("dangerous")) {
                                    aPermissionsUse[i] += "*";
                                }
                            } catch (PackageManager.NameNotFoundException e) {
                                e.getStackTrace();
                            }
                            if ((mPackageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                                aPermissionsUse[i] += "\u2714";
                            }
                        }
                        Arrays.sort(aPermissionsUse);
                    }
                    break;
                case PERMISSIONS:
                    if (mPackageInfo.permissions != null) {
                        Arrays.sort(mPackageInfo.permissions, new Comparator<PermissionInfo>() {
                            public int compare(PermissionInfo o1, PermissionInfo o2) {
                                return o1.name.compareToIgnoreCase(o2.name);
                            }
                        });
                    }
                    break;
                case FEATURES:  // Requested Features
                    if (mPackageInfo.reqFeatures != null) {
                        try {
                            Arrays.sort(mPackageInfo.reqFeatures, new Comparator<FeatureInfo>() {
                                public int compare(FeatureInfo o1, FeatureInfo o2) {
                                    return o1.name.compareToIgnoreCase(o2.name);
                                }
                            });
                        } catch (NullPointerException e) {
                            for (FeatureInfo fi : mPackageInfo.reqFeatures) {
                                if (fi.name == null) fi.name = "_MAJOR";
                                bFi = true;
                            }
                            Arrays.sort(mPackageInfo.reqFeatures, new Comparator<FeatureInfo>() {
                                public int compare(FeatureInfo o1, FeatureInfo o2) {
                                    return o1.name.compareToIgnoreCase(o2.name);
                                }
                            });
                        }
                    }
                    break;
                case CONFIGURATION:
                case SIGNATURES:
                case SHARED_LIBRARY_FILES:
                    break;
                case ACTIVITIES:
                default:
                    if (mPackageInfo.activities != null) {
                        Arrays.sort(mPackageInfo.activities, new Comparator<ActivityInfo>() {
                            public int compare(ActivityInfo o1, ActivityInfo o2) {
                                return o1.name.compareToIgnoreCase(o2.name);
                            }
                        });
                    }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            mActivity.finish();
        }
    }

    /**
     * Return corresponding section's array
     */
    private @Nullable Object[] getNeededArray(int index) {
        switch (index) {
            case SERVICES: return mPackageInfo.services;
            case RECEIVERS: return mPackageInfo.receivers;
            case PROVIDERS: return mPackageInfo.providers;
            case USES_PERMISSIONS: return aPermissionsUse;
            case PERMISSIONS: return mPackageInfo.permissions;
            case FEATURES: return mPackageInfo.reqFeatures;
            case CONFIGURATION: return mPackageInfo.configPreferences;
            case SIGNATURES:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SigningInfo signingInfo = mPackageInfo.signingInfo;
                    return signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                            : signingInfo.getSigningCertificateHistory();
                } else {
                    return mPackageInfo.signatures;
                }
            case SHARED_LIBRARY_FILES: return mPackageInfo.applicationInfo.sharedLibraryFiles;
            case ACTIVITIES:
            default: return mPackageInfo.activities;
        }
    }

    /**
     * Return corresponding section's array
     */
    private int getNeededString(int index) {
        switch (index) {
            case SERVICES: return R.string.no_service;
            case RECEIVERS: return R.string.no_receivers;
            case PROVIDERS: return R.string.no_providers;
            case USES_PERMISSIONS:
            case PERMISSIONS: return R.string.require_no_permission;
            case FEATURES: return R.string.no_feature;
            case CONFIGURATION: return R.string.no_configurations;
            case SIGNATURES: return R.string.no_signatures;
            case SHARED_LIBRARY_FILES: return R.string.no_shared_libs;
            case ACTIVITIES:
            default: return R.string.no_activities;
        }
    }


    private class ActivitiesListAdapter extends BaseAdapter {
        private int count;
        private Object[] arrayOfThings;

        ActivitiesListAdapter() {
            arrayOfThings = getNeededArray(neededProperty);
            if (arrayOfThings == null) count = 0;
            else count = arrayOfThings.length;
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
            Button launchBtn;
            Button createBtn;
            Button editBtn;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public Object getItem(int position) {
            return arrayOfThings[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            switch (neededProperty) {
                case SERVICES:
                    return getServicesView(parent, convertView, position);
                case RECEIVERS:
                    return getReceiverView(parent, convertView, position);
                case PROVIDERS:
                    return getProviderView(parent, convertView, position);
                case USES_PERMISSIONS:
                    return getUsesPermissionsView(convertView, position);
                case PERMISSIONS:
                    return getPermissionsView(parent, convertView, position);
                case FEATURES:
                    return getFeaturesView(parent, convertView, position);
                case CONFIGURATION:
                    return getConfigurationView(parent, convertView, position);
                case SIGNATURES:
                    return getSignatureView(convertView, position);
                case SHARED_LIBRARY_FILES:
                    return getSharedLibsView(convertView, position);
                case ACTIVITIES:
                default:
                    return getActivityView(parent, convertView, position);
            }
        }

        /**
         * See below checkIfConvertViewMatch method.
         * Bored view inflation / creation.
         */
        @SuppressLint("SetTextI18n")
        private View getActivityView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, ACTIVITIES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = ACTIVITIES;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = convertView.findViewById(R.id.softInput);
                viewHolder.launchBtn = convertView.findViewById(R.id.launch);
                viewHolder.createBtn = convertView.findViewById(R.id.create_shortcut_btn);
                viewHolder.editBtn = convertView.findViewById(R.id.edit_shortcut_btn);
                convertView.findViewById(R.id.label).setVisibility(View.GONE);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = mPackageInfo.activities[index];
            final String activityName = activityInfo.name;
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Name
            viewHolder.textView2.setText(activityName.startsWith(mPackageName) ?
                    activityName.replaceFirst(mPackageName, "")
                    : activityName);

            // Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));

            // TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);

            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode)
                    + " | " + getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));

            // Orientation
            viewHolder.textView5.setText(Utils.getActivitiesFlagsString(activityInfo.flags));

            // SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode)
                    + " | " + (activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission));

            // Label
            Button launch = viewHolder.launchBtn;
            launch.setText(activityInfo.loadLabel(mPackageManager));
            boolean isExported = activityInfo.exported;
            launch.setEnabled(isExported);
            if (isExported) {
                launch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent();
                        intent.setClassName(mPackageName, activityName);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            if (getActivity() != null)
                                getActivity().recreate();
                            Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                viewHolder.createBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String iconResourceName = null;
                        try {
                            ComponentName activity = new ComponentName(activityInfo.packageName, activityName);
                            iconResourceName = mPackageManager.getResourcesForActivity(activity)
                                    .getResourceName(activityInfo.getIconResource());
                        } catch (PackageManager.NameNotFoundException e) {
                            Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                        }
                        LauncherIconCreator.createLauncherIcon(getActivity(), activityInfo,
                                (String) activityInfo.loadLabel(mPackageManager),
                                activityInfo.loadIcon(mPackageManager), iconResourceName);
                    }
                });
                viewHolder.editBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (getFragmentManager() != null) {
                            DialogFragment dialog = new EditShortcutDialogFragment();
                            Bundle args = new Bundle();
                            args.putParcelable(EditShortcutDialogFragment.ARG_ACTIVITY_INFO, activityInfo);
                            dialog.setArguments(args);
                            dialog.show(getFragmentManager(), EditShortcutDialogFragment.TAG);
                        }
                    }
                });
                viewHolder.createBtn.setVisibility(View.VISIBLE);
                viewHolder.editBtn.setVisibility(View.VISIBLE);
            } else {
                viewHolder.createBtn.setVisibility(View.GONE);
                viewHolder.editBtn.setVisibility(View.GONE);
            }

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getServicesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, SERVICES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = SERVICES;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.orientation);
                //convertView.findViewById(R.id.icon).setVisibility(View.GONE);
                convertView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                convertView.findViewById(R.id.launchMode).setVisibility(View.GONE);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ServiceInfo serviceInfo = mPackageInfo.services[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Label
            viewHolder.textView1.setText(serviceInfo.loadLabel(mPackageManager));

            // Name
            viewHolder.textView2.setText(serviceInfo.name.startsWith(mPackageName) ?
                    serviceInfo.name.replaceFirst(mPackageName, "")
                    : serviceInfo.name);

            // Icon
            viewHolder.imageView.setImageDrawable(serviceInfo.loadIcon(mPackageManager));

            // Flags and 1Permission
            viewHolder.textView3.setText(Utils.getServiceFlagsString(serviceInfo.flags)
                    + (serviceInfo.permission != null ? "\n" + serviceInfo.permission : "\n"));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getReceiverView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, RECEIVERS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = RECEIVERS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView5 = convertView.findViewById(R.id.orientation);
                viewHolder.textView6 = convertView.findViewById(R.id.softInput);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ActivityInfo activityInfo = mPackageInfo.receivers[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Label
            viewHolder.textView1.setText(activityInfo.loadLabel(mPackageManager));

            // Name
            viewHolder.textView2.setText(activityInfo.name.startsWith(mPackageName) ?
                    activityInfo.name.replaceFirst(mPackageName, "")
                    : activityInfo.name);

            // Icon
            viewHolder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));

            // TaskAffinity
            viewHolder.textView3.setText(getString(R.string.taskAffinity) + ": " + activityInfo.taskAffinity);

            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.launch_mode) + ": " + Utils.getLaunchMode(activityInfo.launchMode)
                    + " | " + getString(R.string.orientation) + ": " + Utils.getOrientationString(activityInfo.screenOrientation));

            // Orientation
            viewHolder.textView5.setText(activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission);

            // SoftInput
            viewHolder.textView6.setText(getString(R.string.softInput) + ": " + Utils.getSoftInputString(activityInfo.softInputMode));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getProviderView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, PROVIDERS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PROVIDERS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.launchMode);
                viewHolder.textView4 = convertView.findViewById(R.id.orientation);
                viewHolder.textView5 = convertView.findViewById(R.id.softInput);
                viewHolder.textView6 = convertView.findViewById(R.id.taskAffinity);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ProviderInfo providerInfo = mPackageInfo.providers[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            try {
                // Label
                viewHolder.textView1.setText(providerInfo.loadLabel(mPackageManager));

                // Name
                viewHolder.textView2.setText(providerInfo.name.startsWith(mPackageName) ?
                        providerInfo.name.replaceFirst(mPackageName, "")
                        : providerInfo.name);

                // Icon
                viewHolder.imageView.setImageDrawable(providerInfo.loadIcon(mPackageManager));

                // Uri permission
                viewHolder.textView3.setText(getString(R.string.grant_uri_permission) + ": " + providerInfo.grantUriPermissions);

                // Path permissions
                PathPermission[] pathPermissions = providerInfo.pathPermissions;
                String finalString;
                if (pathPermissions != null) {
                    StringBuilder builder = new StringBuilder();
                    String read = getString(R.string.read);
                    String write = getString(R.string.write);
                    for (PathPermission permission : pathPermissions) {
                        builder.append(read).append(": ").append(permission.getReadPermission());
                        builder.append("/");
                        builder.append(write).append(": ").append(permission.getWritePermission());
                        builder.append(", ");
                    }
                    Utils.checkStringBuilderEnd(builder);
                    finalString = builder.toString();
                } else
                    finalString = "null";
                viewHolder.textView4.setText(getString(R.string.path_permissions) + ": " + finalString);//+"\n"+providerInfo.readPermission +"\n"+providerInfo.writePermission);

                // Pattern matchers
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

                // Authority
                viewHolder.textView6.setText(getString(R.string.authority) + ": " + providerInfo.authority);

            } catch (NullPointerException e) {
                viewHolder.textView1.setText("ERROR retrieving: try uninstall re-install apk");
            }

            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        @SuppressLint("SetTextI18n")
        private View getUsesPermissionsView(View convertView, int index) {
            final String s = aPermissionsUse[index].split(" ")[0];
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(mActivity);
            }

            TextView textView = (TextView) convertView;
            textView.setTextIsSelectable(true);
            if (Build.VERSION.SDK_INT > 22) {
                textView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
            }
            if (aPermissionsUse[index].startsWith("android.permission"))
                textView.setText("\u23e9 " + aPermissionsUse[index].substring(18));
            else textView.setText("\u23e9 " + aPermissionsUse[index]);
            if (aPermissionsUse[index].endsWith("*\u2714")) {
                textView.setTextColor(Color.BLACK);
                textView.setBackgroundColor(mColorGrey2);
            } else if (aPermissionsUse[index].endsWith("\u2714")) {
                textView.setTextColor(Color.DKGRAY);
                textView.setBackgroundColor(mColorGrey1);
            } else if (aPermissionsUse[index].endsWith("*")) {
                textView.setTextColor(Color.GRAY);
                textView.setBackgroundColor(mColorGrey2);
            } else {
                textView.setTextColor(Color.GRAY);
                textView.setBackgroundColor(mColorGrey1);
            }

            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_very_small);
            textView.setPadding(medium_size, small_size, medium_size, small_size);
            textView.setTextSize(12);
            // FIXME: Do something with these!
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        Toast.makeText(mActivity,
                                s + "\n" + mPackageManager.getPermissionInfo(s, PackageManager.GET_META_DATA).loadDescription(mPackageManager)
                                        + "\n\n#" + Utils.getProtectionLevelString(mPackageManager.getPermissionInfo(s, PackageManager.GET_META_DATA).protectionLevel)
                                        + "\n" + mPackageManager.getPermissionInfo(s, PackageManager.GET_META_DATA).packageName
                                        + "\n" + mPackageManager.getPermissionInfo(s, PackageManager.GET_META_DATA).group
                                        + permAppOp(s)
                                , Toast.LENGTH_LONG).show();
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            });

            return convertView;
        }

        private View getSharedLibsView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(mActivity);
            }

            TextView textView = (TextView) convertView;
            textView.setTextIsSelectable(true);
            textView.setText(mPackageInfo.applicationInfo.sharedLibraryFiles[index]);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_very_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, small_size, medium_size, small_size);

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getPermissionsView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, PERMISSIONS)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_primary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = PERMISSIONS;
                viewHolder.imageView = convertView.findViewById(R.id.icon);
                viewHolder.textView1 = convertView.findViewById(R.id.label);
                viewHolder.textView2 = convertView.findViewById(R.id.name);
                viewHolder.textView3 = convertView.findViewById(R.id.taskAffinity);
                viewHolder.textView4 = convertView.findViewById(R.id.orientation);
                viewHolder.textView5 = convertView.findViewById(R.id.launchMode);
                convertView.findViewById(R.id.softInput).setVisibility(View.GONE);
                convertView.findViewById(R.id.launch).setVisibility(View.GONE);
                convertView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                convertView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final PermissionInfo permissionInfo = mPackageInfo.permissions[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Label
            viewHolder.textView1.setText(permissionInfo.loadLabel(mPackageManager));

            // Name
            viewHolder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                    permissionInfo.name.replaceFirst(mPackageName, "")
                    : permissionInfo.name);

            // Icon
            viewHolder.imageView.setImageDrawable(permissionInfo.loadIcon(mPackageManager));

            // Description
            viewHolder.textView3.setText(permissionInfo.loadDescription(mPackageManager));

            // LaunchMode
            viewHolder.textView4.setText(getString(R.string.group) + ": " + permissionInfo.group
                    + permAppOp(permissionInfo.name));

            // Protection level
            viewHolder.textView5.setText(getString(R.string.protection_level) + ": " + Utils.getProtectionLevelString(permissionInfo.protectionLevel));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getFeaturesView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, FEATURES)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_secondary, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.currentViewType = FEATURES;
                viewHolder.textView1 = convertView.findViewById(R.id.name);
                viewHolder.textView2 = convertView.findViewById(R.id.flags);
                viewHolder.textView3 = convertView.findViewById(R.id.gles_ver);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final FeatureInfo featureInfo = mPackageInfo.reqFeatures[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // Name
            viewHolder.textView1.setText(featureInfo.name);

            // Flags
            viewHolder.textView2.setText(getString(R.string.flags) + ": " + Utils.getFeatureFlagsString(featureInfo.flags)
                    + (Build.VERSION.SDK_INT >= 24 && featureInfo.version != 0 ? " | minV%:" + featureInfo.version : ""));

            // GLES ver
            viewHolder.textView3.setText(getString(R.string.gles_ver) + ": " + (bFi && !featureInfo.name.equals("_MAJOR") ? "_" : Utils.getOpenGL(featureInfo.reqGlEsVersion)));

            return convertView;
        }

        /**
         * Boring view inflation / creation
         */
        @SuppressLint("SetTextI18n")
        private View getConfigurationView(ViewGroup viewGroup, View convertView, int index) {
            ViewHolder viewHolder;
            if (checkIfConvertViewMatch(convertView, CONFIGURATION)) {
                convertView = mLayoutInflater.inflate(R.layout.item_app_details_tertiary, viewGroup, false);
                viewHolder = new ViewHolder();
                viewHolder.currentViewType = CONFIGURATION;
                viewHolder.textView1 = convertView.findViewById(R.id.reqgles);
                viewHolder.textView2 = convertView.findViewById(R.id.reqfea);
                viewHolder.textView3 = convertView.findViewById(R.id.reqkey);
                viewHolder.textView4 = convertView.findViewById(R.id.reqnav);
                viewHolder.textView5 = convertView.findViewById(R.id.reqtouch);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final ConfigurationInfo configurationInfo = mPackageInfo.configPreferences[index];
            convertView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);

            // GLES ver
            viewHolder.textView1.setText(getString(R.string.gles_ver) + ": " + Utils.getOpenGL(configurationInfo.reqGlEsVersion));

            // Flag & others
            viewHolder.textView2.setText(getString(R.string.input_features) + ": " + configurationInfo.reqInputFeatures);

            viewHolder.textView3.setText("KeyboardType" + ": " + configurationInfo.reqKeyboardType);

            viewHolder.textView4.setText("Navigation" + ": " + configurationInfo.reqNavigation);

            viewHolder.textView5.setText("Touchscreen" + ": " + configurationInfo.reqTouchScreen);


            return convertView;
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        @SuppressLint("SetTextI18n")
        private View getSignatureView(View convertView, int index) {
            if (!(convertView instanceof TextView)) {
                convertView = new TextView(mActivity);
            }

            TextView textView = (TextView) convertView;
            textView.setText(Utils.signCert((Signature) arrayOfThings[index]) + "\n" +
                    ((Signature) arrayOfThings[index]).toCharsString());
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setTextIsSelectable(true);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, 0, medium_size, small_size);

            return convertView;
        }

        /**
         * Here we check if recycled view match requested type. Tag can be null if recycled view comes from
         * groups that doesn't implement {@link ViewHolder}, such as groups that use only a simple text view.
         */
        private boolean checkIfConvertViewMatch(View convertView, int requestedGroup) {
            return convertView == null || convertView.getTag() == null || ((ViewHolder) convertView.getTag()).currentViewType != requestedGroup;
        }
    }

    private String permAppOp(String s) {
        if (Build.VERSION.SDK_INT >= 23 && AppOpsManager.permissionToOp(s) != null) {
            return "\nAppOP> " + AppOpsManager.permissionToOp(s);
        } else {
            return "";
        }
    }
}