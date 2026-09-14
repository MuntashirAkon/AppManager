// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.List;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class AudioPlayerActivity extends PerProcessActivity {
    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        showAudioDialog(false);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showAudioDialog(true);
    }

    private void showAudioDialog(boolean updateExisting) {
        List<Uri> uriList = IntentCompat.getDataUris(getIntent());
        Uri[] uris = uriList == null ? new Uri[0] : uriList.toArray(new Uri[0]);
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment existing = fragmentManager.findFragmentByTag(AudioPlayerDialogFragment.TAG);
        if (existing instanceof AudioPlayerDialogFragment) {
            AudioPlayerDialogFragment dialog = (AudioPlayerDialogFragment) existing;
            if (dialog.isDialogShowing()) {
                if (updateExisting && uris.length > 0) {
                    dialog.updatePlaylist(uris);
                }
                return;
            }
            fragmentManager.beginTransaction().remove(dialog).commitNowAllowingStateLoss();
        }
        AudioPlayerDialogFragment dialog = AudioPlayerDialogFragment.getInstance(uris, true);
        dialog.show(fragmentManager, AudioPlayerDialogFragment.TAG);
    }
}
