// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlParser;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FileUtils;

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

    private String code;
    private LinearProgressIndicator mProgressIndicator;
    private boolean isWrapped = false;  // Do not wrap by default
    private AppCompatEditText container;
    private SpannableString formattedContent;
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
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        final Intent intent = getIntent();
        final Uri packageUri = IntentCompat.getDataUri(intent);
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageUri == null && packageName == null) {
            showErrorAndFinish();
            return;
        }
        final PackageManager pm = getApplicationContext().getPackageManager();
        new Thread(() -> {
            if (packageUri != null) {
                try {
                    int key = ApkFile.createInstance(packageUri, intent.getType());
                    apkFile = ApkFile.getInstance(key);
                    runOnUiThread(this::setWrapped);
                } catch (ApkFile.ApkFileException e) {
                    Log.e("Manifest", "Error: ", e);
                    runOnUiThread(this::showErrorAndFinish);
                    return;
                }
                String archiveFilePath;
                try {
                    archiveFilePath = apkFile.getBaseEntry().getRealCachedFile().getAbsolutePath();
                } catch (IOException | RemoteException e) {
                    return;
                }
                PackageInfo packageInfo = pm.getPackageArchiveInfo(archiveFilePath, 0);
                if (packageInfo != null) {
                    packageName = packageInfo.packageName;
                    final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    applicationInfo.publicSourceDir = archiveFilePath;
                    applicationInfo.sourceDir = archiveFilePath;
                    runOnUiThread(() -> setTitle(applicationInfo.loadLabel(pm)));
                } // else Could be a split apk
            } else {
                try {
                    ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
                    int key = ApkFile.createInstance(applicationInfo);
                    apkFile = ApkFile.getInstance(key);
                    runOnUiThread(() -> {
                        setTitle(applicationInfo.loadLabel(pm));
                        setWrapped();
                    });
                } catch (PackageManager.NameNotFoundException | ApkFile.ApkFileException e) {
                    Log.e("Manifest", "Error: ", e);
                    runOnUiThread(this::showErrorAndFinish);
                }
            }
        }).start();

    }

    @UiThread
    private void showErrorAndFinish() {
        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        FileUtils.closeQuietly(apkFile);
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
                } catch (AndroidBinXmlParser.XmlParserException e) {
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

    private void getManifest() throws AndroidBinXmlParser.XmlParserException {
        if (apkFile != null) {
            ByteBuffer byteBuffer = apkFile.getBaseEntry().manifest;
            // Reset properties
            byteBuffer.position(0);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            code = AndroidBinXmlDecoder.decode(byteBuffer, true);
        }
    }
}
