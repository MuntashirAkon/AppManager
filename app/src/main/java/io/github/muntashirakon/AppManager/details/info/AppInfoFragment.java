// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_BLACK;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DEFAULT;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DISABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_ENABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_JUST_WARN;
import static io.github.muntashirakon.AppManager.compat.ManifestCompat.permission.TERMUX_RUN_COMMAND;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.hasDumpPermission;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayShortToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.provider.Settings;
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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.behavior.DexOptimizationDialog;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.DomainVerificationManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.fm.OpenWithDialogFragment;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileMetaManager;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.shortcut.LauncherShortcuts;
import io.github.muntashirakon.AppManager.ssaid.ChangeSsaidDialog;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.IntentUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

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
    private List<MagiskProcess> magiskHiddenProcesses;
    private List<MagiskProcess> magiskDeniedProcesses;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private boolean isExternalApk;
    private boolean isRootEnabled;
    private boolean isAdbEnabled;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> export = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument("*/*"));
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
        isRootEnabled = Ops.isRoot();
        isAdbEnabled = Ops.isAdb();
        mPackageManager = mActivity.getPackageManager();
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        // Recycler view
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
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
                AppDetailsItem<?> appDetailsItem = appDetailsItems.get(0);
                mPackageInfo = (PackageInfo) appDetailsItem.vanillaItem;
                mPackageName = appDetailsItem.name;
                mInstalledPackageInfo = mainModel.getInstalledPackageInfo();
                isExternalApk = mainModel.isExternalApk();
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
            ThreadUtils.postOnBackgroundThread(() -> {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    String data = clipData.getItemAt(0).coerceToText(mActivity).toString().trim()
                            .toLowerCase(Locale.ROOT);
                    if (data.matches("[0-9a-f: \n]+")) {
                        data = data.replaceAll("[: \n]+", "");
                        Signature[] signatures = PackageUtils.getSigningInfo(mPackageInfo, isExternalApk);
                        if (signatures != null && signatures.length == 1) {
                            byte[] certBytes = signatures[0].toByteArray();
                            Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
                            for (Pair<String, String> digest : digests) {
                                if (digest.second.equals(data)) {
                                    if (digest.first.equals(DigestUtils.MD5) || digest.first.equals(DigestUtils.SHA_1)) {
                                        ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified_using_unreliable_hash));
                                    } else ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified));
                                    return;
                                }
                            }
                        }
                        ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.not_verified));
                    }
                }
            });
        });
        model.getTagCloud().observe(getViewLifecycleOwner(), this::setupTagCloud);
        model.getAppInfo().observe(getViewLifecycleOwner(), this::setupVerticalView);
        model.getInstallExistingResult().observe(getViewLifecycleOwner(), statusMessagePair ->
                new MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(mPackageLabel)
                        .setIcon(mApplicationInfo.loadIcon(mPackageManager))
                        .setMessage(statusMessagePair.second)
                        .setNegativeButton(R.string.close, null)
                        .show());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mainModel != null && !mainModel.isExternalApk()) {
            inflater.inflate(R.menu.fragment_app_info_actions, menu);
            menu.findItem(R.id.action_magisk_hide).setVisible(MagiskHide.available());
            menu.findItem(R.id.action_magisk_denylist).setVisible(MagiskDenyList.available());
            menu.findItem(R.id.action_open_in_termux).setVisible(Ops.isRoot());
            menu.findItem(R.id.action_battery_opt).setVisible(Ops.isPrivileged());
            menu.findItem(R.id.action_net_policy).setVisible(Ops.isPrivileged());
            menu.findItem(R.id.action_install).setVisible(Ops.isPrivileged() && Users.getUsersIds().length > 1);
            menu.findItem(R.id.action_optimize).setVisible(Ops.isPrivileged() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (isExternalApk) return;
        boolean isDebuggable;
        if (mApplicationInfo != null) {
            isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } else isDebuggable = false;
        MenuItem runInTermuxMenu = menu.findItem(R.id.action_run_in_termux);
        if (runInTermuxMenu != null) {
            runInTermuxMenu.setVisible(isDebuggable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_share_apk) {
            showProgressIndicator(true);
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Path tmpApkSource = ApkUtils.getSharableApkFile(mPackageInfo);
                    ThreadUtils.postOnMainThread(() -> {
                        showProgressIndicator(false);
                        Context ctx = ContextUtils.getContext();
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
            BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                    Collections.singletonList(new UserPackagePair(mPackageName, mainModel.getUserHandle())));
            fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
            fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
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
            if (PermissionUtils.hasTermuxPermission()) {
                openInTermux();
            } else requestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                if (granted) openInTermux();
            });
        } else if (itemId == R.id.action_run_in_termux) {
            if (PermissionUtils.hasTermuxPermission()) {
                runInTermux();
            } else requestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                if (granted) runInTermux();
            });
        } else if (itemId == R.id.action_magisk_hide) {
            if (mainModel == null) return true;
            displayMagiskHideDialog();
        } else if (itemId == R.id.action_magisk_denylist) {
            if (mainModel == null) return true;
            displayMagiskDenyListDialog();
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
            if (!UserHandleHidden.isApp(mApplicationInfo.uid)) {
                UIUtils.displayLongToast(R.string.netpolicy_cannot_be_modified_for_core_apps);
                return true;
            }
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(mActivity);
            Integer[] polices = new Integer[netPolicyMap.size()];
            CharSequence[] policyStrings = new String[netPolicyMap.size()];
            Integer selectedPolicies = NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid);
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
            }
            new SearchableFlagsDialogBuilder<>(mActivity, polices, policyStrings, selectedPolicies)
                    .setTitle(R.string.net_policy)
                    .showSelectAll(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, selections) -> {
                        int flags = 0;
                        for (int flag : selections) {
                            flags |= flag;
                        }
                        try {
                            NetworkPolicyManagerCompat.setUidPolicy(mApplicationInfo.uid, flags);
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
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (OutputStream outputStream = mActivity.getContentResolver().openOutputStream(uri)) {
                        if (outputStream == null) {
                            throw new IOException("Unable to open output stream.");
                        }
                        Bitmap bitmap = getBitmapFromDrawable(mApplicationInfo.loadIcon(mPackageManager));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.flush();
                        ThreadUtils.postOnMainThread(() -> displayShortToast(R.string.saved_successfully));
                    } catch (IOException e) {
                        Log.e(TAG, e);
                        ThreadUtils.postOnMainThread(() -> displayShortToast(R.string.saving_failed));
                    }
                });
            });
        } else if (itemId == R.id.action_install) {
            List<UserInfo> users = Users.getUsers();
            String[] userNames = new String[users.size()];
            int i = 0;
            for (UserInfo info : users) {
                userNames[i++] = info.name == null ? String.valueOf(info.id) : info.name;
            }
            new SearchableItemsDialogBuilder<>(mActivity, userNames)
                    .setTitle(R.string.select_user)
                    .setOnItemClickListener((dialog, which, item1) -> {
                        model.installExisting(users.get(which).id);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.action_add_to_profile) {
            List<ProfileMetaManager> profiles = ProfileManager.getProfileMetadata();
            List<CharSequence> profileNames = new ArrayList<>(profiles.size());
            for (ProfileMetaManager profileMetaManager : profiles) {
                profileNames.add(new SpannableStringBuilder(profileMetaManager.getProfileName()).append("\n")
                        .append(getSecondaryText(mActivity, getSmallerText(profileMetaManager.toLocalizedString(mActivity)))));
            }
            new SearchableMultiChoiceDialogBuilder<>(mActivity, profiles, profileNames)
                    .setTitle(R.string.add_to_profile)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                        for (ProfileMetaManager metaManager : selectedItems) {
                            try {
                                metaManager.appendPackages(Collections.singletonList(mPackageName));
                                metaManager.writeProfile();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .show();
        } else if (itemId == R.id.action_optimize) {
            if (!Ops.isPrivileged()) {
                UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
                return false;
            }
            DexOptimizationDialog dialog = DexOptimizationDialog.getInstance(new String[]{mPackageName});
            dialog.show(getChildFragmentManager(), DexOptimizationDialog.TAG);
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
        int apkFileKey = mainModel.getApkFileKey();
        try {
            // Reserve ApkFile in case the activity is destroyed
            ApkFile.getInAdvance(apkFileKey);
            startActivity(PackageInstallerActivity.getLaunchableInstance(requireContext(), apkFileKey));
        } catch (Exception e) {
            // Error occurred, so the APK file wasn't used by the installer.
            // Close the APK file to remove one instance.
            ApkFile.getInstance(apkFileKey).close();
        }
    }

    @UiThread
    private void refreshDetails() {
        if (mainModel == null || isDetached()) return;
        showProgressIndicator(true);
        mainModel.triggerPackageChange();
    }

    @UiThread
    private void setupTagCloud(AppInfoViewModel.TagCloud tagCloud) {
        mTagCloud.removeAllViews();
        if (mainModel == null) return;
        // Add tracker chip
        if (!tagCloud.trackerComponents.isEmpty()) {
            CharSequence[] trackerComponentNames = new CharSequence[tagCloud.trackerComponents.size()];
            int blockedColor = ColorCodes.getComponentTrackerBlockedIndicatorColor(mActivity);
            for (int i = 0; i < trackerComponentNames.length; ++i) {
                ComponentRule rule = tagCloud.trackerComponents.get(i);
                trackerComponentNames[i] = rule.isBlocked() ? getColoredText(rule.name, blockedColor) : rule.name;
            }
            addChip(getResources().getQuantityString(R.plurals.no_of_trackers, tagCloud.trackerComponents.size(),
                    tagCloud.trackerComponents.size()), tagCloud.areAllTrackersBlocked
                    ? ColorCodes.getComponentTrackerBlockedIndicatorColor(mActivity)
                    : ColorCodes.getComponentTrackerIndicatorColor(mActivity)).setOnClickListener(v -> {
                if (!isExternalApk && isRootEnabled) {
                    new SearchableMultiChoiceDialogBuilder<>(mActivity, tagCloud.trackerComponents, trackerComponentNames)
                            .setTitle(R.string.trackers)
                            .addSelections(tagCloud.trackerComponents)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.block, (dialog, which, selectedItems) -> {
                                showProgressIndicator(true);
                                ThreadUtils.postOnBackgroundThread(() -> {
                                    mainModel.addRules(selectedItems, true);
                                    ThreadUtils.postOnMainThread(() -> {
                                        if (!isDetached()) {
                                            showProgressIndicator(false);
                                        }
                                        displayShortToast(R.string.done);
                                    });
                                });
                            })
                            .setNeutralButton(R.string.unblock, (dialog, which, selectedItems) -> {
                                showProgressIndicator(true);
                                ThreadUtils.postOnBackgroundThread(() -> {
                                    mainModel.removeRules(selectedItems, true);
                                    ThreadUtils.postOnMainThread(() -> {
                                        if (!isDetached()) {
                                            showProgressIndicator(false);
                                        }
                                        displayShortToast(R.string.done);
                                    });
                                });
                            })
                            .show();
                } else {
                    new SearchableItemsDialogBuilder<>(mActivity, trackerComponentNames)
                            .setTitle(R.string.trackers)
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
        } else if (!mainModel.isExternalApk()) addChip(R.string.user_app);
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
                    new SearchableItemsDialogBuilder<>(mActivity, entryNames)
                            .setTitle(R.string.splits)
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
            addChip(R.string.requested_large_heap);
        }
        if (tagCloud.hostsToOpen != null) {
            addChip(R.string.app_info_tag_open_links, tagCloud.canOpenLinks ? ColorCodes.getFailureColor(mActivity)
                    : ColorCodes.getSuccessColor(mActivity)).setOnClickListener(v -> {
                SearchableItemsDialogBuilder<String> builder = new SearchableItemsDialogBuilder<>(mActivity, new ArrayList<>(tagCloud.hostsToOpen.keySet()))
                        .setTitle(R.string.title_domains_supported_by_the_app)
                        .setNegativeButton(R.string.close, null);
                if (PermissionUtils.hasSelfOrRemotePermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)) {
                    // Enable/disable directly from the app
                    builder.setPositiveButton(tagCloud.canOpenLinks ? R.string.disable : R.string.enable,
                            (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        DomainVerificationManagerCompat
                                                .setDomainVerificationLinkHandlingAllowed(mPackageName, !tagCloud.canOpenLinks, mainModel.getUserHandle());
                                    }
                                    ThreadUtils.postOnMainThread(() -> {
                                        UIUtils.displayShortToast(R.string.done);
                                        refreshDetails();
                                    });
                                } catch (Throwable th) {
                                    th.printStackTrace();
                                    ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
                                }
                            }));
                } else {
                    builder.setPositiveButton(R.string.app_settings, (dialog, which) -> {
                        try {
                            startActivity(IntentUtils.getAppDetailsSettings(mPackageName));
                        } catch (Throwable ignore) {
                        }
                    });
                }
                builder.show();
            });
        }
        if (tagCloud.runningServices.size() > 0) {
            addChip(R.string.running, ColorCodes.getComponentRunningIndicatorColor(mActivity)).setOnClickListener(v -> {
                mProgressIndicator.show();
                executor.submit(() -> {
                    CharSequence[] runningServices = new CharSequence[tagCloud.runningServices.size()];
                    for (int i = 0; i < runningServices.length; ++i) {
                        runningServices[i] = new SpannableStringBuilder()
                                .append(tagCloud.runningServices.get(i).service.getShortClassName())
                                .append("\n")
                                .append(getSmallerText(new SpannableStringBuilder()
                                        .append(getStyledKeyValue(mActivity, R.string.process_name,
                                                tagCloud.runningServices.get(i).process)).append("\n")
                                        .append(getStyledKeyValue(mActivity, R.string.pid,
                                                String.valueOf(tagCloud.runningServices.get(i).pid)))));
                    }
                    DialogTitleBuilder titleBuilder = new DialogTitleBuilder(mActivity)
                            .setTitle(R.string.running_services);
                    if (hasDumpPermission() && FeatureController.isLogViewerEnabled()) {
                        titleBuilder.setSubtitle(R.string.running_services_logcat_hint);
                    }
                    ThreadUtils.postOnMainThread(() -> {
                        mProgressIndicator.hide();
                        new SearchableItemsDialogBuilder<>(mActivity, runningServices)
                                .setTitle(titleBuilder.build())
                                .setOnItemClickListener((dialog, which, item) -> {
                                    if (!FeatureController.isLogViewerEnabled()) return;
                                    Intent logViewerIntent = new Intent(mActivity.getApplicationContext(), LogViewerActivity.class)
                                            .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + tagCloud.runningServices.get(which).pid)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    mActivity.startActivity(logViewerIntent);
                                })
                                .setNeutralButton(R.string.force_stop, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                                    try {
                                        PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                                        ThreadUtils.postOnMainThread(this::refreshDetails);
                                    } catch (RemoteException | SecurityException e) {
                                        Log.e(TAG, e);
                                        displayLongToast(R.string.failed_to_stop, mPackageLabel);
                                    }
                                }))
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
                });
            });
        }
        if (tagCloud.isForceStopped) {
            addChip(R.string.stopped, ColorCodes.getAppForceStoppedIndicatorColor(mActivity));
        }
        if (!tagCloud.isAppEnabled) {
            addChip(R.string.disabled_app, ColorCodes.getAppDisabledIndicatorColor(mActivity));
        }
        if (tagCloud.isAppSuspended) {
            addChip(R.string.suspended, ColorCodes.getAppSuspendedIndicatorColor(mActivity));
        }
        if (tagCloud.isAppHidden) {
            addChip(R.string.hidden, ColorCodes.getAppHiddenIndicatorColor(mActivity));
        }
        magiskHiddenProcesses = tagCloud.magiskHiddenProcesses;
        if (tagCloud.isMagiskHideEnabled) {
            addChip(R.string.magisk_hide_enabled).setOnClickListener(v -> displayMagiskHideDialog());
        }
        magiskDeniedProcesses = tagCloud.magiskDeniedProcesses;
        if (tagCloud.isMagiskDenyListEnabled) {
            addChip(R.string.magisk_denylist).setOnClickListener(v -> displayMagiskDenyListDialog());
        }
        if (tagCloud.canWriteAndExecute) {
            addChip("WX", ColorCodes.getAppWriteAndExecuteIndicatorColor(mActivity))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(mActivity)
                                    .setTitle("WX")
                                    .setMessage(R.string.app_can_write_and_execute_in_same_place)
                                    .enableAnchors()
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        if (tagCloud.hasKeyStoreItems) {
            Chip chip;
            if (tagCloud.hasMasterKeyInKeyStore) {
                chip = addChip(R.string.keystore, ColorCodes.getAppKeystoreIndicatorColor(mActivity));
            } else chip = addChip(R.string.keystore);
            chip.setOnClickListener(view -> new SearchableItemsDialogBuilder<>(mActivity, KeyStoreUtils
                    .getKeyStoreFiles(mApplicationInfo.uid, mainModel.getUserHandle()))
                    .setTitle(R.string.keystore)
                    .setNegativeButton(R.string.close, null)
                    .show());
        }
        if (!tagCloud.backups.isEmpty()) {
            addChip(R.string.backup).setOnClickListener(v -> {
                BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                        Collections.singletonList(new UserPackagePair(mPackageName, mainModel.getUserHandle())),
                        BackupRestoreDialogFragment.MODE_RESTORE | BackupRestoreDialogFragment.MODE_DELETE);
                fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
            });
        }
        if (!tagCloud.isBatteryOptimized) {
            addChip(R.string.no_battery_optimization, ColorCodes.getAppNoBatteryOptimizationIndicatorColor(mActivity))
                    .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
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
            addChip(R.string.has_net_policy).setOnClickListener(v -> new SearchableItemsDialogBuilder<>(mActivity, readablePolicies)
                    .setTitle(R.string.net_policy)
                    .setNegativeButton(R.string.ok, null)
                    .show());
        }
        if (tagCloud.ssaid != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addChip(R.string.ssaid, ColorCodes.getAppSsaidIndicatorColor(mActivity)).setOnClickListener(v -> {
                ChangeSsaidDialog changeSsaidDialog = ChangeSsaidDialog.getInstance(mPackageName, mApplicationInfo.uid,
                        tagCloud.ssaid);
                changeSsaidDialog.setSsaidChangedInterface((newSsaid, isSuccessful) -> {
                    displayLongToast(isSuccessful ? R.string.restart_to_reflect_changes : R.string.failed_to_change_ssaid);
                    if (isSuccessful) tagCloud.ssaid = newSsaid;
                });
                changeSsaidDialog.show(getChildFragmentManager(), ChangeSsaidDialog.TAG);
            });
        }
        if (tagCloud.uriGrants != null) {
            addChip(R.string.saf).setOnClickListener(v -> {
                CharSequence[] uriGrants = new CharSequence[tagCloud.uriGrants.size()];
                for (int i = 0; i < tagCloud.uriGrants.size(); ++i) {
                    uriGrants[i] = tagCloud.uriGrants.get(i).uri.toString();
                }
                new SearchableItemsDialogBuilder<>(mActivity, uriGrants)
                        .setTitle(R.string.saf)
                        .setNegativeButton(R.string.close, null)
                        .show();
            });
        }
        if (tagCloud.usesPlayAppSigning) {
            addChip(R.string.uses_play_app_signing, ColorCodes.getAppPlayAppSigningIndicatorColor(mActivity))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(mActivity)
                                    .setTitle(R.string.uses_play_app_signing)
                                    .setMessage(R.string.uses_play_app_signing_description)
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        if (tagCloud.staticSharedLibraryNames != null) {
            addChip(R.string.static_shared_library).setOnClickListener(v -> {
                new SearchableMultiChoiceDialogBuilder<>(mActivity, tagCloud.staticSharedLibraryNames, tagCloud.staticSharedLibraryNames)
                        .setTitle(R.string.shared_libs)
                        .setPositiveButton(R.string.close, null)
                        .setNeutralButton(R.string.uninstall, (dialog, which, selectedItems) -> {
                            int userId = mainModel.getUserHandle();
                            final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            new ScrollableDialogBuilder(mActivity,
                                    isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                                    .setTitle(mPackageLabel)
                                    .setPositiveButton(R.string.uninstall, (dialog1, which1, keepData) -> {
                                        if (selectedItems.size() == 1) {
                                            ThreadUtils.postOnBackgroundThread(() -> {
                                                PackageInstallerCompat installer = PackageInstallerCompat
                                                        .getNewInstance();
                                                installer.setAppLabel(mPackageLabel);
                                                boolean uninstalled = installer.uninstall(selectedItems.get(0), userId, keepData);
                                                ThreadUtils.postOnMainThread(() -> {
                                                    if (uninstalled) {
                                                        displayLongToast(R.string.uninstalled_successfully, mPackageLabel);
                                                        mActivity.finish();
                                                    } else {
                                                        displayLongToast(R.string.failed_to_uninstall, mPackageLabel);
                                                    }
                                                });
                                            });
                                        } else {
                                            Intent intent = new Intent(mActivity, BatchOpsService.class);
                                            ArrayList<Integer> userIds = new ArrayList<>(selectedItems.size());
                                            for (int i = 0; i < selectedItems.size(); ++i) {
                                                userIds.add(userId);
                                            }
                                            intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, selectedItems);
                                            intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, userIds);
                                            intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNINSTALL);
                                            ContextCompat.startForegroundService(mActivity, intent);
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, (dialog1, which1, keepData) -> {
                                        if (dialog != null) dialog.cancel();
                                    })
                                    .show();
                        })
                        .show();
            });
        }
    }

    @UiThread
    private void displayMagiskHideDialog() {
        SearchableMultiChoiceDialogBuilder<MagiskProcess> builder;
        builder = getMagiskProcessDialog(magiskHiddenProcesses, (dialog, which, mp, isChecked) -> executor.submit(() -> {
            mp.setEnabled(isChecked);
            if (MagiskHide.apply(mp)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                    cb.setMagiskHide(mp);
                    ThreadUtils.postOnMainThread(this::refreshDetails);
                }
            } else {
                mp.setEnabled(!isChecked);
                ThreadUtils.postOnMainThread(() -> displayLongToast(isChecked ? R.string.failed_to_enable_magisk_hide
                        : R.string.failed_to_disable_magisk_hide));
            }
        }));
        if (builder != null) {
            builder.setTitle(R.string.magisk_hide_enabled).show();
        }
    }

    @UiThread
    private void displayMagiskDenyListDialog() {
        SearchableMultiChoiceDialogBuilder<MagiskProcess> builder;
        builder = getMagiskProcessDialog(magiskDeniedProcesses, (dialog, which, mp, isChecked) -> executor.submit(() -> {
            mp.setEnabled(isChecked);
            if (MagiskDenyList.apply(mp)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                    cb.setMagiskDenyList(mp);
                    ThreadUtils.postOnMainThread(this::refreshDetails);
                }
            } else {
                mp.setEnabled(!isChecked);
                ThreadUtils.postOnMainThread(() -> displayLongToast(isChecked ? R.string.failed_to_enable_magisk_deny_list
                        : R.string.failed_to_disable_magisk_deny_list));
            }
        }));
        if (builder != null) {
            builder.setTitle(R.string.magisk_denylist).show();
        }
    }

    @Nullable
    public SearchableMultiChoiceDialogBuilder<MagiskProcess> getMagiskProcessDialog(
            @Nullable List<MagiskProcess> magiskProcesses,
            SearchableMultiChoiceDialogBuilder.OnMultiChoiceClickListener<MagiskProcess> multiChoiceClickListener) {
        if (magiskProcesses == null || magiskProcesses.isEmpty()) {
            return null;
        }
        List<Integer> selectedIndexes = new ArrayList<>();
        CharSequence[] processes = new CharSequence[magiskProcesses.size()];
        int i = 0;
        for (MagiskProcess mp : magiskProcesses) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (mp.isIsolatedProcess()) {
                sb.append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.isolated)));
                if (mp.isRunning()) {
                    sb.append(", ").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running)));
                }
            } else if (mp.isRunning()) {
                sb.append("\n").append(UIUtils.getSecondaryText(mActivity, getString(R.string.running)));
            }
            processes[i] = new SpannableStringBuilder(mp.name).append(UIUtils.getSmallerText(sb));
            if (mp.isEnabled()) {
                selectedIndexes.add(i);
            }
            i++;
        }
        return new SearchableMultiChoiceDialogBuilder<>(mActivity, magiskProcesses, processes)
                .addSelections(ArrayUtils.convertToIntArray(selectedIndexes))
                .setTextSelectable(true)
                .setOnMultiChoiceClickListener(multiChoiceClickListener)
                .setNegativeButton(R.string.close, null);
    }

    private void setHorizontalActions() {
        mHorizontalLayout.removeAllViews();
        if (mainModel != null && !mainModel.isExternalApk()) {
            boolean isFrozen = FreezeUtils.isFrozen(mApplicationInfo);
            // Set open
            Intent launchIntent = PackageUtils.getLaunchIntentForPackage(requireContext(), mPackageName,
                    mainModel.getUserHandle());
            if (launchIntent != null && !isFrozen) {
                addToHorizontalLayout(R.string.launch_app, R.drawable.ic_open_in_new)
                        .setOnClickListener(v -> {
                            try {
                                ActivityManagerCompat.startActivity(requireContext(), launchIntent,
                                        mainModel.getUserHandle());
                            } catch (Throwable th) {
                                UIUtils.displayLongToast(th.getLocalizedMessage());
                            }
                        });
            }
            // Set freeze/unfreeze
            if (Ops.isPrivileged() && !isFrozen) {
                MaterialButton freezeButton = addToHorizontalLayout(R.string.freeze, R.drawable.ic_snowflake);
                freezeButton.setOnClickListener(v -> {
                    if (BuildConfig.APPLICATION_ID.equals(mPackageName)) {
                        new MaterialAlertDialogBuilder(mActivity)
                                .setMessage(R.string.are_you_sure)
                                .setPositiveButton(R.string.yes, (d, w) -> freeze(true))
                                .setNegativeButton(R.string.no, null)
                                .show();
                    } else freeze(true);
                });
                freezeButton.setOnLongClickListener(v -> {
                    createFreezeShortcut(false);
                    return true;
                });
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_trash_can).setOnClickListener(v -> {
                int userId = mainModel.getUserHandle();
                if (!Ops.isPrivileged() && userId != UserHandleHidden.myUserId()) {
                    // Could be work profile
                    // FIXME: 22/1/23 Find a way to communicate the result to App Manager
                    try {
                        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                        uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                        ActivityManagerCompat.startActivity(requireContext(), uninstallIntent, userId);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast(th.getLocalizedMessage());
                    }
                    return;
                }
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                        isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                        .setTitle(mPackageLabel)
                        .setPositiveButton(R.string.uninstall, (dialog, which, keepData) -> ThreadUtils.postOnBackgroundThread(() -> {
                            PackageInstallerCompat installer = PackageInstallerCompat
                                    .getNewInstance();
                            installer.setAppLabel(mPackageLabel);
                            boolean uninstalled = installer.uninstall(mPackageName, userId, keepData);
                            ThreadUtils.postOnMainThread(() -> {
                                if (uninstalled) {
                                    displayLongToast(R.string.uninstalled_successfully, mPackageLabel);
                                    mActivity.finish();
                                } else {
                                    displayLongToast(R.string.failed_to_uninstall, mPackageLabel);
                                }
                            });
                        }))
                        .setNegativeButton(R.string.cancel, (dialog, which, keepData) -> {
                            if (dialog != null) dialog.cancel();
                        });
                if (Ops.isPrivileged()) {
                    builder.setCheckboxLabel(R.string.keep_data_and_app_signing_signatures);
                }
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    builder.setNeutralButton(R.string.uninstall_updates, (dialog, which, keepData) ->
                            ThreadUtils.postOnBackgroundThread(() -> {
                                PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                installer.setAppLabel(mPackageLabel);
                                boolean isSuccessful = installer.uninstall(mPackageName, UserHandleHidden.USER_ALL, keepData);
                                if (isSuccessful) {
                                    ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.update_uninstalled_successfully, mPackageLabel));
                                } else {
                                    ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_uninstall_updates, mPackageLabel));
                                }
                            }));
                }
                builder.show();
            });
            // Enable/disable app (root/ADB only)
            if (Ops.isPrivileged() && isFrozen) {
                // Enable app
                MaterialButton unfreezeButton = addToHorizontalLayout(R.string.unfreeze, R.drawable.ic_snowflake_off);
                unfreezeButton.setOnClickListener(v -> freeze(false));
                unfreezeButton.setOnLongClickListener(v -> {
                    createFreezeShortcut(true);
                    return true;
                });
            }
            if (Ops.isPrivileged() || ServiceHelper.checkIfServiceIsRunning(mActivity,
                    NoRootAccessibilityService.class)) {
                // Force stop
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    addToHorizontalLayout(R.string.force_stop, R.drawable.ic_power_settings)
                            .setOnClickListener(v -> {
                                if (isAdbEnabled || isRootEnabled) {
                                    ThreadUtils.postOnBackgroundThread(() -> {
                                        try {
                                            PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                                            ThreadUtils.postOnMainThread(this::refreshDetails);
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
                addToHorizontalLayout(R.string.clear_data, R.drawable.ic_clear_data)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mPackageLabel)
                                .setMessage(R.string.clear_data_message)
                                .setPositiveButton(R.string.clear, (dialog, which) -> {
                                    if (isAdbEnabled || isRootEnabled) {
                                        ThreadUtils.postOnBackgroundThread(() -> {
                                            if (PackageManagerCompat.clearApplicationUserData(mPackageName, mainModel.getUserHandle())) {
                                                ThreadUtils.postOnMainThread(this::refreshDetails);
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
                addToHorizontalLayout(R.string.clear_cache, R.drawable.ic_clear_cache)
                        .setOnClickListener(v -> {
                            if (isAdbEnabled || isRootEnabled) {
                                ThreadUtils.postOnBackgroundThread(() -> {
                                    if (PackageManagerCompat.deleteApplicationCacheFilesAsUser(mPackageName, mainModel.getUserHandle())) {
                                        ThreadUtils.postOnMainThread(this::refreshDetails);
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
                addToHorizontalLayout(R.string.view_in_settings, R.drawable.ic_settings)
                        .setOnClickListener(v -> {
                            try {
                                ActivityManagerCompat.startActivity(requireContext(),
                                        IntentUtils.getAppDetailsSettings(mPackageName),
                                        mainModel.getUserHandle());
                            } catch (Throwable th) {
                                UIUtils.displayLongToast(th.getLocalizedMessage());
                            }
                        });
            }
        } else if (FeatureController.isInstallerEnabled()) {
            if (mInstalledPackageInfo == null) {
                // App not installed
                addToHorizontalLayout(R.string.install, R.drawable.ic_get_app)
                        .setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {
                    // Needs update
                    addToHorizontalLayout(R.string.whats_new, io.github.muntashirakon.ui.R.drawable.ic_information)
                            .setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, mPackageInfo);
                                args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, mInstalledPackageInfo);
                                WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                                dialogFragment.setArguments(args);
                                dialogFragment.show(mActivity.getSupportFragmentManager(), WhatsNewDialogFragment.TAG);
                            });
                    addToHorizontalLayout(R.string.update, R.drawable.ic_get_app)
                            .setOnClickListener(v -> install());
                } else if (installedVersionCode == thisVersionCode) {
                    // Needs reinstall
                    addToHorizontalLayout(R.string.reinstall, R.drawable.ic_get_app)
                            .setOnClickListener(v -> install());
                } else {
                    // Needs downgrade
                    if (Ops.isPrivileged()) {
                        addToHorizontalLayout(R.string.downgrade, R.drawable.ic_get_app)
                                .setOnClickListener(v -> install());
                    }
                }
            }
        }
        // Set manifest
        if (FeatureController.isManifestEnabled()) {
            addToHorizontalLayout(R.string.manifest, R.drawable.ic_package).setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                startActivityForSplit(intent);
            });
        }
        // Set scanner
        if (FeatureController.isScannerEnabled()) {
            addToHorizontalLayout(R.string.scanner, R.drawable.ic_security).setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ScannerActivity.class);
                intent.putExtra(ScannerActivity.EXTRA_IS_EXTERNAL, isExternalApk);
                startActivityForSplit(intent);
            });
        }
        // Root only features
        if (!mainModel.isExternalApk()) {
            // Shared prefs (root only)
            final List<Path> sharedPrefs = new ArrayList<>();
            Path[] tmpPaths = getSharedPrefs(mApplicationInfo.dataDir);
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
                addToHorizontalLayout(R.string.shared_prefs, R.drawable.ic_view_list)
                        .setOnClickListener(v -> new SearchableItemsDialogBuilder<>(mActivity, sharedPrefNames)
                                .setTitle(R.string.shared_prefs)
                                .setOnItemClickListener((dialog, which, item) -> {
                                    Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which).getUri());
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mPackageLabel);
                                    startActivity(intent);
                                })
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }
            // Databases (root only)
            final List<Path> databases = new ArrayList<>();
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
                        .setOnClickListener(v -> new SearchableItemsDialogBuilder<>(mActivity, databases2)
                                .setTitle(R.string.databases)
                                .setOnItemClickListener((dialog, which, item) -> executor.submit(() -> {
                                    // Vacuum database
                                    Runner.runCommand(new String[]{"sqlite3", databases.get(which).getFilePath(), "vacuum"});
                                    ThreadUtils.postOnMainThread(() -> {
                                        OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(databases.get(which), "application/vnd.sqlite3");
                                        fragment.show(getChildFragmentManager(), OpenWithDialogFragment.TAG);
                                    });
                                }))
                                .setNegativeButton(R.string.close, null)
                                .show());
            }
        }  // End root only features
        // Set F-Droid
        Intent fdroid_intent = new Intent(Intent.ACTION_VIEW);
        fdroid_intent.setData(Uri.parse("https://f-droid.org/packages/" + mPackageName));
        List<ResolveInfo> resolvedActivities = mPackageManager.queryIntentActivities(fdroid_intent, 0);
        if (resolvedActivities.size() > 0) {
            addToHorizontalLayout(R.string.fdroid, R.drawable.ic_frost_fdroid)
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
            addToHorizontalLayout(R.string.open_in_aurora_store, R.drawable.ic_frost_aurorastore)
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

    @UiThread
    private void startActivityForSplit(Intent intent) {
        if (mainModel == null) return;
        try (ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey())) {
            if (apkFile.isSplit()) {
                // Display a list of apks
                List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                CharSequence[] entryNames = new CharSequence[apkEntries.size()];
                for (int i = 0; i < apkEntries.size(); ++i) {
                    entryNames[i] = apkEntries.get(i).toShortLocalizedString(requireActivity());
                }
                new SearchableItemsDialogBuilder<>(mActivity, entryNames)
                        .setTitle(R.string.select_apk)
                        .setOnItemClickListener((dialog, which, item) -> executor.submit(() -> {
                            try {
                                File file = apkEntries.get(which).getRealCachedFile();
                                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension("apk"));
                                ThreadUtils.postOnMainThread(() -> startActivity(intent));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                // Open directly
                File file = new File(mApplicationInfo.publicSourceDir);
                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                startActivity(intent);
            }
        }
    }

    @GuardedBy("mListItems")
    private void setPathsAndDirectories(@NonNull AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Paths and directories
            mListItems.add(ListItem.newGroupStart(getString(R.string.paths_and_directories)));
            // Source directory (apk path)
            if (appInfo.sourceDir != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.source_dir), appInfo.sourceDir,
                        openAsFolderInFM(requireContext(), appInfo.sourceDir)));
            }
            // Data dir
            if (appInfo.dataDir != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.data_dir), appInfo.dataDir,
                        openAsFolderInFM(requireContext(), appInfo.dataDir)));
            }
            // Device-protected data dir
            if (appInfo.dataDeDir != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.dev_protected_data_dir), appInfo.dataDeDir,
                        openAsFolderInFM(requireContext(), appInfo.dataDeDir)));
            }
            // External data dirs
            if (appInfo.extDataDirs.size() == 1) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.external_data_dir), appInfo.extDataDirs.get(0),
                        openAsFolderInFM(requireContext(), appInfo.extDataDirs.get(0))));
            } else {
                for (int i = 0; i < appInfo.extDataDirs.size(); ++i) {
                    mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.external_multiple_data_dir, i),
                            appInfo.extDataDirs.get(i), openAsFolderInFM(requireContext(), appInfo.extDataDirs.get(i))));
                }
            }
            // Native JNI library dir
            if (appInfo.jniDir != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.native_library_dir), appInfo.jniDir,
                        openAsFolderInFM(requireContext(), appInfo.jniDir)));
            }
        }
    }

    @GuardedBy("mListItems")
    private void setMoreInfo(AppInfoViewModel.AppInfo appInfo) {
        synchronized (mListItems) {
            // Set more info
            mListItems.add(ListItem.newGroupStart(getString(R.string.more_info)));

            // Set installer version info
            if (isExternalApk && mInstalledPackageInfo != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.installed_version),
                        getString(R.string.version_name_with_code, mInstalledPackageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo)), v -> {
                            Intent appDetailsIntent = AppDetailsActivity.getIntent(mActivity, mPackageName,
                                    UserHandleHidden.myUserId());
                            mActivity.startActivity(appDetailsIntent);
                        });
                listItem.setActionIcon(io.github.muntashirakon.ui.R.drawable.ic_information);
                mListItems.add(listItem);
            }

            // SDK
            final StringBuilder sdk = new StringBuilder();
            sdk.append(getString(R.string.sdk_max)).append(LangUtils.getSeparatorString()).append(String.format(Locale.getDefault(), "%d",
                    mApplicationInfo.targetSdkVersion));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                sdk.append(", ").append(getString(R.string.sdk_min)).append(LangUtils.getSeparatorString())
                        .append(String.format(Locale.getDefault(), "%d", mApplicationInfo.minSdkVersion));
            }
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.sdk), sdk.toString()));

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
                ListItem flagsItem = ListItem.newSelectableRegularItem(getString(R.string.sdk_flags), flags.toString());
                flagsItem.setMonospace(true);
                mListItems.add(flagsItem);
            }
            if (isExternalApk) return;

            mListItems.add(ListItem.newRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
            mListItems.add(ListItem.newRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
            if (!mPackageName.equals(mApplicationInfo.processName)) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.process_name), mApplicationInfo.processName));
            }
            if (appInfo.installerApp != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.installer_app), appInfo.installerApp));
            }
            mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.user_id), String.format(Locale.getDefault(), "%d",
                    mApplicationInfo.uid)));
            if (mPackageInfo.sharedUserId != null)
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.shared_user_id), mPackageInfo.sharedUserId));
            if (appInfo.primaryCpuAbi != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.primary_abi),
                        appInfo.primaryCpuAbi));
            }
            if (appInfo.zygotePreloadName != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.zygote_preload_name),
                        appInfo.zygotePreloadName));
            }
            if (!isExternalApk) {
                mListItems.add(ListItem.newRegularItem(getString(R.string.hidden_api_enforcement_policy),
                        getHiddenApiEnforcementPolicy(appInfo.hiddenApiEnforcementPolicy)));
            }
            if (appInfo.seInfo != null) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.selinux), appInfo.seInfo));
            }
            // Main activity
            if (appInfo.mainActivity != null) {
                final ComponentName launchComponentName = appInfo.mainActivity.getComponent();
                if (launchComponentName != null) {
                    final String mainActivity = launchComponentName.getClassName();
                    mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.main_activity), mainActivity,
                            view -> startActivity(appInfo.mainActivity)));
                }
            }
        }
    }

    @NonNull
    private String getHiddenApiEnforcementPolicy(int policy) {
        switch (policy) {
            case HIDDEN_API_ENFORCEMENT_DEFAULT:
                return getString(R.string.hidden_api_enf_default_policy);
            default:
            case HIDDEN_API_ENFORCEMENT_DISABLED:
                return getString(R.string.hidden_api_enf_policy_none);
            case HIDDEN_API_ENFORCEMENT_JUST_WARN:
                return getString(R.string.hidden_api_enf_policy_warn);
            case HIDDEN_API_ENFORCEMENT_ENABLED:
                return getString(R.string.hidden_api_enf_policy_dark_grey_and_black);
            case HIDDEN_API_ENFORCEMENT_BLACK:
                return getString(R.string.hidden_api_enf_policy_black);
        }
    }

    private void setDataUsage(@NonNull AppInfoViewModel.AppInfo appInfo) {
        AppUsageStatsManager.DataUsage dataUsage = appInfo.dataUsage;
        if (dataUsage == null) return;
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.newGroupStart(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_transmitted), getReadableSize(dataUsage.getTx())));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_received), getReadableSize(dataUsage.getRx())));
        }
    }

    @UiThread
    @GuardedBy("mListItems")
    private void setupVerticalView(AppInfoViewModel.AppInfo appInfo) {
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
    }

    @Nullable
    private Path[] getSharedPrefs(@Nullable String sourceDir) {
        if (sourceDir == null) return null;
        try {
            Path sharedPath = Paths.get(sourceDir).findFile("shared_prefs");
            return sharedPath.listFiles();
        } catch (FileNotFoundException e) {
            return null;
        }

    }

    @Nullable
    private Path[] getDatabases(@Nullable String sourceDir) {
        if (sourceDir == null) return null;
        try {
            Path sharedPath = Paths.get(sourceDir).findFile("databases");
            return sharedPath.listFiles((dir, name) -> !(name.endsWith("-journal")
                    || name.endsWith("-wal") || name.endsWith("-shm")));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @NonNull
    private Chip addChip(@StringRes int resId, @ColorInt int color) {
        Chip chip = (Chip) LayoutInflater.from(mActivity).inflate(R.layout.item_chip, mTagCloud, false);
        chip.setText(resId);
        chip.setChipBackgroundColor(ColorStateList.valueOf(color));
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text, @ColorInt int color) {
        Chip chip = (Chip) LayoutInflater.from(mActivity).inflate(R.layout.item_chip, mTagCloud, false);
        chip.setText(text);
        chip.setChipBackgroundColor(ColorStateList.valueOf(color));
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(@StringRes int resId) {
        Chip chip = (Chip) LayoutInflater.from(mActivity).inflate(R.layout.item_chip, mTagCloud, false);
        chip.setText(resId);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text) {
        Chip chip = (Chip) LayoutInflater.from(mActivity).inflate(R.layout.item_chip, mTagCloud, false);
        chip.setText(text);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private MaterialButton addToHorizontalLayout(@StringRes int stringResId, @DrawableRes int iconResId) {
        MaterialButton button = (MaterialButton) getLayoutInflater().inflate(R.layout.item_app_info_action, mHorizontalLayout, false);
        button.setBackgroundTintList(ColorStateList.valueOf(ColorCodes.getListItemColor1(requireContext())));
        button.setText(stringResId);
        button.setIconResource(iconResId);
        mHorizontalLayout.addView(button);
        return button;
    }

    @GuardedBy("mListItems")
    private void setStorageAndCache(AppInfoViewModel.AppInfo appInfo) {
        if (FeatureController.isUsageAccessEnabled()) {
            // Grant optional READ_PHONE_STATE permission
            if (!PermissionUtils.hasSelfPermission(Manifest.permission.READ_PHONE_STATE) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                ThreadUtils.postOnMainThread(() -> requestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                    if (granted) model.loadAppInfo();
                }));
            }
        }
        if (!PermissionUtils.hasUsageStatsPermission(mActivity)) {
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(mActivity)
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
            mListItems.add(ListItem.newGroupStart(getString(R.string.storage_and_cache)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.app_size), getReadableSize(sizeInfo.codeSize)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_size), getReadableSize(sizeInfo.dataSize)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.cache_size), getReadableSize(sizeInfo.cacheSize)));
            if (sizeInfo.obbSize != 0) {
                mListItems.add(ListItem.newInlineItem(getString(R.string.obb_size), getReadableSize(sizeInfo.obbSize)));
            }
            if (sizeInfo.mediaSize != 0) {
                mListItems.add(ListItem.newInlineItem(getString(R.string.media_size), getReadableSize(sizeInfo.mediaSize)));
            }
            mListItems.add(ListItem.newInlineItem(getString(R.string.total_size), getReadableSize(sizeInfo.getTotalSize())));
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        // Set App Icon
        ImageLoader.displayImage(mApplicationInfo, iconView);
        // (Re)load views
        model.loadPackageLabel();
        model.loadTagCloud();
        ThreadUtils.postOnMainThread(() -> {
            if (isAdded() && !isDetached()) {
                setHorizontalActions();
            }
        });
        model.loadAppInfo();
        ThreadUtils.postOnMainThread(() -> showProgressIndicator(false));
    }

    @MainThread
    private void freeze(boolean freeze) {
        if (mainModel == null) return;
        try {
            if (freeze) {
                FreezeUtils.freeze(mPackageName, mainModel.getUserHandle());
            } else {
                FreezeUtils.unfreeze(mPackageName, mainModel.getUserHandle());
            }
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, e);
            displayLongToast(freeze ? R.string.failed_to_freeze : R.string.failed_to_unfreeze, mPackageLabel);
        }
    }

    private void createFreezeShortcut(boolean isFrozen) {
        if (mainModel == null) return;
        List<Integer> allFlags = new ArrayList<>(3);
        for (int i = 0; i < 3; ++i) {
            allFlags.add(1 << i);
        }
        new SearchableMultiChoiceDialogBuilder<>(mActivity, allFlags, R.array.freeze_unfreeze_flags)
                .setTitle(R.string.freeze_unfreeze)
                .setPositiveButton(R.string.create_shortcut, (dialog, which, selections) -> {
                    int flags = 0;
                    for (int flag : selections) {
                        flags |= flag;
                    }
                    LauncherShortcuts.createFreezeUnfreezeShortcut(requireContext(), mPackageLabel,
                            getBitmapFromDrawable(iconView.getDrawable()), mPackageName, mainModel.getUserHandle(),
                            flags, isFrozen);
                })
                .show();
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
}
