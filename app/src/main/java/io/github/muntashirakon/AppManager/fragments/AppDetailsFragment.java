package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
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
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.activities.AppDetailsActivity;
import io.github.muntashirakon.AppManager.activities.AppInfoActivity;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
import io.github.muntashirakon.AppManager.types.AppDetailsItem;
import io.github.muntashirakon.AppManager.types.RecyclerViewWithEmptyView;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LauncherIconCreator;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;


public class AppDetailsFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    private static final String NEEDED_PROPERTY_INT = "neededProperty";

    @IntDef(value = {
            NONE,
            ACTIVITIES,
            SERVICES,
            RECEIVERS,
            PROVIDERS,
            APP_OPS,
            USES_PERMISSIONS,
            PERMISSIONS,
            FEATURES,
            CONFIGURATION,
            SIGNATURES,
            SHARED_LIBRARY_FILES
    })
    public @interface Property {}
    public static final int NONE = -1;
    public static final int ACTIVITIES = 0;
    public static final int SERVICES = 1;
    public static final int RECEIVERS = 2;
    public static final int PROVIDERS = 3;
    public static final int APP_OPS = 4;
    public static final int USES_PERMISSIONS = 5;
    public static final int PERMISSIONS = 6;
    public static final int FEATURES = 7;
    public static final int CONFIGURATION = 8;
    public static final int SIGNATURES = 9;
    public static final int SHARED_LIBRARY_FILES = 10;

    private @Property int neededProperty;

    private String mPackageName;
    private PackageManager mPackageManager;
    private PackageInfo mPackageInfo;
    private Activity mActivity;
    private AppDetailsRecyclerAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefresh;
    private MenuItem blockingToggler;
    private AppOpsService mAppOpsService;
    private ProgressIndicator mProgressIndicator;
    private TextView mRulesNotAppliedMsg;
    private List<Tuple<String, Integer>> permissionsWithFlags;
    private boolean bFi;

    private static int mColorGrey1;
    private static int mColorGrey2;
    private static int mColorRed;
    private static int mColorDisabled;
    private static int mColorRunning;

    // Load from saved instance if empty constructor is called.
    private boolean isEmptyFragmentConstructCalled = false;
    public AppDetailsFragment() {
        isEmptyFragmentConstructCalled = true;
    }

    public AppDetailsFragment(@Property int neededProperty) {
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
        setHasOptionsMenu(true);
        if (isEmptyFragmentConstructCalled && savedInstanceState != null){
            neededProperty = savedInstanceState.getInt(NEEDED_PROPERTY_INT);
        }
        try {
            mPackageName = Objects.requireNonNull(getActivity()).getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        } catch (NullPointerException e) {
            return;
        }
        mPackageManager = getActivity().getPackageManager();
        mActivity = getActivity();
        if (mActivity != null) {
            mColorGrey1 = Color.TRANSPARENT;
            mColorGrey2 = ContextCompat.getColor(mActivity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(mActivity, R.color.red);
            mColorDisabled = ContextCompat.getColor(mActivity, R.color.disabled_app);
            mColorRunning = ContextCompat.getColor(mActivity, R.color.running);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pager_app_details, container, false);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(mActivity, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(mActivity, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerViewWithEmptyView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity, LinearLayoutManager.VERTICAL, false));
        TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(getNeededString(neededProperty));
        recyclerView.setEmptyView(emptyView);
        mProgressIndicator = mActivity.findViewById(R.id.progress_linear);
        showProgressIndicator(true);
        mRulesNotAppliedMsg = mActivity.findViewById(R.id.alert_text);
        mRulesNotAppliedMsg.setVisibility(View.GONE);
        mRulesNotAppliedMsg.setText(R.string.rules_not_applied);
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mAdapter.reset();
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_app_details_action, menu);
        blockingToggler = menu.findItem(R.id.action_toggle_blocking);
        if (neededProperty <= PROVIDERS && AppPref.isRootEnabled() && !(Boolean) AppPref.get(
                AppPref.PREF_GLOBAL_BLOCKING_ENABLED, AppPref.TYPE_BOOLEAN)) {
            blockingToggler.setVisible(true);
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                if (cb.isRulesApplied()) {
                    blockingToggler.setTitle(R.string.menu_remove_rules);
                } else {
                    blockingToggler.setTitle(R.string.menu_apply_rules);
                }
            }
        } else blockingToggler.setVisible(false);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh_details:
                refreshDetails();
                return true;
            case R.id.action_toggle_blocking:
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
                    boolean isRulesApplied = cb.isRulesApplied();
                    cb.applyRules(!isRulesApplied);
                    if (cb.isRulesApplied()) blockingToggler.setTitle(R.string.menu_remove_rules);
                    else blockingToggler.setTitle(R.string.menu_apply_rules);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null && !TextUtils.isEmpty(AppDetailsActivity.sConstraint)) {
            mAdapter.getFilter().filter(AppDetailsActivity.sConstraint);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        AppDetailsActivity.sConstraint = newText;
        if (mAdapter != null) {
            mAdapter.getFilter().filter(newText);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    synchronized private void applyRules(String componentName, RulesStorageManager.Type type) {
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
            if (cb.hasComponent(componentName)) { // Remove from the list
                cb.removeComponent(componentName);
            } else { // Add to the list
                cb.addComponent(componentName, type);
            }
            // Apply rules
            if ((Boolean) AppPref.get(AppPref.PREF_GLOBAL_BLOCKING_ENABLED,
                    AppPref.TYPE_BOOLEAN) || cb.isRulesApplied()) {
                cb.applyRules(true);
            }
            if (cb.isRulesApplied()) {
                mRulesNotAppliedMsg.setVisibility(View.GONE);
            } else {
                mRulesNotAppliedMsg.setVisibility(View.VISIBLE);
            }
        }
    }

    private void refreshDetails() {
        if (mAdapter != null) mAdapter.reset();
    }

    public void resetFilter() {
        if (mAdapter != null) {
            mAdapter.getFilter().filter(AppDetailsActivity.sConstraint);
        }
    }

    /**
     * Get package info (should only be called from a non-UI thread)
     */
    private void getPackageInfo() {
        try {
            int apiCompatFlags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                apiCompatFlags = PackageManager.GET_SIGNING_CERTIFICATES;
            else apiCompatFlags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                apiCompatFlags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            else apiCompatFlags |= PackageManager.GET_DISABLED_COMPONENTS;

            mPackageInfo = mPackageManager.getPackageInfo(mPackageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | apiCompatFlags | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);

            if (mPackageInfo == null) return;

            switch (neededProperty){
                case SERVICES:
                    if (mPackageInfo.services != null) {
                        Arrays.sort(mPackageInfo.services, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case RECEIVERS:
                    if (mPackageInfo.receivers != null) {
                        Arrays.sort(mPackageInfo.receivers, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case PROVIDERS:
                    if (mPackageInfo.providers != null) {
                        Arrays.sort(mPackageInfo.providers, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case USES_PERMISSIONS:  // Requested Permissions
                    if (mPackageInfo.requestedPermissions == null) permissionsWithFlags = null;
                    else {
                        permissionsWithFlags = new ArrayList<>(mPackageInfo.requestedPermissions.length);
                        for (int i = 0; i < mPackageInfo.requestedPermissions.length; ++i) {
                            permissionsWithFlags.add(
                                    new Tuple<>(mPackageInfo.requestedPermissions[i],
                                    mPackageInfo.requestedPermissionsFlags[i]));
                        }
                    }
                    break;
                case PERMISSIONS:
                    if (mPackageInfo.permissions != null) {
                        Arrays.sort(mPackageInfo.permissions, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
                    break;
                case FEATURES:  // Requested Features
                    if (mPackageInfo.reqFeatures != null) {
                        try {
                            Arrays.sort(mPackageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        } catch (NullPointerException e) {
                            for (FeatureInfo fi : mPackageInfo.reqFeatures) {
                                if (fi.name == null) fi.name = "_MAJOR";
                                bFi = true;
                            }
                            Arrays.sort(mPackageInfo.reqFeatures, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        }
                    }
                    break;
                case APP_OPS:
                case CONFIGURATION:
                case SIGNATURES:
                case SHARED_LIBRARY_FILES:
                    break;
                case ACTIVITIES:
                case NONE:
                default:
                    if (mPackageInfo.activities != null) {
                        Arrays.sort(mPackageInfo.activities, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                    }
            }
        } catch (PackageManager.NameNotFoundException e) {
            mActivity.runOnUiThread(() -> mActivity.finish());
        }
    }

    /**
     * Return corresponding section's array
     */
    private @NonNull List<AppDetailsItem> getNeededList(@Property int index) {
        List<AppDetailsItem> appDetailsItems = new ArrayList<>();
        switch (index) {
            case ACTIVITIES:
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                    if (mPackageInfo.activities != null) {
                        for (ActivityInfo activityInfo : mPackageInfo.activities) {
                            AppDetailsItem appDetailsItem = new AppDetailsItem(activityInfo);
                            appDetailsItem.name = activityInfo.name;
                            appDetailsItem.isBlocked = cb.hasComponent(activityInfo.name);
                            appDetailsItems.add(appDetailsItem);
                        }
                    }
                }
                break;
            case SERVICES:
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                    if (mPackageInfo.services != null) {
                        for (ServiceInfo serviceInfo : mPackageInfo.services) {
                            AppDetailsItem appDetailsItem = new AppDetailsItem(serviceInfo);
                            appDetailsItem.name = serviceInfo.name;
                            appDetailsItem.isBlocked = cb.hasComponent(serviceInfo.name);
                            appDetailsItems.add(appDetailsItem);
                        }
                    }
                }
                break;
            case RECEIVERS:
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                    if (mPackageInfo.receivers != null) {
                        for (ActivityInfo activityInfo : mPackageInfo.receivers) {
                            AppDetailsItem appDetailsItem = new AppDetailsItem(activityInfo);
                            appDetailsItem.name = activityInfo.name;
                            appDetailsItem.isBlocked = cb.hasComponent(activityInfo.name);
                            appDetailsItems.add(appDetailsItem);
                        }
                    }
                }
                break;
            case PROVIDERS:
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                    if (mPackageInfo.providers != null) {
                        for (ProviderInfo providerInfo : mPackageInfo.providers) {
                            AppDetailsItem appDetailsItem = new AppDetailsItem(providerInfo);
                            appDetailsItem.name = providerInfo.name;
                            appDetailsItem.isBlocked = cb.hasComponent(providerInfo.name);
                            appDetailsItems.add(appDetailsItem);
                        }
                    }
                }
                break;
            case APP_OPS:
                if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                    mAppOpsService = new AppOpsService(mActivity);
                    try {
                        List<AppOpsManager.PackageOps> packageOpsList = mAppOpsService.getOpsForPackage(-1, mPackageName, null);
                        List<AppOpsManager.OpEntry> opEntries = new ArrayList<>();
                        if (packageOpsList.size() == 1)
                            opEntries.addAll(packageOpsList.get(0).getOps());
                        packageOpsList = mAppOpsService.getOpsForPackage(-1, mPackageName, AppOpsManager.sAlwaysShownOp);
                        if (packageOpsList.size() == 1)
                            opEntries.addAll(packageOpsList.get(0).getOps());
                        if (opEntries.size() > 0) {
                            Set<String> uniqueSet = new HashSet<>();
                            for (AppOpsManager.OpEntry opEntry : opEntries) {
                                if (uniqueSet.contains(opEntry.getOpStr())) continue;
                                AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry);
                                appDetailsItem.name = opEntry.getOpStr();
                                appDetailsItems.add(appDetailsItem);
                                uniqueSet.add(opEntry.getOpStr());
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            appDetailsItems.sort((o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        } else {
                            Collections.sort(appDetailsItems, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
                        }
                    } catch (Exception ignored) {
                    }
                }
                break;
            case USES_PERMISSIONS:
                if (permissionsWithFlags != null) {
                    for(Tuple<String, Integer> permissionWithFlags: permissionsWithFlags) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(permissionWithFlags);
                        appDetailsItem.name = permissionWithFlags.getFirst();
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case PERMISSIONS:
                if (mPackageInfo.permissions != null) {
                    for(PermissionInfo permissionInfo: mPackageInfo.permissions) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(permissionInfo);
                        appDetailsItem.name = permissionInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case FEATURES:
                if (mPackageInfo.reqFeatures != null) {
                    for(FeatureInfo featureInfo: mPackageInfo.reqFeatures) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(featureInfo);
                        appDetailsItem.name = featureInfo.name;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case CONFIGURATION:
                if (mPackageInfo.configPreferences != null) {
                    for(ConfigurationInfo configurationInfo: mPackageInfo.configPreferences) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(configurationInfo);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case SIGNATURES:
                Signature[] signatures;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SigningInfo signingInfo = mPackageInfo.signingInfo;
                    signatures = signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                            : signingInfo.getSigningCertificateHistory();
                } else {
                    signatures = mPackageInfo.signatures;
                }
                if (signatures != null) {
                    for(Signature signature: signatures) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(signature);
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case SHARED_LIBRARY_FILES:
                if (mPackageInfo.applicationInfo.sharedLibraryFiles != null) {
                    for(String sharedLibrary: mPackageInfo.applicationInfo.sharedLibraryFiles) {
                        AppDetailsItem appDetailsItem = new AppDetailsItem(sharedLibrary);
                        appDetailsItem.name = sharedLibrary;
                        appDetailsItems.add(appDetailsItem);
                    }
                }
                break;
            case NONE:
            default:
        }
        return appDetailsItems;
    }

    /**
     * Return corresponding section's array
     */
    private int getNeededString(@Property int index) {
        switch (index) {
            case SERVICES: return R.string.no_service;
            case RECEIVERS: return R.string.no_receivers;
            case PROVIDERS: return R.string.no_providers;
            case APP_OPS:
                if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                    return R.string.no_app_ops;
                } else return R.string.only_works_in_root_mode;
            case USES_PERMISSIONS:
            case PERMISSIONS: return R.string.require_no_permission;
            case FEATURES: return R.string.no_feature;
            case CONFIGURATION: return R.string.no_configurations;
            case SIGNATURES: return R.string.no_signatures;
            case SHARED_LIBRARY_FILES: return R.string.no_shared_libs;
            case ACTIVITIES:
            case NONE:
            default: return R.string.no_activities;
        }
    }

    private void showProgressIndicator(boolean show) {
        if (mProgressIndicator == null) return;
        if (show) {
            mProgressIndicator.setVisibility(View.VISIBLE);
            mProgressIndicator.show();
        } else {
            mProgressIndicator.hide();
            mProgressIndicator.setVisibility(View.GONE);
        }
    }

    public static boolean isComponentDisabled(@NonNull PackageManager pm, @NonNull ComponentInfo componentInfo) {
        ComponentName componentName = new ComponentName(componentInfo.packageName, componentInfo.name);
        int componentEnabledSetting = pm.getComponentEnabledSetting(componentName);

        switch (componentEnabledSetting) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                return !componentInfo.isEnabled();
        }
    }

    @NonNull
    private String permAppOp(String s) {
        String opStr = AppOpsManager.permissionToOp(s);
        return opStr != null ? "\nAppOp: " + opStr : "";
    }

    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> implements Filterable {
        private List<AppDetailsItem> mAdapterList;
        private List<AppDetailsItem> mDefaultList;
        private @Property int requestedProperty;
        private Filter mFilter;
        private String mConstraint;
        private Boolean isRootEnabled = true;
        private Boolean isADBEnabled = true;
        private List<String> runningServices;

        AppDetailsRecyclerAdapter() {}

        void reset() {
            isRootEnabled = AppPref.isRootEnabled();
            isADBEnabled = AppPref.isAdbEnabled();
            final Boolean isBlockingEnabled = (Boolean) AppPref.get(AppPref.PREF_GLOBAL_BLOCKING_ENABLED, AppPref.TYPE_BOOLEAN);
            showProgressIndicator(true);
            new Thread(() -> {
                getPackageInfo();
                requestedProperty = neededProperty;
                mAdapterList = getNeededList(requestedProperty);
                if (requestedProperty == SERVICES)
                    runningServices = PackageUtils.getRunningServicesForPackage(mPackageName);
                mDefaultList = mAdapterList;
                final AtomicInteger rules_msg_visibility = new AtomicInteger(View.GONE);
                if (requestedProperty <= AppDetailsFragment.PROVIDERS) {
                    try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mActivity, mPackageName)) {
                        if (isRootEnabled && !isBlockingEnabled && cb.componentCount() > 0 && !cb.isRulesApplied()) {
                            rules_msg_visibility.set(View.VISIBLE);
                        }
                    }
                }
                mActivity.runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(AppDetailsActivity.sConstraint)) {
                        getFilter().filter(AppDetailsActivity.sConstraint);
                    }
                    mRulesNotAppliedMsg.setVisibility(rules_msg_visibility.get());
                    notifyDataSetChanged();
                    showProgressIndicator(false);
                });
            }).start();
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        FilterResults filterResults = new FilterResults();
                        if (charSequence == null || mDefaultList == null) {
                            filterResults.count = mDefaultList == null ? 0 : mDefaultList.size();
                            filterResults.values = mDefaultList;
                            return filterResults;
                        }
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<AppDetailsItem> list = new ArrayList<>(mDefaultList.size());
                        for (AppDetailsItem item : mDefaultList) {
                            if (item.name.toLowerCase(Locale.ROOT).contains(constraint))
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
                            mAdapterList = (List<AppDetailsItem>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            TextView textView6;
            TextView textView7;
            TextView textView8;
            ImageView imageView;
            ImageButton blockBtn;
            Button createBtn;
            Button editBtn;
            Button launchBtn;
            Switch toggleSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                switch (requestedProperty) {
                    case ACTIVITIES:
                        imageView = itemView.findViewById(R.id.icon);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.launchMode);
                        textView5 = itemView.findViewById(R.id.orientation);
                        textView6 = itemView.findViewById(R.id.softInput);
                        launchBtn = itemView.findViewById(R.id.launch);
                        blockBtn  = itemView.findViewById(R.id.block_component);
                        createBtn = itemView.findViewById(R.id.create_shortcut_btn);
                        editBtn   = itemView.findViewById(R.id.edit_shortcut_btn);
                        itemView.findViewById(R.id.label).setVisibility(View.GONE);
                        break;
                    case SERVICES:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.orientation);
                        blockBtn  = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.taskAffinity).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launchMode).setVisibility(View.GONE);
                        itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case RECEIVERS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.launchMode);
                        textView5 = itemView.findViewById(R.id.orientation);
                        textView6 = itemView.findViewById(R.id.softInput);
                        blockBtn  = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case PROVIDERS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.launchMode);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.softInput);
                        textView6 = itemView.findViewById(R.id.taskAffinity);
                        blockBtn  = itemView.findViewById(R.id.block_component);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        break;
                    case PERMISSIONS:
                        imageView = itemView.findViewById(R.id.icon);
                        textView1 = itemView.findViewById(R.id.label);
                        textView2 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.taskAffinity);
                        textView4 = itemView.findViewById(R.id.orientation);
                        textView5 = itemView.findViewById(R.id.launchMode);
                        itemView.findViewById(R.id.softInput).setVisibility(View.GONE);
                        itemView.findViewById(R.id.launch).setVisibility(View.GONE);
                        itemView.findViewById(R.id.create_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.edit_shortcut_btn).setVisibility(View.GONE);
                        itemView.findViewById(R.id.block_component).setVisibility(View.GONE);
                        break;
                    case APP_OPS:
                        textView1 = itemView.findViewById(R.id.op_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        textView6 = itemView.findViewById(R.id.perm_name);
                        textView7 = itemView.findViewById(R.id.op_mode_running_duration);
                        textView8 = itemView.findViewById(R.id.op_accept_reject_time);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
                        break;
                    case USES_PERMISSIONS:
                        textView1 = itemView.findViewById(R.id.perm_name);
                        textView2 = itemView.findViewById(R.id.perm_description);
                        textView3 = itemView.findViewById(R.id.perm_protection_level);
                        textView4 = itemView.findViewById(R.id.perm_package_name);
                        textView5 = itemView.findViewById(R.id.perm_group);
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn);
                        break;
                    case FEATURES:
                        textView1 = itemView.findViewById(R.id.name);
                        textView2 = itemView.findViewById(R.id.flags);
                        textView3 = itemView.findViewById(R.id.gles_ver);
                        break;
                    case CONFIGURATION:
                        textView1 = itemView.findViewById(R.id.reqgles);
                        textView2 = itemView.findViewById(R.id.reqfea);
                        textView3 = itemView.findViewById(R.id.reqkey);
                        textView4 = itemView.findViewById(R.id.reqnav);
                        textView5 = itemView.findViewById(R.id.reqtouch);
                        break;
                    case SIGNATURES:
                    case SHARED_LIBRARY_FILES:
                    case NONE:
                    default:
                        break;
                }
            }
        }

        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams")
            final View view;
            switch (requestedProperty) {
                case ACTIVITIES:
                case SERVICES:
                case RECEIVERS:
                case PROVIDERS:
                case PERMISSIONS:
                case NONE:
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
                    break;
                case APP_OPS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_appop, parent, false);
                    break;
                case USES_PERMISSIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_perm, parent, false);
                    break;
                case FEATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_secondary, parent, false);
                    break;
                case CONFIGURATION:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_tertiary, parent, false);
                    break;
                case SIGNATURES:
                case SHARED_LIBRARY_FILES:
                    view = new TextView(mActivity);
                    break;
            }
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            switch (requestedProperty) {
                case SERVICES:
                    getServicesView(holder, position);
                    break;
                case RECEIVERS:
                    getReceiverView(holder, position);
                    break;
                case PROVIDERS:
                    getProviderView(holder, position);
                    break;
                case APP_OPS:
                    getAppOpsView(holder, position);
                    break;
                case USES_PERMISSIONS:
                    getUsesPermissionsView(holder, position);
                    break;
                case PERMISSIONS:
                    getPermissionsView(holder, position);
                    break;
                case FEATURES:
                    getFeaturesView(holder, position);
                    break;
                case CONFIGURATION:
                    getConfigurationView(holder, position);
                    break;
                case SIGNATURES:
                    getSignatureView(holder, position);
                    break;
                case SHARED_LIBRARY_FILES:
                    getSharedLibsView(holder, position);
                    break;
                case ACTIVITIES:
                case NONE:
                default:
                    getActivityView(holder, position);
                    break;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        /**
         * Bored view inflation / creation.
         */
        private void getActivityView(@NonNull ViewHolder holder, int index) {
            final View view = holder.itemView;
            final AppDetailsItem appDetailsItem = mAdapterList.get(index);
            final ActivityInfo activityInfo = (ActivityInfo) appDetailsItem.vanillaItem;
            final String activityName = activityInfo.name;
            // Background color: regular < disabled < blocked
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (isComponentDisabled(mPackageManager, activityInfo)) {
                view.setBackgroundColor(mColorDisabled);
            }
            if (appDetailsItem.isBlocked) {
                view.setBackgroundColor(mColorRed);
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Name
            if (mConstraint != null && activityName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(Utils.getHighlightedText(activityName, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(activityName.startsWith(mPackageName) ?
                        activityName.replaceFirst(mPackageName, "") : activityName);
            }
            // Icon
            holder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));
            // TaskAffinity
            holder.textView3.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.taskAffinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.getDefault(), "%s: %s | %s: %s",
                    getString(R.string.launch_mode), Utils.getLaunchMode(activityInfo.launchMode),
                    getString(R.string.orientation), Utils.getOrientationString(activityInfo.screenOrientation)));
            // Orientation
            holder.textView5.setText(Utils.getActivitiesFlagsString(activityInfo.flags));
            // SoftInput
            holder.textView6.setText(String.format(Locale.getDefault(), "%s: %s | %s",
                    getString(R.string.softInput), Utils.getSoftInputString(activityInfo.softInputMode),
                    (activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission)));
            // Label
            Button launch = holder.launchBtn;
            String appLabel = mPackageInfo.applicationInfo.loadLabel(mPackageManager).toString();
            String activityLabel = activityInfo.loadLabel(mPackageManager).toString();
            launch.setText(activityLabel.equals(appLabel) || TextUtils.isEmpty(activityLabel) ?
                    Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(activityInfo.name))
                    : activityLabel);
            boolean isExported = activityInfo.exported;
            launch.setEnabled(isExported);
            if (isExported && !appDetailsItem.isBlocked) {
                launch.setOnClickListener(v -> {
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
                });
                holder.createBtn.setOnClickListener(v -> {
                    String iconResourceName = null;
                    try {
                        ComponentName activity = new ComponentName(activityInfo.packageName, activityName);
                        iconResourceName = mPackageManager.getResourcesForActivity(activity)
                                .getResourceName(activityInfo.getIconResource());
                    } catch (PackageManager.NameNotFoundException e) {
                        Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                    } catch (Resources.NotFoundException ignore) {}
                    LauncherIconCreator.createLauncherIcon(getActivity(), activityInfo,
                            (String) activityInfo.loadLabel(mPackageManager),
                            activityInfo.loadIcon(mPackageManager), iconResourceName);
                });
                holder.editBtn.setOnClickListener(v -> {
                    if (getFragmentManager() != null) {
                        DialogFragment dialog = new EditShortcutDialogFragment();
                        Bundle args = new Bundle();
                        args.putParcelable(EditShortcutDialogFragment.ARG_ACTIVITY_INFO, activityInfo);
                        dialog.setArguments(args);
                        dialog.show(getFragmentManager(), EditShortcutDialogFragment.TAG);
                    }
                });
                holder.createBtn.setVisibility(View.VISIBLE);
                holder.editBtn.setVisibility(View.VISIBLE);
            } else {
                holder.createBtn.setVisibility(View.GONE);
                holder.editBtn.setVisibility(View.GONE);
            }
            // Blocking
            if (isRootEnabled) {
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(activityName, RulesStorageManager.Type.ACTIVITY);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    mAdapterList.set(index, appDetailsItem);
                    notifyItemChanged(index);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        /**
         * Boring view inflation / creation
         */
        private void getServicesView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsItem appDetailsItem = mAdapterList.get(index);
            final ServiceInfo serviceInfo = (ServiceInfo) appDetailsItem.vanillaItem;
            // Background color: regular < running < disabled < blocked
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (runningServices.contains(serviceInfo.name)) {
                view.setBackgroundColor(mColorRunning);
            }
            if (isComponentDisabled(mPackageManager, serviceInfo)) {
                view.setBackgroundColor(mColorDisabled);
            }
            if (appDetailsItem.isBlocked) {
                view.setBackgroundColor(mColorRed);
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(serviceInfo.name)));
            // Name
            if (mConstraint != null && serviceInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(Utils.getHighlightedText(serviceInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(serviceInfo.name.startsWith(mPackageName) ?
                        serviceInfo.name.replaceFirst(mPackageName, "") : serviceInfo.name);
            }
            // Icon
            holder.imageView.setImageDrawable(serviceInfo.loadIcon(mPackageManager));
            // Flags and Permission
            holder.textView3.setText(String.format(Locale.getDefault(), "%s\n%s",
                    Utils.getServiceFlagsString(serviceInfo.flags),
                    (serviceInfo.permission != null ? serviceInfo.permission : "")));
            // Blocking
            if (isRootEnabled) {
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(serviceInfo.name, RulesStorageManager.Type.SERVICE);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    mAdapterList.set(index, appDetailsItem);
                    notifyItemChanged(index);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        /**
         * Boring view inflation / creation
         */
        private void getReceiverView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsItem appDetailsItem = mAdapterList.get(index);
            final ActivityInfo activityInfo = (ActivityInfo) appDetailsItem.vanillaItem;
            // Background color: regular < disabled < blocked
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (isComponentDisabled(mPackageManager, activityInfo)) {
                view.setBackgroundColor(mColorDisabled);
            }
            if (appDetailsItem.isBlocked) {
                view.setBackgroundColor(mColorRed);
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(activityInfo.name)));
            // Name
            if (mConstraint != null && activityInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(Utils.getHighlightedText(activityInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(activityInfo.name.startsWith(mPackageName) ?
                        activityInfo.name.replaceFirst(mPackageName, "")
                        : activityInfo.name);
            }
            // Icon
            holder.imageView.setImageDrawable(activityInfo.loadIcon(mPackageManager));
            // TaskAffinity
            holder.textView3.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.taskAffinity), activityInfo.taskAffinity));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.getDefault(), "%s: %s | %s: %s",
                    getString(R.string.launch_mode), Utils.getLaunchMode(activityInfo.launchMode),
                    getString(R.string.orientation), Utils.getOrientationString(activityInfo.screenOrientation)));
            // Orientation
            holder.textView5.setText(activityInfo.permission == null ? getString(R.string.require_no_permission) : activityInfo.permission);
            // SoftInput
            holder.textView6.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.softInput), Utils.getSoftInputString(activityInfo.softInputMode)));
            // Blocking
            if (isRootEnabled) {
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(activityInfo.name, RulesStorageManager.Type.RECEIVER);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    mAdapterList.set(index, appDetailsItem);
                    notifyItemChanged(index);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        /**
         * Boring view inflation / creation
         */
        private void getProviderView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final AppDetailsItem appDetailsItem = mAdapterList.get(index);
            final ProviderInfo providerInfo = (ProviderInfo) appDetailsItem.vanillaItem;
            final String providerName = providerInfo.name;
            // Background color: regular < disabled < blocked
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            if (isComponentDisabled(mPackageManager, providerInfo)) {
                view.setBackgroundColor(mColorDisabled);
            }
            if (appDetailsItem.isBlocked) {
                view.setBackgroundColor(mColorRed);
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_restore_black_24dp));
            } else {
                holder.blockBtn.setImageDrawable(mActivity.getDrawable(R.drawable.ic_block_black_24dp));
            }
            // Label
            holder.textView1.setText(Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(providerName)));
            // Icon
            holder.imageView.setImageDrawable(providerInfo.loadIcon(mPackageManager));
            // Uri permission
            holder.textView3.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.grant_uri_permission), providerInfo.grantUriPermissions));
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
            } else finalString = "null";
            holder.textView4.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.path_permissions), finalString)); // +"\n"+providerInfo.readPermission +"\n"+providerInfo.writePermission);
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
            holder.textView5.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.patterns_allowed), finalString1));
            // Authority
            holder.textView6.setText(String.format(Locale.getDefault(), "%s: %s", getString(R.string.authority), providerInfo.authority));
            // Name
            if (mConstraint != null && providerName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(Utils.getHighlightedText(providerName, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(providerName.startsWith(mPackageName) ?
                        providerName.replaceFirst(mPackageName, "") : providerName);
            }
            // Blocking
            if (isRootEnabled) {
                holder.blockBtn.setVisibility(View.VISIBLE);
                holder.blockBtn.setOnClickListener(v -> {
                    applyRules(providerName, RulesStorageManager.Type.PROVIDER);
                    appDetailsItem.isBlocked = !appDetailsItem.isBlocked;
                    mAdapterList.set(index, appDetailsItem);
                    notifyItemChanged(index);
                });
            } else holder.blockBtn.setVisibility(View.GONE);
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private void getAppOpsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            AppOpsManager.OpEntry opEntry = (AppOpsManager.OpEntry) mAdapterList.get(index).vanillaItem;
            final String opStr = mAdapterList.get(index).name;
            PermissionInfo permissionInfo = null;
            try {
                String permName = AppOpsManager.opToPermission(opEntry.getOp());
                if (permName != null)
                    permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException | IllegalArgumentException | IndexOutOfBoundsException ignore) {}
            // Set op name
            String opName = "(" + opEntry.getOp() + ") ";
            if (mConstraint != null && opStr.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                opName += Utils.getHighlightedText(opStr, mConstraint, mColorRed);
            } else {
                opName += opStr;
            }
            holder.textView1.setText(opName);
            // Set op mode, running and duration
            String opRunningInfo = mActivity.getString(R.string.mode) + ": " + opEntry.getMode();
            if (opEntry.isRunning()) opRunningInfo += ", " + mActivity.getString(R.string.running);
            if (opEntry.getDuration() != 0)
                opRunningInfo += ", " + mActivity.getString(R.string.duration) + ": " +
                        Utils.getFormattedDuration(mActivity, opEntry.getDuration(), true);
            holder.textView7.setText(opRunningInfo);
            // Set accept time and/or reject time
            if (opEntry.getTime() != 0 || opEntry.getRejectTime() != 0) {
                String opTime = "";
                if (opEntry.getTime() != 0)
                    opTime = mActivity.getString(R.string.accept_time) + ": " +
                            Utils.getFormattedDuration(mActivity, opEntry.getTime())
                            + " " + mActivity.getString(R.string.ago);
                if (opEntry.getRejectTime() != 0)
                    opTime += (opTime.equals("") ? "" : "\n") + mActivity.getString(R.string.reject_time)
                            + ": " + Utils.getFormattedDuration(mActivity, opEntry.getRejectTime())
                            + " " + mActivity.getString(R.string.ago);
                holder.textView8.setVisibility(View.VISIBLE);
                holder.textView8.setText(opTime);
            } else holder.textView8.setVisibility(View.GONE);
            // Set others
            if (permissionInfo != null) {
                // Set permission name
                holder.textView6.setVisibility(View.VISIBLE);
                holder.textView6.setText(String.format("%s: %s", mActivity.getString(R.string.permission_name), permissionInfo.name));
                // Description
                CharSequence description = permissionInfo.loadDescription(mPackageManager);
                if (description != null) {
                    holder.textView2.setVisibility(View.VISIBLE);
                    holder.textView2.setText(description);
                } else holder.textView2.setVisibility(View.GONE);
                // Protection level
                String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
                holder.textView3.setText(String.format(Locale.getDefault(), "\u2691 %s", protectionLevel));
                if (protectionLevel.contains("dangerous"))
                    view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
                // Set package name
                if (permissionInfo.packageName != null) {
                    holder.textView4.setVisibility(View.VISIBLE);
                    holder.textView4.setText(String.format("%s: %s",
                            mActivity.getString(R.string.package_name), permissionInfo.packageName));
                } else holder.textView4.setVisibility(View.GONE);
                // Set group name
                if (permissionInfo.group != null) {
                    holder.textView5.setVisibility(View.VISIBLE);
                    holder.textView5.setText(String.format("%s: %s",
                            mActivity.getString(R.string.group), permissionInfo.group));
                } else holder.textView5.setVisibility(View.GONE);
            } else {
                holder.textView2.setVisibility(View.GONE);
                holder.textView3.setVisibility(View.GONE);
                holder.textView4.setVisibility(View.GONE);
                holder.textView5.setVisibility(View.GONE);
                holder.textView6.setVisibility(View.GONE);
            }
            // Op Switch
            holder.toggleSwitch.setVisibility(View.VISIBLE);
            if (opEntry.getMode().equals(AppOpsManager.modeToName(AppOpsManager.MODE_ALLOWED))) {
                // op granted
                holder.toggleSwitch.setChecked(true);
            } else {
                holder.toggleSwitch.setChecked(false);
            }
            holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> new Thread(() -> {
                if (buttonView.isPressed()) {
                    if (isChecked) {
                        // Enable op
                        try {
                            mAppOpsService.setMode(opEntry.getOp(), -1, mPackageName, AppOpsManager.MODE_ALLOWED);
                            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
                                cb.setAppOp(String.valueOf(opEntry.getOp()), AppOpsManager.MODE_ALLOWED);
                            }
                            if (mAppOpsService.checkOperation(opEntry.getOp(), -1, mPackageName).equals(AppOpsManager.modeToName(AppOpsManager.MODE_ALLOWED))) {
                                AppOpsManager.OpEntry opEntry1 = new AppOpsManager.OpEntry(opEntry.getOp(),
                                        opEntry.getOpStr(), opEntry.isRunning(), AppOpsManager.modeToName(AppOpsManager.MODE_ALLOWED), opEntry.getTime(),
                                        opEntry.getRejectTime(), opEntry.getDuration(),
                                        opEntry.getProxyUid(), opEntry.getProxyPackageName());
                                AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry1);
                                appDetailsItem.name = opEntry1.getOpStr();
                                mActivity.runOnUiThread(() -> {
                                    mAdapterList.set(index, appDetailsItem);
                                    notifyDataSetChanged();
                                });
                            } else {
                                mActivity.runOnUiThread(() -> {
                                    Toast.makeText(mActivity, R.string.app_op_cannot_be_enabled, Toast.LENGTH_LONG).show();
                                    holder.toggleSwitch.setChecked(false);
                                });
                            }
                        } catch (Exception e) {
                            mActivity.runOnUiThread(() -> {
                                Toast.makeText(mActivity, R.string.failed_to_enable_op, Toast.LENGTH_LONG).show();
                                holder.toggleSwitch.setChecked(false);
                            });
                        }
                    } else {
                        // Disable permission
                        try {
                            mAppOpsService.setMode(opEntry.getOp(), -1, mPackageName, AppOpsManager.MODE_IGNORED);
                            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
                                cb.setAppOp(String.valueOf(opEntry.getOp()), AppOpsManager.MODE_IGNORED);
                            }
                            if (mAppOpsService.checkOperation(opEntry.getOp(), -1, mPackageName).equals(AppOpsManager.modeToName(AppOpsManager.MODE_IGNORED))) {
                                AppOpsManager.OpEntry opEntry1 = new AppOpsManager.OpEntry(opEntry.getOp(),
                                        opEntry.getOpStr(), opEntry.isRunning(), AppOpsManager.modeToName(AppOpsManager.MODE_IGNORED), opEntry.getTime(),
                                        opEntry.getRejectTime(), opEntry.getDuration(),
                                        opEntry.getProxyUid(), opEntry.getProxyPackageName());
                                AppDetailsItem appDetailsItem = new AppDetailsItem(opEntry1);
                                appDetailsItem.name = opEntry1.getOpStr();
                                mActivity.runOnUiThread(() -> {
                                    mAdapterList.set(index, appDetailsItem);
                                    notifyDataSetChanged();
                                });
                            } else {
                                mActivity.runOnUiThread(() -> {
                                    Toast.makeText(mActivity, R.string.app_op_cannot_be_disabled, Toast.LENGTH_LONG).show();
                                    holder.toggleSwitch.setChecked(true);
                                });
                            }
                        } catch (Exception e) {
                            mActivity.runOnUiThread(() -> {
                                Toast.makeText(mActivity, R.string.failed_to_disable_op, Toast.LENGTH_LONG).show();
                                holder.toggleSwitch.setChecked(true);
                            });
                        }
                    }
                }
            }).start());
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private void getUsesPermissionsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            @SuppressWarnings("unchecked")
            Tuple<String, Integer> permTuple = (Tuple<String, Integer>) mAdapterList.get(index).vanillaItem;
            final String permName = permTuple.getFirst();
            final int permFlags = permTuple.getSecond();
            PermissionInfo permissionInfo = null;
            try {
                permissionInfo = mPackageManager.getPermissionInfo(permName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException ignore) {}

            // Set permission name
            if (mConstraint != null && permName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView1.setText(Utils.getHighlightedText(permName, mConstraint, mColorRed));
            } else {
                holder.textView1.setText(permName);
            }
            // Set others
            if (permissionInfo != null) {
                // Description
                CharSequence description = permissionInfo.loadDescription(mPackageManager);
                if (description != null) {
                    holder.textView2.setVisibility(View.VISIBLE);
                    holder.textView2.setText(description);
                } else holder.textView2.setVisibility(View.GONE);
                // Protection level
                String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
                holder.textView3.setText(String.format(Locale.getDefault(), "\u2691 %s", protectionLevel));
                if (protectionLevel.contains("dangerous"))
                    view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
                // Set package name
                if (permissionInfo.packageName != null) {
                    holder.textView4.setVisibility(View.VISIBLE);
                    holder.textView4.setText(String.format("%s: %s",
                            mActivity.getString(R.string.package_name), permissionInfo.packageName));
                } else holder.textView4.setVisibility(View.GONE);
                // Set group name
                if (permissionInfo.group != null) {
                    holder.textView5.setVisibility(View.VISIBLE);
                    holder.textView5.setText(String.format("%s: %s",
                            mActivity.getString(R.string.group), permissionInfo.group));
                } else holder.textView5.setVisibility(View.GONE);
                // Permission Switch
                if ((isRootEnabled || isADBEnabled) && protectionLevel.contains("dangerous")) {
                    holder.toggleSwitch.setVisibility(View.VISIBLE);
                    if ((permFlags & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        // Permission granted
                        holder.toggleSwitch.setChecked(true);
                    } else {
                        holder.toggleSwitch.setChecked(false);
                    }
                    holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                            new Thread(() -> {
                        if (isChecked) {
                            // Enable permission
                            if (!Runner.run(mActivity, String.format("pm grant %s %s", mPackageName, permName)).isSuccessful()) {
                                mActivity.runOnUiThread(() -> {
                                    Toast.makeText(mActivity, "Failed to grant permission.", Toast.LENGTH_LONG).show();
                                    holder.toggleSwitch.setChecked(false);
                                });
                            } else {
                                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
                                    cb.setPermission(permName, true);
                                }
                            }
                        } else {
                            // Disable permission
                            if (!Runner.run(mActivity, String.format("pm revoke %s %s", mPackageName, permName)).isSuccessful()) {
                                mActivity.runOnUiThread(() -> {
                                    Toast.makeText(mActivity, "Failed to revoke permission.", Toast.LENGTH_LONG).show();
                                    holder.toggleSwitch.setChecked(true);
                                });
                            } else {
                                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mActivity, mPackageName)) {
                                    cb.setPermission(permName, false);
                                }
                            }
                        }
                    }).start());
                } else holder.toggleSwitch.setVisibility(View.GONE);
            } else {
                holder.textView2.setVisibility(View.GONE);
                holder.textView3.setVisibility(View.GONE);
                holder.textView4.setVisibility(View.GONE);
                holder.textView5.setVisibility(View.GONE);
                holder.toggleSwitch.setVisibility(View.GONE);
            }
        }

        private void getSharedLibsView(@NonNull ViewHolder holder, int index) {
            TextView textView = (TextView) holder.itemView;
            textView.setTextIsSelectable(true);
            textView.setText((String) mAdapterList.get(index).vanillaItem);
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_very_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, small_size, medium_size, small_size);
        }

        /**
         * Boring view inflation / creation
         */
        private void getPermissionsView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final PermissionInfo permissionInfo = (PermissionInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Label
            holder.textView1.setText(permissionInfo.loadLabel(mPackageManager));
            // Name
            if (mConstraint != null && permissionInfo.name.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.textView2.setText(Utils.getHighlightedText(permissionInfo.name, mConstraint, mColorRed));
            } else {
                holder.textView2.setText(permissionInfo.name.startsWith(mPackageName) ?
                        permissionInfo.name.replaceFirst(mPackageName, "") : permissionInfo.name);
            }
            // Icon
            holder.imageView.setImageDrawable(permissionInfo.loadIcon(mPackageManager));
            // Description
            holder.textView3.setText(permissionInfo.loadDescription(mPackageManager));
            // LaunchMode
            holder.textView4.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.group), permissionInfo.group + permAppOp(permissionInfo.name)));
            // Protection level
            String protectionLevel = Utils.getProtectionLevelString(permissionInfo);
            holder.textView5.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.protection_level), protectionLevel));
            if (protectionLevel.contains("dangerous"))
                view.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.red));
        }

        /**
         * Boring view inflation / creation
         */
        private void getFeaturesView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final FeatureInfo featureInfo = (FeatureInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // Name
            holder.textView1.setText(featureInfo.name);
            // Flags
            holder.textView2.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.flags), Utils.getFeatureFlagsString(featureInfo.flags)
                    + (Build.VERSION.SDK_INT >= 24 && featureInfo.version != 0 ? " | minV%:" + featureInfo.version : "")));
            // GLES ver
            holder.textView3.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.gles_ver), (bFi && !featureInfo.name.equals("_MAJOR") ? "_"
                            : Utils.getOpenGL(featureInfo.reqGlEsVersion))));
        }

        /**
         * Boring view inflation / creation
         */
        private void getConfigurationView(@NonNull ViewHolder holder, int index) {
            View view = holder.itemView;
            final ConfigurationInfo configurationInfo = (ConfigurationInfo) mAdapterList.get(index).vanillaItem;
            view.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            // GLES ver
            holder.textView1.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.gles_ver), Utils.getOpenGL(configurationInfo.reqGlEsVersion)));
            // Flag & others
            holder.textView2.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.input_features), configurationInfo.reqInputFeatures));
            holder.textView3.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.keyboard_type), configurationInfo.reqKeyboardType));
            holder.textView4.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.navigation), configurationInfo.reqNavigation));
            holder.textView5.setText(String.format(Locale.getDefault(), "%s: %s",
                    getString(R.string.touchscreen), configurationInfo.reqTouchScreen));
        }

        /**
         * We do not need complex views, Use recycled view if possible
         */
        private void getSignatureView(@NonNull ViewHolder holder, int index) {
            TextView textView = (TextView) holder.itemView;
            final Signature signature = (Signature) mAdapterList.get(index).vanillaItem;
            textView.setText(String.format(Locale.getDefault(), "%s\n%s",
                    Utils.signCert(signature), signature.toCharsString()));
            textView.setBackgroundColor(index % 2 == 0 ? mColorGrey1 : mColorGrey2);
            textView.setTextIsSelectable(true);
            int medium_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            int small_size = mActivity.getResources().getDimensionPixelSize(R.dimen.padding_small);
            textView.setTextSize(12);
            textView.setPadding(medium_size, 0, medium_size, small_size);
        }
    }
}
