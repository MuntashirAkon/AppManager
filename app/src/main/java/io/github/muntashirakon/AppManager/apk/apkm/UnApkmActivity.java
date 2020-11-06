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

package io.github.muntashirakon.AppManager.apk.apkm;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class UnApkmActivity extends AppCompatActivity {
    private InputStream inputStream;
    private AlertDialog dialog;
    private ActivityResultLauncher<String> exportManifest = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                new UnApkmThread(uri).start();
            });

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        // Check if action is matched
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            finish();
            return;
        }
        // Read Uri
        Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }
        dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setCancelable(false)
                .setView(getLayoutInflater().inflate(R.layout.dialog_progress, null))
                .create();
        // Open input stream
        try {
            String fileName = IOUtils.getFileName(getContentResolver(), uri);
            if (fileName != null) dialog.setTitle(fileName);
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) finish();
            exportManifest.launch(fileName != null ? IOUtils.trimExtension(fileName) + ".apks" : null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.conversion_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
        }
        super.onDestroy();
    }

    class UnApkmThread extends Thread {
        Uri uri;

        UnApkmThread(Uri uri) {
            this.uri = uri;
        }

        @Override
        public void run() {
            runOnUiThread(() -> dialog.show());
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) throw new IOException();
                UnApkm.decryptFile(inputStream, outputStream);
                runOnUiThread(() -> {
                    Toast.makeText(UnApkmActivity.this, R.string.done, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(UnApkmActivity.this, R.string.conversion_failed, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }
    }
}
