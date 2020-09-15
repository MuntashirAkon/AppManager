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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.progressindicator.ProgressIndicator;

import net.dongliu.apk.parser.ApkFile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
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
                            "broadcast|overlay|library|original-package|restrict-update)\\b|/?>)",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static String code;
    private ProgressIndicator mProgressIndicator;
    private boolean isWrapped = true;  // Wrap by default
    private AppCompatEditText container;
    private SpannableString formattedContent;
    private String archiveFilePath;
    private String packageName;
    @Nullable
    private ParcelFileDescriptor fd;
    private ActivityResultLauncher<String> exportManifest = registerForActivityResult(new ActivityResultContracts.CreateDocument(), uri -> {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            Objects.requireNonNull(outputStream).write(code.getBytes());
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
        final Intent intent = getIntent();
        final Uri packageUri = intent.getData();
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageUri == null && packageName == null) {
            showErrorAndFinish();
            return;
        }
        final PackageManager pm = getApplicationContext().getPackageManager();
        if (packageUri != null) {
            PackageInfo packageInfo = null;
            if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
                try {
                    fd = getContentResolver().openFileDescriptor(packageUri, "r");
                    if (fd == null) {
                        throw new FileNotFoundException("FileDescription cannot be null");
                    }
                    archiveFilePath = IOUtils.getFileFromFd(fd).getAbsolutePath();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    showErrorAndFinish();
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
                setTitle(applicationInfo.loadLabel(pm));
            } // else Could be a split apk
            setWrapped();
        } else {
            try {
                setTitle(pm.getApplicationInfo(packageName, 0).loadLabel(pm));
                setWrapped();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
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
        IOUtils.closeSilently(fd);
        super.onDestroy();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_wrap:
                setWrapped();
                return true;
            case R.id.action_save:
                String fileName = packageName + "_AndroidManifest.xml";
                exportManifest.launch(fileName);
        }
        return super.onOptionsItemSelected(item);
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
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
                        finish();
                    });
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
            ApkFile apkFile = new ApkFile(archiveFilePath);
            apkFile.setPreferredLocale(Locale.getDefault());
            code = Utils.getProperXml(apkFile.getManifestXml());
        } else {
            AssetManager mCurAm;
            XmlResourceParser xml;
            Resources mCurResources;
            try {
                // https://stackoverflow.com/questions/35474016/store-and-extract-map-from-android-resource-file
                mCurAm = createPackageContext(packageName, CONTEXT_IGNORE_SECURITY
                        | CONTEXT_INCLUDE_CODE).getAssets();
                mCurResources = new Resources(mCurAm, getResources().getDisplayMetrics(), null);
                xml = mCurAm.openXmlResourceParser("AndroidManifest.xml");
                code = Utils.getProperXml(getXMLText(xml, mCurResources).toString());
            } catch (IOException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    protected static void insertSpaces(StringBuffer sb, int num) {
        if (sb == null)
            return;
        for (int i = 0; i < num; i++)
            sb.append(" ");
    }

    @NonNull
    private static CharSequence getAttribs(@NonNull XmlResourceParser xrp, Resources currentResources) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < xrp.getAttributeCount(); i++) {
            if (xrp.getAttributeName(i).length() != 0)
                sb.append("\n").append(xrp.getAttributeName(i)).append("=\"").append(resolveValue(xrp.getAttributeValue(i), currentResources)).append("\"");
            else
                sb.append("\n").append(xrp.getAttributeType(i)).append(Integer.toHexString(xrp.getAttributeNameResource(i)))
                        .append("=\"").append(resolveValue(xrp.getAttributeValue(i), currentResources)).append("\"");
        }
        return sb;
    }

    private static String resolveValue(String in, Resources r) {
        if (in == null)
            return "null";
        if (!in.startsWith("@"))
            return in;
        int num = Integer.parseInt(in.substring(1));
        try {
            return r.getString(num).replaceAll("&", "&amp;")
                    .replaceAll("\"", "&quot;")//
                    .replaceAll("'", "&apos;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;");
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return in;
        } catch (RuntimeException e) {
            try {
                if (r.getResourceEntryName(num).length() > 0)
                    return r.getResourceTypeName(num) + "/" + r.getResourceEntryName(num);
                else return r.getResourceTypeName(num) + "/" + in;
            } catch (Resources.NotFoundException e2) {
                e2.printStackTrace();
                return in;
            }
        }
    }

    @NonNull
    static CharSequence getXMLText(@NonNull XmlResourceParser xrp, Resources currentResources) {
        StringBuffer sb = new StringBuffer();
        int indent = 0;
        try {
            int eventType = xrp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        indent += 1;
                        sb.append("\n");
                        insertSpaces(sb, indent);
                        sb.append("<").append(xrp.getName()).append(getAttribs(xrp, currentResources)).append(">");
                        break;
                    case XmlPullParser.END_TAG:
                        indent -= 1;
                        sb.append("\n");
                        insertSpaces(sb, indent);
                        sb.append("</").append(xrp.getName()).append(">");
                        break;

                    case XmlPullParser.TEXT:
                        sb.append(xrp.getText());
                        break;

                    case XmlPullParser.CDSECT:
                        sb.append("<!CDATA[").append(xrp.getText()).append("]]>");
                        break;

                    case XmlPullParser.PROCESSING_INSTRUCTION:
                        sb.append("<?").append(xrp.getText()).append("?>");
                        break;

                    case XmlPullParser.COMMENT:
                        sb.append("<!--").append(xrp.getText()).append("-->");
                        break;
                }
                eventType = xrp.nextToken();
            }
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }
        return sb;
    }

}
