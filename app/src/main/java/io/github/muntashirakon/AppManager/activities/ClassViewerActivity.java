package io.github.muntashirakon.AppManager.activities;

// NOTE: Patterns here are taken from https://github.com/billthefarmer/editor
// Some of them might be slightly modified

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;

public class ClassViewerActivity extends AppCompatActivity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_CLASS_NAME = "class_name";
    public static final String EXTRA_CLASS_DUMP = "class_dump";

    public static final String EXTRA_IS_WRAPPED = "is_wrapped";

    public final static Pattern KEYWORDS = Pattern.compile
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

    public final static Pattern TYPES = Pattern.compile
            ("\\b(j?bool(ean)?|[uj]?(byte|char|double|float|int(eger)?|" +
                    "long|short))\\b", Pattern.MULTILINE);

    public final static Pattern CC_COMMENT = Pattern.compile
            ("//.*$|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/",
                    Pattern.MULTILINE);

    public final static Pattern CLASS = Pattern.compile
            ("\\b[A-Z][A-Za-z0-9_]+\\b", Pattern.MULTILINE);

    private String classDump;
    private ProgressIndicator mProgressIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_IS_WRAPPED, false))
            setContentView(R.layout.activity_any_viewer_wrapped);
        else setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));

        mProgressIndicator = findViewById(R.id.progress_linear);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            CharSequence appName = getIntent().getCharSequenceExtra(ClassViewerActivity.EXTRA_APP_NAME);
            String className = getIntent().getStringExtra(ClassViewerActivity.EXTRA_CLASS_NAME);
            if (className != null) {
                String barName = className.substring(className.lastIndexOf(".") + 1);
                actionBar.setSubtitle(barName);
            }
            if (appName != null) actionBar.setTitle(appName);
            else actionBar.setTitle(R.string.class_viewer);
        }

        classDump = getIntent().getStringExtra(ClassViewerActivity.EXTRA_CLASS_DUMP);
        displayContent();
    }

    private void displayContent() {
        mProgressIndicator.show();
        final TextView textView = findViewById(R.id.any_view);
        final int typeClassColor = ContextCompat.getColor(this, R.color.ocean_blue);
        final int keywordsColor = ContextCompat.getColor(this, R.color.dark_orange);
        final int commentColor = Color.GREEN;
        new Thread(() -> {
            SpannableString spannableString = new SpannableString(classDump);
            Matcher matcher = TYPES.matcher(classDump);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                        matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            matcher.usePattern(CLASS);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(typeClassColor), matcher.start(),
                        matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            matcher.usePattern(KEYWORDS);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(keywordsColor), matcher.start(),
                        matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            matcher.usePattern(CC_COMMENT);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(commentColor), matcher.start(),
                        matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            runOnUiThread(() -> {
                textView.setText(spannableString);
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
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_wrap) {
            getIntent().putExtra(EXTRA_IS_WRAPPED, !getIntent().getBooleanExtra(EXTRA_IS_WRAPPED, false));
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
