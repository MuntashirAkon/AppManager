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

package io.github.muntashirakon.AppManager.crypto;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.BaseActivity;

public class OpenPGPCryptoActivity extends BaseActivity {

    private final ActivityResultLauncher<IntentSenderRequest> confirmationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                Intent broadcastIntent = new Intent(OpenPGPCrypto.ACTION_OPEN_PGP_INTERACTION_END);
                sendBroadcast(broadcastIntent);
                finish();
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (getIntent() != null) onNewIntent(getIntent());
        else finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        PendingIntent pi = Objects.requireNonNull(intent.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
        confirmationLauncher.launch(new IntentSenderRequest.Builder(pi).build());
    }
}
