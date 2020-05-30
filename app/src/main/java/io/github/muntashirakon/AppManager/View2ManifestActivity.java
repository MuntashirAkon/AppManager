package io.github.muntashirakon.AppManager;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.appcompat.app.AppCompatActivity;

public class View2ManifestActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private WebView mWebView;
    private ProgressDialog mProgressDialog;

    private static String code;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mWebView = new WebView(this);
        setContentView(mWebView);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getString(R.string.loading));

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);

        String applicationLabel = null;
        try {
            applicationLabel = getPackageManager().getApplicationInfo(packageName, 0).loadLabel(getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
            finish();
        }

        setTitle("\u2622 " + applicationLabel);
        new AsyncManifestLoader(View2ManifestActivity.this).execute(packageName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void displayContent() {
        WebSettings settings = mWebView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setUseWideViewPort(true);
        mWebView.setWebChromeClient(new MyWebChromeClient());
        mWebView.loadData(code,"text/xml","UTF-8");
    }

    private void handleError() {
        Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
        finish();
    }

    final class MyWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            if (progress == 100)
                showProgressBar(false);
        }
    }

    private void showProgressBar(boolean show) {
        if (show)
            mProgressDialog.show();
        else
            mProgressDialog.dismiss();
    }

    /**
     * This AsyncTask takes manifest file path as argument
     */
    private static class AsyncManifestLoader extends AsyncTask<String, Integer, Boolean> {
        private WeakReference<View2ManifestActivity> mActivity = null;

        private AsyncManifestLoader(View2ManifestActivity pActivity) {
            link(pActivity);
        }

        private void link(View2ManifestActivity pActivity) {
            mActivity = new WeakReference<View2ManifestActivity>(pActivity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mActivity.get() != null) mActivity.get().showProgressBar(true);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            String packageName = strings[0];
            XmlResourceParser xml = null;
            AssetManager mCurAm = null;
            Resources mCurResources = null;
            try {
                //Log.e("package",packageName);//https://stackoverflow.com/questions/35474016/store-and-extract-map-from-android-resource-file
                mCurAm = mActivity.get().createPackageContext(packageName,
                        CONTEXT_IGNORE_SECURITY | CONTEXT_INCLUDE_CODE).getAssets();
                mCurResources = new Resources(mCurAm, mActivity.get().getResources().getDisplayMetrics(), null);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            try {
                xml = mCurAm.openXmlResourceParser("AndroidManifest.xml");
                //this.mInput.setText("/sdcard/" + getPkgName() + ".txt");
                code = io.github.muntashirakon.AppManager.utils.Utils.getProperXml(getXMLText(xml, mCurResources).toString());
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return (code != null);
        }

        /**
         * Do not hide progressDialog here, WebView will hide it when content will be displayed
         */
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

    protected static void insertSpaces(StringBuffer sb, int num) {
        if (sb == null)
            return;
        for (int i = 0; i < num; i++)
            sb.append(" ");
    }
    private static CharSequence getAttribs(XmlResourceParser xrp, Resources currentResources) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < xrp.getAttributeCount(); i++){
            if (xrp.getAttributeName(i).length()!=0)
                sb.append("\n" + xrp.getAttributeName(i) + "=\""
                        + resolveValue(xrp.getAttributeValue(i), currentResources)
                        + "\"");
            else
                sb.append("\n" + xrp.getAttributeType(i)
                        +Integer.toHexString(xrp.getAttributeNameResource(i)) + "=\""
                        + resolveValue(xrp.getAttributeValue(i), currentResources)
                        + "\"");
        }
        return sb;
    }
    private static String resolveValue(String in, Resources r) {
        if (in == null )
            return "null";
        if (!in.startsWith("@"))
            return in;
        int num = Integer.parseInt(in.substring(1));
        try {
            return r.getString(num).replaceAll("&",  "&amp;")
                    .replaceAll("\"", "&quot;")//
                    .replaceAll("'",  "&apos;")
                    .replaceAll("<",  "&lt;")
                    .replaceAll(">",  "&gt;");
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return in;
        } catch (RuntimeException e) {
            try {
                if (r.getResourceEntryName(num).length()>0)
                    return r.getResourceTypeName(num)+"/"+r.getResourceEntryName(num);
                else return r.getResourceTypeName(num)+"/"+in;
            } catch (Resources.NotFoundException e2){
                e2.printStackTrace();
                return in;
            }
        }
    }

    static CharSequence getXMLText(XmlResourceParser xrp, Resources currentResources) {
        StringBuffer sb = new StringBuffer();
        int indent = 0;
        try {
            int eventType = xrp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                // for sb
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        indent += 1;
                        sb.append("\n");
                        insertSpaces(sb, indent);
                        sb.append("<" + xrp.getName());
                        sb.append(getAttribs(xrp, currentResources));
                        sb.append(">");
                        break;
                    case XmlPullParser.END_TAG:
                        indent -= 1;
                        sb.append("\n");
                        insertSpaces(sb, indent);
                        sb.append("</" + xrp.getName() + ">");
                        break;

                    case XmlPullParser.TEXT:
                        sb.append("" + xrp.getText());
                        break;

                    case XmlPullParser.CDSECT:
                        sb.append("<!CDATA[" + xrp.getText() + "]]>");
                        break;

                    case XmlPullParser.PROCESSING_INSTRUCTION:
                        sb.append("<?" + xrp.getText() + "?>");
                        break;

                    case XmlPullParser.COMMENT:
                        sb.append("<!--" + xrp.getText() + "-->");
                        break;
                }
                eventType = xrp.nextToken();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            //showError("Reading XML", ioe);
        } catch (XmlPullParserException xppe) {
            xppe.printStackTrace();
            //showError("Parsing XML", xppe);
        }
        return sb;
    }
}
