// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.collection.SparseArrayCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.UUID;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.IntegerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;

// Copyright 2020 Muntashir Al-Islam
// Copyright 2017 k3b
// Copyright 2012 Intrications
public class ActivityInterceptor extends BaseActivity {
    public static final String TAG = ActivityInterceptor.class.getSimpleName();

    public static final String EXTRA_PACKAGE_NAME = BuildConfig.APPLICATION_ID + ".intent.extra.PACKAGE_NAME";
    public static final String EXTRA_CLASS_NAME = BuildConfig.APPLICATION_ID + ".intent.extra.CLASS_NAME";
    public static final String EXTRA_ACTION = BuildConfig.APPLICATION_ID + ".intent.extra.ACTION";
    // TODO(29/8/21): Enable getting activity result for activities launched with root
    public static final String EXTRA_ROOT = BuildConfig.APPLICATION_ID + ".intent.extra.ROOT";
    // Root only
    public static final String EXTRA_USER_HANDLE = BuildConfig.APPLICATION_ID + ".intent.extra.USER_HANDLE";
    // Whether to trigger the Intent on startup, requires `auth` parameter to be set
    public static final String EXTRA_TRIGGER_ON_START = BuildConfig.APPLICATION_ID + ".intent.extra.TRIGGER_ON_START";
    public static final String EXTRA_AUTH = BuildConfig.APPLICATION_ID + ".intent.extra.AUTH";

    private static final String INTENT_EDITED = "intent_edited";

