// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.HashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;

public class FmActivity extends BaseActivity {
    public static final Set<String> SUPPORTED_EDITOR_EXTENSIONS = new HashSet<String>() {{
        add("cmd");
        add("conf");
        add("css");
        add("csv");
        add("java");
        add("kt");
        add("htm");
        add("html");
        add("js");
        add("json");
        add("log");
        add("lua");
        add("m3u");
        add("properties");
        add("prop");
        add("proto");
        add("py");
        add("sh");
        add("smali");
        add("tokens");
        add("txt");
        add("tsv");
        add("xhtml");
        add("xml");
        add("version");
    }};

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        Uri uri = getIntent().getData();
        if (savedInstanceState == null) {
            Fragment fragment = FmFragment.getNewInstance(uri != null ? uri : Uri.fromFile(Environment.getExternalStorageDirectory()));
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_layout, fragment, FmFragment.TAG)
                    .commit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) {
            Intent intent2 = new Intent(this, FmActivity.class);
            intent2.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
            intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent2);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
