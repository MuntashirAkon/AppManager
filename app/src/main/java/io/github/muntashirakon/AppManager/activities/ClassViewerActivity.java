package io.github.muntashirakon.AppManager.activities;

// NOTE: Patterns here are taken from https://github.com/billthefarmer/editor
// Some of them might be slightly modified

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            ("\\b(j?bool(ean)?|(u|j)?(byte|char|double|float|int(eger)?|" +
                    "long|short))\\b", Pattern.MULTILINE);

    public final static Pattern CC_COMMENT = Pattern.compile
            ("//.*$|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/",
                    Pattern.MULTILINE);

    public final static Pattern CLASS = Pattern.compile
            ("\\b[A-Z][A-Za-z0-9_]+\\b", Pattern.MULTILINE);

    private String classDump;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_IS_WRAPPED, false))
            setContentView(R.layout.activity_any_viewer_wrapped);
        else
            setContentView(R.layout.activity_any_viewer);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
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
        TextView textView = findViewById(R.id.any_view);
        Matcher matcher;
        // Take precautions
        classDump = classDump.trim().replaceAll("<", "&lt;")
                .replaceAll(">", "&#62;")
                .replaceAll(" ", "&nbsp;")
                .replaceAll("\n", "<br/>");

        int darkOrange = getResources().getColor(R.color.dark_orange);
        matcher = TYPES.matcher(highlightText(CLASS.matcher(classDump), getResources().getColor(R.color.ocean_blue)));
        matcher = KEYWORDS.matcher(highlightText(matcher, darkOrange));
        matcher = CC_COMMENT.matcher(highlightText(matcher, darkOrange));
        textView.setText(Html.fromHtml(highlightText(matcher, Color.GREEN)));
    }

    private String highlightText(Matcher matcher, int color) {
        String textFormat = "<font color='#%06X'>%s</font>";
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String formattedText = String.format(textFormat, (0xFFFFFF & color), matcher.group());
            matcher.appendReplacement(sb, formattedText);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_any_viewer_actions, menu);
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
