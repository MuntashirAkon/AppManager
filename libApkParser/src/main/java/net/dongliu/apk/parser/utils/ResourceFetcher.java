package net.dongliu.apk.parser.utils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * fetch dependency resource file from android source
 *
 * @author Liu Dong dongliu@live.cn
 */
public class ResourceFetcher {

    // from https://android.googlesource.com/platform/frameworks/base/+/master/core/res/res/values/public.xml
    private void fetchSystemAttrIds()
            throws IOException, SAXException, ParserConfigurationException {
        String url = "https://android.googlesource.com/platform/frameworks/base/+/master/core/res/res/values/public.xml";
        String html = getUrl(url);
        String xml = retrieveCode(html);

        if (xml != null) {
            parseAttributeXml(xml);
        }
    }

    private void parseAttributeXml(String xml)
            throws IOException, ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        final List<Pair<Integer, String>> attrIds = new ArrayList<>();
        DefaultHandler dh = new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) throws SAXException {
                if (!qName.equals("public")) {
                    return;
                }
                String type = attributes.getValue("type");
                if (type == null) {
                    return;
                }
                if (type.equals("attr")) {
                    //attr ids.
                    String idStr = attributes.getValue("id");
                    if (idStr == null) {
                        return;
                    }
                    String name = attributes.getValue("name");
                    if (idStr.startsWith("0x")) {
                        idStr = idStr.substring(2);
                    }
                    int id = Integer.parseInt(idStr, 16);
                    attrIds.add(new Pair<>(id, name));
                }
            }
        };
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), dh);
        for (Pair<Integer, String> pair : attrIds) {
            System.out.println(String.format("%s=%d", pair.getRight(), pair.getLeft()));
        }
    }


    // the android system r style.
    // see http://developer.android.com/reference/android/R.style.html
    // from https://android.googlesource.com/platform/frameworks/base/+/master/api/current.txt r.style section

    private void fetchSystemStyle() throws IOException {
        String url = "https://android.googlesource.com/platform/frameworks/base/+/master/api/current.txt";
        String html = getUrl(url);
        String code = retrieveCode(html);
        if (code == null) {
            System.err.println("code area not found");
            return;
        }
        int begin = code.indexOf("R.style");
        int end = code.indexOf("}", begin);
        String styleCode = code.substring(begin, end);
        String[] lines = styleCode.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("field public static final")) {
                line = Strings.substringBefore(line, ";").replace("deprecated ", "")
                        .substring("field public static final int ".length()).replace("_", ".");
                System.out.println(line);
            }
        }
    }

    private String getUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(10000);
            byte[] bytes = Inputs.readAllAndClose(conn.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private String retrieveCode(String html) {
        Matcher matcher = Pattern.compile("<ol class=\"prettyprint\">(.*?)</ol>").matcher(html);
        if (matcher.find()) {
            String codeHtml = matcher.group(1);
            return codeHtml.replace("</li>", "\n").replaceAll("<[^>]+>", "").replace("&lt;", "<")
                    .replace("&quot;", "\"").replace("&gt;", ">");
        } else {
            return null;
        }
    }

    public static void main(String[] args)
            throws ParserConfigurationException, SAXException, IOException {
        ResourceFetcher fetcher = new ResourceFetcher();
        fetcher.fetchSystemAttrIds();
        //fetcher.fetchSystemStyle();
    }
}
