// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.annotation.UserIdInt;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class RulesTypeSelectionDialogFragment extends DialogFragment {
    public static final String TAG = "RulesTypeSelectionDialogFragment";
    public static final String ARG_MODE = "ARG_MODE";  // int
    public static final String ARG_URI = "ARG_URI";  // Uri
    public static final String ARG_PKG = "ARG_PKG";  // Package Names or null (for all)
    public static final String ARG_USERS = "ARG_USERS";  // Integer array of user handles

    @IntDef(value = {
            MODE_IMPORT,
            MODE_EXPORT
    })
    public @interface Mode {
    }

    public static final int MODE_IMPORT = 1;
    public static final int MODE_EXPORT = 2;

    public static final RuleType[] RULE_TYPES = new RuleType[]{
            RuleType.ACTIVITY,
            RuleType.SERVICE,
            RuleType.RECEIVER,
            RuleType.PROVIDER,
            RuleType.APP_OP,
            RuleType.PERMISSION,
    };

    private FragmentActivity mActivity;
    @Nullable
    private Uri mUri;
    private List<String> mPackages = null;
    private HashSet<RuleType> mSelectedTypes;
    @UserIdInt
    private int[] mUserIds;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mActivity = requireActivity();
        Bundle args = requireArguments();
        @Mode int mode = args.getInt(ARG_MODE, MODE_EXPORT);
        mPackages = args.getStringArrayList(ARG_PKG);
        mUri = BundleCompat.getParcelable(args, ARG_URI, Uri.class);
        mUserIds = args.getIntArray(ARG_USERS);
        if (mUserIds == null) mUserIds = new int[]{UserHandleHidden.myUserId()};
        if (mUri == null) return super.onCreateDialog(savedInstanceState);
        List<Integer> ruleIndexes = new ArrayList<>();
        for (int i = 0; i < RULE_TYPES.length; ++i) {
            ruleIndexes.add(i);
        }
        mSelectedTypes = new HashSet<>(RULE_TYPES.length);
        return new SearchableMultiChoiceDialogBuilder<>(mActivity, ruleIndexes, R.array.rule_types)
                .setTitle(mode == MODE_IMPORT ? R.string.import_options : R.string.export_options)
                .addSelections(ruleIndexes)
                .setPositiveButton(mode == MODE_IMPORT ? R.string.pref_import : R.string.pref_export,
                        (dialog1, which, selections) -> {
                            for (int i : selections) {
                                mSelectedTypes.add(RULE_TYPES[i]);
                            }
                            Log.d("TestImportExport", "Types: %s\nURI: %s", mSelectedTypes, mUri);
                            if (mActivity instanceof SettingsActivity) {
                                ((SettingsActivity) mActivity).progressIndicator.show();
                            }
                            if (mode == MODE_IMPORT) {
                                handleImport();
                            } else handleExport();
                        })
                .setNegativeButton(getResources().getString(R.string.cancel), null)
                .create();
    }

    private void handleExport() {
        if (mUri == null) {
            return;
        }
        WeakReference<FragmentActivity> activityRef = new WeakReference<>(mActivity);
        ThreadUtils.postOnBackgroundThread(() -> {
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("rules_exporter");
            wakeLock.acquire();
            try {
                RulesExporter exporter = new RulesExporter(new ArrayList<>(mSelectedTypes), mPackages, mUserIds);
                exporter.saveRules(mUri);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.the_export_was_successful));
            } catch (IOException e) {
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.export_failed));
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
            hideProgressBar(activityRef);
        });
    }

    private void handleImport() {
        if (mUri == null) {
            return;
        }
        WeakReference<FragmentActivity> activityRef = new WeakReference<>(mActivity);
        ThreadUtils.postOnBackgroundThread(() -> {
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("rules_exporter");
            wakeLock.acquire();
            try (RulesImporter importer = new RulesImporter(new ArrayList<>(mSelectedTypes), mUserIds)) {
                importer.addRulesFromUri(mUri);
                if (mPackages != null) importer.setPackagesToImport(mPackages);
                importer.applyRules(true);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.the_import_was_successful));
            } catch (IOException e) {
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.import_failed));
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
            hideProgressBar(activityRef);
        });
    }

    private void hideProgressBar(@NonNull WeakReference<FragmentActivity> activityRef) {
        if (activityRef.get() instanceof SettingsActivity) {
            ThreadUtils.postOnMainThread(() -> {
                FragmentActivity activity = activityRef.get();
                if (activity != null) {
                    ((SettingsActivity) activity).progressIndicator.hide();
                }
            });
        }
    }
}
