// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.Formatter;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AnyThread;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.internal.util.TextUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService;
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
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
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
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.IntentUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.SsaidSettings;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_FLAG_MONOSPACE;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.TERMUX_PERM_RUN_COMMAND;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.hasDumpPermission;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayShortToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

public class AppInfoFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    public static final String TAG = "AppInfoFragment";

    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private ViewGroup mHorizontalLayout;
    private ViewGroup mTagCloud;
    private SwipeRefreshLayout mSwipeRefresh;
    private CharSequence mPackageLabel;
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private AppDetailsViewModel mainModel;
    private AppInfoViewModel model;
    private AppInfoRecyclerAdapter adapter;
    // Headers
    private TextView labelView;
    private TextView packageNameView;
    private TextView versionView;
    private ImageView iconView;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private boolean isExternalApk;
    private boolean isRootEnabled;
    private boolean isAdbEnabled;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> export = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument());
    private final BetterActivityResult<String, Boolean> requestPerm = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.RequestPermission());
    private final BetterActivityResult<Intent, ActivityResult> activityLauncher = BetterActivityResult
            .registerActivityForResult(this);

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
        iconView = view.findViewById(R.id.icon);
        versionView = view.findViewById(R.id.version);
        adapter = new AppInfoRecyclerAdapter(mActivity);
        recyclerView.setAdapter(adapter);
        // Set observer
        mainModel.get(AppDetailsFragment.APP_INFO).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && !appDetailsItems.isEmpty() && mainModel.isPackageExist()) {
                AppDetailsItem appDetailsItem = appDetailsItems.get(0);
                mPackageInfo = (PackageInfo) appDetailsItem.vanillaItem;
                mPackageName = appDetailsItem.name;
                mInstalledPackageInfo = mainModel.getInstalledPackageInfo();
                isExternalApk = mainModel.getIsExternalApk();
                mApplicationInfo = mPackageInfo.applicationInfo;
                // Set package name
                packageNameView.setText(mPackageName);
                packageNameView.setOnClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Package Name", mPackageName);
                    clipboard.setPrimaryClip(clip);
                    displayShortToast(R.string.copied_to_clipboard);
                });
                // Set App Version
                CharSequence version = getString(R.string.version_name_with_code, mPackageInfo.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo));
                versionView.setText(version);
                // Set others
                executor.submit(this::loadPackageInfo);
            } else showProgressIndicator(false);
        });
        model.getPackageLabel().observe(getViewLifecycleOwner(), packageLabel -> {
            mPackageLabel = packageLabel;
            // Set Application Name, aka Label
            labelView.setText(mPackageLabel);
        });
        iconView.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            executor.submit(() -> {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    String data = clipData.getItemAt(0).getText().toString().trim().toLowerCase(Locale.ROOT);
                    if (data.matches("[0-9a-f: \n]+")) {
                        data = data.replaceAll("[: \n]+", "");
                        Signature[] signatures = PackageUtils.getSigningInfo(mPackageInfo, isExternalApk);
                        if (signatures != null && signatures.length == 1) {
                            byte[] certBytes = signatures[0].toByteArray();
                            Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
                            for (Pair<String, String> digest : digests) {
                                if (digest.second.equals(data)) {
                                    if (digest.first.equals(DigestUtils.MD5) || digest.first.equals(DigestUtils.SHA_1)) {
                                        runOnUiThread(() -> displayLongToast(R.string.verified_using_unreliable_hash));
                                    } else runOnUiThread(() -> displayLongToast(R.string.verified));
                                    return;
                                }
                            }
                        }
                        runOnUiThread(() -> displayLongToast(R.string.not_verified));
                    }
                }
            });
        });
        setupTagCloud();
        setupVerticalView();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mainModel != null && !mainModel.getIsExternalApk()) {
            inflater.inflate(R.menu.fragment_app_info_actions, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (isExternalApk) return;
        try {
            menu.findItem(R.id.action_open_in_termux).setVisible(isRootEnabled);
            menu.findItem(R.id.action_enable_magisk_hide).setVisible(isRootEnabled);
            boolean isDebuggable = false;
            if (mApplicationInfo != null) {
                isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            }
            menu.findItem(R.id.action_run_in_termux).setVisible(isDebuggable);
            menu.findItem(R.id.action_battery_opt).setVisible(isRootEnabled || isAdbEnabled);
            menu.findItem(R.id.action_net_policy).setVisible(isRootEnabled || isAdbEnabled);
        } catch (NullPointerException ignore) {
            // Options menu is uninitialised for some reason, just suppress the exception
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_share_apk) {
            executor.submit(() -> {
                try {
                    Path tmpApkSource = ApkUtils.getSharableApkFile(mPackageInfo);
                    UiThreadHandler.run(() -> {
                        Context ctx = AppManager.getContext();
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .setType("application/*")
                                .putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(tmpApkSource))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_apk))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    });
                } catch (Exception e) {
                    Log.e(TAG, e);
                    displayLongToast(R.string.failed_to_extract_apk_file);
                }
            });
        } else if (itemId == R.id.action_backup) {
            if (mainModel == null) return true;
            BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
            Bundle args = new Bundle();
            args.putParcelableArrayList(BackupDialogFragment.ARG_PACKAGE_PAIRS, new ArrayList<>(
                    Collections.singleton(new UserPackagePair(mPackageName, mainModel.getUserHandle()))));
            backupDialogFragment.setArguments(args);
            backupDialogFragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
            backupDialogFragment.show(mActivity.getSupportFragmentManager(), BackupDialogFragment.TAG);
        } else if (itemId == R.id.action_view_settings) {
            startActivity(IntentUtils.getAppDetailsSettings(mPackageName));
        } else if (itemId == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(System.currentTimeMillis()) + ".am.tsv";
            export.launch(fileName, uri -> {
                if (uri == null || mainModel == null) {
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
            } else requestPerm.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) openInTermux();
            });
        } else if (itemId == R.id.action_run_in_termux) {
            if (PermissionUtils.hasTermuxPermission(mActivity)) {
                runInTermux();
            } else requestPerm.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) runInTermux();
            });
        } else if (itemId == R.id.action_enable_magisk_hide) {
            if (mainModel == null) return true;
            if (MagiskUtils.hide(mPackageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                    cb.setMagiskHide(true);
                    refreshDetails();
                }
            } else {
                displayLongToast(R.string.failed_to_enable_magisk_hide);
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
                Log.e(TAG, "No DUMP permission.");
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
                        Bitmap bitmap = FileUtils.getBitmapFromDrawable(mApplicationInfo.loadIcon(mPackageManager));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.flush();
                        displayShortToast(R.string.saved_successfully);
                    }
                } catch (IOException e) {
                    Log.e(TAG, e);
                    displayShortToast(R.string.saving_failed);
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
        if (mainModel == null) return;
        Intent intent = new Intent(this.getContext(), PackageInstallerActivity.class);
        intent.putExtra(PackageInstallerActivity.EXTRA_APK_FILE_KEY, mainModel.getApkFileKey());
        try {
            startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    @UiThread
    private void refreshDetails() {
        if (mainModel == null || executor.isShutdown()) return;
        showProgressIndicator(true);
        executor.submit(() -> {
            if (mainModel != null) {
                mainModel.setIsPackageChanged();
            }
        });
    }

    @UiThread
    private void setupTagCloud() {
        model.getTagCloud().observe(getViewLifecycleOwner(), tagCloud -> {
            mTagCloud.removeAllViews();
            if (mainModel == null) return;
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
                                            displayShortToast(R.string.done);
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
                                            displayShortToast(R.string.done);
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
                    try (ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey())) {
                        // Display a list of apks
                        List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                        CharSequence[] entryNames = new CharSequence[tagCloud.splitCount];
                        for (int i = 0; i < tagCloud.splitCount; ++i) {
                            entryNames[i] = apkEntries.get(i + 1).toLocalizedString(mActivity);
                        }
                        new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.splits)
                                .setItems(entryNames, null)
                                .setNegativeButton(R.string.close, null)
                                .show();
                    }
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
            if (tagCloud.runningServices.size() > 0) {
                addChip(R.string.running, R.color.running).setOnClickListener(v -> {
                    mProgressIndicator.show();
                    executor.submit(() -> {
                        CharSequence[] runningServices = new CharSequence[tagCloud.runningServices.size()];
                        for (int i = 0; i < runningServices.length; ++i) {
                            runningServices[i] = new SpannableStringBuilder()
                                    .append(getBoldString(tagCloud.runningServices.get(i).service.getShortClassName()))
                                    .append("\n")
                                    .append(getSmallerText(new SpannableStringBuilder()
                                            .append(getStyledKeyValue(mActivity, R.string.process_name,
                                                    tagCloud.runningServices.get(i).process)).append("\n")
                                            .append(getStyledKeyValue(mActivity, R.string.pid,
                                                    String.valueOf(tagCloud.runningServices.get(i).pid)))));
                        }
                        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(mActivity)
                                .setTitle(R.string.running_services);
                        if (PermissionUtils.hasDumpPermission() && FeatureController.isLogViewerEnabled()) {
                            titleBuilder.setSubtitle(R.string.running_services_logcat_hint);
                        }
                        runOnUiThread(() -> {
                            mProgressIndicator.hide();
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
                                    .setCustomTitle(titleBuilder.build())
                                    .setItems(runningServices, (dialog, which) -> {
                                        if (!FeatureController.isLogViewerEnabled()) return;
                                        Intent logViewerIntent = new Intent(mActivity.getApplicationContext(), LogViewerActivity.class)
                                                .setAction(LogViewerActivity.ACTION_LAUNCH)
                                                .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + tagCloud.runningServices.get(which).pid)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        mActivity.startActivity(logViewerIntent);
                                    })
                                    .setNeutralButton(R.string.force_stop, (dialog, which) -> executor.submit(() -> {
                                        try {
                                            PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                                            runOnUiThread(this::refreshDetails);
                                        } catch (RemoteException | SecurityException e) {
                                            Log.e(TAG, e);
                                            displayLongToast(R.string.failed_to_stop, mPackageLabel);
                                        }
                                    }))
                                    .setNegativeButton(R.string.close, null);
                            builder.show();
                        });
                    });
                });
            }
            if (tagCloud.isForceStopped) {
                addChip(R.string.stopped, R.color.stopped);
            }
            if (!tagCloud.isAppEnabled) {
                addChip(R.string.disabled_app, R.color.disabled_user);
            }
            if (tagCloud.isAppSuspended) {
                addChip(R.string.suspended, R.color.stopped);
            }
            if (tagCloud.isAppHidden) {
                addChip(R.string.hidden, R.color.disabled_user);
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
                                displayLongToast(R.string.failed_to_disable_magisk_hide);
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
            if (tagCloud.backups.length > 0) {
                CharSequence[] backupNames = new CharSequence[tagCloud.backups.length];
                for (int i = 0; i < tagCloud.backups.length; ++i) {
                    backupNames[i] = tagCloud.backups[i].toLocalizedString(mActivity);
                }
                addChip(R.string.backup).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.backup)
                        .setItems(backupNames, null)
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
            if (tagCloud.ssaid != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                    AtomicReference<Button> applyButton = new AtomicReference<>();
                    AtomicReference<Button> resetButton = new AtomicReference<>();
                    AtomicReference<String> ssaid = new AtomicReference<>(tagCloud.ssaid);

                    alertDialog.setOnShowListener(dialog -> {
                        applyButton.set(alertDialog.getButton(AlertDialog.BUTTON_POSITIVE));
                        resetButton.set(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL));
                        applyButton.get().setVisibility(View.GONE);
                        applyButton.get().setOnClickListener(v2 -> executor.submit(() -> {
                            try {
                                SsaidSettings ssaidSettings = new SsaidSettings(mPackageName, mApplicationInfo.uid);
                                if (ssaidSettings.setSsaid(ssaid.get())) {
                                    model.loadTagCloud();
                                    runOnUiThread(() -> displayLongToast(R.string.restart_to_reflect_changes));
                                } else {
                                    runOnUiThread(() -> displayLongToast(R.string.failed_to_change_ssaid));
                                }
                                alertDialog.dismiss();
                            } catch (IOException ignore) {
                            }
                        }));
                        resetButton.get().setVisibility(View.GONE);
                        resetButton.get().setOnClickListener(v2 -> {
                            ssaid.set(tagCloud.ssaid);
                            ssaidHolder.setText(ssaid.get());
                            resetButton.get().setVisibility(View.GONE);
                            applyButton.get().setVisibility(View.GONE);
                        });
                    });
                    ssaidHolder.setText(tagCloud.ssaid);
                    ssaidHolder.setTypeface(Typeface.MONOSPACE);
                    ssaidHolder.setOnClickListener(v2 -> {
                        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("SSAID", ssaid.get());
                        clipboard.setPrimaryClip(clip);
                        displayShortToast(R.string.copied_to_clipboard);
                    });
                    ssaidInputLayout.setEndIconOnClickListener(v2 -> {
                        ssaid.set(SsaidSettings.generateSsaid(mPackageName));
                        ssaidHolder.setText(ssaid.get());
                        if (!tagCloud.ssaid.equals(ssaid.get())) {
                            if (resetButton.get() != null) {
                                resetButton.get().setVisibility(View.VISIBLE);
                            }
                            if (applyButton.get() != null) {
                                applyButton.get().setVisibility(View.VISIBLE);
                            }
                        }
                    });
                    alertDialog.show();
                });
            }
            if (tagCloud.uriGrants != null) {
                addChip(R.string.saf).setOnClickListener(v -> {
                    CharSequence[] uriGrants = new CharSequence[tagCloud.uriGrants.size()];
                    for (int i = 0; i < tagCloud.uriGrants.size(); ++i) {
                        uriGrants[i] = tagCloud.uriGrants.get(i).uri.toString();
                    }
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.saf)
                            .setItems(uriGrants, null)
                            .setNegativeButton(R.string.close, null)
                            .show();
                });
            }
        });
    }

    private void setHorizontalActions() {
        mHorizontalLayout.removeAllViews();
        if (mainModel != null && !mainModel.getIsExternalApk()) {
            // Set open
            final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
            if (launchIntentForPackage != null) {
                addToHorizontalLayout(R.string.launch_app, R.drawable.ic_open_in_new_black_24dp)
                        .setOnClickListener(v -> {
                            try {
                                startActivity(launchIntentForPackage);
                            } catch (Throwable th) {
                                UIUtils.displayLongToast(th.getLocalizedMessage());
                            }
                        });
            }
            // Set disable
            if (isRootEnabled || isAdbEnabled) {
                if (mApplicationInfo.enabled) {
                    addToHorizontalLayout(R.string.disable, R.drawable.ic_block_black_24dp).setOnClickListener(v -> {
                        if (BuildConfig.APPLICATION_ID.equals(mPackageName)) {
                            new MaterialAlertDialogBuilder(mActivity)
                                    .setMessage(R.string.are_you_sure)
                                    .setPositiveButton(R.string.yes, (d, w) -> disable())
                                    .setNegativeButton(R.string.no, null)
                                    .show();
                        } else disable();
                    });
                }
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_trash_can_outline).setOnClickListener(v -> {
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (AppPref.isRootOrAdbEnabled()) {
                    ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                            isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                            .setCheckboxLabel(R.string.keep_data_and_app_signing_signatures)
                            .setTitle(mPackageLabel)
                            .setPositiveButton(R.string.uninstall, (dialog, which, keepData) -> executor.submit(() -> {
                                try {
                                    PackageInstallerCompat.uninstall(mPackageName, mainModel.getUserHandle(), keepData);
                                    runOnUiThread(() -> {
                                        displayLongToast(R.string.uninstalled_successfully, mPackageLabel);
                                        mActivity.finish();
                                    });
                                } catch (Exception e) {
                                    Log.e(TAG, e);
                                    runOnUiThread(() -> displayLongToast(R.string.failed_to_uninstall, mPackageLabel));
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
                                        runOnUiThread(() -> displayLongToast(R.string.update_uninstalled_successfully, mPackageLabel));
                                    } else {
                                        runOnUiThread(() -> displayLongToast(R.string.failed_to_uninstall_updates, mPackageLabel));
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
            // Enable/disable app (root/ADB only)
            if (isRootEnabled || isAdbEnabled) {
                if (!mApplicationInfo.enabled) {
                    // Enable app
                    addToHorizontalLayout(R.string.enable, R.drawable.ic_baseline_get_app_24).setOnClickListener(v -> {
                        try {
                            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0,
                                    mainModel.getUserHandle());
                        } catch (RemoteException | SecurityException e) {
                            Log.e(TAG, e);
                            displayLongToast(R.string.failed_to_enable, mPackageLabel);
                        }
                    });
                }
            }
            if (isAdbEnabled || isRootEnabled || ServiceHelper.checkIfServiceIsRunning(mActivity,
                    NoRootAccessibilityService.class)) {
                // Force stop
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    addToHorizontalLayout(R.string.force_stop, R.drawable.ic_baseline_power_settings_new_24)
                            .setOnClickListener(v -> {
                                if (isAdbEnabled || isRootEnabled) {
                                    executor.submit(() -> {
                                        try {
                                            PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                                            runOnUiThread(this::refreshDetails);
                                        } catch (RemoteException | SecurityException e) {
                                            Log.e(TAG, e);
                                            displayLongToast(R.string.failed_to_stop, mPackageLabel);
                                        }
                                    });
                                } else {
                                    // Use accessibility
                                    AccessibilityMultiplexer.getInstance().enableForceStop(true);
                                    activityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                            result -> {
                                                AccessibilityMultiplexer.getInstance().enableForceStop(false);
                                                refreshDetails();
                                            });
                                }
                            });
                }
                // Clear data
                addToHorizontalLayout(R.string.clear_data, R.drawable.ic_trash_can_outline)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mPackageLabel)
                                .setMessage(R.string.clear_data_message)
                                .setPositiveButton(R.string.clear, (dialog, which) -> {
                                    if (isAdbEnabled || isRootEnabled) {
                                        executor.submit(() -> {
                                            if (PackageManagerCompat.clearApplicationUserData(mPackageName, mainModel.getUserHandle())) {
                                                runOnUiThread(this::refreshDetails);
                                            }
                                        });
                                    } else {
                                        // Use accessibility
                                        AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                        AccessibilityMultiplexer.getInstance().enableClearData(true);
                                        activityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                                result -> {
                                                    AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                                    AccessibilityMultiplexer.getInstance().enableClearData(false);
                                                    refreshDetails();
                                                });
                                    }
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show());
                // Clear cache
                addToHorizontalLayout(R.string.clear_cache, R.drawable.ic_trash_can_outline)
                        .setOnClickListener(v -> {
                            if (isAdbEnabled || isRootEnabled) {
                                executor.submit(() -> {
                                    if (PackageManagerCompat.deleteApplicationCacheFilesAsUser(mPackageName, mainModel.getUserHandle())) {
                                        runOnUiThread(this::refreshDetails);
                                    }
                                });
                            } else {
                                // Use accessibility
                                AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                AccessibilityMultiplexer.getInstance().enableClearCache(true);
                                activityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                        result -> {
                                            AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(false);
                                            AccessibilityMultiplexer.getInstance().enableClearCache(false);
                                            refreshDetails();
                                        });
                            }
                        });
            } else {
                // Display Android settings button
                addToHorizontalLayout(R.string.view_in_settings, R.drawable.ic_baseline_power_settings_new_24)
                        .setOnClickListener(v -> startActivity(IntentUtils.getAppDetailsSettings(mPackageName)));
            }
        } else if (FeatureController.isInstallerEnabled()) {
            if (mInstalledPackageInfo == null) {
                // App not installed
                addToHorizontalLayout(R.string.install, R.drawable.ic_baseline_get_app_24)
                        .setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {
                    // Needs update
                    addToHorizontalLayout(R.string.whats_new, R.drawable.ic_information_variant)
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
            addToHorizontalLayout(R.string.manifest, R.drawable.ic_package_variant).setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                try (ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey())) {
                    if (apkFile.isSplit()) {
                        // Display a list of apks
                        List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                        CharSequence[] entryNames = new CharSequence[apkEntries.size()];
                        for (int i = 0; i < apkEntries.size(); ++i) {
                            entryNames[i] = apkEntries.get(i).toShortLocalizedString(requireActivity());
                        }
                        new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.select_apk)
                                .setItems(entryNames, (dialog, which) -> executor.submit(() -> {
                                    try {
                                        File file = apkEntries.get(which).getRealCachedFile();
                                        intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton()
                                                .getMimeTypeFromExtension("apk"));
                                        UiThreadHandler.run(() -> startActivity(intent));
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
                            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension("apk"));
                        } else {
                            intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
                        }
                        startActivity(intent);
                    }
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
                addToHorizontalLayout(R.string.databases, R.drawable.ic_database)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.databases)
                                .setItems(databases2, (dialog, i) -> {
                                    // TODO: 7/7/21 VACUUM the database before opening it
                                    Context ctx = AppManager.getContext();
                                    Path dbPath = new Path(ctx, databases.get(i));
                                    Intent openFile = new Intent(Intent.ACTION_VIEW);
                                    openFile.setDataAndType(FmProvider.getContentUri(dbPath), "application/vnd.sqlite3");
                                    openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    if (openFile.resolveActivityInfo(ctx.getPackageManager(), 0) != null) {
                                        ctx.startActivity(openFile);
                                    }
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
            PackageInfo auroraInfo = mPackageManager.getPackageInfo(PACKAGE_NAME_AURORA_STORE, 0);
            if (PackageInfoCompat.getLongVersionCode(auroraInfo) == 36L || !auroraInfo.applicationInfo.enabled) {
                // Aurora Store is disabled or the installed version has promotional apps
                throw new PackageManager.NameNotFoundException();
            }
            addToHorizontalLayout(R.string.store, R.drawable.ic_frost_aurorastore_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setPackage(PACKAGE_NAME_AURORA_STORE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + mPackageName));
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        View v = mHorizontalLayout.getChildAt(0);
        if (v != null) v.requestFocus();
    }

    @GuardedBy("mListItems")
    private void setPathsAndDirectories(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Paths and directories
            mListItems.add(ListItem.getGroupHeader(getString(R.string.paths_and_directories)));
            // Source directory (apk path)
            if (appInfo.sourceDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.source_dir), appInfo.sourceDir,
                        openAsFolderInFM(requireContext(), appInfo.sourceDir)));
            }
            // Split source directories
            if (appInfo.splitEntries.size() > 0) {
                for (ApkFile.Entry entry : appInfo.splitEntries) {
                    mListItems.add(ListItem.getSelectableRegularItem(entry.toShortLocalizedString(mActivity),
                            entry.getApkSource(), openAsFolderInFM(requireContext(), entry.getApkSource())));
                }
            }
            // Data dir
            if (appInfo.dataDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.data_dir), appInfo.dataDir,
                        openAsFolderInFM(requireContext(), appInfo.dataDir)));
            }
            // Device-protected data dir
            if (appInfo.dataDeDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.dev_protected_data_dir), appInfo.dataDeDir,
                        openAsFolderInFM(requireContext(), appInfo.dataDeDir)));
            }
            // External data dirs
            if (appInfo.extDataDirs.size() == 1) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_data_dir), appInfo.extDataDirs.get(0),
                        openAsFolderInFM(requireContext(), appInfo.extDataDirs.get(0))));
            } else {
                for (int i = 0; i < appInfo.extDataDirs.size(); ++i) {
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_multiple_data_dir, i),
                            appInfo.extDataDirs.get(i), openAsFolderInFM(requireContext(), appInfo.extDataDirs.get(i))));
                }
            }
            // Native JNI library dir
            if (appInfo.jniDir != null) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.native_library_dir), appInfo.jniDir,
                        openAsFolderInFM(requireContext(), appInfo.jniDir)));
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
                listItem.actionIcon = R.drawable.ic_information_variant;
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
        AppUsageStatsManager.DataUsage dataUsage = appInfo.dataUsage;
        if (dataUsage == null) return;
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.getGroupHeader(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_transmitted), getReadableSize(dataUsage.getTx())));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_received), getReadableSize(dataUsage.getRx())));
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
                    if (FeatureController.isUsageAccessEnabled()) {
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
        return sharedPath.listFiles((dir, name) -> !(name.endsWith("-journal")
                || name.endsWith("-wal") || name.endsWith("-shm")));
    }

    @NonNull
    private Chip addChip(@StringRes int resId, @ColorRes int color) {
        Chip chip = new Chip(mTagCloud.getContext());
        chip.setText(resId);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text, @ColorRes int color) {
        Chip chip = new Chip(mTagCloud.getContext());
        chip.setText(text);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(@StringRes int resId) {
        Chip chip = new Chip(mTagCloud.getContext());
        chip.setText(resId);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text) {
        Chip chip = new Chip(mTagCloud.getContext());
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
        if (FeatureController.isUsageAccessEnabled()) {
            // Grant optional READ_PHONE_STATE permission
            if (!PermissionUtils.hasPermission(mActivity, Manifest.permission.READ_PHONE_STATE) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                runOnUiThread(() -> requestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                    if (granted) model.loadAppInfo();
                }));
            }
        }
        if (!PermissionUtils.hasUsageStatsPermission(mActivity)) {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.grant_usage_access)
                    .setMessage(R.string.grant_usage_acess_message)
                    .setPositiveButton(R.string.go, (dialog, which) -> {
                        try {
                            activityLauncher.launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), result -> {
                                if (PermissionUtils.hasUsageStatsPermission(mActivity)) {
                                    FeatureController.getInstance().modifyState(FeatureController
                                            .FEAT_USAGE_ACCESS, true);
                                    // Reload app info
                                    model.loadAppInfo();
                                }
                            });
                        } catch (SecurityException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.never_ask, (dialog, which) -> FeatureController.getInstance().modifyState(
                            FeatureController.FEAT_USAGE_ACCESS, false))
                    .setCancelable(false)
                    .show());
            return;
        }
        PackageSizeInfo sizeInfo = appInfo.sizeInfo;
        if (sizeInfo == null) return;
        synchronized (mListItems) {
            mListItems.add(ListItem.getGroupHeader(getString(R.string.storage_and_cache)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.app_size), getReadableSize(sizeInfo.codeSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_size), getReadableSize(sizeInfo.dataSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.cache_size), getReadableSize(sizeInfo.cacheSize)));
            if (sizeInfo.obbSize != 0) {
                mListItems.add(ListItem.getInlineItem(getString(R.string.obb_size), getReadableSize(sizeInfo.obbSize)));
            }
            if (sizeInfo.mediaSize != 0) {
                mListItems.add(ListItem.getInlineItem(getString(R.string.media_size), getReadableSize(sizeInfo.mediaSize)));
            }
            mListItems.add(ListItem.getInlineItem(getString(R.string.total_size), getReadableSize(sizeInfo.getTotalSize())));
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        // Set App Icon
        Drawable icon = mApplicationInfo.loadIcon(mPackageManager);
        runOnUiThread(() -> {
            if (isAdded() && !isDetached()) {
                iconView.setImageDrawable(icon);
            }
        });
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

    @MainThread
    private void disable() {
        try {
            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    0, mainModel.getUserHandle());
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, e);
            displayLongToast(R.string.failed_to_disable, mPackageLabel);
        }
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

    @AnyThread
    private void runOnUiThread(Runnable runnable) {
        UiThreadHandler.run(runnable);
    }
}