    private static final SparseArrayCompat<String> INTENT_FLAG_TO_STRING = new SparseArrayCompat<String>() {
        {
            put(Intent.FLAG_GRANT_READ_URI_PERMISSION, "FLAG_GRANT_READ_URI_PERMISSION");
            put(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, "FLAG_GRANT_WRITE_URI_PERMISSION");
            put(Intent.FLAG_FROM_BACKGROUND, "FLAG_FROM_BACKGROUND");
            put(Intent.FLAG_DEBUG_LOG_RESOLUTION, "FLAG_DEBUG_LOG_RESOLUTION");
            put(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES, "FLAG_EXCLUDE_STOPPED_PACKAGES");
            put(Intent.FLAG_INCLUDE_STOPPED_PACKAGES, "FLAG_INCLUDE_STOPPED_PACKAGES");
            put(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, "FLAG_GRANT_PERSISTABLE_URI_PERMISSION");
            put(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, "FLAG_GRANT_PREFIX_URI_PERMISSION");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(Intent.FLAG_DIRECT_BOOT_AUTO, "FLAG_DIRECT_BOOT_AUTO");
            }
            put(0x00000200, "FLAG_IGNORE_EPHEMERAL");
            put(Intent.FLAG_ACTIVITY_NO_HISTORY, "FLAG_ACTIVITY_NO_HISTORY");
            put(Intent.FLAG_ACTIVITY_SINGLE_TOP, "FLAG_ACTIVITY_SINGLE_TOP");
            put(Intent.FLAG_ACTIVITY_NEW_TASK, "FLAG_ACTIVITY_NEW_TASK");
            put(Intent.FLAG_ACTIVITY_MULTIPLE_TASK, "FLAG_ACTIVITY_MULTIPLE_TASK");
            put(Intent.FLAG_ACTIVITY_CLEAR_TOP, "FLAG_ACTIVITY_CLEAR_TOP");
            put(Intent.FLAG_ACTIVITY_FORWARD_RESULT, "FLAG_ACTIVITY_FORWARD_RESULT");
            put(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP, "FLAG_ACTIVITY_PREVIOUS_IS_TOP");
            put(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS");
            put(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT, "FLAG_ACTIVITY_BROUGHT_TO_FRONT");
            put(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED");
            put(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY, "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY");
            put(Intent.FLAG_ACTIVITY_NEW_DOCUMENT, "FLAG_ACTIVITY_NEW_DOCUMENT");
            put(Intent.FLAG_ACTIVITY_NO_USER_ACTION, "FLAG_ACTIVITY_NO_USER_ACTION");
            put(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, "FLAG_ACTIVITY_REORDER_TO_FRONT");
            put(Intent.FLAG_ACTIVITY_NO_ANIMATION, "FLAG_ACTIVITY_NO_ANIMATION");
            put(Intent.FLAG_ACTIVITY_CLEAR_TASK, "FLAG_ACTIVITY_CLEAR_TASK");
            put(Intent.FLAG_ACTIVITY_TASK_ON_HOME, "FLAG_ACTIVITY_TASK_ON_HOME");
            put(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS, "FLAG_ACTIVITY_RETAIN_IN_RECENTS");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT, "FLAG_ACTIVITY_LAUNCH_ADJACENT");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                put(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL, "FLAG_ACTIVITY_MATCH_EXTERNAL");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                put(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER, "FLAG_ACTIVITY_REQUIRE_NON_BROWSER");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                put(Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT, "FLAG_ACTIVITY_REQUIRE_DEFAULT");
            }
        }
    };

    // TODO(25/1/21): Add support for receiver flags
    private static final SparseArrayCompat<String> INTENT_RECEIVER_FLAG_TO_STRING = new SparseArrayCompat<String>() {
        {
            put(Intent.FLAG_RECEIVER_REGISTERED_ONLY, "FLAG_RECEIVER_REGISTERED_ONLY");
            put(Intent.FLAG_RECEIVER_REPLACE_PENDING, "FLAG_RECEIVER_REPLACE_PENDING");
            put(Intent.FLAG_RECEIVER_FOREGROUND, "FLAG_RECEIVER_FOREGROUND");
            put(0x80000000, "FLAG_RECEIVER_OFFLOAD");
            put(Intent.FLAG_RECEIVER_NO_ABORT, "FLAG_RECEIVER_NO_ABORT");
            put(0x04000000, "FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT");
            put(0x02000000, "FLAG_RECEIVER_BOOT_UPGRADE");
            put(0x01000000, "FLAG_RECEIVER_INCLUDE_BACKGROUND");
            put(0x00800000, "FLAG_RECEIVER_EXCLUDE_BACKGROUND");
            put(0x00400000, "FLAG_RECEIVER_FROM_SHELL");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                put(Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS, "FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS");
            }
        }
    };

    private static final List<String> INTENT_CATEGORIES = new ArrayList<String>() {
        {
            add(Intent.CATEGORY_DEFAULT);
            add(Intent.CATEGORY_BROWSABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(Intent.CATEGORY_VOICE);
            }
            add(Intent.CATEGORY_ALTERNATIVE);
            add(Intent.CATEGORY_SELECTED_ALTERNATIVE);
            add(Intent.CATEGORY_TAB);
            add(Intent.CATEGORY_LAUNCHER);
            add(Intent.CATEGORY_LEANBACK_LAUNCHER);
            add("android.intent.category.CAR_LAUNCHER");
            add("android.intent.category.LEANBACK_SETTINGS");
            add(Intent.CATEGORY_INFO);
            add(Intent.CATEGORY_HOME);
            add("android.intent.category.HOME_MAIN");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent.CATEGORY_SECONDARY_HOME);
            }
            add("android.intent.category.SETUP_WIZARD");
            add("android.intent.category.LAUNCHER_APP");
            add(Intent.CATEGORY_PREFERENCE);
            add(Intent.CATEGORY_DEVELOPMENT_PREFERENCE);
            add(Intent.CATEGORY_EMBED);
            add(Intent.CATEGORY_APP_MARKET);
            add(Intent.CATEGORY_MONKEY);
            add(Intent.CATEGORY_TEST);
            add(Intent.CATEGORY_UNIT_TEST);
            add(Intent.CATEGORY_SAMPLE_CODE);
            add(Intent.CATEGORY_OPENABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Intent.CATEGORY_TYPED_OPENABLE);
            }
            add(Intent.CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST);
            add(Intent.CATEGORY_CAR_DOCK);
            add(Intent.CATEGORY_DESK_DOCK);
            add(Intent.CATEGORY_LE_DESK_DOCK);
            add(Intent.CATEGORY_HE_DESK_DOCK);
            add(Intent.CATEGORY_CAR_MODE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Intent.CATEGORY_VR_HOME);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET);
            }
            add(Intent.CATEGORY_APP_BROWSER);
            add(Intent.CATEGORY_APP_CALCULATOR);
            add(Intent.CATEGORY_APP_CALENDAR);
            add(Intent.CATEGORY_APP_CONTACTS);
            add(Intent.CATEGORY_APP_EMAIL);
            add(Intent.CATEGORY_APP_GALLERY);
            add(Intent.CATEGORY_APP_MAPS);
            add(Intent.CATEGORY_APP_MESSAGING);
            add(Intent.CATEGORY_APP_MUSIC);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent.CATEGORY_APP_FILES);
            }
        }
    };

    private abstract class IntentUpdateTextWatcher implements TextWatcher {
        private final TextView mTextView;

        IntentUpdateTextWatcher(TextView textView) {
            mTextView = textView;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mAreTextWatchersActive) {
                try {
                    String modifiedContent = mTextView.getText().toString();
                    onUpdateIntent(modifiedContent);
                    showTextViewIntentData(mTextView);
                    showResetIntentButton(true);
                    refreshUI();
                } catch (Exception e) {
                    Toast.makeText(ActivityInterceptor.this, e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }

        abstract protected void onUpdateIntent(String modifiedContent);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    private MaterialAutoCompleteTextView mActionView;
    private MaterialAutoCompleteTextView mDataView;
    private MaterialAutoCompleteTextView mTypeView;
    private MaterialAutoCompleteTextView mUriView;
    private MaterialAutoCompleteTextView mPackageNameView;
    private MaterialAutoCompleteTextView mClassNameView;
    private TextInputEditText mIdView;
    private TextInputEditText mUserIdEdit;

    @Nullable
    private HistoryEditText mHistory;

    private CategoriesRecyclerViewAdapter mCategoriesAdapter;
    private FlagsRecyclerViewAdapter mFlagsAdapter;
    private ExtrasRecyclerViewAdapter mExtrasAdapter;
    private MatchingActivitiesRecyclerViewAdapter mMatchingActivitiesAdapter;
    private TextView mActivitiesHeader;
    private Button mResendIntentButton;
    private Button mResetIntentButton;

    /**
     * String representation of intent as URI
     */
    @Nullable
    private String mOriginalIntent;
    /**
     * Extras that are lost during intent to string conversion
     */
    @Nullable
    private Bundle mAdditionalExtras;
    @Nullable
    private Intent mMutableIntent;
    @Nullable
    private ComponentName mRequestedComponent;

    private boolean mUseRoot;
    private int mUserHandle;

    @Nullable
    private Integer mLastResultCode = null;
    @Nullable
    private Intent mLastResultIntent = null;

    private volatile boolean mAreTextWatchersActive;

    private final ActivityResultLauncher<Intent> mIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                mLastResultCode = result.getResultCode();
                mLastResultIntent = result.getData();

                // Forward the result of the activity to the caller activity
                setResult(result.getResultCode(), data);

                refreshUI();
                Uri uri = data == null ? null : data.getData();
                Toast.makeText(ActivityInterceptor.this,
                        String.format("%s: (%s)", getString(R.string.activity_result), uri),
                        Toast.LENGTH_LONG).show();
            });

    @Override
    public void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_interceptor);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        // Get Intent
        Intent intent = new Intent(getIntent());
        mUseRoot = Ops.isRoot() && intent.getBooleanExtra(EXTRA_ROOT, false);
        mUserHandle = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId());
        intent.removeExtra(EXTRA_ROOT);
        intent.removeExtra(EXTRA_USER_HANDLE);
        intent.setPackage(null);
        intent.setComponent(null);
        // Get ComponentName if set
        String pkgName;
        if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            intent.removeExtra(EXTRA_PACKAGE_NAME);
            intent.setPackage(pkgName);
            updateTitle(pkgName);
        } else pkgName = null;
        if (intent.hasExtra(EXTRA_CLASS_NAME)) {
            String className;
            className = intent.getStringExtra(EXTRA_CLASS_NAME);
            intent.removeExtra(EXTRA_CLASS_NAME);
            if (pkgName != null && className != null) {
                mRequestedComponent = new ComponentName(pkgName, className);
                intent.setComponent(mRequestedComponent);
                updateSubtitle(mRequestedComponent);
            }
        }
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (action != null) {
            intent.setAction(action);
        }
        // For shortcut/startup trigger: Need authorization to prevent abuse
        if (intent.getBooleanExtra(EXTRA_TRIGGER_ON_START, false)
                && AuthManager.getKey().equals(intent.getStringExtra(EXTRA_AUTH))) {
            intent.removeExtra(EXTRA_TRIGGER_ON_START);
            intent.removeExtra(EXTRA_AUTH);
            // Trigger intent
            launchIntent(intent, mRequestedComponent == null);
            // Fall-through
        }
        // Whether the Intent was edited
        final boolean isVisible = savedInstanceState != null && savedInstanceState.getBoolean(INTENT_EDITED);
        init(intent, isVisible);
    }

    private void init(@NonNull Intent intent, boolean isEdited) {
        // Store the Intent
        storeOriginalIntent(intent);
        // Load Intent data
        showInitialIntent(isEdited);
        // Save Intent data to history
        if (mHistory != null && mRequestedComponent == null) {
            mHistory.saveHistory();
        }
    }

    private void storeOriginalIntent(@NonNull Intent intent) {
        // Store original intent as URI string
        mOriginalIntent = getUri(intent);
        // Get a new intent from the URI
        Intent copyIntent = cloneIntent(mOriginalIntent);
        // Store extras that are not available in the URI
        Bundle originalExtras = intent.getExtras();
        if (copyIntent == null || originalExtras == null) {
            return;
        }
        Bundle additionalExtrasBundle = new Bundle(originalExtras);
        for (String key : originalExtras.keySet()) {
            if (copyIntent.hasExtra(key)) {
                additionalExtrasBundle.remove(key);
            }
        }
        if (!additionalExtrasBundle.isEmpty()) {
            mAdditionalExtras = additionalExtrasBundle;
        }
    }

    /**
     * Create a clone of the original Intent and display it for editing
     */
    private void showInitialIntent(boolean isVisible) {
        // Copy a mutable version of the intent
        mMutableIntent = cloneIntent(mOriginalIntent);
        // Setup views
        setupVariables();
        // Setup watchers to watch and update changes
        setupTextWatchers();
        // Display intent data
        showAllIntentData(null);
        // Display reset button if the intent was modified
        showResetIntentButton(isVisible);
    }

    /**
     * @param textViewToIgnore The {@link TextView} which should not be updated.
     */
    private void showAllIntentData(@Nullable TextView textViewToIgnore) {
        showTextViewIntentData(textViewToIgnore);
        // Display categories
        mCategoriesAdapter.setDefaultList(mMutableIntent != null ? mMutableIntent.getCategories() : null);
        // Display flags
        mFlagsAdapter.setDefaultList(getFlags());
        // Display extras
        mExtrasAdapter.setDefaultList(getExtras());
        refreshUI();
    }

    private void updateTitle(@Nullable String packageName) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (packageName != null) {
                // TODO: 4/2/22 Fetch label in a different thread, for the given user
                actionBar.setTitle(PackageUtils.getPackageLabel(getPackageManager(), packageName));
            } else actionBar.setTitle(R.string.interceptor);
        }
    }

    private void updateSubtitle(@Nullable ComponentName cn) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (cn == null) {
                actionBar.setSubtitle(null);
                return;
            }
            PackageManager pm = getPackageManager();
            try {
                // Load label for the given user
                ActivityInfo info = pm.getActivityInfo(cn, 0);
                actionBar.setSubtitle(info.loadLabel(pm));
            } catch (PackageManager.NameNotFoundException e) {
                actionBar.setSubtitle(cn.getClassName());
            }
        }
    }

    @NonNull
    private List<Pair<String, Object>> getExtras() {
        Bundle intentBundle;
        if (mMutableIntent == null || (intentBundle = mMutableIntent.getExtras()) == null) {
            return Collections.emptyList();
        }
        List<Pair<String, Object>> extras = new ArrayList<>();
        for (String extraKey : intentBundle.keySet()) {
            Object extraValue = intentBundle.get(extraKey);
            if (extraValue == null) continue;
            extras.add(new Pair<>(extraKey, extraValue));
        }
        return extras;
    }

    /**
     * @param textViewToIgnore The {@link TextView} which should not be updated.
     */
    private void showTextViewIntentData(@Nullable TextView textViewToIgnore) {
        if (mMutableIntent == null) return;
        // Disable text watchers temporarily to prevent triggering modifications
        mAreTextWatchersActive = false;
        try {
            if (textViewToIgnore != mActionView) {
                mActionView.setText(mMutableIntent.getAction());
            }
            if (textViewToIgnore != mDataView && mMutableIntent.getDataString() != null) {
                mDataView.setText(mMutableIntent.getDataString());
            }
            if (textViewToIgnore != mTypeView) {
                mTypeView.setText(mMutableIntent.getType());
            }
            if (textViewToIgnore != mPackageNameView) {
                mPackageNameView.setText(mMutableIntent.getPackage());
            }
            if (textViewToIgnore != mClassNameView) {
                ComponentName cn = mMutableIntent.getComponent();
                mClassNameView.setText(cn != null ? cn.getClassName() : null);
            }
            if (textViewToIgnore != mUriView) {
                mUriView.setText(getUri(mMutableIntent));
            }
            if (textViewToIgnore != mIdView) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mIdView.setText(mMutableIntent.getIdentifier());
                }
            }
        } finally {
            mAreTextWatchersActive = true;
        }
    }

    @NonNull
    private List<String> getFlags() {
        if (mMutableIntent == null) {
            return Collections.emptyList();
        }
        int flags = mMutableIntent.getFlags();
        ArrayList<String> flagsStrings = new ArrayList<>();
        for (int i = 0; i < INTENT_FLAG_TO_STRING.size(); ++i) {
            if ((flags & INTENT_FLAG_TO_STRING.keyAt(i)) != 0) {
                flagsStrings.add(INTENT_FLAG_TO_STRING.valueAt(i));
            }
        }
        return flagsStrings;
    }

    private void checkAndShowMatchingActivities() {
        if (mMutableIntent == null) return;
        List<ResolveInfo> resolveInfo = getMatchingActivities();
        if (resolveInfo.isEmpty()) {
            mResendIntentButton.setEnabled(false);
            mActivitiesHeader.setVisibility(View.GONE);
        } else {
            mResendIntentButton.setEnabled(true);
            mActivitiesHeader.setVisibility(View.VISIBLE);
        }
        mActivitiesHeader.setText(getString(R.string.matching_activities));
        mMatchingActivitiesAdapter.setDefaultList(resolveInfo);
    }

    @NonNull
    private List<ResolveInfo> getMatchingActivities() {
        if (mMutableIntent == null) {
            return Collections.emptyList();
        }
        if (mUseRoot || SelfPermissions.checkCrossUserPermission(mUserHandle, false)) {
            try {
                return PackageManagerCompat.queryIntentActivities(this, mMutableIntent, 0, mUserHandle);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return getPackageManager().queryIntentActivities(mMutableIntent, 0);
    }

    private void setupVariables() {
        mActionView = findViewById(R.id.action_edit);
        mDataView = findViewById(R.id.data_edit);
        mTypeView = findViewById(R.id.type_edit);
        mUriView = findViewById(R.id.uri_edit);
        mPackageNameView = findViewById(R.id.package_edit);
        mClassNameView = findViewById(R.id.class_edit);
        mIdView = findViewById(R.id.type_id);

        mHistory = new HistoryEditText(this, mActionView, mDataView, mTypeView, mUriView, mPackageNameView, mClassNameView);

        // Setup user ID edit
        mUserIdEdit = findViewById(R.id.user_id_edit);
        mUserIdEdit.setText(String.valueOf(mUserHandle));
        mUserIdEdit.setEnabled(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS)
                || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL));
        // Setup root
        MaterialCheckBox useRootCheckBox = findViewById(R.id.use_root);
        useRootCheckBox.setChecked(mUseRoot);
        useRootCheckBox.setVisibility(Ops.isRoot() ? View.VISIBLE : View.GONE);
        useRootCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mUseRoot != isChecked) {
                mUseRoot = isChecked;
                refreshUI();
            }
        });
        // Setup identifier
        TextInputLayout idLayout = findViewById(R.id.type_id_layout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            idLayout.setEndIconOnClickListener(v -> mIdView.setText(UUID.randomUUID().toString()));
        } else idLayout.setVisibility(View.GONE);

        // Setup categories
        findViewById(R.id.intent_categories_add_btn).setOnClickListener(v ->
                new TextInputDropdownDialogBuilder(this, R.string.category)
                        .setTitle(R.string.category)
                        .setDropdownItems(INTENT_CATEGORIES, -1, true)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                            if (!TextUtils.isEmpty(inputText)) {
                                //noinspection ConstantConditions
                                mMutableIntent.addCategory(inputText.toString().trim());
                                mCategoriesAdapter.setDefaultList(mMutableIntent.getCategories());
                                showTextViewIntentData(null);
                            }
                        })
                        .show());
        RecyclerView categoriesRecyclerView = findViewById(R.id.intent_categories);
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mCategoriesAdapter = new CategoriesRecyclerViewAdapter(this);
        categoriesRecyclerView.setAdapter(mCategoriesAdapter);

        // Setup flags
        findViewById(R.id.intent_flags_add_btn).setOnClickListener(v ->
                new TextInputDropdownDialogBuilder(this, R.string.flags)
                        .setTitle(R.string.flags)
                        .setDropdownItems(getAllFlags(), -1, true)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                            if (!TextUtils.isEmpty(inputText) && mMutableIntent != null) {
                                int i = getFlagIndex(String.valueOf(inputText).trim());
                                if (i >= 0) {
                                    mMutableIntent.addFlags(INTENT_FLAG_TO_STRING.keyAt(i));
                                } else {
                                    try {
                                        int flag = IntegerCompat.decode(String.valueOf(inputText).trim());
                                        mMutableIntent.addFlags(flag);
                                    } catch (NumberFormatException e) {
                                        return;
                                    }
                                }
                                mFlagsAdapter.setDefaultList(getFlags());
                                showTextViewIntentData(null);
                            }
                        })
                        .show());
        RecyclerView flagsRecyclerView = findViewById(R.id.intent_flags);
        flagsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mFlagsAdapter = new FlagsRecyclerViewAdapter(this);
        flagsRecyclerView.setAdapter(mFlagsAdapter);

        // Setup extras
        findViewById(R.id.intent_extras_add_btn).setOnClickListener(v -> {
            AddIntentExtraFragment fragment = new AddIntentExtraFragment();
            fragment.setOnSaveListener((mode, prefItem) -> {
                if (mMutableIntent != null) {
                    IntentCompat.addToIntent(mMutableIntent, prefItem);
                    mExtrasAdapter.setDefaultList(getExtras());
                }
            });
            Bundle args = new Bundle();
            args.putInt(AddIntentExtraFragment.ARG_MODE, AddIntentExtraFragment.MODE_CREATE);
            fragment.setArguments(args);
            fragment.show(getSupportFragmentManager(), AddIntentExtraFragment.TAG);
        });
        RecyclerView extrasRecyclerView = findViewById(R.id.intent_extras);
        extrasRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mExtrasAdapter = new ExtrasRecyclerViewAdapter(this);
        extrasRecyclerView.setAdapter(mExtrasAdapter);

        // Setup matching activities
        mActivitiesHeader = findViewById(R.id.intent_matching_activities_header);
        if (mRequestedComponent != null) {
            // Hide matching activities since specific component requested
            mActivitiesHeader.setVisibility(View.GONE);
        }
        RecyclerView matchingActivitiesRecyclerView = findViewById(R.id.intent_matching_activities);
        matchingActivitiesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mMatchingActivitiesAdapter = new MatchingActivitiesRecyclerViewAdapter(this);
        matchingActivitiesRecyclerView.setAdapter(mMatchingActivitiesAdapter);

        mResendIntentButton = findViewById(R.id.resend_intent_button);
        mResetIntentButton = findViewById(R.id.reset_intent_button);

        // Send Intent on clicking the resend intent button
        mResendIntentButton.setOnClickListener(v -> {
            if (mMutableIntent == null) return;
            launchIntent(mMutableIntent, mRequestedComponent == null);
        });
        // Reset Intent data on clicking the reset intent button
        mResetIntentButton.setOnClickListener(v -> {
            mAreTextWatchersActive = false;
            showInitialIntent(false);
            mAreTextWatchersActive = true;
            refreshUI();
        });
    }

    private void setupTextWatchers() {
        mActionView.addTextChangedListener(new IntentUpdateTextWatcher(mActionView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent != null) {
                    mMutableIntent.setAction(modifiedContent);
                }
            }
        });
        mDataView.addTextChangedListener(new IntentUpdateTextWatcher(mDataView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent != null) {
                    // setDataAndType clears type so we save it
                    String savedType = mMutableIntent.getType();
                    mMutableIntent.setDataAndType(Uri.parse(modifiedContent), savedType);
                }
            }
        });
        mTypeView.addTextChangedListener(new IntentUpdateTextWatcher(mTypeView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent != null) {
                    // setDataAndType clears data so we save it
                    String dataString = mMutableIntent.getDataString();
                    mMutableIntent.setDataAndType(Uri.parse(dataString), modifiedContent);
                }
            }
        });
        mPackageNameView.addTextChangedListener(new IntentUpdateTextWatcher(mPackageNameView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent != null) {
                    mMutableIntent.setPackage(TextUtils.isEmpty(modifiedContent) ? null : modifiedContent);
                }
            }
        });
        mClassNameView.addTextChangedListener(new IntentUpdateTextWatcher(mClassNameView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent == null) return;
                String packageName = mMutableIntent.getPackage();
                if (packageName == null && !TextUtils.isEmpty(modifiedContent)) {
                    UIUtils.displayShortToast(R.string.set_package_name_first);
                    mAreTextWatchersActive = false;
                    mClassNameView.setText(null);
                    mAreTextWatchersActive = true;
                    return;
                }
                if (TextUtils.isEmpty(modifiedContent)) {
                    mRequestedComponent = null;
                    mMutableIntent.setComponent(null);
                    return;
                }
                mRequestedComponent = new ComponentName(packageName, (modifiedContent.startsWith(".") ?
                        packageName : "") + modifiedContent);
                mMutableIntent.setComponent(mRequestedComponent);
            }
        });
        mUriView.addTextChangedListener(new IntentUpdateTextWatcher(mUriView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                // no error yet so continue
                mMutableIntent = cloneIntent(modifiedContent);
                // this time must update all content since extras/flags may have been changed
                showAllIntentData(mUriView);
            }
        });
        mIdView.addTextChangedListener(new IntentUpdateTextWatcher(mIdView) {
            @Override
            protected void onUpdateIntent(String modifiedContent) {
                if (mMutableIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mMutableIntent.setIdentifier(modifiedContent);
                }
            }
        });
        mUserIdEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s == null) return;
                try {
                    mUserHandle = Integer.decode(s.toString());
                    refreshUI();
                } catch (NumberFormatException ignore) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void showResetIntentButton(boolean visible) {
        mResendIntentButton.setText(R.string.send_edited_intent);
        mResetIntentButton.setVisibility((visible) ? View.VISIBLE : View.GONE);
    }

    private void copyIntentDetails() {
        Utils.copyToClipboard(this, "Intent Details", getIntentDetailsString());
    }

    private void copyIntentAsCommand() {
        if (mMutableIntent == null) {
            return;
        }
        List<String> args = IntentCompat.flattenToCommand(mMutableIntent);
        String command = String.format(Locale.ROOT, "%s start --user %d %s", RunnerUtils.CMD_AM, mUserHandle,
                TextUtils.join(" ", args));
        Utils.copyToClipboard(this, "am command", command);
    }

    private void pasteIntentDetails() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null) return;
        for (int i = 0; i < clipData.getItemCount(); ++i) {
            // Iterate over all ClipData to find if there is any saved Intent
            ClipData.Item item = clipData.getItemAt(i);
            String text = item.coerceToText(this).toString();
            // Get ROOT <bool> and USER <id> since they aren't part of IntentCompat.unflattenFromString()
            String[] lines = text.split("\n");
            mUseRoot = false;
            mUserHandle = UserHandleHidden.myUserId();
            int parseCount = 0;
            for (String line : lines) {
                if (TextUtils.isEmpty(line)) continue;
                StringTokenizer tokenizer = new StringTokenizer(line, "\t");
                switch (tokenizer.nextToken()) {
                    case "ROOT":
                        mUseRoot = Ops.isRoot() && Boolean.parseBoolean(tokenizer.nextToken());
                        ++parseCount;
                        break;
                    case "USER":
                        int userId = Integer.decode(tokenizer.nextToken());
                        if (SelfPermissions.checkCrossUserPermission(userId, false)) {
                            mUserHandle = userId;
                        }
                        ++parseCount;
                        break;
                }
                if (parseCount == 2) {
                    // Got both ROOT and USER, no need to continue the loop
                    break;
                }
            }
            // Rebuild Intent
            Intent intent = IntentCompat.unflattenFromString(text);
            if (intent != null) {
                // Requested component set to NULL in case it was set previously
                mRequestedComponent = null;
                init(intent, false);
                break;
            }
        }
    }

    private void refreshUI() {
        if (mMutableIntent == null) return;
        if (mRequestedComponent == null) {
            // Since no explicit component requested, display matching activities
            checkAndShowMatchingActivities();
        } else {
            // Hide matching activities since specific component requested
            mActivitiesHeader.setVisibility(View.GONE);
            mResendIntentButton.setEnabled(true);
        }
        updateTitle(mMutableIntent.getPackage());
        updateSubtitle(mMutableIntent.getComponent());
    }

    @NonNull
    private Intent createShareIntent() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, getIntentDetailsString());
        return share;
    }

    @NonNull
    private String getIntentDetailsString() {
        if (mMutableIntent == null) {
            return "";
        }
        PackageManager pm = getPackageManager();
        List<ResolveInfo> resolveInfo = getMatchingActivities();
        int numberOfMatchingActivities = resolveInfo.size();

        StringBuilder result = new StringBuilder();
        // NOTE: At least 1 tab have to be present in each non-empty line. Empty lines are ignored.
        // URI <URI> (unused)
        result.append("URI\t").append(getUri(mMutableIntent)).append("\n");
        // ROOT <bool>
        if (mUseRoot) result.append("ROOT\t").append(mUseRoot).append("\n");
        // USER <id>
        if (mUserHandle != UserHandleHidden.myUserId()) {
            result.append("USER\t").append(mUserHandle).append("\n");
        }
        result.append("\n");
        // Convert the Intent to parsable string
        result.append(IntentCompat.flattenToString(mMutableIntent)).append("\n");
        // MATCHING ACTIVITIES <match-count>
        result.append("MATCHING ACTIVITIES\t").append(numberOfMatchingActivities).append("\n");
        // Calculate the number of spaces needed in order to align activity items properly
        int spaceCount = String.valueOf(numberOfMatchingActivities).length();
        StringBuilder spaces = new StringBuilder();
        while ((spaceCount--) != 0) spaces.append(" ");
        for (int i = 0; i < numberOfMatchingActivities; ++i) {
            ActivityInfo activityinfo = resolveInfo.get(i).activityInfo;
            // Line 1: <no> LABEL   <activity-label>
            // Line 2:      NAME    <activity-name>
            // Line 3:      PACKAGE <package-name>
            result.append(i).append("\tLABEL  \t").append(activityinfo.loadLabel(pm)).append("\n");
            result.append(spaces).append("\tNAME   \t").append(activityinfo.name).append("\n");
            result.append(spaces).append("\tPACKAGE\t").append(activityinfo.packageName).append("\n");
        }
        // Add activity results
        if (mLastResultCode != null) {
            result.append("\n");
            // ACTIVITY RESULT <result-code>
            result.append("ACTIVITY RESULT\t").append(mLastResultCode).append("\n");
            if (mLastResultIntent != null) {
                // Print the last result intent with RESULT prefix so that it will not be parsed by the parser
                result.append(IntentCompat.describeIntent(mLastResultIntent, "RESULT"));
            }
        }
        return result.toString();
    }

    public void launchIntent(@NonNull Intent intent, boolean createChooser) {
        boolean needPrivilege = mUseRoot || mUserHandle != UserHandleHidden.myUserId();
        try {
            if (createChooser) {
                Intent chooserIntent = Intent.createChooser(intent, mResendIntentButton != null ?
                        mResendIntentButton.getText() : getString(R.string.open));
                if (needPrivilege) {
                    // TODO: 4/2/22 Support sending activity result back to the original app
                    ActivityManagerCompat.startActivity(chooserIntent, mUserHandle);
                } else {
                    mIntentLauncher.launch(chooserIntent);
                }
            } else { // Launch a fixed component
                if (needPrivilege) {
                    // TODO: 4/2/22 Support sending activity result back to the original app
                    ActivityManagerCompat.startActivity(intent, mUserHandle);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mIntentLauncher.launch(intent);
                }
            }
        } catch (Throwable th) {
            Log.e(TAG, th);
            UIUtils.displayLongToast(R.string.error_with_details, th.getClass().getName() + ": " + th.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_activity_interceptor_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_copy_as_default) {
            copyIntentDetails();
            return true;
        } else if (id == R.id.action_copy_as_command) {
            copyIntentAsCommand();
            return true;
        } else if (id == R.id.action_paste) {
            pasteIntentDetails();
            return true;
        } else if (id == R.id.action_shortcut) {
            try {
                ActionBar actionBar = getSupportActionBar();
                CharSequence shortcutName = null;
                if (actionBar != null) {
                    shortcutName = actionBar.getSubtitle();
                }
                if (shortcutName == null) {
                    shortcutName = Objects.requireNonNull(getTitle());
                }
                Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground));
                Intent intent = new Intent(mMutableIntent);
                // Add necessary extras
                intent.putExtra(EXTRA_AUTH, AuthManager.getKey());
                intent.putExtra(EXTRA_TRIGGER_ON_START, true);
                intent.putExtra(EXTRA_ACTION, intent.getAction());
                if (mUseRoot) {
                    intent.putExtra(EXTRA_ROOT, true);
                }
                if (mUserHandle != UserHandleHidden.myUserId()) {
                    intent.putExtra(EXTRA_USER_HANDLE, mUserHandle);
                }
                if (mRequestedComponent != null) {
                    intent.putExtra(EXTRA_PACKAGE_NAME, mRequestedComponent.getPackageName());
                    intent.putExtra(EXTRA_CLASS_NAME, mRequestedComponent.getClassName());
                }
                intent.setClass(getApplicationContext(), ActivityInterceptor.class);
                InterceptorShortcutInfo shortcutInfo = new InterceptorShortcutInfo(intent);
                shortcutInfo.setName(shortcutName);
                shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(icon));
                CreateShortcutDialogFragment dialog = CreateShortcutDialogFragment.getInstance(shortcutInfo);
                dialog.show(getSupportFragmentManager(), CreateShortcutDialogFragment.TAG);
            } catch (Throwable th) {
                Log.e(TAG, th);
                UIUtils.displayLongToast(R.string.error_with_details, th.getClass().getName() + ": " + th.getMessage());
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAreTextWatchersActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Inhibit new activity animation when resetting intent details
        overridePendingTransition(0, 0);
        mAreTextWatchersActive = true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResetIntentButton != null) {
            outState.putBoolean(INTENT_EDITED, mResetIntentButton.getVisibility() == View.VISIBLE);
        }
        if (mHistory != null) {
            mHistory.saveHistory();
        }
    }

    @Nullable
    private static String getUri(@Nullable Intent src) {
        try {
            return (src != null) ? IntentCompat.toUri(src, Intent.URI_INTENT_SCHEME) : null;
        } catch (BadParcelableException e) {
            // TODO: 4/2/22 Add support for invalid classes. This could be done in the following way:
            //  1. Upon detecting a BPE (and the class name), ask the user to select the source application
            //  2. Load the source application via the DexClassLoader
            //  3. Use Class.forName() to load the class and it's class loader to recognize the Parcelable
            //  The other option is to skip the problematic classes.
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private Intent cloneIntent(@Nullable String intentUri) {
        if (intentUri == null) return null;
        try {
            Intent clone;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                clone = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME | Intent.URI_ANDROID_APP_SCHEME
                        | Intent.URI_ALLOW_UNSAFE);
            } else clone = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
            // Restore extras that are lost in the intent to string conversion
            if (mAdditionalExtras != null) {
                clone.putExtras(mAdditionalExtras);
            }
            return clone;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NonNull
    private List<String> getAllFlags() {
        List<String> allFlags = new ArrayList<>();
        for (int i = 0; i < INTENT_FLAG_TO_STRING.size(); ++i) {
            allFlags.add(INTENT_FLAG_TO_STRING.valueAt(i));
        }
        return allFlags;
    }

    private int getFlagIndex(String flagStr) {
        for (int i = 0; i < INTENT_FLAG_TO_STRING.size(); ++i) {
            if (INTENT_FLAG_TO_STRING.valueAt(i).equals(flagStr)) return i;
        }
        return -1;
    }

    private static class CategoriesRecyclerViewAdapter extends RecyclerView.Adapter<CategoriesRecyclerViewAdapter.ViewHolder> {
        private final List<String> mCategories = new ArrayList<>();
        private final ActivityInterceptor mActivity;

        public CategoriesRecyclerViewAdapter(ActivityInterceptor activity) {
            mActivity = activity;
        }

        public void setDefaultList(@Nullable Collection<String> categories) {
            AdapterUtils.notifyDataSetChanged(this, mCategories, categories);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String category = mCategories.get(position);
            holder.title.setText(category);
            holder.title.setTextIsSelectable(true);
            holder.actionIcon.setOnClickListener(v -> {
                if (mActivity.mMutableIntent != null) {
                    mActivity.mMutableIntent.removeCategory(category);
                    setDefaultList(mActivity.mMutableIntent.getCategories());
                    mActivity.showTextViewIntentData(null);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mCategories.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            MaterialButton actionIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                actionIcon = itemView.findViewById(R.id.item_action);
            }
        }
    }

    private static class FlagsRecyclerViewAdapter extends RecyclerView.Adapter<FlagsRecyclerViewAdapter.ViewHolder> {
        private final List<String> mFlags = new ArrayList<>();
        private final ActivityInterceptor mActivity;

        public FlagsRecyclerViewAdapter(ActivityInterceptor activity) {
            mActivity = activity;
        }

        public void setDefaultList(@Nullable Collection<String> flags) {
            AdapterUtils.notifyDataSetChanged(this, mFlags, flags);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String flagName = mFlags.get(position);
            holder.title.setText(flagName);
            holder.title.setTextIsSelectable(true);
            holder.actionIcon.setOnClickListener(v -> {
                int i = INTENT_FLAG_TO_STRING.indexOfValue(flagName);
                if (i >= 0 && mActivity.mMutableIntent != null) {
                    IntentCompat.removeFlags(mActivity.mMutableIntent, INTENT_FLAG_TO_STRING.keyAt(i));
                    setDefaultList(mActivity.getFlags());
                    mActivity.showTextViewIntentData(null);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mFlags.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            MaterialButton actionIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                actionIcon = itemView.findViewById(R.id.item_action);
            }
        }
    }

    private static class ExtrasRecyclerViewAdapter extends RecyclerView.Adapter<ExtrasRecyclerViewAdapter.ViewHolder> {
        private final List<Pair<String, Object>> mExtras = new ArrayList<>();
        private final ActivityInterceptor mActivity;

        public ExtrasRecyclerViewAdapter(ActivityInterceptor activity) {
            mActivity = activity;
        }

        public void setDefaultList(@Nullable List<Pair<String, Object>> extras) {
            AdapterUtils.notifyDataSetChanged(this, mExtras, extras);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Pair<String, Object> extraItem = mExtras.get(position);
            holder.title.setText(extraItem.first);
            holder.title.setTextIsSelectable(true);
            holder.subtitle.setText(extraItem.second.toString());
            holder.subtitle.setTextIsSelectable(true);
            holder.actionIcon.setOnClickListener(v -> {
                if (mActivity.mMutableIntent != null) {
                    mActivity.mMutableIntent.removeExtra(extraItem.first);
                    mActivity.showTextViewIntentData(null);
                    mExtras.remove(position);
                    notifyItemRemoved(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mExtras.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            ImageView icon;
            MaterialButton actionIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
                actionIcon = itemView.findViewById(R.id.item_open);
                actionIcon.setIconResource(R.drawable.ic_trash_can);
                icon = itemView.findViewById(R.id.item_icon);
                icon.setVisibility(View.GONE);
            }
        }
    }

    private static class MatchingActivitiesRecyclerViewAdapter extends RecyclerView.Adapter<MatchingActivitiesRecyclerViewAdapter.ViewHolder> {
        private final List<ResolveInfo> mMatchingActivities = new ArrayList<>();
        private final PackageManager mPm;
        private final ActivityInterceptor mActivity;

        public MatchingActivitiesRecyclerViewAdapter(ActivityInterceptor activity) {
            mActivity = activity;
            mPm = activity.getPackageManager();
        }

        public void setDefaultList(@Nullable List<ResolveInfo> matchingActivities) {
            AdapterUtils.notifyDataSetChanged(this, mMatchingActivities, matchingActivities);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ResolveInfo resolveInfo = mMatchingActivities.get(position);
            ActivityInfo info = resolveInfo.activityInfo;
            holder.title.setText(info.loadLabel(mPm));
            String activityName = info.name;
            String name = info.packageName + "\n" + activityName;
            holder.subtitle.setText(name);
            holder.subtitle.setTextIsSelectable(true);
            String tag = info.packageName + "_" + activityName;
            holder.icon.setTag(tag);
            ImageLoader.getInstance().displayImage(tag, info, holder.icon);
            holder.actionIcon.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity.mMutableIntent);
                intent.setClassName(info.packageName, activityName);
                IntentCompat.removeFlags(intent, Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                mActivity.launchIntent(intent, false);
            });
        }

        @Override
        public int getItemCount() {
            return mMatchingActivities.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            ImageView icon;
            MaterialButton actionIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
                actionIcon = itemView.findViewById(R.id.item_open);
                icon = itemView.findViewById(R.id.item_icon);
            }
        }
    }
}