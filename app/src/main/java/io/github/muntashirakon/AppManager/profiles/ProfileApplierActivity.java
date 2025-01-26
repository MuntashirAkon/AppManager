// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import org.json.JSONException;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.io.Path;

public class ProfileApplierActivity extends BaseActivity {
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    public static final String EXTRA_PROFILE_ID = "prof";
    public static final String EXTRA_STATE = "state";
    private static final String EXTRA_NOTIFY = "notify";

    @StringDef({ST_SIMPLE, ST_ADVANCED})
    public @interface ShortcutType {
    }

    public static final String ST_SIMPLE = "simple";
    public static final String ST_ADVANCED = "advanced";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context,
                                           @NonNull String profileId,
                                           @ShortcutType @Nullable String shortcutType,
                                           @Nullable String state) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        if (shortcutType == null) {
            if (state != null) { // State => It's a simple shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE);
                intent.putExtra(EXTRA_STATE, state);
            } else { // Otherwise it's an advance shortcut
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
            }
        } else {
            intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutType);
            if (state != null) {
                intent.putExtra(EXTRA_STATE, state);
            } else if (shortcutType.equals(ST_SIMPLE)) {
                // Shortcut is set to simple but no state set
                intent.putExtra(EXTRA_STATE, AppsProfile.STATE_ON);
            }
        }
        return intent;
    }

    @NonNull
    public static Intent getAutomationIntent(@NonNull Context context,
                                             @NonNull String profileId,
                                             @Nullable String state) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        if (state != null) { // State => Automatic trigger
            intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE);
            intent.putExtra(EXTRA_STATE, state);
            // Avoid issuing completion notification
            intent.putExtra(EXTRA_NOTIFY, false);
        } else { // Manual trigger
            intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
        }
        return intent;
    }

    @NonNull
    public static Intent getApplierIntent(@NonNull Context context, @NonNull String profileId) {
        // Compatibility: Old shortcuts still store profile name instead of profile ID.
        String realProfileId = ProfileManager.getProfileIdCompat(profileId);
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, realProfileId);
        intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED);
        return intent;
    }

    public static class ProfileApplierInfo {
        public AppsProfile profile;
        public String profileId;
        @ShortcutType
        public String shortcutType;
        @Nullable
        public String state;
        public boolean notify;
    }

    private final Queue<Intent> mQueue = new LinkedList<>();
    private ProfileApplierViewModel mViewModel;

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(ProfileApplierViewModel.class);
        synchronized (mQueue) {
            mQueue.add(getIntent());
        }
        mViewModel.mProfileLiveData.observe(this, this::handleShortcut);
        next();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        synchronized (mQueue) {
            mQueue.add(intent);
        }
        super.onNewIntent(intent);
    }

    private void next() {
        Intent intent;
        synchronized (mQueue) {
            intent = mQueue.poll();
        }
        if (intent == null) {
            finish();
            return;
        }
        @ShortcutType
        String shortcutType = intent.getStringExtra(EXTRA_SHORTCUT_TYPE);
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        String profileState = intent.getStringExtra(EXTRA_STATE);
        boolean notify = intent.getBooleanExtra(EXTRA_NOTIFY, true);
        if (shortcutType == null || profileId == null) {
            // Invalid shortcut
            return;
        }
        ProfileApplierInfo info = new ProfileApplierInfo();
        info.profileId = profileId;
        info.shortcutType = shortcutType;
        info.state = profileState;
        info.notify = notify;
        mViewModel.loadProfile(info);
    }

    private void handleShortcut(@Nullable ProfileApplierInfo info) {
        if (info == null) {
            next();
            return;
        }
        info.state = info.state != null ? info.state : info.profile.state;
        switch (info.shortcutType) {
            case ST_SIMPLE:
                // There must be a state
                Objects.requireNonNull(info.state);
                Intent intent = new Intent(this, ProfileApplierService.class);
                intent.putExtra(ProfileApplierService.EXTRA_QUEUE_ITEM, ProfileQueueItem.fromProfiledApplierInfo(info));
                intent.putExtra(ProfileApplierService.EXTRA_NOTIFY, info.notify);
                ContextCompat.startForegroundService(this, intent);
                next();
                break;
            case ST_ADVANCED:
                final String[] statesL = new String[]{
                        getString(R.string.on),
                        getString(R.string.off)
                };
                @AppsProfile.ProfileState final List<String> states = Arrays.asList(AppsProfile.STATE_ON, AppsProfile.STATE_OFF);
                DialogTitleBuilder titleBuilder = new DialogTitleBuilder(this)
                        .setTitle(getString(R.string.apply_profile, info.profile.name))
                        .setSubtitle(R.string.choose_a_profile_state);
                new SearchableSingleChoiceDialogBuilder<>(this, states, statesL)
                        .setTitle(titleBuilder.build())
                        .setSelection(info.state)
                        .setPositiveButton(R.string.ok, (dialog, which, selectedState) -> {
                            info.state = selectedState;
                            Intent aIntent = new Intent(this, ProfileApplierService.class);
                            aIntent.putExtra(ProfileApplierService.EXTRA_QUEUE_ITEM, ProfileQueueItem.fromProfiledApplierInfo(info));
                            aIntent.putExtra(ProfileApplierService.EXTRA_NOTIFY, info.notify);
                            ContextCompat.startForegroundService(this, aIntent);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener(dialog -> next())
                        .show();
                break;
            default:
                next();
        }
    }

    public static class ProfileApplierViewModel extends AndroidViewModel {
        final MutableLiveData<ProfileApplierInfo> mProfileLiveData = new MutableLiveData<>();

        public ProfileApplierViewModel(@NonNull Application application) {
            super(application);
        }

        public void loadProfile(ProfileApplierInfo info) {
            ThreadUtils.postOnBackgroundThread(() -> {
                Path profilePath = ProfileManager.findProfilePathById(info.profileId);
                try {
                    info.profile = AppsProfile.fromPath(profilePath);
                    mProfileLiveData.postValue(info);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
