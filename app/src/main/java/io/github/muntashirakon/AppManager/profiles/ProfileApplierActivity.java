// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;

public class ProfileApplierActivity extends BaseActivity {
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_STATE = "state";

    @StringDef({ST_SIMPLE, ST_ADVANCED})
    public @interface ShortcutType {
    }

    public static final String ST_SIMPLE = "simple";
    public static final String ST_ADVANCED = "advanced";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull String profileName,
                                           @ShortcutType @Nullable String shortcutType, @Nullable String state) {
        Intent intent = new Intent(context, ProfileApplierActivity.class);
        intent.putExtra(EXTRA_PROFILE_NAME, profileName);
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
                intent.putExtra(EXTRA_STATE, ProfileMetaManager.STATE_ON);
            }
        }
        return intent;
    }

    private final Queue<Intent> mQueue = new LinkedList<>();

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        synchronized (mQueue) {
            mQueue.add(getIntent());
        }
        next();
    }

    @Override
    protected void onNewIntent(Intent intent) {
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
        @ShortcutType String shortcutType = getIntent().getStringExtra(EXTRA_SHORTCUT_TYPE);
        String profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        String profileState = getIntent().getStringExtra(EXTRA_STATE);
        if (shortcutType == null || profileName == null) {
            // Invalid shortcut
            return;
        }
        handleShortcut(shortcutType, profileName, profileState);
    }

    private void handleShortcut(@NonNull @ShortcutType String shortcutType, @NonNull String profileName, @Nullable String state) {
        switch (shortcutType) {
            case ST_SIMPLE:
                Intent intent = new Intent(this, ProfileApplierService.class);
                intent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                // There must be a state
                intent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, Objects.requireNonNull(state));
                ContextCompat.startForegroundService(this, intent);
                next();
                break;
            case ST_ADVANCED:
                final String[] statesL = new String[]{
                        getString(R.string.on),
                        getString(R.string.off)
                };
                @ProfileMetaManager.ProfileState final List<String> states = Arrays.asList(ProfileMetaManager.STATE_ON, ProfileMetaManager.STATE_OFF);
                new SearchableSingleChoiceDialogBuilder<>(this, states, statesL)
                        .setTitle(R.string.profile_state)
                        .setSelection(state)
                        .setPositiveButton(R.string.ok, (dialog, which, selectedState) -> {
                            Intent aIntent = new Intent(this, ProfileApplierService.class);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_NAME, profileName);
                            aIntent.putExtra(ProfileApplierService.EXTRA_PROFILE_STATE, selectedState);
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


}
