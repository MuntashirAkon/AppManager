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

// NOTE: Some patterns here are taken from https://github.com/billthefarmer/editor

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;

public class ClassViewerActivity extends BaseActivity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_CLASS_NAME = "class_name";
    public static final String EXTRA_CLASS_DUMP = "class_dump";

    private static final Pattern KEYWORDS = Pattern.compile
            ("\\b(abstract|and|arguments|as(m|sert|sociativity)?|auto|break|" +
                    "case|catch|chan|char|class|con(st|tinue|venience)|continue|" +
                    "de(bugger|f|fault|fer|in|init|l|lete)|didset|do(ne)?|dynamic" +
                    "(type)?|el(if|se)|enum|esac|eval|ex(cept|ec|plicit|port|" +
                    "tends|tension|tern)|fal(lthrough|se)|fi(nal|nally)?|for|" +
                    "friend|from|func(tion)?|get|global|go(to)?|if|" +
                    "im(plements|port)|in(fix|it|line|out|stanceof|terface|" +
                    "ternal)?|is|lambda|lazy|left|let|local|map|mut(able|ating)|" +
                    "namespace|native|new|nil|none|nonmutating|not|null|" +
                    "operator|optional|or|override|package|pass|postfix|" +
                    "pre(cedence|fix)|print|private|prot(ected|ocol)|public|" +
                    "raise|range|register|required|return|right|select|self|" +
                    "set|signed|sizeof|static|strictfp|struct|subscript|super|" +
                    "switch|synchronized|template|th(en|is|rows?)|transient|" +
                    "true|try|type(alias|def|id|name|of)?|un(ion|owned|signed)|" +
                    "using|var|virtual|void|volatile|weak|wh(ere|ile)|willset|" +
                    "with|yield)\\b", Pattern.MULTILINE);

    private static final Pattern TYPES = Pattern.compile
            ("\\b(j?bool(ean)?|[uj]?(byte|char|double|float|int(eger)?|" +
                    "long|short))\\b", Pattern.MULTILINE);
    private static final Pattern CC_COMMENT = Pattern.compile
            ("//.*$|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/",
                    Pattern.MULTILINE);
    private static final Pattern CLASS = Pattern.compile
            ("\\b[A-Z][A-Za-z0-9_]+\\b", Pattern.MULTILINE);

    private String classDump;
    private SpannableString formattedContent;
    private boolean isWrapped = true;  // Wrap by default
    private AppCompatEditText container;
    private ProgressIndicator mProgressIndicator;
    private String className;
    private ActivityResultLauncher<String> exportManifest = registerForActivityResult(new ActivityResultContracts.CreateDocument(), uri -> {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            Objects.requireNonNull(outputStream).write(classDump.getBytes());
            outputStream.flush();
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            CharSequence appName = getIntent().getCharSequenceExtra(EXTRA_APP_NAME);
            className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
            if (className != null) {
                String barName;
                try {
                    barName = className.substring(className.lastIndexOf(".") + 1);
                } catch (Exception e) {
                    barName = className;
                }
                actionBar.setSubtitle(barName);
            }
            if (appName != null) actionBar.setTitle(appName);
            else actionBar.setTitle(R.string.class_viewer);
        }
        classDump = getIntent().getStringExtra(EXTRA_CLASS_DUMP);
        setWrapped();
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
        final int typeClassColor = ContextCompat.getColor(this, R.color.ocean_blue);
        final int keywordsColor = ContextCompat.getColor(this, R.color.dark_orange);
        final int commentColor = ContextCompat.getColor(this, R.color.textColorSecondary);
        new Thread(() -> {
            if (formattedContent == null) {
                formattedContent = new SpannableString(classDump);
                Matcher matcher = TYPES.matcher(classDump);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(CLASS);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(KEYWORDS);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(keywordsColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                matcher.usePattern(CC_COMMENT);
                while (matcher.find()) {
                    formattedContent.setSpan(new ForegroundColorSpan(commentColor), matcher.start(),
                            matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            runOnUiThread(() -> {
                container.setText(formattedContent);
                mProgressIndicator.hide();
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
            case R.id.action_wrap: setWrapped(); return true;
            case R.id.action_save:
                String fileName = className +  ".java";
                exportManifest.launch(fileName);
        }
        return super.onOptionsItemSelected(item);
    }
}
