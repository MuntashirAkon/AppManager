/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.details.info;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.*;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.android.internal.util.TextUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.details.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileMetaManager;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.*;
import io.github.muntashirakon.io.ProxyFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_FLAG_MONOSPACE;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.TERMUX_PERM_RUN_COMMAND;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.hasDumpPermission;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class AppInfoFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";
    private static final String ACTIVITY_NAME_AURORA_STORE = "com.aurora.store.ui.details.DetailsActivity";

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private LinearLayout mHorizontalLayout;
    private ChipGroup mTagCloud;
    private SwipeRefreshLayout mSwipeRefresh;
    private CharSequence mPackageLabel;
    private LinearProgressIndicator mProgressIndicator;
    private AppDetailsViewModel mainModel;
    private AppInfoViewModel model;
    private AppInfoRecyclerAdapter adapter;
    // Headers
    private TextView labelView;
    private TextView packageNameView;
    private TextView versionView;
    private ImageView iconView;
    private IconLoaderThread iconLoaderThread;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private boolean isExternalApk;
    private boolean isRootEnabled;
    private boolean isAdbEnabled;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> export = BetterActivityResult.registerForActivityResult(this, new ActivityResultContracts.CreateDocument());
    private final BetterActivityResult<String, Boolean> termux = BetterActivityResult.registerForActivityResult(this, new ActivityResultContracts.RequestPermission());
    private final BetterActivityResult<Intent, ActivityResult> activityLauncher = BetterActivityResult.registerActivityForResult(this);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        model = new ViewModelProvider(this).get(AppInfoViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity = (AppDetailsActivity) requireActivity();
        mainModel = mActivity.model;
        if (mainModel == null) return;
        model.setMainModel(mainModel);
        isRootEnabled = AppPref.isRootEnabled();
        isAdbEnabled = AppPref.isAdbEnabled();
        mPackageManager = mActivity.getPackageManager();
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(UIUtils.getAccentColor(mActivity));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(mActivity));
        mSwipeRefresh.setOnRefreshListener(this);
        // Recycler view
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        // Horizontal view
        mHorizontalLayout = view.findViewById(R.id.horizontal_layout);
        // Progress indicator
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgressIndicator(true);
        // Header
        mTagCloud = view.findViewById(R.id.tag_cloud);
        labelView = view.findViewById(R.id.label);
        packageNameView = view.findViewById(R.id.packageName);
        mPackageName = mainModel.getPackageName();
        iconView = view.findViewById(R.id.icon);
        versionView = view.findViewById(R.id.version);
        isExternalApk = mainModel.getIsExternalApk();
        // Set adapter only after package info is loaded
        executor.submit(() -> {
            mPackageName = mainModel.getPackageName();
            if (mPackageName == null) {
                mainModel.setPackageInfo(false);
                mPackageName = mainModel.getPackageName();
            }
            isExternalApk = mainModel.getIsExternalApk();
            adapter = new AppInfoRecyclerAdapter(mActivity);
            runOnUiThread(() -> recyclerView.setAdapter(adapter));
        });
        // Set observer
        mainModel.get(AppDetailsFragment.APP_INFO).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (!appDetailsItems.isEmpty() && mainModel.isPackageExist()) {
                AppDetailsItem appDetailsItem = appDetailsItems.get(0);
                mPackageInfo = (PackageInfo) appDetailsItem.vanillaItem;
                mPackageName = appDetailsItem.name;
                // Set package name
                packageNameView.setText(mPackageName);
                packageNameView.setOnClickListener(v -> {
                    // TODO: Copy to clipboard
                });
                // Set App Version
                CharSequence version = getString(R.string.version_name_with_code, mPackageInfo.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo));
                versionView.setText(version);
                // Set others
                executor.submit(this::loadPackageInfo);
            }
        });
        model.getPackageLabel().observe(getViewLifecycleOwner(), packageLabel -> {
            mPackageLabel = packageLabel;
            // Set Application Name, aka Label
            labelView.setText(mPackageLabel);
        });
        setupTagCloud();
        setupVerticalView();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (!mainModel.getIsExternalApk()) inflater.inflate(R.menu.fragment_app_info_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (isExternalApk) return;
        menu.findItem(R.id.action_open_in_termux).setVisible(isRootEnabled);
        menu.findItem(R.id.action_enable_magisk_hide).setVisible(isRootEnabled);
        boolean isDebuggable = false;
        if (mApplicationInfo != null) {
            isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        menu.findItem(R.id.action_run_in_termux).setVisible(isDebuggable);
        menu.findItem(R.id.action_battery_opt).setVisible(isRootEnabled || isAdbEnabled);
        menu.findItem(R.id.action_net_policy).setVisible(isRootEnabled || isAdbEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_share_apk) {
            executor.submit(() -> {
                try {
                    File tmpApkSource = ApkUtils.getSharableApkFile(mPackageInfo);
                    runOnUiThread(() -> {
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .setType("application/*")
                                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(mActivity, BuildConfig.APPLICATION_ID + ".provider", tmpApkSource))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getString(R.string.share_apk)));
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show());
                }
            });
        } else if (itemId == R.id.action_backup) {
            BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
            Bundle args = new Bundle();
            args.putParcelableArrayList(BackupDialogFragment.ARG_PACKAGE_PAIRS, new ArrayList<>(
                    Collections.singleton(new UserPackagePair(mPackageName, mainModel.getUserHandle()))));
            backupDialogFragment.setArguments(args);
            backupDialogFragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
            backupDialogFragment.show(mActivity.getSupportFragmentManager(), BackupDialogFragment.TAG);
        } else if (itemId == R.id.action_view_settings) {
            Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
            infoIntent.setData(Uri.parse("package:" + mPackageName));
            startActivity(infoIntent);
        } else if (itemId == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(System.currentTimeMillis()) + ".am.tsv";
            export.launch(fileName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle exportArgs = new Bundle();
                ArrayList<String> packages = new ArrayList<>();
                packages.add(mPackageName);
                exportArgs.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                exportArgs.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                exportArgs.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, packages);
                exportArgs.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, new int[]{mainModel.getUserHandle()});
                dialogFragment.setArguments(exportArgs);
                dialogFragment.show(mActivity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
        } else if (itemId == R.id.action_open_in_termux) {
            if (PermissionUtils.hasTermuxPermission(mActivity)) {
                openInTermux();
            } else termux.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) openInTermux();
            });
        } else if (itemId == R.id.action_run_in_termux) {
            if (PermissionUtils.hasTermuxPermission(mActivity)) {
                runInTermux();
            } else termux.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) runInTermux();
            });
        } else if (itemId == R.id.action_enable_magisk_hide) {
            if (MagiskUtils.hide(mPackageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                    cb.setMagiskHide(true);
                    refreshDetails();
                }
            } else {
                Toast.makeText(mActivity, R.string.failed_to_enable_magisk_hide, Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_battery_opt) {
            if (hasDumpPermission()) {
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable, (dialog, which) -> {
                            Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "-" + mPackageName});
                            refreshDetails();
                        })
                        .setNegativeButton(R.string.disable, (dialog, which) -> {
                            Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "+" + mPackageName});
                            refreshDetails();
                        })
                        .show();
            } else {
                Log.e("AppInfo", "No DUMP permission.");
            }
        } else if (itemId == R.id.action_net_policy) {
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(mActivity);
            int[] polices = new int[netPolicyMap.size()];
            String[] policyStrings = new String[netPolicyMap.size()];
            boolean[] choices = new boolean[netPolicyMap.size()];
            AtomicInteger selectedPolicies = new AtomicInteger(NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid));
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
                if (selectedPolicies.get() == 0) {
                    choices[i] = polices[i] == NetworkPolicyManager.POLICY_NONE;
                } else {
                    choices[i] = (selectedPolicies.get() & polices[i]) != 0;
                }
            }
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.net_policy)
                    .setMultiChoiceItems(policyStrings, choices, (dialog, which, isChecked) -> {
                        int currentPolicies = selectedPolicies.get();
                        if (isChecked) selectedPolicies.set(currentPolicies | polices[which]);
                        else selectedPolicies.set(currentPolicies & ~polices[which]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        try {
                            NetworkPolicyManagerCompat.setUidPolicy(mApplicationInfo.uid, selectedPolicies.get());
                            refreshDetails();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    })
                    .show();
        } else if (itemId == R.id.action_extract_icon) {
            String iconName = mPackageLabel + "_icon.png";
            export.launch(iconName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    try (OutputStream outputStream = mActivity.getContentResolver().openOutputStream(uri)) {
                        if (outputStream == null) {
                            throw new IOException("Unable to open output stream.");
                        }
                        Bitmap bitmap = IOUtils.getBitmapFromDrawable(mApplicationInfo.loadIcon(mPackageManager));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.flush();
                        Toast.makeText(mActivity, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(mActivity, R.string.saving_failed, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (itemId == R.id.action_add_to_profile) {
            HashMap<String, ProfileMetaManager> profilesMap = ProfileManager.getProfileMetadata();
            List<CharSequence> profileNames = new ArrayList<>(profilesMap.size());
            List<ProfileMetaManager> profiles = new ArrayList<>(profilesMap.size());
            ProfileMetaManager profileMetaManager;
            Spannable summary;
            for (String profileName : profilesMap.keySet()) {
                profileMetaManager = profilesMap.get(profileName);
                //noinspection ConstantConditions
                summary = TextUtils.joinSpannable(", ", profileMetaManager.getLocalisedSummaryOrComment(mActivity));
                profiles.add(profileMetaManager);
                profileNames.add(new SpannableStringBuilder(profileName).append("\n").append(getSecondaryText(mActivity, getSmallerText(summary))));
            }
            new SearchableMultiChoiceDialogBuilder<>(mActivity, profiles, profileNames)
                    .setTitle(R.string.add_to_profile)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                        for (ProfileMetaManager metaManager : selectedItems) {
                            if (metaManager.profile != null) {
                                try {
                                    metaManager.profile.packages = ArrayUtils.appendElement(String.class,
                                            metaManager.profile.packages, mPackageName);
                                    metaManager.writeProfile();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    })
                    .show();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        refreshDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onDetach() {
        executor.shutdownNow();
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        IOUtils.deleteDir(mActivity.getExternalCacheDir());
        super.onDestroy();
    }

    private void openInTermux() {
        runWithTermux(new String[]{"su", "-", String.valueOf(mApplicationInfo.uid)});
    }

    private void runInTermux() {
        runWithTermux(new String[]{"su", "-c", "run-as", mPackageName});
    }

    private void runWithTermux(String[] command) {
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", Utils.TERMUX_LOGIN_PATH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", command);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        try {
            ActivityCompat.startForegroundService(mActivity, intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void install() {
        Intent intent = new Intent(this.getContext(), PackageInstallerActivity.class);
        intent.putExtra(PackageInstallerActivity.EXTRA_APK_FILE_KEY, mainModel.getApkFileKey());
        try {
            startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    private void refreshDetails() {
        showProgressIndicator(true);
        mainModel.setIsPackageChanged();
    }

    @UiThread
    private void setupTagCloud() {
        model.getTagCloud().observe(getViewLifecycleOwner(), tagCloud -> {
            mTagCloud.removeAllViews();
            // Add tracker chip
            if (!tagCloud.trackerComponents.isEmpty()) {
                CharSequence[] trackerComponentNames = new CharSequence[tagCloud.trackerComponents.size()];
                for (int i = 0; i < trackerComponentNames.length; ++i) {
                    trackerComponentNames[i] = tagCloud.trackerComponents.get(i).name;
                }
                addChip(getResources().getQuantityString(R.plurals.no_of_trackers, tagCloud.trackerComponents.size(),
                        tagCloud.trackerComponents.size()), R.color.tracker).setOnClickListener(v -> {
                    if (!isExternalApk && isRootEnabled) {
                        new SearchableMultiChoiceDialogBuilder<>(mActivity, tagCloud.trackerComponents, trackerComponentNames)
                                .setTitle(R.string.trackers)
                                .setSelections(tagCloud.trackerComponents)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.block, (dialog, which, selectedItems) -> {
                                    showProgressIndicator(true);
                                    executor.submit(() -> {
                                        mainModel.addRules(selectedItems, true);
                                        runOnUiThread(() -> {
                                            if (isDetached()) return;
                                            showProgressIndicator(false);
                                            Toast.makeText(mActivity, R.string.done, Toast.LENGTH_SHORT).show();
                                        });
                                    });
                                })
                                .setNeutralButton(R.string.unblock, (dialog, which, selectedItems) -> {
                                    showProgressIndicator(true);
                                    executor.submit(() -> {
                                        mainModel.removeRules(selectedItems, true);
                                        runOnUiThread(() -> {
                                            if (isDetached()) return;
                                            showProgressIndicator(false);
                                            Toast.makeText(mActivity, R.string.done, Toast.LENGTH_SHORT).show();
                                        });
                                    });
                                })
                                .show();
                    } else {
                        new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.trackers)
                                .setItems(trackerComponentNames, null)
                                .setNegativeButton(R.string.close, null)
                                .show();
                    }
                });
            }
            if (tagCloud.isSystemApp) {
                if (tagCloud.isSystemlessPath) {
                    addChip(R.string.systemless_app);
                } else addChip(R.string.system_app);
                if (tagCloud.isUpdatedSystemApp) {
                    addChip(R.string.updated_app);
                }
            } else if (!mainModel.getIsExternalApk()) addChip(R.string.user_app);
            if (tagCloud.splitCount > 0) {
                addChip(getResources().getQuantityString(R.plurals.no_of_splits, tagCloud.splitCount,
                        tagCloud.splitCount)).setOnClickListener(v -> {
                    ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
                    // Display a list of apks
                    List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                    String[] entryNames = new String[tagCloud.splitCount];
                    for (int i = 0; i < tagCloud.splitCount; ++i) {
                        entryNames[i] = apkEntries.get(i + 1).toLocalizedString(mActivity);
                    }
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.splits)
                            .setItems(entryNames, null)
                            .setNegativeButton(R.string.close, null)
                            .show();
                });
            }
            if (tagCloud.isDebuggable) {
                addChip(R.string.debuggable);
            }
            if (tagCloud.isTestOnly) {
                addChip(R.string.test_only);
            }
            if (!tagCloud.hasCode) {
                addChip(R.string.no_code);
            }
            if (tagCloud.hasRequestedLargeHeap) {
                addChip(R.string.requested_large_heap, R.color.tracker);
            }
            if (tagCloud.isRunning) {
                addChip(R.string.running, R.color.running).setOnClickListener(v ->
                        mActivity.viewPager.setCurrentItem(AppDetailsFragment.SERVICES));
            }
            if (tagCloud.isForceStopped) {
                addChip(R.string.stopped, R.color.stopped);
            }
            if (!tagCloud.isAppEnabled) {
                addChip(R.string.disabled_app, R.color.disabled_user);
            }
            if (tagCloud.isMagiskHideEnabled) {
                addChip(R.string.magisk_hide_enabled).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.magisk_hide_enabled)
                        .setMessage(R.string.disable_magisk_hide)
                        .setPositiveButton(R.string.disable, (dialog, which) -> {
                            if (MagiskUtils.unhide(mPackageName)) {
                                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                                    cb.setMagiskHide(false);
                                    refreshDetails();
                                }
                            } else {
                                Toast.makeText(mActivity, R.string.failed_to_disable_magisk_hide,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
            }
            if (tagCloud.hasKeyStoreItems) {
                Chip chip;
                if (tagCloud.hasMasterKeyInKeyStore) {
                    chip = addChip(R.string.keystore, R.color.tracker);
                } else chip = addChip(R.string.keystore);
                chip.setOnClickListener(view -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.keystore)
                        .setItems(KeyStoreUtils.getKeyStoreFiles(mApplicationInfo.uid,
                                mainModel.getUserHandle()).toArray(new String[0]), null)
                        .setNegativeButton(R.string.close, null)
                        .show());
            }
            if (tagCloud.readableBackupNames.length > 0) {
                addChip(R.string.backup).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.backup)
                        .setItems(tagCloud.readableBackupNames, null)
                        .setNegativeButton(R.string.close, null)
                        .show());
            }
            if (isDetached()) return;
            if (!tagCloud.isBatteryOptimized) {
                addChip(R.string.no_battery_optimization, R.color.red_orange).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.enable_battery_optimization)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "-" + mPackageName});
                            refreshDetails();
                        })
                        .show());
            }
            if (tagCloud.netPolicies > 0) {
                String[] readablePolicies = NetworkPolicyManagerCompat.getReadablePolicies(mActivity, tagCloud.netPolicies)
                        .values().toArray(new String[0]);
                addChip(R.string.has_net_policy).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.net_policy)
                        .setItems(readablePolicies, null)
                        .setNegativeButton(R.string.ok, null)
                        .show());
            }
            if (tagCloud.ssaid != null) {
                addChip(R.string.ssaid, R.color.red_orange).setOnClickListener(v -> {
                    View view = getLayoutInflater().inflate(R.layout.dialog_ssaid_info, null);
                    AlertDialog alertDialog = new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.ssaid)
                            .setView(view)
                            .setPositiveButton(R.string.apply, null)
                            .setNegativeButton(R.string.close, null)
                            .setNeutralButton(R.string.reset_to_default, null)
                            .create();
                    TextInputEditText ssaidHolder = view.findViewById(R.id.ssaid);
                    TextInputLayout ssaidInputLayout = view.findViewById(android.R.id.text1);
                    AtomicReference<Button> positiveButton = new AtomicReference<>();
                    AtomicReference<Button> neutralButton = new AtomicReference<>();
                    AtomicReference<String> ssaid = new AtomicReference<>(tagCloud.ssaid);

                    alertDialog.setOnShowListener(dialog -> {
                        positiveButton.set(alertDialog.getButton(AlertDialog.BUTTON_POSITIVE));
                        neutralButton.set(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL));
                        positiveButton.get().setVisibility(View.GONE);
                        positiveButton.get().setOnClickListener(v2 -> {
                            SsaidSettings ssaidSettings = new SsaidSettings(mPackageName, mApplicationInfo.uid);
                            if (ssaidSettings.setSsaid(ssaid.get())) {
                                model.loadTagCloud();
                                Toast.makeText(mActivity, R.string.restart_to_reflect_changes, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(mActivity, R.string.failed_to_change_ssaid, Toast.LENGTH_LONG).show();
                            }
                            alertDialog.dismiss();
                        });
                        neutralButton.get().setVisibility(View.GONE);
                        neutralButton.get().setOnClickListener(v2 -> {
                            ssaid.set(tagCloud.ssaid);
                            ssaidHolder.setText(ssaid.get());
                            neutralButton.get().setVisibility(View.GONE);
                            positiveButton.get().setVisibility(View.GONE);
                        });
                    });
                    ssaidHolder.setText(tagCloud.ssaid);
                    ssaidHolder.setTypeface(Typeface.MONOSPACE);
                    ssaidHolder.setOnClickListener(v2 -> {
                        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("SSAID", ssaid.get());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(mActivity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                    });
                    ssaidInputLayout.setEndIconOnClickListener(v2 -> {
                        ssaid.set(SsaidSettings.generateSsaid(mPackageName));
                        ssaidHolder.setText(ssaid.get());
                        if (!tagCloud.ssaid.equals(ssaid.get())) {
                            if (neutralButton.get() != null) {
                                neutralButton.get().setVisibility(View.VISIBLE);
                            }
                            if (positiveButton.get() != null) {
                                positiveButton.get().setVisibility(View.VISIBLE);
                            }
                        }
                    });
                    alertDialog.show();
                });
            }
        });
    }

    private void setHorizontalActions() {
        mHorizontalLayout.removeAllViews();
        if (!mainModel.getIsExternalApk()) {
            // Set open
            final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
            if (launchIntentForPackage != null) {
                addToHorizontalLayout(R.string.launch_app, R.drawable.ic_open_in_new_black_24dp)
                        .setOnClickListener(v -> startActivity(launchIntentForPackage));
            }
            // Set disable
            if (isRootEnabled || isAdbEnabled) {
                if (mApplicationInfo.enabled) {
                    addToHorizontalLayout(R.string.disable, R.drawable.ic_block_black_24dp).setOnClickListener(v -> {
                        try {
                            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                    0, mainModel.getUserHandle());
                        } catch (RemoteException e) {
                            Log.e("AppInfo", e);
                            Toast.makeText(mActivity, getString(R.string.failed_to_disable, mPackageLabel), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (AppPref.isRootOrAdbEnabled()) {
                    ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                            isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                            .setCheckboxLabel(R.string.keep_data_and_signatures)
                            .setTitle(mPackageLabel)
                            .setPositiveButton(R.string.uninstall, (dialog, which, keepData) -> executor.submit(() -> {
                                try {
                                    PackageInstallerCompat.uninstall(mPackageName, mainModel.getUserHandle(), keepData);
                                    runOnUiThread(() -> {
                                        Toast.makeText(mActivity, getString(R.string.uninstalled_successfully, mPackageLabel), Toast.LENGTH_LONG).show();
                                        mActivity.finish();
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_uninstall, mPackageLabel), Toast.LENGTH_LONG).show());
                                }
                            }))
                            .setNegativeButton(R.string.cancel, (dialog, which, keepData) -> {
                                if (dialog != null) dialog.cancel();
                            });
                    if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                        builder.setNeutralButton(R.string.uninstall_updates, (dialog, which, keepData) ->
                                executor.submit(() -> {
                                    Runner.Result result = RunnerUtils.uninstallPackageUpdate(mPackageName, mainModel.getUserHandle(), keepData);
                                    if (result.isSuccessful()) {
                                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.update_uninstalled_successfully, mPackageLabel), Toast.LENGTH_LONG).show());
                                    } else {
                                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_uninstall_updates, mPackageLabel), Toast.LENGTH_LONG).show());
                                    }
                                }));
                    }
                    builder.show();
                } else {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                    startActivity(uninstallIntent);
                }
            });
            // Enable/disable app (root only)
            if (isRootEnabled || isAdbEnabled) {
                if (!mApplicationInfo.enabled) {
                    // Enable app
                    addToHorizontalLayout(R.string.enable, R.drawable.ic_baseline_get_app_24).setOnClickListener(v -> {
                        try {
                            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0,
                                    mainModel.getUserHandle());
                        } catch (RemoteException e) {
                            Log.e("AppInfo", e);
                            Toast.makeText(mActivity, getString(R.string.failed_to_enable, mPackageLabel), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                // Force stop
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    addToHorizontalLayout(R.string.force_stop, R.drawable.ic_baseline_power_settings_new_24).setOnClickListener(v -> executor.submit(() -> {
                        try {
                            PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                            runOnUiThread(this::refreshDetails);
                        } catch (RemoteException | SecurityException e) {
                            runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_stop, mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }));
                }
                // Clear data
                addToHorizontalLayout(R.string.clear_data, R.drawable.ic_delete_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mPackageLabel)
                                .setMessage(R.string.clear_data_message)
                                .setPositiveButton(R.string.clear, (dialog, which) ->
                                        executor.submit(() -> {
                                            if (PackageManagerCompat.clearApplicationUserData(mPackageName, mainModel.getUserHandle())) {
                                                runOnUiThread(this::refreshDetails);
                                            }
                                        }))
                                .setNegativeButton(R.string.cancel, null)
                                .show());
                // Clear cache
                if (isRootEnabled) {
                    addToHorizontalLayout(R.string.clear_cache, R.drawable.ic_delete_black_24dp)
                            .setOnClickListener(v -> executor.submit(() -> {
                                if (PackageManagerCompat.deleteApplicationCacheFilesAsUser(mPackageName, mainModel.getUserHandle())) {
                                    runOnUiThread(this::refreshDetails);
                                }
                            }));
                }
            }  // End root only
        } else if (FeatureController.isInstallerEnabled()) {
            if (mInstalledPackageInfo == null) {
                // App not installed
                addToHorizontalLayout(R.string.install, R.drawable.ic_baseline_get_app_24)
                        .setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {  // FIXME: Check for signature
                    // Needs update
                    addToHorizontalLayout(R.string.whats_new, R.drawable.ic_info_outline_black_24dp)
                            .setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, mPackageInfo);
                                args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, mInstalledPackageInfo);
                                WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                                dialogFragment.setArguments(args);
                                dialogFragment.show(mActivity.getSupportFragmentManager(), WhatsNewDialogFragment.TAG);
                            });
                    addToHorizontalLayout(R.string.update, R.drawable.ic_baseline_get_app_24)
                            .setOnClickListener(v -> install());
                } else if (installedVersionCode == thisVersionCode) {
                    // Needs reinstall
                    addToHorizontalLayout(R.string.reinstall, R.drawable.ic_baseline_get_app_24)
                            .setOnClickListener(v -> install());
                } else {
                    // Needs downgrade
                    if (AppPref.isRootOrAdbEnabled()) {
                        addToHorizontalLayout(R.string.downgrade, R.drawable.ic_baseline_get_app_24)
                                .setOnClickListener(v -> install());
                    }
                }
            }
        }
        // Set manifest
        if (FeatureController.isManifestEnabled()) {
            addToHorizontalLayout(R.string.manifest, R.drawable.ic_tune_black_24dp).setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
                if (apkFile.isSplit()) {
                    // Display a list of apks
                    List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                    String[] entryNames = new String[apkEntries.size()];
                    for (int i = 0; i < apkEntries.size(); ++i) {
                        entryNames[i] = apkEntries.get(i).toLocalizedString(requireActivity());
                    }
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.select_apk)
                            .setItems(entryNames, (dialog, which) -> executor.submit(() -> {
                                try {
                                    File file = apkEntries.get(which).getRealCachedFile();
                                    intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                                    runOnUiThread(() -> startActivity(intent));
                                } catch (IOException | RemoteException e) {
                                    e.printStackTrace();
                                }
                            }))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    // Open directly
                    if (mainModel.getIsExternalApk()) {
                        File file = new File(mApplicationInfo.publicSourceDir);
                        intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                    } else {
                        intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
                    }
                    startActivity(intent);
                }
            });
        }
        // Set scanner
        if (FeatureController.isScannerEnabled()) {
            addToHorizontalLayout(R.string.scanner, R.drawable.ic_baseline_security_24).setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ScannerActivity.class);
                intent.putExtra(ScannerActivity.EXTRA_IS_EXTERNAL, isExternalApk);
                File file = new File(mApplicationInfo.publicSourceDir);
                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                startActivity(intent);
            });
        }
        // Root only features
        if (!mainModel.getIsExternalApk()) {
            // Shared prefs (root only)
            final List<ProxyFile> sharedPrefs = new ArrayList<>();
            ProxyFile[] tmpPaths = getSharedPrefs(mApplicationInfo.dataDir);
            if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getSharedPrefs(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            }
            if (!sharedPrefs.isEmpty()) {
                CharSequence[] sharedPrefNames = new CharSequence[sharedPrefs.size()];
                for (int i = 0; i < sharedPrefs.size(); ++i) {
                    sharedPrefNames[i] = sharedPrefs.get(i).getName();
                }
                addToHorizontalLayout(R.string.shared_prefs, R.drawable.ic_view_list_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.shared_prefs)
                                .setItems(sharedPrefNames, (dialog, which) -> {
                                    Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which).getAbsolutePath());
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mPackageLabel);
                                    startActivity(intent);
                                })
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }
            // Databases (root only)
            final List<ProxyFile> databases = new ArrayList<>();
            tmpPaths = getDatabases(mApplicationInfo.dataDir);
            if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getDatabases(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            }
            if (!databases.isEmpty()) {
                CharSequence[] databases2 = new CharSequence[databases.size()];
                for (int i = 0; i < databases.size(); ++i) {
                    databases2[i] = databases.get(i).getName();
                }
                addToHorizontalLayout(R.string.databases, R.drawable.ic_assignment_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.databases)
                                .setItems(databases2, (dialog, which) -> {
                                    // TODO(10/9/20): Need a custom ContentProvider
                                })
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }
        }  // End root only features
        // Set F-Droid
        Intent fdroid_intent = new Intent(Intent.ACTION_VIEW);
        fdroid_intent.setData(Uri.parse("https://f-droid.org/packages/" + mPackageName));
        List<ResolveInfo> resolvedActivities = mPackageManager.queryIntentActivities(fdroid_intent, 0);
        if (resolvedActivities.size() > 0) {
            addToHorizontalLayout(R.string.fdroid, R.drawable.ic_frost_fdroid_black_24dp)
                    .setOnClickListener(v -> {
                        try {
                            startActivity(fdroid_intent);
                        } catch (Exception ignored) {
                        }
                    });
        }
        // Set Aurora Store
        try {
            if (!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_STORE, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.store, R.drawable.ic_frost_aurorastore_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setClassName(PACKAGE_NAME_AURORA_STORE, ACTIVITY_NAME_AURORA_STORE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @GuardedBy("mListItems")
    private void setPathsAndDirectories(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Paths and directories
            mListItems.add(ListItem.getGroupHeader(getString(R.string.paths_and_directories)));
            // Source directory (apk path)
            if (appInfo.sourceDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.source_dir), appInfo.sourceDir,
                        openAsFolderInFM(appInfo.sourceDir)));
            }
            // Split source directories
            if (appInfo.splitEntries.size() > 0) {
                for (ApkFile.Entry entry : appInfo.splitEntries) {
                    mListItems.add(ListItem.getSelectableRegularItem(entry.toLocalizedString(mActivity),
                            entry.getApkSource(), openAsFolderInFM(entry.getApkSource())));
                }
            }
            // Data dir
            if (appInfo.dataDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.data_dir), appInfo.dataDir,
                        openAsFolderInFM(appInfo.dataDir)));
            }
            // Device-protected data dir
            if (appInfo.dataDeDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.dev_protected_data_dir), appInfo.dataDeDir,
                        openAsFolderInFM(appInfo.dataDeDir)));
            }
            // External data dirs
            if (appInfo.extDataDirs.size() == 1) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_data_dir), appInfo.extDataDirs.get(0),
                        openAsFolderInFM(appInfo.extDataDirs.get(0))));
            } else {
                for (int i = 0; i < appInfo.extDataDirs.size(); ++i) {
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_multiple_data_dir, i),
                            appInfo.extDataDirs.get(i), openAsFolderInFM(appInfo.extDataDirs.get(i))));
                }
            }
            // Native JNI library dir
            if (appInfo.jniDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.native_library_dir), appInfo.jniDir,
                        openAsFolderInFM(appInfo.jniDir)));
            }
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @GuardedBy("mListItems")
    private void setMoreInfo(AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Set more info
            mListItems.add(ListItem.getGroupHeader(getString(R.string.more_info)));

            // Set installer version info
            if (isExternalApk && mInstalledPackageInfo != null) {
                ListItem listItem = ListItem.getSelectableRegularItem(getString(R.string.installed_version),
                        getString(R.string.version_name_with_code, mInstalledPackageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo)), v -> {
                            Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                            appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, mPackageName);
                            mActivity.startActivity(appDetailsIntent);
                        });
                listItem.actionIcon = R.drawable.ic_info_outline_black_24dp;
                mListItems.add(listItem);
            }

            // SDK
            final StringBuilder sdk = new StringBuilder();
            sdk.append(getString(R.string.sdk_max)).append(": ").append(mApplicationInfo.targetSdkVersion);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                sdk.append(", ").append(getString(R.string.sdk_min)).append(": ").append(mApplicationInfo.minSdkVersion);
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.sdk), sdk.toString()));

            // Set Flags
            final StringBuilder flags = new StringBuilder();
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                flags.append("FLAG_DEBUGGABLE");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_TEST_ONLY");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_MULTIARCH) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_MULTIARCH");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_HARDWARE_ACCELERATED");

            if (flags.length() != 0) {
                ListItem flagsItem = ListItem.getSelectableRegularItem(getString(R.string.sdk_flags), flags.toString());
                flagsItem.flags |= LIST_ITEM_FLAG_MONOSPACE;
                mListItems.add(flagsItem);
            }
            if (isExternalApk) return;

            mListItems.add(ListItem.getRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
            mListItems.add(ListItem.getRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
            if (!mPackageName.equals(mApplicationInfo.processName)) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.process_name), mApplicationInfo.processName));
            }
            if (appInfo.installerApp != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.installer_app), appInfo.installerApp));
            }
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.user_id), Integer.toString(mApplicationInfo.uid)));
            if (mPackageInfo.sharedUserId != null)
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.shared_user_id), mPackageInfo.sharedUserId));
            // Main activity
            if (appInfo.mainActivity != null) {
                final ComponentName launchComponentName = appInfo.mainActivity.getComponent();
                if (launchComponentName != null) {
                    final String mainActivity = launchComponentName.getClassName();
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.main_activity), mainActivity,
                            view -> startActivity(appInfo.mainActivity)));
                }
            }
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    private void setDataUsage(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.getGroupHeader(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_transmitted), getReadableSize(appInfo.dataTx)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_received), getReadableSize(appInfo.dataRx)));
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @UiThread
    @GuardedBy("mListItems")
    private void setupVerticalView() {
        model.getAppInfo().observe(getViewLifecycleOwner(), appInfo -> {
            synchronized (mListItems) {
                mListItems.clear();
                if (!isExternalApk) {
                    setPathsAndDirectories(appInfo);
                    setDataUsage(appInfo);
                    // Storage and Cache
                    if ((boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
                        setStorageAndCache(appInfo);
                    }
                }
                setMoreInfo(appInfo);
                adapter.setAdapterList(mListItems);
            }
        });
    }

    @Nullable
    private ProxyFile[] getSharedPrefs(@NonNull String sourceDir) {
        ProxyFile sharedPath = new ProxyFile(sourceDir, "shared_prefs");
        return sharedPath.listFiles();
    }

    private ProxyFile[] getDatabases(@NonNull String sourceDir) {
        ProxyFile sharedPath = new ProxyFile(sourceDir, "databases");
        return sharedPath.listFiles((dir, name) -> !name.endsWith("-journal"));
    }

    @NonNull
    private View.OnClickListener openAsFolderInFM(String dir) {
        return view -> {
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(Uri.parse(dir), "resource/folder");
            openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (openFile.resolveActivityInfo(mPackageManager, 0) != null)
                startActivity(openFile);
        };
    }

    @NonNull
    private Chip addChip(@StringRes int resId, @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text, @SuppressWarnings("SameParameterValue") @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(@StringRes int resId) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private View addToHorizontalLayout(@StringRes int stringResId, @DrawableRes int iconResId) {
        View view = getLayoutInflater().inflate(R.layout.item_app_info_actions, mHorizontalLayout, false);
        TextView textView = view.findViewById(R.id.item_text);
        textView.setText(stringResId);
        textView.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(mActivity, iconResId), null, null);
        mHorizontalLayout.addView(view);
        return view;
    }

    @GuardedBy("mListItems")
    private void setStorageAndCache(AppInfoViewModel.AppInfo appInfo) {
        if (!Utils.hasUsageStatsPermission(mActivity)) {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.grant_usage_access)
                    .setMessage(R.string.grant_usage_acess_message)
                    .setPositiveButton(R.string.go, (dialog, which) -> activityLauncher.launch(new Intent(
                            Settings.ACTION_USAGE_ACCESS_SETTINGS), result -> {
                        if (Utils.hasUsageStatsPermission(mActivity)) {
                            AppPref.set(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL, true);
                            // Reload app info
                            model.loadAppInfo();
                        }
                    }))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.never_ask, (dialog, which) ->
                            AppPref.set(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL, false))
                    .setCancelable(false)
                    .show());
            return;
        }
        synchronized (mListItems) {
            mListItems.add(ListItem.getGroupHeader(getString(R.string.storage_and_cache)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.app_size), getReadableSize(appInfo.codeSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_size), getReadableSize(appInfo.dataSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.cache_size), getReadableSize(appInfo.cacheSize)));
            if (appInfo.obbSize != 0) {
                mListItems.add(ListItem.getInlineItem(getString(R.string.obb_size), getReadableSize(appInfo.obbSize)));
            }
            if (appInfo.mediaSize != 0) {
                mListItems.add(ListItem.getInlineItem(getString(R.string.media_size), getReadableSize(appInfo.mediaSize)));
            }
            mListItems.add(ListItem.getInlineItem(getString(R.string.total_size), getReadableSize(appInfo.codeSize
                    + appInfo.dataSize + appInfo.cacheSize + appInfo.obbSize + appInfo.mediaSize)));
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        mInstalledPackageInfo = mainModel.getInstalledPackageInfo();
        mApplicationInfo = mPackageInfo.applicationInfo;
        // Set App Icon
        if (iconLoaderThread != null) iconLoaderThread.interrupt();
        iconLoaderThread = new IconLoaderThread(iconView, mApplicationInfo);
        iconLoaderThread.start();
        // (Re)load views
        model.loadPackageLabel();
        model.loadTagCloud();
        runOnUiThread(() -> {
            if (isAdded() && !isDetached()) {
                setHorizontalActions();
            }
        });
        model.loadAppInfo();
        runOnUiThread(() -> showProgressIndicator(false));
    }

    /**
     * Get Unix time to formatted time.
     *
     * @param time Unix time
     * @return Formatted time
     */
    @NonNull
    private String getTime(long time) {
        return DateUtils.formatWeekMediumDateTime(time);
    }

    /**
     * Format sizes (bytes to B, KB, MB etc.).
     *
     * @param size Size in Bytes
     * @return Formatted size
     */
    private String getReadableSize(long size) {
        return Formatter.formatFileSize(mActivity, size);
    }

    private void showProgressIndicator(boolean show) {
        if (mProgressIndicator == null) return;
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    private void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }
}
