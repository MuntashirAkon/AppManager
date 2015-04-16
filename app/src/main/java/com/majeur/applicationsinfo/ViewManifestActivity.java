package com.majeur.applicationsinfo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.majeur.xmlapkparser.AXMLPrinter;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Activity that shows AndroidManifest.xml of any apps. Application package name must be passed as extra.
 * To correctly display returned xml file, we show it in a {@link android.webkit.WebView}. The way we do that
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

    private String mPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        mWebView = new WebView(this);
        setContentView(mWebView);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getString(R.string.loading));

        mPath = getFilesDir() + "/data.xml";
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

        setTitle(getString(R.string.manifest) + ": " + applicationLabel);
        new AsyncManifestLoader().execute(filePath);
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
        mWebView.loadUrl(Uri.fromFile(new File(mPath)).toString());
    }

    private void handleError() {
        Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        File file = new File(mPath);
        file.delete();
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
    private class AsyncManifestLoader extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar(true);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            String filePath = strings[0];
            String code = getProperXml(AXMLPrinter.getManifestXMLFromAPK(filePath));

            if (code == null) return false;

            try {
                File file = new File(mPath);
                if (!file.exists())
                    file.createNewFile();

                FileOutputStream output = new FileOutputStream(file);
                output.write(code.getBytes());
                output.flush();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        /**
         * Format xml file to correct indentation ...
         */
        private String getProperXml(String dirtyXml) {
            try {
                Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new ByteArrayInputStream(dirtyXml.getBytes("utf-8"))));

                XPath xPath = XPathFactory.newInstance().newXPath();
                NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                        document,
                        XPathConstants.NODESET);

                for (int i = 0; i < nodeList.getLength(); ++i) {
                    Node node = nodeList.item(i);
                    node.getParentNode().removeChild(node);
                }

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                StringWriter stringWriter = new StringWriter();
                StreamResult streamResult = new StreamResult(stringWriter);

                transformer.transform(new DOMSource(document), streamResult);

                return stringWriter.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Do not hide progressDialog here, WebView will hide it when content will be displayed
         */
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result)
                displayContent();
            else
                handleError();
        }
    }
}
