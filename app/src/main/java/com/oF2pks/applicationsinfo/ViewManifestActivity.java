package com.oF2pks.applicationsinfo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.oF2pks.xmlapkparser.AXMLPrinter;

import java.lang.ref.WeakReference;
import java.net.URLEncoder;

/**
 * Activity that shows AndroidManifest.xml of any apps. Application package name must be passed as extra.
 * To correctly display returned xml file, we show it in a {@link WebView}. The way we do that
 * is not very natural, but it's the simplest way to do that.
 * So, asynchronously, we get raw xml string and save it to a file. Then we ask to WebView to display this file,
 * by this way, WebView auto detect xml and display it nicely.
 * File do not need to be kept. We delete it to keep used memory as low as possible, but anyway, each time
 * we will show application's manifest, the same file will be used, so used memory will not grow.
 */
public class ViewManifestActivity extends Activity {

    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private WebView mWebView;
    private ProgressDialog mProgressDialog;

    private static String code;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        mWebView = new WebView(this);
        setContentView(mWebView);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getString(R.string.loading));

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);

        String filePath = null, applicationLabel = null;
        try {
            filePath = getPackageManager().getPackageInfo(packageName, 0).applicationInfo.sourceDir;
            applicationLabel = getPackageManager().getApplicationInfo(packageName, 0).loadLabel(getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
            finish();
        }

        setTitle("\u2707" + applicationLabel);
        new AsyncManifestLoader(ViewManifestActivity.this).execute(filePath);
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
        if (Build.VERSION.SDK_INT >18) mWebView.loadData(code,"text/xml","UTF-8");
        else mWebView.loadData(URLEncoder.encode(code).replaceAll("\\+"," "),"text/plain","UTF-8");
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
        private WeakReference<ViewManifestActivity> mActivity = null;
        private AsyncManifestLoader (ViewManifestActivity pActivity) {
            link(pActivity);
        }

        private void link (ViewManifestActivity pActivity) {
            mActivity = new WeakReference<ViewManifestActivity>(pActivity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(mActivity.get() != null) mActivity.get().showProgressBar(true);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            String filePath = strings[0];
            code = com.oF2pks.applicationsinfo.utils.Utils.getProperXml(AXMLPrinter.getManifestXMLFromAPK(filePath, "AndroidManifest.xml"));
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
}
