package com.majeur.applicationsinfo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.majeur.applicationsinfo.view.LargeCodeView;
import com.majeur.xmlapkparser.AXMLPrinter;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
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

public class ViewManifestActivity extends Activity {

    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private LargeCodeView mLargeCodeView;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        mLargeCodeView = new LargeCodeView(this);
        setContentView(mLargeCodeView);

        mProgressDialog = new ProgressDialog(this);
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

        setTitle(applicationLabel);
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

    private class AsyncManifestLoader extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(String... strings) {
            String filePath = strings[0];

            return getProperXml(AXMLPrinter.getManifestXMLFromAPK(filePath));
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mLargeCodeView.setContent(s);
            mProgressDialog.dismiss();
        }
    }
}
