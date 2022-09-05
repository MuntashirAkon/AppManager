// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ManifestViewerActivity extends BaseActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    private LinearProgressIndicator mProgressIndicator;
    private boolean isWrapped = false;  // Do not wrap by default
    private AppCompatEditText container;
    private CharSequence formattedContent;
    private ManifestViewerViewModel mModel;
    private final ActivityResultLauncher<String> exportManifest = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/xml"),
            uri -> {
                if (uri == null || formattedContent == null) {
                    // Back button pressed.
                    return;
                }
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream == null) throw new IOException();
                    outputStream.write(formattedContent.toString().getBytes());
                    outputStream.flush();
                    Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("WrongConstant")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mModel = new ViewModelProvider(this).get(ManifestViewerViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        final Intent intent = getIntent();
        final Uri packageUri = IntentCompat.getDataUri(intent);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageUri == null && packageName == null) {
            showErrorAndFinish();
            return;
        }
        mModel.setTagColor(ContextCompat.getColor(this, R.color.pink));
        mModel.setAttrValueColor(ContextCompat.getColor(this, R.color.ocean_blue));
        mModel.getManifestContent().observe(this, manifest -> {
            formattedContent = manifest;
            setWrapped();
        });
        mModel.loadApkFile(packageUri, intent.getType(), packageName);
    }

    @UiThread
    private void showErrorAndFinish() {
        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
        menu.findItem(R.id.action_java_smali_toggle).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_wrap) {
            setWrapped();
        } else if (id == R.id.action_save) {
            String fileName = mModel.getPackageName() + "_AndroidManifest.xml";
            exportManifest.launch(fileName);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void setWrapped() {
        if (container != null) container.setVisibility(View.GONE);
        if (isWrapped) container = findViewById(R.id.any_view_wrapped);
        else container = findViewById(R.id.any_view);
        container.setVisibility(View.VISIBLE);
        container.setKeyListener(null);
        container.setTextColor(ContextCompat.getColor(this, R.color.dark_orange));
        displayContent();
        isWrapped = !isWrapped;
    }

    private void displayContent() {
        container.setText(formattedContent);
        mProgressIndicator.hide();
    }
}
