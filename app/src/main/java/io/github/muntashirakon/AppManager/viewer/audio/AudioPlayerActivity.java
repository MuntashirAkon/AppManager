// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class AudioPlayerActivity extends BaseActivity {
    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_audio_player);
        List<Uri> uriList = IntentCompat.getDataUris(getIntent());
        if (uriList == null) {
            finish();
            return;
        }
        AudioPlayerDialogFragment fragment = AudioPlayerDialogFragment.getInstance(uriList.toArray(new Uri[0]), true);
        fragment.show(getSupportFragmentManager(), AudioPlayerDialogFragment.TAG);
    }
}
