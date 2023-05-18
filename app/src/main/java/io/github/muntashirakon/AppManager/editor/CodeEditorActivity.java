// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.io.Paths;

public class CodeEditorActivity extends BaseActivity {
    public static final String ALIAS_EDITOR = "io.github.muntashirakon.AppManager.editor.EditorActivity";

    private static final String EXTRA_READ_ONLY = "read_only";

    public static Intent getIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String title, @Nullable String subtitle, boolean readOnly) {
        return new Intent(context, CodeEditorActivity.class)
                .setData(uri)
                .putExtra(EXTRA_READ_ONLY, readOnly)
                .putExtra(Intent.EXTRA_TITLE, title)
                .putExtra(Intent.EXTRA_SUBJECT, subtitle);
    }

    public static Intent getIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String title, @Nullable String subtitle) {
        return new Intent(context, CodeEditorActivity.class)
                .setData(uri)
                .putExtra(Intent.EXTRA_TITLE, title)
                .putExtra(Intent.EXTRA_SUBJECT, subtitle);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_code_editor);
        setSupportActionBar(findViewById(R.id.toolbar));
        LinearProgressIndicator progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
        if (title == null) {
            title = getString(R.string.title_code_editor);
        }
        String subtitle = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        Uri fileUri = IntentCompat.getDataUri(getIntent());
        boolean readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);
        if (subtitle == null) {
            if (fileUri != null) {
                subtitle = Paths.trimPathExtension(fileUri.getLastPathSegment());
            } else {
                subtitle = "Untitled.txt";
            }
        }
        if (fileUri == null) {
            progressIndicator.hide();
        }
        CodeEditorFragment.Options options = new CodeEditorFragment.Options.Builder()
                .setUri(fileUri)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setEnableSharing(false)
                .setJavaSmaliToggle(false)
                .setReadOnly(readOnly)
                .build();
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putParcelable(CodeEditorFragment.ARG_OPTIONS, options);
        fragment.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
