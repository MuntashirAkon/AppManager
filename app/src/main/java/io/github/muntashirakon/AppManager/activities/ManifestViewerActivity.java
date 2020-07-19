package io.github.muntashirakon.AppManager.activities;

// NOTE: Commented lines were taken from View2ManifestActivity.java

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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

import com.google.android.material.progressindicator.ProgressIndicator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.xmlapkparser.AXMLPrinter;

public class ManifestViewerActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private static final String MIME_XML = "text/xml";
    private static final int RESULT_CODE_EXPORT = 849;

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
    private String filePath;
    private String packageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_any_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        filePath = null;
        String applicationLabel = null;
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
        setWrapped();
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
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
            case R.id.action_wrap: setWrapped(); return true;
            case R.id.action_save:
                String fileName = packageName +  "_AndroidManifest.xml";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_XML);
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
            Objects.requireNonNull(outputStream).write(code.getBytes());
            outputStream.flush();
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.saving_failed, Toast.LENGTH_SHORT).show();
        }
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
                code = io.github.muntashirakon.AppManager.utils.Utils.getProperXml(AXMLPrinter.getManifestXMLFromAPK(filePath, "AndroidManifest.xml"));
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
//            if (mActivity.get() != null) mActivity.get().showProgressIndicator(true);
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
//                mActivity.get().showProgressIndicator(false);
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
