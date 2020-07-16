package io.github.muntashirakon.AppManager.activities;

// NOTE: Patterns here are taken from https://github.com/billthefarmer/editor
// Some of them might be slightly modified

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;

public class ClassViewerActivity extends AppCompatActivity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_CLASS_NAME = "class_name";
    public static final String EXTRA_CLASS_DUMP = "class_dump";

    private static final String MIME_JAVA = "text/x-java-source";
    private static final int RESULT_CODE_EXPORT = 849;

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
    private TextView container;
    private ProgressIndicator mProgressIndicator;
    String className;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
            case R.id.action_wrap: setWrapped(); return true;
            case R.id.action_save:
                String fileName = className +  ".java";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_JAVA);
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(intent, RESULT_CODE_EXPORT);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        if (requestCode != RESULT_CODE_EXPORT) return;
        if (data == null) return;
        Uri uri = data.getData();
        if(uri == null) return;
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            Objects.requireNonNull(outputStream).write(classDump.getBytes());
            outputStream.flush();
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
