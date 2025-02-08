// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_BLACK;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DEFAULT;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_DISABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_ENABLED;
import static io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat.HIDDEN_API_ENFORCEMENT_JUST_WARN;
import static io.github.muntashirakon.AppManager.compat.ManifestCompat.permission.TERMUX_RUN_COMMAND;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayShortToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;
import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeShortcutInfo;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DomainVerificationManagerCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.SensorServiceCompat;
import io.github.muntashirakon.AppManager.debloat.BloatwareDetailsDialog;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.ssaid.ChangeSsaidDialog;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.GrantUriUtils;
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

public class AppInfoFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, MenuProvider {
    public static final String TAG = "AppInfoFragment";

    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";

    private PackageManager mPackageManager;
    private String mPackageName;
    private int mUserId;
    @Nullable
    private String mInstallerPackageName;
    private PackageInfo mPackageInfo;
    @Nullable
    private PackageInfo mInstalledPackageInfo;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private ViewGroup mHorizontalLayout;
    private ViewGroup mTagCloud;
    private SwipeRefreshLayout mSwipeRefresh;
    private CharSequence mAppLabel;
    private LinearProgressIndicator mProgressIndicator;
    private AppDetailsViewModel mMainModel;
    private AppInfoViewModel mAppInfoModel;
    private AppInfoRecyclerAdapter mAdapter;
    // Headers
    private TextView mLabelView;
    private TextView mPackageNameView;
    private TextView mVersionView;
    private ImageView mIconView;
    private List<MagiskProcess> mMagiskHiddenProcesses;
    private List<MagiskProcess> mMagiskDeniedProcesses;
    private Future<?> mTagCloudFuture;
    private Future<?> mActionsFuture;
    private Future<?> mListFuture;
    private Future<?> mMenuPreparationResult;

