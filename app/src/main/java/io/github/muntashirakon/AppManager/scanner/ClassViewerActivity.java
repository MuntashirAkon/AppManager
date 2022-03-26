// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

// NOTE: Some patterns here are taken from https://github.com/billthefarmer/editor

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

// Copyright 2015 Google, Inc.
public class ClassViewerActivity extends BaseActivity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_CLASS_NAME = "class_name";
    public static final String EXTRA_CLASS_CONTENT = "class_content";

    private static final Pattern SMALI_KEYWORDS = Pattern.compile(
            "\\b(invoke-(virtual(/range|)|direct|static|interface|super|polymorphic|custom)|" +
                    "move(-(result(-wide|-object|)|exception)|(-wide|-object|)(/16|/from16|))|" +
                    "new-(array|instance)|const(-(string(/jumbo|)|" +
                    "class|wide(/16|/32|/high16|))|/4|/16|/high16|ructor|)|private|public|protected|final|static|" +
                    "(add|sub|cmp|mul|div|rem|and|or|xor|shl|shr|ushr)-(int|float|double|long)(/2addr|/lit16|/lit8|)|" +
                    "(neg|not)-(int|long|float|double)|(int|long|float|double|byte)(-to|)-(int|long|float|double|byte)|" +
                    "fill-array-data|filled-new-array(/range|)|([ais](ge|pu)t|return)(-(object|boolean|byte|char|short|wide|void)|)|" +
                    "check-cast|throw|array-length|goto|if-((ge|le|ne|eq|lt|gt)z?)|monitor-(enter|exit)|synthetic|system)\\b", Pattern.MULTILINE);

    private static final Pattern SMALI_CLASS = Pattern.compile("\\[*(L\\w+/[^;]+;|[ZBCSIJFDV])", Pattern.MULTILINE);

    private static final Pattern SMALI_COMMENT = Pattern.compile("#.*$", Pattern.MULTILINE);

    private static final Pattern SMALI_VALUE = Pattern.compile("((\"(?:\\\\\\\\[^\"]|\\\\\\\\\"|.)*?\")" +
            "|\\b-?(0x[0-9a-f]+|[0-9]+)\\b)", Pattern.MULTILINE);

    private static final Pattern SMALI_LABELS = Pattern.compile("\\b([pv][0-9]+|:(?!L)[\\w]+|->)\\b",
            Pattern.MULTILINE);

    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
            "\\b(abstract|and|arguments|as(sert|sociativity)?|auto|break|" +
                    "case|catch|chan|char|class|con(st|tinue|venience)|continue|" +
                    "de(bugger|fault|fer|in|init)|didset|do(ne)?|dynamic" +
                    "(type)?|el(if|se)|enum|esac|eval|ex(cept|ec|plicit|port|" +
                    "tends|tension|tern)|fallthrough|fi(nal|nally)?|for|" +
                    "friend|from|func(tion)?|get|global|go(to)?|if|" +
                    "im(plements|port)|in(fix|it|line|out|stanceof|terface|" +
                    "ternal)|lazy|left|let|local|map|mut(able|ating)|" +
                    "namespace|native|new|nonmutating|not|" +
                    "operator|optional|or|override|package|pass|postfix|" +
                    "pre(cedence|fix)|print|private|prot(ected|ocol)|public|" +
                    "raise|range|register|required|return|right|select|self|" +
                    "set|signed|sizeof|static|strictfp|struct|subscript|super|" +
                    "switch|synchronized|template|th(en|rows?)|transient|" +
                    "try|type(alias|def|id|name|of)?|un(ion|owned|signed)|" +
                    "using|var|virtual|volatile|weak|wh(ere|ile)|willset|" +
                    "with|yield)\\b", Pattern.MULTILINE);

    private static final Pattern JAVA_TYPES = Pattern.compile
            ("\\b(boolean|byte|char|double|float|int|long|short|void|this)\\b|[()\\[\\]{};]", Pattern.MULTILINE);
    private static final Pattern JAVA_COMMENT = Pattern.compile("//.*$|(?s)/\\*.*?\\*/", Pattern.MULTILINE);
    private static final Pattern JAVA_CLASS = Pattern.compile
            ("\\b[A-Z][A-Za-z0-9_]+\\b", Pattern.MULTILINE);
    private static final Pattern JAVA_VALUE = Pattern.compile("(\"(?:\\\\\\\\[^\"]|\\\\\\\\\"|.)*?\")" +
            "|\\b((?!\\d)-?(0x[\\da-f]+|\\d+)\\.?0?[fl]?|true|false|null)\\b", Pattern.MULTILINE);

    @RequiresApi(Build.VERSION_CODES.O)
    @Nullable
    private SpannableString formattedSmaliContent;
    @Nullable
    private SpannableString formattedJavaContent;
    private boolean isWrapped = true;  // Wrap by default
    private AppCompatEditText container;
    private LinearProgressIndicator mProgressIndicator;
    @RequiresApi(Build.VERSION_CODES.O)
    private Uri uri;
    @RequiresApi(Build.VERSION_CODES.O)
    private Path smaliPath;
    @Deprecated
    private String content;
    private String className;
    private boolean isDisplayingSmali = true;
    private final ActivityResultLauncher<String> saveJavaOrSmali = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    Objects.requireNonNull(outputStream).write(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            isDisplayingSmali ? formattedSmaliContent.toString().getBytes(StandardCharsets.UTF_8) :
                            formattedJavaContent.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            uri = getIntent().getParcelableExtra(EXTRA_URI);
            if (uri == null) {
                finish();
                return;
            }
        } else {
            content = getIntent().getStringExtra(EXTRA_CLASS_CONTENT);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            CharSequence appName = getIntent().getCharSequenceExtra(EXTRA_APP_NAME);
            String barName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                barName = FileUtils.trimExtension(uri.getLastPathSegment());
            } else barName = className;
            actionBar.setSubtitle(barName);
            if (appName != null) actionBar.setTitle(appName);
            else actionBar.setTitle(R.string.class_viewer);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            smaliPath = new Path(this, uri);
        }
        updateUi();
    }

    private void updateUi() {
        if (container != null) container.setVisibility(View.GONE);
        if (isWrapped) container = findViewById(R.id.any_view_wrapped);
        else container = findViewById(R.id.any_view);
        container.setVisibility(View.VISIBLE);
        container.setKeyListener(null);
        container.setTextColor(ContextCompat.getColor(this, R.color.dark_orange));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isDisplayingSmali) {
            displaySmaliContent();
        } else {
            displayJavaContent();
        }
        isWrapped = !isWrapped;
    }

    private void displayJavaContent() {
        mProgressIndicator.show();
        final int typeClassColor = ContextCompat.getColor(this, R.color.ocean_blue);
        final int keywordsColor = ContextCompat.getColor(this, R.color.purple_y);
        final int commentColor = ContextCompat.getColor(this, R.color.textColorSecondary);
        final int valueColor = ContextCompat.getColor(this, R.color.redder_than_you);
        new Thread(() -> {
            if (formattedJavaContent == null) {
                String javaContent;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        javaContent = ScannerUtils.toJavaCode(Objects.requireNonNull(formattedSmaliContent).toString(), -1);
                    } else javaContent = content;
                } catch (Throwable e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                formattedJavaContent = new SpannableString(javaContent);
                Matcher matcher = JAVA_TYPES.matcher(javaContent);
                while (matcher.find()) {
                    formattedJavaContent.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(JAVA_CLASS);
                while (matcher.find()) {
                    formattedJavaContent.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(JAVA_KEYWORDS);
                while (matcher.find()) {
                    formattedJavaContent.setSpan(new ForegroundColorSpan(keywordsColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    formattedJavaContent.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(JAVA_VALUE);
                while (matcher.find()) {
                    formattedJavaContent.setSpan(new ForegroundColorSpan(valueColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(JAVA_COMMENT);
                while (matcher.find()) {
                    formattedJavaContent.setSpan(new ForegroundColorSpan(commentColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    formattedJavaContent.setSpan(new StyleSpan(Typeface.ITALIC), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            runOnUiThread(() -> {
                container.setText(formattedJavaContent);
                mProgressIndicator.hide();
            });
        }).start();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void displaySmaliContent() {
        mProgressIndicator.show();
        final int typeClassColor = ContextCompat.getColor(this, R.color.ocean_blue);
        final int keywordsColor = ContextCompat.getColor(this, R.color.purple_y);
        final int commentColor = ContextCompat.getColor(this, R.color.textColorSecondary);
        final int valueColor = ContextCompat.getColor(this, R.color.redder_than_you);
        final int labelColor = ContextCompat.getColor(this, R.color.green_mountain);
        new Thread(() -> {
            if (formattedSmaliContent == null) {
                String smaliContent;
                try {
                    try (InputStream is = smaliPath.openInputStream()) {
                        smaliContent = FileUtils.getInputStreamContent(is);
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                formattedSmaliContent = new SpannableString(smaliContent);
                Matcher matcher = SMALI_VALUE.matcher(smaliContent);
                while (matcher.find()) {
                    formattedSmaliContent.setSpan(new ForegroundColorSpan(valueColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(SMALI_LABELS);
                while (matcher.find()) {
                    formattedSmaliContent.setSpan(new ForegroundColorSpan(labelColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(SMALI_CLASS);
                while (matcher.find()) {
                    formattedSmaliContent.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(SMALI_KEYWORDS);
                while (matcher.find()) {
                    formattedSmaliContent.setSpan(new ForegroundColorSpan(keywordsColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    formattedSmaliContent.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(SMALI_COMMENT);
                while (matcher.find()) {
                    formattedSmaliContent.setSpan(new ForegroundColorSpan(commentColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    formattedSmaliContent.setSpan(new StyleSpan(Typeface.ITALIC), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            runOnUiThread(() -> {
                container.setText(formattedSmaliContent);
                mProgressIndicator.hide();
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            menu.findItem(R.id.action_java_smali_toggle).setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_wrap) {
            updateUi();
        } else if (id == R.id.action_save) {
            String fileName;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                fileName = FileUtils.trimExtension(uri.getLastPathSegment())
                        + (isDisplayingSmali ? ".smali" : ".java");
            } else fileName = className + ".java";
            saveJavaOrSmali.launch(fileName);
        } else if (id == R.id.action_java_smali_toggle) {
            isDisplayingSmali = !isDisplayingSmali;
            item.setTitle(isDisplayingSmali ? R.string.java : R.string.smali);
            updateUi();
        } else return super.onOptionsItemSelected(item);
        return true;
    }
}
