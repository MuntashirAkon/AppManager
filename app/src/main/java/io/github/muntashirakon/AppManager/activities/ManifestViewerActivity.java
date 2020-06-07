package io.github.muntashirakon.AppManager.activities;

// NOTE: Commented lines were taken from View2ManifestActivity.java

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.xmlapkparser.AXMLPrinter;

public class ManifestViewerActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    /**
     * To use a wrapped layout, instead of the default one
     */
    public static final String EXTRA_IS_WRAPPED = "is_wrapped";

    public final static Pattern QUOTATIONS = Pattern.compile("\"([^\"]*)\"", Pattern.MULTILINE);

    public final static Pattern MANIFEST_TAGS = Pattern.compile
            ("(&lt;/?(manifest|application|compatible-screens|instrumentation|permission" +
                            "(-group|-tree)?|supports-(gl-texture|screens)|uses-(configuration|" +
                            "feature|permission(-sdk-23)?|sdk)|activity(-alias)?|meta-data|service|" +
                            "receiver|provider|uses-library|intent-filter|layout|eat-comment|" +
                            "grant-uri-permissions|path-permission|action|category|data|protected-" +
                            "broadcast|overlay|library|original-package|restrict-update)\\b|/?&#62;)",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static String code;
    private ProgressBar mProgressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_IS_WRAPPED, false))
            setContentView(R.layout.activity_any_viewer_wrapped);
        else
            setContentView(R.layout.activity_any_viewer);

        mProgressBar = findViewById(R.id.progress_horizontal);

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String filePath = null, applicationLabel = null;
        try {
            assert packageName != null;
            filePath = getPackageManager().getPackageInfo(packageName, 0).applicationInfo.sourceDir;
            applicationLabel = getPackageManager().getApplicationInfo(packageName, 0).loadLabel(getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
            finish();
        }

        setTitle("\u2707 " + applicationLabel);
        new AsyncManifestLoader(ManifestViewerActivity.this).execute(filePath);
//        new AsyncManifestLoaderPkg(ManifestViewerActivity.this).execute(packageName);

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

    private void displayContent() {
        final TextView textView = findViewById(R.id.any_view);
        textView.setTextColor(getResources().getColor(R.color.dark_orange));
        Matcher matcher;
        final StringBuffer sb = new StringBuffer();
        String textFormat = "<font color='#%06X'>%s</font>";
        // Take precautions
        code = code.trim().replaceAll("<", "&lt;")
                .replaceAll(">", "&#62;")
                .replaceAll(" ", "&nbsp;")
                .replaceAll("\n", "<br/>");

        int tagColor = getResources().getColor(R.color.pink);
        matcher = MANIFEST_TAGS.matcher(code);
        while (matcher.find()) {
            String formattedText = String.format(textFormat, (0xFFFFFF & tagColor), matcher.group());
            matcher.appendReplacement(sb, formattedText);
        }
        matcher.appendTail(sb);
        matcher = QUOTATIONS.matcher(sb.toString());
        sb.setLength(0);
        textFormat = "<i>" + textFormat + "</i>";
        int attr_value = getResources().getColor(R.color.ocean_blue);
        while (matcher.find()) {
            String formattedText = String.format(textFormat, (0xFFFFFF & attr_value), matcher.group());
            matcher.appendReplacement(sb, formattedText);
        }
        matcher.appendTail(sb);
        final ManifestViewerActivity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Spanned spanned = Html.fromHtml(sb.toString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(spanned);
                        activity.showProgressBar(false);
                    }
                });
            }
        }).start();
    }

    private void handleError() {
        Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
        finish();
    }

    private void showProgressBar(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * This AsyncTask takes filepath file path as argument
     */
    private static class AsyncManifestLoader extends AsyncTask<String, Integer, Boolean> {
        private WeakReference<ManifestViewerActivity> mActivity = null;
        private AsyncManifestLoader (ManifestViewerActivity pActivity) {
            link(pActivity);
        }

        private void link (ManifestViewerActivity pActivity) {
            mActivity = new WeakReference<>(pActivity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(mActivity.get() != null) mActivity.get().showProgressBar(true);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            String filePath = strings[0];
            code = io.github.muntashirakon.AppManager.utils.Utils.getProperXml(AXMLPrinter.getManifestXMLFromAPK(filePath, "AndroidManifest.xml"));
            return (code != null);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (mActivity.get() != null) {
                if (result)
                    mActivity.get().displayContent();
                else
                    mActivity.get().handleError();
            }
        }
    }

//    /**
//     * This AsyncTask takes manifest file path as argument
//     */
//    private static class AsyncManifestLoaderPkg extends AsyncTask<String, Integer, Boolean> {
//        private WeakReference<ManifestViewerActivity> mActivity = null;
//
//        private AsyncManifestLoaderPkg(ManifestViewerActivity pActivity) {
//            link(pActivity);
//        }
//
//        private void link(ManifestViewerActivity pActivity) {
//            mActivity = new WeakReference<>(pActivity);
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            if (mActivity.get() != null) mActivity.get().showProgressBar(true);
//        }
//
//        @Override
//        protected Boolean doInBackground(String... strings) {
//            String packageName = strings[0];
//            XmlResourceParser xml = null;
//            AssetManager mCurAm = null;
//            Resources mCurResources = null;
//            try {
//                //https://stackoverflow.com/questions/35474016/store-and-extract-map-from-android-resource-file
//                mCurAm = mActivity.get().createPackageContext(packageName,
//                        CONTEXT_IGNORE_SECURITY | CONTEXT_INCLUDE_CODE).getAssets();
//                mCurResources = new Resources(mCurAm, mActivity.get().getResources().getDisplayMetrics(), null);
//            } catch (PackageManager.NameNotFoundException e) {
//                e.printStackTrace();
//                return false;
//            }
//            try {
//                xml = mCurAm.openXmlResourceParser("AndroidManifest.xml");
//                //this.mInput.setText("/sdcard/" + getPkgName() + ".txt");
//                code = io.github.muntashirakon.AppManager.utils.Utils.getProperXml(getXMLText(xml, mCurResources).toString());
//            } catch (IOException e) {
//                e.printStackTrace();
//                return false;
//            }
//
//            return (code != null);
//        }
//
//        @Override
//        protected void onPostExecute(Boolean result) {
//            super.onPostExecute(result);
//            if (mActivity.get() != null) {
//                mActivity.get().showProgressBar(false);
//                if (result)
//                    mActivity.get().displayContent();
//                else
//                    mActivity.get().handleError();
//            }
//        }
//    }
//
//    protected static void insertSpaces(StringBuffer sb, int num) {
//        if (sb == null)
//            return;
//        for (int i = 0; i < num; i++)
//            sb.append(" ");
//    }
//
//    private static CharSequence getAttribs(XmlResourceParser xrp, Resources currentResources) {
//        StringBuffer sb = new StringBuffer();
//        for (int i = 0; i < xrp.getAttributeCount(); i++){
//            if (xrp.getAttributeName(i).length()!=0)
//                sb.append("\n" + xrp.getAttributeName(i) + "=\""
//                        + resolveValue(xrp.getAttributeValue(i), currentResources)
//                        + "\"");
//            else
//                sb.append("\n" + xrp.getAttributeType(i)
//                        +Integer.toHexString(xrp.getAttributeNameResource(i)) + "=\""
//                        + resolveValue(xrp.getAttributeValue(i), currentResources)
//                        + "\"");
//        }
//        return sb;
//    }
//
//    private static String resolveValue(String in, Resources r) {
//        if (in == null )
//            return "null";
//        if (!in.startsWith("@"))
//            return in;
//        int num = Integer.parseInt(in.substring(1));
//        try {
//            return r.getString(num).replaceAll("&",  "&amp;")
//                    .replaceAll("\"", "&quot;")//
//                    .replaceAll("'",  "&apos;")
//                    .replaceAll("<",  "&lt;")
//                    .replaceAll(">",  "&gt;");
//        } catch (NumberFormatException e) {
//            e.printStackTrace();
//            return in;
//        } catch (RuntimeException e) {
//            try {
//                if (r.getResourceEntryName(num).length()>0)
//                    return r.getResourceTypeName(num)+"/"+r.getResourceEntryName(num);
//                else return r.getResourceTypeName(num)+"/"+in;
//            } catch (Resources.NotFoundException e2){
//                e2.printStackTrace();
//                return in;
//            }
//        }
//    }
//
//    static CharSequence getXMLText(XmlResourceParser xrp, Resources currentResources) {
//        StringBuffer sb = new StringBuffer();
//        int indent = 0;
//        try {
//            int eventType = xrp.getEventType();
//            while (eventType != XmlPullParser.END_DOCUMENT) {
//                // for sb
//                switch (eventType) {
//                    case XmlPullParser.START_TAG:
//                        indent += 1;
//                        sb.append("\n");
//                        insertSpaces(sb, indent);
//                        sb.append("<" + xrp.getName());
//                        sb.append(getAttribs(xrp, currentResources));
//                        sb.append(">");
//                        break;
//                    case XmlPullParser.END_TAG:
//                        indent -= 1;
//                        sb.append("\n");
//                        insertSpaces(sb, indent);
//                        sb.append("</" + xrp.getName() + ">");
//                        break;
//
//                    case XmlPullParser.TEXT:
//                        sb.append("" + xrp.getText());
//                        break;
//
//                    case XmlPullParser.CDSECT:
//                        sb.append("<!CDATA[" + xrp.getText() + "]]>");
//                        break;
//
//                    case XmlPullParser.PROCESSING_INSTRUCTION:
//                        sb.append("<?" + xrp.getText() + "?>");
//                        break;
//
//                    case XmlPullParser.COMMENT:
//                        sb.append("<!--" + xrp.getText() + "-->");
//                        break;
//                }
//                eventType = xrp.nextToken();
//            }
//        } catch (IOException ioe) {
//            ioe.printStackTrace();
//            //showError("Reading XML", ioe);
//        } catch (XmlPullParserException xppe) {
//            xppe.printStackTrace();
//            //showError("Parsing XML", xppe);
//        }
//        return sb;
//    }

}