    private boolean mIsExternalApk;
    private int mLoadedItemCount;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> mExport = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument("*/*"));
    private final BetterActivityResult<String, Boolean> mRequestPerm = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.RequestPermission());
    private final BetterActivityResult<Intent, ActivityResult> mActivityLauncher = BetterActivityResult
            .registerActivityForResult(this);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppInfoModel = new ViewModelProvider(this).get(AppInfoViewModel.class);
        mMainModel = new ViewModelProvider(requireActivity()).get(AppDetailsViewModel.class);
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
        mAppInfoModel.setMainModel(mMainModel);
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
        mLabelView = view.findViewById(R.id.label);
        mPackageNameView = view.findViewById(R.id.packageName);
        mIconView = view.findViewById(R.id.icon);
        mVersionView = view.findViewById(R.id.version);
        mAdapter = new AppInfoRecyclerAdapter(requireContext());
        recyclerView.setAdapter(mAdapter);
        mActivity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // Set observer
        mMainModel.get(AppDetailsFragment.APP_INFO).observe(getViewLifecycleOwner(), appDetailsItems -> {
            mLoadedItemCount = 0;
            if (appDetailsItems == null || appDetailsItems.isEmpty() || !mMainModel.isPackageExist()) {
                showProgressIndicator(false);
                return;
            }
            ++mLoadedItemCount;
            AppDetailsItem<?> appDetailsItem = appDetailsItems.get(0);
            mPackageInfo = (PackageInfo) appDetailsItem.item;
            mApplicationInfo = mPackageInfo.applicationInfo;
            mPackageName = appDetailsItem.name;
            mUserId = mMainModel.getUserId();
            mInstalledPackageInfo = mMainModel.getInstalledPackageInfo();
            mIsExternalApk = mMainModel.isExternalApk();
            if (!mIsExternalApk) {
                mInstallerPackageName = PackageManagerCompat.getInstallerPackageName(mPackageName, mUserId);
            }
            // Set icon
            ImageLoader.getInstance().displayImage(mPackageName, mApplicationInfo, mIconView);
            // Set package name
            mPackageNameView.setText(mPackageName);
            mPackageNameView.setOnClickListener(v ->
                    Utils.copyToClipboard(ContextUtils.getContext(), "Package name", mPackageName));
            // Set App Version
            CharSequence version = getString(R.string.version_name_with_code, mPackageInfo.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo));
            mVersionView.setText(version);
            // Load app label
            mAppInfoModel.loadAppLabel(mApplicationInfo);
            // Load tag cloud
            mAppInfoModel.loadTagCloud(mPackageInfo, mIsExternalApk);
            // Load horizontal actions
            setupHorizontalActions();
            // Load other info
            mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
        });
        mAppInfoModel.getAppLabel().observe(getViewLifecycleOwner(), appLabel -> {
            ++mLoadedItemCount;
            if (mLoadedItemCount >= 4) {
                showProgressIndicator(false);
            }
            mAppLabel = appLabel;
            // Set Application Name, aka Label
            mLabelView.setText(mAppLabel);
        });
        mMainModel.getFreezeTypeLiveData().observe(getViewLifecycleOwner(), freezeType -> {
            int freezeTypeN = Optional.ofNullable(freezeType)
                    .orElse(Prefs.Blocking.getDefaultFreezingMethod());
            showFreezeDialog(freezeTypeN, freezeType != null);
        });
        mIconView.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) ContextUtils.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ThreadUtils.postOnBackgroundThread(() -> {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    String data = clipData.getItemAt(0).coerceToText(ContextUtils.getContext()).toString().trim()
                            .toLowerCase(Locale.ROOT);
                    if (data.matches("[0-9a-f: \n]+")) {
                        data = data.replaceAll("[: \n]+", "");
                        SignerInfo signerInfo = PackageUtils.getSignerInfo(mPackageInfo, mIsExternalApk);
                        if (signerInfo != null) {
                            X509Certificate[] certs = signerInfo.getCurrentSignerCerts();
                            if (certs != null && certs.length == 1) {
                                try {
                                    Pair<String, String>[] digests = DigestUtils.getDigests(certs[0].getEncoded());
                                    for (Pair<String, String> digest : digests) {
                                        if (digest.second.equals(data)) {
                                            if (digest.first.equals(DigestUtils.MD5) || digest.first.equals(DigestUtils.SHA_1)) {
                                                ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified_using_unreliable_hash));
                                            } else
                                                ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.verified));
                                            return;
                                        }
                                    }
                                } catch (CertificateEncodingException ignore) {
                                }
                            }
                        }
                        ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.not_verified));
                    }
                }
            });
        });
        mAppInfoModel.getTagCloud().observe(getViewLifecycleOwner(), this::setupTagCloud);
        mAppInfoModel.getAppInfo().observe(getViewLifecycleOwner(), this::setupVerticalView);
        mAppInfoModel.getInstallExistingResult().observe(getViewLifecycleOwner(), statusMessagePair ->
                new MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(mAppLabel)
                        .setIcon(mApplicationInfo.loadIcon(mPackageManager))
                        .setMessage(statusMessagePair.second)
                        .setNegativeButton(R.string.close, null)
                        .show());
        mMainModel.getTagsAlteredLiveData().observe(getViewLifecycleOwner(), altered -> {
            // Reload tag cloud
            mAppInfoModel.loadTagCloud(mPackageInfo, mIsExternalApk);
        });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mMainModel != null && !mMainModel.isExternalApk()) {
            inflater.inflate(R.menu.fragment_app_info_actions, menu);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        if (mIsExternalApk) return;
        MenuItem magiskHideMenu = menu.findItem(R.id.action_magisk_hide);
        MenuItem magiskDenyListMenu = menu.findItem(R.id.action_magisk_denylist);
        MenuItem openInTermuxMenu = menu.findItem(R.id.action_open_in_termux);
        MenuItem runInTermuxMenu = menu.findItem(R.id.action_run_in_termux);
        MenuItem batteryOptMenu = menu.findItem(R.id.action_battery_opt);
        MenuItem sensorsMenu = menu.findItem(R.id.action_sensor);
        MenuItem netPolicyMenu = menu.findItem(R.id.action_net_policy);
        MenuItem installMenu = menu.findItem(R.id.action_install);
        MenuItem optimizeMenu = menu.findItem(R.id.action_optimize);
        mMenuPreparationResult = ThreadUtils.postOnBackgroundThread(() -> {
            boolean magiskHideAvailable = MagiskHide.available();
            boolean magiskDenyListAvailable = MagiskDenyList.available();
            boolean rootAvailable = RunnerUtils.isRootAvailable();
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            ThreadUtils.postOnMainThread(() -> {
                if (magiskHideMenu != null) {
                    magiskHideMenu.setVisible(magiskHideAvailable);
                }
                if (magiskDenyListMenu != null) {
                    magiskDenyListMenu.setVisible(magiskDenyListAvailable);
                }
                if (openInTermuxMenu != null) {
                    openInTermuxMenu.setVisible(rootAvailable);
                }
            });
        });
        boolean isDebuggable;
        if (mApplicationInfo != null) {
            isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } else isDebuggable = false;
        if (runInTermuxMenu != null) {
            runInTermuxMenu.setVisible(isDebuggable);
        }
        if (batteryOptMenu != null) {
            batteryOptMenu.setVisible(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER));
        }
        if (sensorsMenu != null) {
            sensorsMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS));
        }
        if (netPolicyMenu != null) {
            netPolicyMenu.setVisible(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY));
        }
        if (installMenu != null) {
            installMenu.setVisible(Users.getUsersIds().length > 1 && SelfPermissions.canInstallExistingPackages());
        }
        if (optimizeMenu != null) {
            optimizeMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID.equals(mInstallerPackageName)));
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_share_apk) {
            showProgressIndicator(true);
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Path tmpApkSource = ApkUtils.getSharableApkFile(requireContext(), mPackageInfo);
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
            if (mMainModel == null) return true;
            BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstanceWithPref(
                    Collections.singletonList(new UserPackagePair(mPackageName, mUserId)), mUserId);
            fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            fragment.setOnActionCompleteListener((mode, failedPackages) -> {
                showProgressIndicator(false);
                mMainModel.getTagsAlteredLiveData().setValue(true);
            });
            fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
        } else if (itemId == R.id.action_view_settings) {
            try {
                ActivityManagerCompat.startActivity(IntentUtils.getAppDetailsSettings(mPackageName), mUserId);
            } catch (Throwable th) {
                UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
            }
        } else if (itemId == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(mActivity, System.currentTimeMillis()) + ".am.tsv";
            mExport.launch(fileName, uri -> {
                if (uri == null || mMainModel == null) {
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
                exportArgs.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, new int[]{mUserId});
                dialogFragment.setArguments(exportArgs);
                dialogFragment.show(mActivity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
        } else if (itemId == R.id.action_open_in_termux) {
            if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) {
                openInTermux();
            } else {
                mRequestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                    if (granted) openInTermux();
                });
            }
        } else if (itemId == R.id.action_run_in_termux) {
            if (SelfPermissions.checkSelfPermission(TERMUX_RUN_COMMAND)) {
                runInTermux();
            } else {
                mRequestPerm.launch(TERMUX_RUN_COMMAND, granted -> {
                    if (granted) runInTermux();
                });
            }
        } else if (itemId == R.id.action_magisk_hide) {
            displayMagiskHideDialog();
        } else if (itemId == R.id.action_magisk_denylist) {
            displayMagiskDenyListDialog();
        } else if (itemId == R.id.action_battery_opt) {
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .setNegativeButton(R.string.disable, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .show();
            } else {
                Log.e(TAG, "No DUMP permission.");
            }
        } else if (itemId == R.id.action_sensor) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sensors)
                        .setMessage(R.string.choose_what_to_do)
                        .setPositiveButton(R.string.enable, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                            try {
                                SensorServiceCompat.enableSensor(mPackageName, mUserId, true);
                                mMainModel.getTagsAlteredLiveData().postValue(true);
                                ThreadUtils.postOnMainThread(() ->
                                        UIUtils.displayShortToast(R.string.done));
                            } catch (IOException e) {
                                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                                        getString(R.string.failed)
                                                + LangUtils.getSeparatorString()
                                                + e.getMessage()));
                            }
                        }))
                        .setNegativeButton(R.string.disable, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                            try {
                                SensorServiceCompat.enableSensor(mPackageName, mUserId, false);
                                mMainModel.getTagsAlteredLiveData().postValue(true);
                                ThreadUtils.postOnMainThread(() ->
                                        UIUtils.displayShortToast(R.string.done));
                            } catch (IOException e) {
                                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                                        getString(R.string.failed)
                                                + LangUtils.getSeparatorString()
                                                + e.getMessage()));
                            }
                        }))
                        .show();
            } else {
                Log.e(TAG, "No sensor permission.");
            }
        } else if (itemId == R.id.action_net_policy) {
            if (!UserHandleHidden.isApp(mApplicationInfo.uid)) {
                UIUtils.displayLongToast(R.string.netpolicy_cannot_be_modified_for_core_apps);
                return true;
            }
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(ContextUtils.getContext());
            Integer[] polices = new Integer[netPolicyMap.size()];
            CharSequence[] policyStrings = new String[netPolicyMap.size()];
            int selectedPolicies = NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid);
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
                        NetworkPolicyManagerCompat.setUidPolicy(mApplicationInfo.uid, flags);
                        mMainModel.getTagsAlteredLiveData().setValue(true);
                    })
                    .show();
        } else if (itemId == R.id.action_extract_icon) {
            String iconName = mAppLabel + "_icon.png";
            mExport.launch(iconName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (OutputStream outputStream = Paths.get(uri).openOutputStream()) {
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
            CharSequence[] userNames = new String[users.size()];
            int i = 0;
            for (UserInfo info : users) {
                userNames[i++] = info.toLocalizedString(requireContext());
            }
            new SearchableItemsDialogBuilder<>(mActivity, userNames)
                    .setTitle(R.string.select_user)
                    .setOnItemClickListener((dialog, which, item1) -> {
                        mAppInfoModel.installExisting(mPackageName, users.get(which).id);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.action_add_to_profile) {
            AddToProfileDialogFragment dialog = AddToProfileDialogFragment.getInstance(new String[]{mPackageName});
            dialog.show(getChildFragmentManager(), AddToProfileDialogFragment.TAG);
        } else if (itemId == R.id.action_optimize) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && (SelfPermissions.isSystemOrRootOrShell() || BuildConfig.APPLICATION_ID.equals(mInstallerPackageName))) {
                DexOptDialog dialog = DexOptDialog.getInstance(new String[]{mPackageName});
                dialog.show(getChildFragmentManager(), DexOptDialog.TAG);
            } else UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
        } else return false;
        return true;
    }

    @Override
    public void onMenuClosed(@NonNull Menu menu) {
        if (mMenuPreparationResult != null) {
            mMenuPreparationResult.cancel(true);
        }
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
        if (mTagCloudFuture != null) mTagCloudFuture.cancel(true);
        if (mActionsFuture != null) mActionsFuture.cancel(true);
        if (mListFuture != null) mListFuture.cancel(true);
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
            UIUtils.displayLongToast("Error: " + e.getMessage());
        }
    }

    private void install() {
        ApkSource apkSource = mMainModel != null ? mMainModel.getApkSource() : null;
        if (apkSource == null) return;
        try {
            startActivity(PackageInstallerActivity.getLaunchableInstance(requireContext(), apkSource));
        } catch (Exception e) {
            UIUtils.displayLongToast("Error: " + e.getMessage());
        }
    }

    @UiThread
    private void refreshDetails() {
        if (mMainModel == null || isDetached()) return;
        showProgressIndicator(true);
        mMainModel.triggerPackageChange();
    }

    @MainThread
    private void setupTagCloud(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        if (mTagCloudFuture != null) mTagCloudFuture.cancel(true);
        mTagCloudFuture = ThreadUtils.postOnBackgroundThread(() -> {
            List<TagItem> tagItems = getTagCloudItems(tagCloud);
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                ++mLoadedItemCount;
                if (mLoadedItemCount >= 4) {
                    showProgressIndicator(false);
                }
                mTagCloud.removeAllViews();
                for (TagItem tagItem : tagItems) {
                    if (isDetached()) return;
                    mTagCloud.addView(tagItem.toChip(mTagCloud.getContext(), mTagCloud));
                }
            });
        });
    }

    @WorkerThread
    @NonNull
    private List<TagItem> getTagCloudItems(@NonNull AppInfoViewModel.TagCloud tagCloud) {
        Objects.requireNonNull(mMainModel);
        Context context = mTagCloud.getContext();
        List<TagItem> tagItems = new LinkedList<>();
        // Add tracker chip
        if (!tagCloud.trackerComponents.isEmpty()) {
            CharSequence[] trackerComponentNames = new CharSequence[tagCloud.trackerComponents.size()];
            int blockedColor = ColorCodes.getComponentTrackerBlockedIndicatorColor(context);
            for (int i = 0; i < trackerComponentNames.length; ++i) {
                ComponentRule rule = tagCloud.trackerComponents.get(i);
                trackerComponentNames[i] = rule.isBlocked() ? getColoredText(rule.name, blockedColor) : rule.name;
            }
            TagItem trackerTag = new TagItem();
            tagItems.add(trackerTag);
            trackerTag.setText(getResources().getQuantityString(R.plurals.no_of_trackers,
                            tagCloud.trackerComponents.size(), tagCloud.trackerComponents.size()))
                    .setColor(tagCloud.areAllTrackersBlocked
                            ? ColorCodes.getComponentTrackerBlockedIndicatorColor(context)
                            : ColorCodes.getComponentTrackerIndicatorColor(context))
                    .setOnClickListener(v -> {
                        if (!mIsExternalApk && SelfPermissions.canModifyAppComponentStates(mUserId, mPackageName, mMainModel.isTestOnlyApp())) {
                            new SearchableMultiChoiceDialogBuilder<>(v.getContext(), tagCloud.trackerComponents, trackerComponentNames)
                                    .setTitle(R.string.trackers)
                                    .addSelections(tagCloud.trackerComponents)
                                    .setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.block, (dialog, which, selectedItems) -> {
                                        showProgressIndicator(true);
                                        ThreadUtils.postOnBackgroundThread(() -> {
                                            mMainModel.addRules(selectedItems, true);
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
                                            mMainModel.removeRules(selectedItems, true);
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
                            new SearchableItemsDialogBuilder<>(v.getContext(), trackerComponentNames)
                                    .setTitle(R.string.trackers)
                                    .setNegativeButton(R.string.close, null)
                                    .show();
                        }
                    });
        }
        if (tagCloud.isSystemApp) {
            tagItems.add(new TagItem()
                    .setTextRes(tagCloud.isSystemlessPath ? R.string.systemless_app : R.string.system_app));
            if (tagCloud.isUpdatedSystemApp) {
                tagItems.add(new TagItem().setTextRes(R.string.updated_app));
            }
        } else if (!mIsExternalApk) {
            tagItems.add(new TagItem().setTextRes(R.string.user_app));
        }
        if (tagCloud.splitCount > 0) {
            TagItem splitTag = new TagItem();
            tagItems.add(splitTag);
            splitTag.setText(getResources().getQuantityString(R.plurals.no_of_splits, tagCloud.splitCount,
                            tagCloud.splitCount))
                    .setOnClickListener(v -> {
                        ApkFile apkFile = mMainModel.getApkFile();
                        if (apkFile == null) {
                            return;
                        }
                        // Display a list of apks
                        List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                        CharSequence[] entryNames = new CharSequence[tagCloud.splitCount];
                        for (int i = 0; i < tagCloud.splitCount; ++i) {
                            entryNames[i] = apkEntries.get(i + 1).toLocalizedString(v.getContext());
                        }
                        new SearchableItemsDialogBuilder<>(v.getContext(), entryNames)
                                .setTitle(R.string.splits)
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
        }
        if (tagCloud.isDebuggable) {
            tagItems.add(new TagItem().setTextRes(R.string.debuggable));
        }
        if (tagCloud.isTestOnly) {
            tagItems.add(new TagItem().setTextRes(R.string.test_only));
        }
        if (!tagCloud.hasCode) {
            tagItems.add(new TagItem().setTextRes(R.string.no_code));
        }
        if (tagCloud.hasRequestedLargeHeap) {
            tagItems.add(new TagItem().setTextRes(R.string.requested_large_heap));
        }
        if (tagCloud.hostsToOpen != null) {
            TagItem openLinksTag = new TagItem();
            tagItems.add(openLinksTag);
            openLinksTag.setTextRes(R.string.app_info_tag_open_links)
                    .setColor(tagCloud.canOpenLinks ? ColorCodes.getFailureColor(context)
                            : ColorCodes.getSuccessColor(context))
                    .setOnClickListener(v -> {
                        SearchableItemsDialogBuilder<String> builder = new SearchableItemsDialogBuilder<>(v.getContext(), new ArrayList<>(tagCloud.hostsToOpen.keySet()))
                                .setTitle(R.string.title_domains_supported_by_the_app)
                                .setNegativeButton(R.string.close, null);
                        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)) {
                            // Enable/disable directly from the app
                            builder.setPositiveButton(tagCloud.canOpenLinks ? R.string.disable : R.string.enable,
                                    (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                DomainVerificationManagerCompat.setDomainVerificationLinkHandlingAllowed(
                                                        mPackageName, !tagCloud.canOpenLinks, mUserId);
                                            }
                                            mMainModel.getTagsAlteredLiveData().postValue(true);
                                            ThreadUtils.postOnMainThread(() ->
                                                    UIUtils.displayShortToast(R.string.done));
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
        if (!tagCloud.runningServices.isEmpty()) {
            TagItem runningTag = new TagItem();
            tagItems.add(runningTag);
            runningTag.setTextRes(R.string.running)
                    .setColor(ColorCodes.getComponentRunningIndicatorColor(context))
                    .setOnClickListener(v ->
                            displayRunningServices(tagCloud.runningServices, v.getContext()));
        }
        if (tagCloud.isForceStopped) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.stopped)
                    .setColor(ColorCodes.getAppForceStoppedIndicatorColor(context)));
        }
        if (!tagCloud.isAppEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.disabled_app)
                    .setColor(ColorCodes.getAppDisabledIndicatorColor(context)));
        }
        if (tagCloud.isAppSuspended) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.suspended)
                    .setColor(ColorCodes.getAppSuspendedIndicatorColor(context)));
        }
        if (tagCloud.isAppHidden) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.hidden)
                    .setColor(ColorCodes.getAppHiddenIndicatorColor(context)));
        }
        mMagiskHiddenProcesses = tagCloud.magiskHiddenProcesses;
        if (tagCloud.isMagiskHideEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.magisk_hide_enabled)
                    .setOnClickListener(v -> displayMagiskHideDialog()));
        }
        mMagiskDeniedProcesses = tagCloud.magiskDeniedProcesses;
        if (tagCloud.isMagiskDenyListEnabled) {
            tagItems.add(new TagItem()
                    .setTextRes(R.string.magisk_denylist)
                    .setOnClickListener(v -> displayMagiskDenyListDialog()));
        }
        if (tagCloud.canWriteAndExecute) {
            TagItem wxItem = new TagItem();
            tagItems.add(wxItem);
            wxItem.setText("WX")
                    .setColor(ColorCodes.getAppWriteAndExecuteIndicatorColor(context))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(v.getContext())
                                    .setTitle("WX")
                                    .setMessage(R.string.app_can_write_and_execute_in_same_place)
                                    .enableAnchors()
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        if (tagCloud.isBloatware) {
            TagItem bloatwareTag = new TagItem();
            tagItems.add(bloatwareTag);
            bloatwareTag.setText("Bloatware")
                    .setColor(ColorCodes.getBloatwareIndicatorColor(context))
                    .setOnClickListener(v -> {
                        BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(mPackageName);
                        dialog.show(getChildFragmentManager(), BloatwareDetailsDialog.TAG);
                    });
        }
        if (tagCloud.hasKeyStoreItems) {
            TagItem keyStoreTag = new TagItem();
            tagItems.add(keyStoreTag);
            keyStoreTag.setTextRes(R.string.keystore)
                    .setOnClickListener(view -> new SearchableItemsDialogBuilder<>(view.getContext(), KeyStoreUtils
                            .getKeyStoreFiles(mApplicationInfo.uid, mUserId))
                            .setTitle(R.string.keystore)
                            .setNegativeButton(R.string.close, null)
                            .show());
            if (tagCloud.hasMasterKeyInKeyStore) {
                keyStoreTag.setColor(ColorCodes.getAppKeystoreIndicatorColor(context));
            }
        }
        if (!tagCloud.backups.isEmpty()) {
            TagItem backupTag = new TagItem();
            tagItems.add(backupTag);
            backupTag.setTextRes(R.string.backup)
                    .setOnClickListener(v -> {
                        BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                                Collections.singletonList(new UserPackagePair(mPackageName, mUserId)),
                                BackupRestoreDialogFragment.MODE_RESTORE | BackupRestoreDialogFragment.MODE_DELETE);
                        fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                        fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                        fragment.show(getParentFragmentManager(), BackupRestoreDialogFragment.TAG);
                    });
        }
        if (!tagCloud.isBatteryOptimized) {
            TagItem batteryOptTag = new TagItem();
            tagItems.add(batteryOptTag);
            batteryOptTag.setTextRes(R.string.no_battery_optimization)
                    .setColor(ColorCodes.getAppNoBatteryOptimizationIndicatorColor(context));
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                batteryOptTag.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                        .setTitle(R.string.battery_optimization)
                        .setMessage(R.string.enable_battery_optimization)
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            if (DeviceIdleManagerCompat.enableBatteryOptimization(mPackageName)) {
                                UIUtils.displayShortToast(R.string.done);
                                mMainModel.getTagsAlteredLiveData().setValue(true);
                            } else {
                                UIUtils.displayShortToast(R.string.failed);
                            }
                        })
                        .show());
            }
        }
        if (!tagCloud.sensorsEnabled) {
            TagItem sensorsTag = new TagItem();
            tagItems.add(sensorsTag);
            sensorsTag.setTextRes(R.string.tag_sensors_disabled);
        }
        if (tagCloud.netPolicies > 0) {
            String[] readablePolicies = NetworkPolicyManagerCompat.getReadablePolicies(context, tagCloud.netPolicies)
                    .values().toArray(new String[0]);
            TagItem netPolicyTag = new TagItem();
            tagItems.add(netPolicyTag);
            netPolicyTag.setTextRes(R.string.has_net_policy)
                    .setOnClickListener(v -> new SearchableItemsDialogBuilder<>(v.getContext(), readablePolicies)
                            .setTitle(R.string.net_policy)
                            .setNegativeButton(R.string.ok, null)
                            .show());
        }
        if (tagCloud.ssaid != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TagItem ssaidTag = new TagItem();
            tagItems.add(ssaidTag);
            ssaidTag.setTextRes(R.string.ssaid)
                    .setColor(ColorCodes.getAppSsaidIndicatorColor(context))
                    .setOnClickListener(v -> {
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
            TagItem safTag = new TagItem();
            tagItems.add(safTag);
            safTag.setTextRes(R.string.saf)
                    .setOnClickListener(v -> {
                        CharSequence[] uriGrants = new CharSequence[tagCloud.uriGrants.size()];
                        for (int i = 0; i < tagCloud.uriGrants.size(); ++i) {
                            uriGrants[i] = GrantUriUtils.toLocalisedString(v.getContext(), tagCloud.uriGrants.get(i).uri);
                        }
                        new SearchableItemsDialogBuilder<>(v.getContext(), uriGrants)
                                .setTitle(R.string.saf)
                                .setTextSelectable(true)
                                .setListBackgroundColorOdd(ColorCodes.getListItemColor0(mActivity))
                                .setListBackgroundColorEven(ColorCodes.getListItemColor1(mActivity))
                                .setNegativeButton(R.string.close, null)
                                .show();
                    });
        }
        if (tagCloud.usesPlayAppSigning) {
            TagItem playAppSigningTag = new TagItem();
            tagItems.add(playAppSigningTag);
            playAppSigningTag.setTextRes(R.string.uses_play_app_signing)
                    .setColor(ColorCodes.getAppPlayAppSigningIndicatorColor(context))
                    .setOnClickListener(v ->
                            new ScrollableDialogBuilder(mActivity)
                                    .setTitle(R.string.uses_play_app_signing)
                                    .setMessage(R.string.uses_play_app_signing_description)
                                    .setNegativeButton(R.string.close, null)
                                    .show());
        }
        if (tagCloud.xposedModuleInfo != null) {
            TagItem xposedItem = new TagItem();
            tagItems.add(xposedItem);
            xposedItem.setText("Xposed")
                    .setOnClickListener(v -> new ScrollableDialogBuilder(v.getContext())
                            .setTitle(R.string.xposed_module_info)
                            .setMessage(tagCloud.xposedModuleInfo.toLocalizedString(v.getContext()))
                            .setNegativeButton(R.string.close, null)
                            .show());
        }
        if (tagCloud.staticSharedLibraryNames != null) {
            TagItem staticSharedLibraryTag = new TagItem();
            tagItems.add(staticSharedLibraryTag);
            staticSharedLibraryTag.setTextRes(R.string.static_shared_library)
                    .setOnClickListener(v -> new SearchableMultiChoiceDialogBuilder<>(v.getContext(), tagCloud.staticSharedLibraryNames, tagCloud.staticSharedLibraryNames)
                            .setTitle(R.string.shared_libs)
                            .setPositiveButton(R.string.close, null)
                            .setNeutralButton(R.string.uninstall, (dialog, which, selectedItems) -> {
                                int userId = mUserId;
                                final boolean isSystemApp = ApplicationInfoCompat.isSystemApp(mApplicationInfo);
                                new ScrollableDialogBuilder(mActivity,
                                        isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                                        .setTitle(mAppLabel)
                                        .setPositiveButton(R.string.uninstall, (dialog1, which1, keepData) -> {
                                            if (selectedItems.size() == 1) {
                                                ThreadUtils.postOnBackgroundThread(() -> {
                                                    PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                                    installer.setAppLabel(mAppLabel);
                                                    boolean uninstalled = installer.uninstall(selectedItems.get(0), userId, false);
                                                    ThreadUtils.postOnMainThread(() -> {
                                                        if (uninstalled) {
                                                            displayLongToast(R.string.uninstalled_successfully, mAppLabel);
                                                            mActivity.finish();
                                                        } else {
                                                            displayLongToast(R.string.failed_to_uninstall, mAppLabel);
                                                        }
                                                    });
                                                });
                                            } else {
                                                Intent intent = new Intent(mActivity, BatchOpsService.class);
                                                ArrayList<Integer> userIds = new ArrayList<>(selectedItems.size());
                                                for (int i = 0; i < selectedItems.size(); ++i) {
                                                    userIds.add(userId);
                                                }
                                                BatchQueueItem item = BatchQueueItem.getBatchOpQueue(
                                                        BatchOpsManager.OP_UNINSTALL, selectedItems, userIds, null);
                                                intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, item);
                                                ContextCompat.startForegroundService(mActivity, intent);
                                            }
                                        })
                                        .setNegativeButton(R.string.cancel, (dialog1, which1, keepData) -> {
                                            if (dialog != null) dialog.cancel();
                                        })
                                        .show();
                            })
                            .show());
        }
        return tagItems;
    }

    private void displayRunningServices(
            @NonNull List<ActivityManager.RunningServiceInfo> runningServices,
            @NonNull Context ctx) {
        showProgressIndicator(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            CharSequence[] runningServiceNames = new CharSequence[runningServices.size()];
            for (int i = 0; i < runningServiceNames.length; ++i) {
                ActivityManager.RunningServiceInfo serviceInfo = runningServices.get(i);
                String title = serviceInfo.service.getShortClassName();
                Spannable description = new SpannableStringBuilder()
                        .append(getStyledKeyValue(ctx, R.string.process_name, serviceInfo.process))
                        .append("\n")
                        .append(getStyledKeyValue(ctx, R.string.pid, String.valueOf(serviceInfo.pid)));
                runningServiceNames[i] = new SpannableStringBuilder()
                        .append(title)
                        .append("\n")
                        .append(getSmallerText(description));
            }
            boolean logViewerAvailable = FeatureController.isLogViewerEnabled()
                    && SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DUMP);
            DialogTitleBuilder titleBuilder = new DialogTitleBuilder(ctx)
                    .setTitle(R.string.running_services);
            if (logViewerAvailable) {
                titleBuilder.setSubtitle(R.string.running_services_logcat_hint);
            }
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                showProgressIndicator(false);
                SearchableItemsDialogBuilder<CharSequence> builder = new SearchableItemsDialogBuilder<>(mActivity, runningServiceNames)
                        .setTitle(titleBuilder.build());
                if (logViewerAvailable) {
                    builder.setOnItemClickListener((dialog, which, item) -> {
                        Intent logViewerIntent = new Intent(mActivity.getApplicationContext(), LogViewerActivity.class)
                                .putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + runningServices.get(which).pid)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mActivity.startActivity(logViewerIntent);
                    });
                }
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                    builder.setNeutralButton(R.string.force_stop, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            PackageManagerCompat.forceStopPackage(mPackageName, mUserId);
                        } catch (SecurityException e) {
                            Log.e(TAG, e);
                            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_stop, mAppLabel));
                        }
                    }));
                }
                builder.setNegativeButton(R.string.close, null);
                if (isDetached()) return;
                builder.show();
            });
        });
    }

    @UiThread
    private void displayMagiskHideDialog() {
        SearchableMultiChoiceDialogBuilder<MagiskProcess> builder;
        builder = getMagiskProcessDialog(mMagiskHiddenProcesses, (dialog, which, mp, isChecked) ->
                ThreadUtils.postOnBackgroundThread(() -> {
                    mp.setEnabled(isChecked);
                    if (MagiskHide.apply(mp)) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mUserId)) {
                            cb.setMagiskHide(mp);
                            mMainModel.getTagsAlteredLiveData().postValue(true);
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
        builder = getMagiskProcessDialog(mMagiskDeniedProcesses, (dialog, which, mp, isChecked) ->
                ThreadUtils.postOnBackgroundThread(() -> {
                    mp.setEnabled(isChecked);
                    if (MagiskDenyList.apply(mp)) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mUserId)) {
                            cb.setMagiskDenyList(mp);
                            mMainModel.getTagsAlteredLiveData().postValue(true);
                        }
                    } else {
                        mp.setEnabled(!isChecked);
                        ThreadUtils.postOnMainThread(() -> displayLongToast(isChecked
                                ? R.string.failed_to_enable_magisk_deny_list
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

    @MainThread
    private void setupHorizontalActions() {
        if (mActionsFuture != null) {
            mActionsFuture.cancel(true);
        }
        mActionsFuture = ThreadUtils.postOnBackgroundThread(() -> {
            List<ActionItem> actionItems = getHorizontalActions();
            ThreadUtils.postOnMainThread(() -> {
                if (isDetached()) return;
                ++mLoadedItemCount;
                if (mLoadedItemCount >= 4) {
                    showProgressIndicator(false);
                }
                mHorizontalLayout.removeAllViews();
                for (ActionItem actionItem : actionItems) {
                    if (isDetached()) return;
                    mHorizontalLayout.addView(actionItem.toActionButton(mHorizontalLayout.getContext(), mHorizontalLayout));
                }
                if (isDetached()) return;
                View v = mHorizontalLayout.getChildAt(0);
                if (v != null) v.requestFocus();
            });
        });
    }

    @WorkerThread
    private List<ActionItem> getHorizontalActions() {
        Objects.requireNonNull(mMainModel);
        List<ActionItem> actionItems = new LinkedList<>();
        if (!mIsExternalApk) {
            boolean isStaticSharedLib = ApplicationInfoCompat.isStaticSharedLibrary(mApplicationInfo);
            boolean isFrozen = FreezeUtils.isFrozen(mApplicationInfo);
            boolean canFreeze = !isStaticSharedLib && SelfPermissions.canFreezeUnfreezePackages();
            // Set open
            Intent launchIntent = PackageManagerCompat.getLaunchIntentForPackage(mPackageName, mUserId);
            if (launchIntent != null && !isFrozen) {
                ActionItem launchAction = new ActionItem(R.string.launch_app, R.drawable.ic_open_in_new);
                actionItems.add(launchAction);
                launchAction.setOnClickListener(v -> {
                    try {
                        ActivityManagerCompat.startActivity(launchIntent, mUserId);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                    }
                });
            }
            // Set freeze/unfreeze
            if (canFreeze && !isFrozen) {
                ActionItem freezeAction = new ActionItem(R.string.freeze, R.drawable.ic_snowflake);
                actionItems.add(freezeAction);
                freezeAction.setOnClickListener(v -> {
                            if (BuildConfig.APPLICATION_ID.equals(mPackageName)) {
                                new MaterialAlertDialogBuilder(mActivity)
                                        .setMessage(R.string.are_you_sure)
                                        .setPositiveButton(R.string.yes, (d, w) -> freeze(true))
                                        .setNegativeButton(R.string.no, null)
                                        .show();
                            } else freeze(true);
                        })
                        .setOnLongClickListener(v -> {
                            createFreezeShortcut(false);
                            return true;
                        });
            }
            // Set uninstall
            ActionItem uninstallAction = new ActionItem(R.string.uninstall, R.drawable.ic_trash_can);
            actionItems.add(uninstallAction);
            uninstallAction.setOnClickListener(v -> {
                if (mUserId != UserHandleHidden.myUserId() && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
                    // Could be for work profile
                    try {
                        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                        uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                        ActivityManagerCompat.startActivity(uninstallIntent, mUserId);
                        // TODO: 19/8/24 Watch for uninstallation
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                    }
                    return;
                }
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                        isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                        .setTitle(mAppLabel)
                        // FIXME: 16/6/23 Does it even work without INSTALL_PACKAGES?
                        .setCheckboxLabel(R.string.keep_data_and_app_signing_signatures)
                        .setPositiveButton(R.string.uninstall, (dialog, which, keepData) -> ThreadUtils.postOnBackgroundThread(() -> {
                            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                            installer.setAppLabel(mAppLabel);
                            boolean uninstalled = installer.uninstall(mPackageName, mUserId, keepData);
                            ThreadUtils.postOnMainThread(() -> {
                                if (uninstalled) {
                                    displayLongToast(R.string.uninstalled_successfully, mAppLabel);
                                    mActivity.finish();
                                } else {
                                    displayLongToast(R.string.failed_to_uninstall, mAppLabel);
                                }
                            });
                        }))
                        .setNegativeButton(R.string.cancel, (dialog, which, keepData) -> {
                            if (dialog != null) dialog.cancel();
                        });
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    builder.setNeutralButton(R.string.uninstall_updates, (dialog, which, keepData) ->
                            ThreadUtils.postOnBackgroundThread(() -> {
                                PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                installer.setAppLabel(mAppLabel);
                                boolean isSuccessful = installer.uninstall(mPackageName, UserHandleHidden.USER_ALL, keepData);
                                if (isSuccessful) {
                                    ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.update_uninstalled_successfully, mAppLabel));
                                } else {
                                    ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_uninstall_updates, mAppLabel));
                                }
                            }));
                }
                builder.show();
            });
            // Enable/disable app (root/ADB only)
            if (canFreeze && isFrozen) {
                // Enable app
                ActionItem unfreezeAction = new ActionItem(R.string.unfreeze, R.drawable.ic_snowflake_off);
                actionItems.add(unfreezeAction);
                unfreezeAction.setOnClickListener(v -> freeze(false))
                        .setOnLongClickListener(v -> {
                            createFreezeShortcut(true);
                            return true;
                        });
            }
            boolean accessibilityServiceRunning = UserHandleHidden.myUserId() == mUserId && ServiceHelper
                    .checkIfServiceIsRunning(mActivity, NoRootAccessibilityService.class);
            if (!isStaticSharedLib && (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                    || accessibilityServiceRunning)) {
                // Force stop
                if (!ApplicationInfoCompat.isStopped(mApplicationInfo) &&
                        (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                                || accessibilityServiceRunning)) {
                    ActionItem forceStopAction = new ActionItem(R.string.force_stop, R.drawable.ic_power_settings);
                    actionItems.add(forceStopAction);
                    forceStopAction.setOnClickListener(v -> {
                        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                            ThreadUtils.postOnBackgroundThread(() -> {
                                try {
                                    PackageManagerCompat.forceStopPackage(mPackageName, mUserId);
                                } catch (SecurityException e) {
                                    Log.e(TAG, e);
                                    displayLongToast(R.string.failed_to_stop, mAppLabel);
                                }
                            });
                        } else {
                            // Use accessibility
                            AccessibilityMultiplexer.getInstance().enableForceStop(true);
                            mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                    result -> {
                                        AccessibilityMultiplexer.getInstance().enableForceStop(false);
                                        refreshDetails();
                                    });
                        }
                    });
                }
            }
            if (!isStaticSharedLib && (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)
                    || accessibilityServiceRunning)) {
                // Clear data
                ActionItem clearDataAction = new ActionItem(R.string.clear_data, R.drawable.ic_clear_data);
                actionItems.add(clearDataAction);
                clearDataAction.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(mAppLabel)
                        .setMessage(R.string.clear_data_message)
                        .setPositiveButton(R.string.clear, (dialog, which) -> {
                            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)) {
                                ThreadUtils.postOnBackgroundThread(() -> {
                                    boolean success = PackageManagerCompat
                                            .clearApplicationUserData(mPackageName, mUserId);
                                    ThreadUtils.postOnMainThread(() -> {
                                        if (success) {
                                            UIUtils.displayShortToast(R.string.done);
                                        } else UIUtils.displayShortToast(R.string.failed);
                                    });
                                });
                            } else {
                                // Use accessibility
                                AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                AccessibilityMultiplexer.getInstance().enableClearData(true);
                                mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                        result -> {
                                            AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                                            AccessibilityMultiplexer.getInstance().enableClearData(false);
                                            refreshDetails();
                                        });
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
            }
            if (!isStaticSharedLib && (SelfPermissions.canClearAppCache() || accessibilityServiceRunning)) {
                // Clear cache
                ActionItem clearCacheAction = new ActionItem(R.string.clear_cache, R.drawable.ic_clear_cache);
                actionItems.add(clearCacheAction);
                clearCacheAction.setOnClickListener(v -> {
                    if (SelfPermissions.canClearAppCache()) {
                        ThreadUtils.postOnBackgroundThread(() -> {
                            boolean success = PackageManagerCompat
                                    .deleteApplicationCacheFilesAsUser(mPackageName, mUserId);
                            ThreadUtils.postOnMainThread(() -> {
                                if (success) {
                                    UIUtils.displayShortToast(R.string.done);
                                } else UIUtils.displayShortToast(R.string.failed);
                            });
                        });
                    } else {
                        // Use accessibility
                        AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(true);
                        AccessibilityMultiplexer.getInstance().enableClearCache(true);
                        mActivityLauncher.launch(IntentUtils.getAppDetailsSettings(mPackageName),
                                result -> {
                                    AccessibilityMultiplexer.getInstance().enableNavigateToStorageAndCache(false);
                                    AccessibilityMultiplexer.getInstance().enableClearCache(false);
                                    refreshDetails();
                                });
                    }
                });
            } else {
                // Display Android settings button
                ActionItem settingAction = new ActionItem(R.string.view_in_settings, R.drawable.ic_settings);
                actionItems.add(settingAction);
                settingAction.setOnClickListener(v -> {
                    try {
                        ActivityManagerCompat.startActivity(IntentUtils.getAppDetailsSettings(mPackageName), mUserId);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast("Error: " + th.getLocalizedMessage());
                    }
                });
            }
        } else if (FeatureController.isInstallerEnabled()) {
            if (mInstalledPackageInfo == null) {
                // App not installed
                ActionItem installAction = new ActionItem(R.string.install, R.drawable.ic_get_app);
                actionItems.add(installAction);
                installAction.setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {
                    // Needs update
                    ActionItem whatsNewAction = new ActionItem(R.string.whats_new, io.github.muntashirakon.ui.R.drawable.ic_information);
                    actionItems.add(whatsNewAction);
                    whatsNewAction.setOnClickListener(v -> {
                        WhatsNewDialogFragment dialogFragment = WhatsNewDialogFragment
                                .getInstance(mPackageInfo, mInstalledPackageInfo);
                        dialogFragment.show(getChildFragmentManager(), WhatsNewDialogFragment.TAG);
                    });
                    ActionItem updateAction = new ActionItem(R.string.update, R.drawable.ic_get_app);
                    actionItems.add(updateAction);
                    updateAction.setOnClickListener(v -> install());
                } else if (installedVersionCode == thisVersionCode) {
                    // Needs reinstall
                    ActionItem reinstallAction = new ActionItem(R.string.reinstall, R.drawable.ic_get_app);
                    actionItems.add(reinstallAction);
                    reinstallAction.setOnClickListener(v -> install());
                } else if (SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
                    // Needs downgrade
                    ActionItem downgradeAction = new ActionItem(R.string.downgrade, R.drawable.ic_get_app);
                    actionItems.add(downgradeAction);
                    downgradeAction.setOnClickListener(v -> install());
                }
            }
        }
        // Set manifest
        if (FeatureController.isManifestEnabled()) {
            ActionItem manifestAction = new ActionItem(R.string.manifest, R.drawable.ic_package);
            actionItems.add(manifestAction);
            manifestAction.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                startActivityForSplit(intent);
            });
        }
        // Set scanner
        if (FeatureController.isScannerEnabled()) {
            ActionItem scannerAction = new ActionItem(R.string.scanner, R.drawable.ic_security);
            actionItems.add(scannerAction);
            scannerAction.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, ScannerActivity.class);
                intent.putExtra(ScannerActivity.EXTRA_IS_EXTERNAL, mIsExternalApk);
                startActivityForSplit(intent);
            });
        }
        // Root only features
        if (!mIsExternalApk) {
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
                ActionItem sharedPrefsAction = new ActionItem(R.string.shared_prefs, R.drawable.ic_view_list);
                actionItems.add(sharedPrefsAction);
                sharedPrefsAction.setOnClickListener(v -> new SearchableItemsDialogBuilder<>(mActivity, sharedPrefNames)
                        .setTitle(R.string.shared_prefs)
                        .setOnItemClickListener((dialog, which, item) -> {
                            Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                            intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which).getUri());
                            intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mAppLabel);
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
                ActionItem dbAction = new ActionItem(R.string.databases, R.drawable.ic_database);
                actionItems.add(dbAction);
                dbAction.setOnClickListener(v -> new SearchableItemsDialogBuilder<>(v.getContext(), databases2)
                        .setTitle(R.string.databases)
                        .setOnItemClickListener((dialog, which, item) -> ThreadUtils.postOnBackgroundThread(() -> {
                            // Vacuum database
                            Runner.runCommand(new String[]{"sqlite3", databases.get(which).getFilePath(), "vacuum"});
                            ThreadUtils.postOnMainThread(() -> {
                                OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(databases.get(which), "application/vnd.sqlite3");
                                if (isDetached()) return;
                                fragment.show(getChildFragmentManager(), OpenWithDialogFragment.TAG);
                            });
                        }))
                        .setNegativeButton(R.string.close, null)
                        .show());
            }
        }  // End root only features
        // Set F-Droid
        Intent fdroidIntent = new Intent(Intent.ACTION_VIEW);
        fdroidIntent.setData(Uri.parse("https://f-droid.org/packages/" + mPackageName));
        List<ResolveInfo> resolvedActivities = mPackageManager.queryIntentActivities(fdroidIntent, 0);
        if (!resolvedActivities.isEmpty()) {
            ActionItem fdroidItem = new ActionItem(R.string.fdroid, R.drawable.ic_frost_fdroid);
            actionItems.add(fdroidItem);
            fdroidItem.setOnClickListener(v -> {
                try {
                    startActivity(fdroidIntent);
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
            ActionItem auroraStoreAction = new ActionItem(R.string.open_in_aurora_store, R.drawable.ic_frost_aurorastore);
            actionItems.add(auroraStoreAction);
            auroraStoreAction.setOnClickListener(v -> {
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
        return actionItems;
    }

    @UiThread
    private void startActivityForSplit(Intent intent) {
        if (mMainModel == null) return;
        ApkFile apkFile = mMainModel.getApkFile();
        if (apkFile != null && apkFile.isSplit()) {
            // Display a list of apks
            List<ApkFile.Entry> apkEntries = apkFile.getEntries();
            CharSequence[] entryNames = new CharSequence[apkEntries.size()];
            for (int i = 0; i < apkEntries.size(); ++i) {
                entryNames[i] = apkEntries.get(i).toShortLocalizedString(requireActivity());
            }
            new SearchableItemsDialogBuilder<>(mActivity, entryNames)
                    .setTitle(R.string.select_apk)
                    .setOnItemClickListener((dialog, which, item) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            File file = apkEntries.get(which).getFile(false);
                            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension("apk"));
                            ThreadUtils.postOnMainThread(() -> {
                                if (isDetached()) return;
                                startActivity(intent);
                            });
                        } catch (IOException e) {
                            UIUtils.displayLongToast("Error: " + e.getMessage());
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
            if (mIsExternalApk && mInstalledPackageInfo != null) {
                ListItem listItem = ListItem.newSelectableRegularItem(getString(R.string.installed_version),
                        getString(R.string.version_name_with_code, mInstalledPackageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo)), v -> {
                            Intent intent = AppDetailsActivity.getIntent(mActivity, mPackageName,
                                    UserHandleHidden.myUserId());
                            mActivity.startActivity(intent);
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
            if (mIsExternalApk) return;

            mListItems.add(ListItem.newRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
            mListItems.add(ListItem.newRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
            if (!mPackageName.equals(mApplicationInfo.processName)) {
                mListItems.add(ListItem.newSelectableRegularItem(getString(R.string.process_name), mApplicationInfo.processName));
            }
            if (appInfo.installerApp != null) {
                ListItem installerItem = ListItem.newSelectableRegularItem(
                        getString(R.string.installer_app), appInfo.installerApp,
                        v -> displayInstallerDialog(Objects.requireNonNull(appInfo.installSource)));
                installerItem.setActionIcon(R.drawable.ic_information_circle);
                mListItems.add(installerItem);
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
            if (!mIsExternalApk) {
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
        if (dataUsage == null) {
            // No permission
            return;
        }
        // Hide data usage if:
        // 1. OS is Android 6.0 onwards, AND
        // 2. The user is not the current user, AND
        // 3. Remote UID is not system UID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mUserId != UserHandleHidden.myUserId()
                && !SelfPermissions.isSystem()) {
            return;
        }
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.newGroupStart(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_transmitted), getReadableSize(dataUsage.getTx())));
            mListItems.add(ListItem.newInlineItem(getString(R.string.data_received), getReadableSize(dataUsage.getRx())));
        }
    }

    @MainThread
    @GuardedBy("mListItems")
    private void setupVerticalView(AppInfoViewModel.AppInfo appInfo) {
        if (mListFuture != null) mListFuture.cancel(true);
        mListFuture = ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mListItems) {
                mListItems.clear();
                if (!mIsExternalApk) {
                    setPathsAndDirectories(appInfo);
                    setDataUsage(appInfo);
                    // Storage and Cache
                    if (FeatureController.isUsageAccessEnabled()) {
                        setStorageAndCache(appInfo);
                    }
                }
                setMoreInfo(appInfo);
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    ++mLoadedItemCount;
                    if (mLoadedItemCount >= 4) {
                        showProgressIndicator(false);
                    }
                    if (isDetached()) return;
                    mAdapter.setAdapterList(mListItems);
                });
            }
        });
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

    @GuardedBy("mListItems")
    private void setStorageAndCache(AppInfoViewModel.AppInfo appInfo) {
        if (FeatureController.isUsageAccessEnabled()) {
            // Grant optional READ_PHONE_STATE permission
            if (AppUsageStatsManager.requireReadPhoneStatePermission()) {
                ThreadUtils.postOnMainThread(() -> mRequestPerm.launch(Manifest.permission.READ_PHONE_STATE, granted -> {
                    if (granted) {
                        mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
                    }
                }));
            }
        }
        if (!SelfPermissions.checkUsageStatsPermission()) {
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.grant_usage_access)
                    .setMessage(R.string.grant_usage_acess_message)
                    .setPositiveButton(R.string.go, (dialog, which) -> {
                        try {
                            mActivityLauncher.launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), result -> {
                                if (SelfPermissions.checkUsageStatsPermission()) {
                                    FeatureController.getInstance().modifyState(FeatureController
                                            .FEAT_USAGE_ACCESS, true);
                                    // Reload app info
                                    mAppInfoModel.loadAppInfo(mPackageInfo, mIsExternalApk);
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

    @MainThread
    private void freeze(boolean freeze) {
        if (mMainModel == null) return;
        if (freeze) {
            mMainModel.loadFreezeType();
        } else {
            // Unfreeze
            ThreadUtils.postOnBackgroundThread(this::doUnfreeze);
        }
    }

    private void showFreezeDialog(int freezeType, boolean isCustom) {
        View view = View.inflate(mActivity, R.layout.item_checkbox, null);
        MaterialCheckBox checkBox = view.findViewById(R.id.checkbox);
        checkBox.setText(R.string.remember_option_for_this_app);
        checkBox.setChecked(isCustom);
        FreezeUnfreeze.getFreezeDialog(mActivity, freezeType)
                .setIcon(R.drawable.ic_snowflake)
                .setTitle(R.string.freeze)
                .setView(view)
                .setPositiveButton(R.string.freeze, (dialog, which, selectedItem) -> {
                    if (selectedItem == null) {
                        return;
                    }
                    ThreadUtils.postOnBackgroundThread(() -> doFreeze(selectedItem, checkBox.isChecked()));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @WorkerThread
    private void doFreeze(@FreezeUtils.FreezeType int freezeType, boolean remember) {
        try {
            if (remember) {
                FreezeUtils.setFreezeMethod(mPackageName, freezeType);
            }
            FreezeUtils.freeze(mPackageName, mUserId, freezeType);
        } catch (Throwable th) {
            Log.e(TAG, th);
            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_freeze, mAppLabel));
        }
    }

    @WorkerThread
    private void doUnfreeze() {
        try {
            FreezeUtils.unfreeze(mPackageName, mUserId);
        } catch (Throwable th) {
            Log.e(TAG, th);
            ThreadUtils.postOnMainThread(() -> displayLongToast(R.string.failed_to_unfreeze, mAppLabel));
        }
    }

    private void createFreezeShortcut(boolean isFrozen) {
        if (mMainModel == null) return;
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
                    Bitmap icon = getBitmapFromDrawable(mIconView.getDrawable());
                    FreezeUnfreezeShortcutInfo shortcutInfo = new FreezeUnfreezeShortcutInfo(mPackageName, mUserId, flags);
                    shortcutInfo.setName(mAppLabel);
                    shortcutInfo.setIcon(isFrozen ? getDimmedBitmap(icon) : icon);
                    CreateShortcutDialogFragment dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                    dialog1.show(getChildFragmentManager(), CreateShortcutDialogFragment.TAG);
                })
                .show();
    }

    private void displayInstallerDialog(@NonNull InstallSourceInfoCompat installSource) {
        List<CharSequence> installerInfoList = new ArrayList<>(3);
        List<String> packageNames = new ArrayList<>(3);
        if (installSource.getInstallingPackageLabel() != null) {
            CharSequence info = new SpannableStringBuilder(getSmallerText(getString(R.string.installer)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getInstallingPackageLabel()))
                    .append("\n")
                    .append(installSource.getInstallingPackageName());
            installerInfoList.add(info);
            packageNames.add(installSource.getInstallingPackageName());
        }
        if (installSource.getInitiatingPackageLabel() != null) {
            CharSequence info = new SpannableStringBuilder(getSmallerText(getString(R.string.actual_installer)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getInitiatingPackageLabel()))
                    .append("\n")
                    .append(installSource.getInitiatingPackageName());
            installerInfoList.add(info);
            packageNames.add(installSource.getInitiatingPackageName());
        }
        if (installSource.getOriginatingPackageLabel() != null) {
            CharSequence info = new SpannableStringBuilder(getSmallerText(getString(R.string.apk_source)))
                    .append("\n")
                    .append(getTitleText(requireContext(), installSource.getOriginatingPackageLabel()))
                    .append("\n")
                    .append(installSource.getOriginatingPackageName());
            installerInfoList.add(info);
            packageNames.add(installSource.getOriginatingPackageName());
        }
        new SearchableItemsDialogBuilder<>(requireContext(), installerInfoList)
                .setTitle(R.string.installer)
                .setOnItemClickListener((dialog, which, item) -> {
                    String packageName = packageNames.get(which);
                    Intent intent = AppDetailsActivity.getIntent(requireContext(), packageName, mUserId);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.close, null)
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
        return DateUtils.formatLongDateTime(requireContext(), time);
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
