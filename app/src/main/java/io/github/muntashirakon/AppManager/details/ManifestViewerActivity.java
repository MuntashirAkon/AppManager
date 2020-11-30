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

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import net.dongliu.apk.parser.ApkParser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ManifestViewerActivity extends BaseActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    private static final Pattern QUOTATIONS = Pattern.compile("\"([^\"]*)\"", Pattern.MULTILINE);
    private static final Pattern MANIFEST_TAGS = Pattern.compile
            ("(</?(manifest|application|compatible-screens|instrumentation|permission" +
                            "(-group|-tree)?|supports-(gl-texture|screens)|uses-(configuration|" +
                            "feature|permission(-sdk-23)?|sdk)|activity(-alias)?|meta-data|service|" +
                            "receiver|provider|uses-library|intent-filter|layout|eat-comment|" +
                            "grant-uri-permissions|path-permission|action|category|data|protected-" +
                            "broadcast|overlay|library|original-package|restrict-update|" +
                            "adopt-permissions|feature-group|key-sets|package|package-verifier|" +
                            "attribution|queries|supports-input|uses-permission-sdk-m|uses-split|" +
                            "profileable)\\b|/?>)",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static String code;
    private LinearProgressIndicator mProgressIndicator;
    private boolean isWrapped = true;  // Wrap by default
    private AppCompatEditText container;
    private SpannableString formattedContent;
    private String archiveFilePath;
    private String packageName;
    private ApkFile apkFile;
    private final ActivityResultLauncher<String> exportManifest = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream == null) throw new IOException();
                    outputStream.write(code.getBytes());
                    outputStream.flush();
                    Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        final Intent intent = getIntent();
        final Uri packageUri = intent.getData();
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageUri == null && packageName == null) {
            showErrorAndFinish();
            return;
        }
        final PackageManager pm = getApplicationContext().getPackageManager();
        if (packageUri != null) {
            new Thread(() -> {
                PackageInfo packageInfo = null;
                if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
                    try {
                        int key = ApkFile.createInstance(packageUri, intent.getType());
                        apkFile = ApkFile.getInstance(key);
                        archiveFilePath = apkFile.getBaseEntry().getCachedFile().getAbsolutePath();
                    } catch (IOException | ApkFile.ApkFileException e) {
                        Log.e("Manifest", "Error: ", e);
                        runOnUiThread(this::showErrorAndFinish);
                        return;
                    }
                } else archiveFilePath = packageUri.getPath();
                if (archiveFilePath != null)
                    packageInfo = pm.getPackageArchiveInfo(archiveFilePath, 0);
                if (packageInfo != null) {
                    packageName = packageInfo.packageName;
                    final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    applicationInfo.publicSourceDir = archiveFilePath;
                    applicationInfo.sourceDir = archiveFilePath;
                    runOnUiThread(() -> setTitle(applicationInfo.loadLabel(pm)));
                } // else Could be a split apk
                runOnUiThread(this::setWrapped);
            }).start();
        } else {
            try {
                ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
                archiveFilePath = applicationInfo.publicSourceDir;
                setTitle(applicationInfo.loadLabel(pm));
                setWrapped();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("Manifest", "Error: ", e);
                showErrorAndFinish();
            }
        }
    }

    @UiThread
    private void showErrorAndFinish() {
        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        IOUtils.closeQuietly(apkFile);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
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
            String fileName = packageName + "_AndroidManifest.xml";
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
        mProgressIndicator.show();
        final int tagColor = ContextCompat.getColor(this, R.color.pink);
        final int attrValueColor = ContextCompat.getColor(this, R.color.ocean_blue);
        new Thread(() -> {
            if (formattedContent == null) {
                try {
                    getManifest();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (code == null) {
                    runOnUiThread(this::showErrorAndFinish);
                    return;
                }
                formattedContent = new SpannableString(code);
                Matcher matcher = MANIFEST_TAGS.matcher(code);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(tagColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(QUOTATIONS);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(attrValueColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            runOnUiThread(() -> {
                container.setText(formattedContent);
                mProgressIndicator.hide();
            });
        }).start();
    }

    private void getManifest() throws IOException {
        if (archiveFilePath != null) {
            ApkParser apkParser = new ApkParser(archiveFilePath);
            apkParser.setPreferredLocale(Locale.getDefault());
            code = Utils.getProperXml(apkParser.getManifestXml());
        }
    }

}
