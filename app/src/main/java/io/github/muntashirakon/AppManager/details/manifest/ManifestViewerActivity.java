// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.editor.CodeEditorFragment;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ManifestViewerActivity extends BaseActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    private ManifestViewerViewModel mModel;

    @SuppressLint("WrongConstant")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_code_editor);
        setSupportActionBar(findViewById(R.id.toolbar));
        mModel = new ViewModelProvider(this).get(ManifestViewerViewModel.class);
        LinearProgressIndicator progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        final Intent intent = getIntent();
        final Uri packageUri = IntentCompat.getDataUri(intent);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageUri == null && packageName == null) {
            showErrorAndFinish();
            return;
        }
        final ApkSource apkSource = packageUri != null ? ApkSource.getApkSource(packageUri, intent.getType()) : null;
        mModel.getManifestLiveData().observe(this, manifest -> {
            CodeEditorFragment.Options options = new CodeEditorFragment.Options.Builder()
                    .setTitle(getString(R.string.manifest_viewer))
                    .setSubtitle("AndroidManifest.xml")
                    .setReadOnly(true)
                    .setUri(manifest)
                    .setJavaSmaliToggle(false)
                    .setEnableSharing(true)
                    .build();
            CodeEditorFragment fragment = new CodeEditorFragment();
            Bundle args = new Bundle();
            args.putParcelable(CodeEditorFragment.ARG_OPTIONS, options);
            fragment.setArguments(args);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit();
        });
        mModel.loadApkFile(apkSource, packageName);
    }

    @UiThread
    private void showErrorAndFinish() {
        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
