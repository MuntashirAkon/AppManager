// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.io.BlockReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.logs.Log;

public class ManifestParser {
    public static final String TAG = ManifestParser.class.getSimpleName();

    // manifest
    private static final String TAG_MANIFEST = "manifest";
    private static final String ATTR_MANIFEST_PACKAGE = "package";
    // manifest -> application
    private static final String TAG_APPLICATION = "application";
    // manifest -> application -> activity|activity-alias|service|receiver|provider
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_RECEIVER = "receiver";
    private static final String TAG_PROVIDER = "provider";
    private static final String ATTR_NAME = "name"; // android:name
    // manifest -> application -> (component) -> intent-filter
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String ATTR_PRIORITY = "priority"; // android:priority
    // manifest -> application -> (component) -> intent-filter -> action|category|data
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_DATA = "data";

    private final @NonNull ByteBuffer mManifestBytes;
    private String mPackageName;

    public ManifestParser(@NonNull byte[] manifestBytes) {
        this(ByteBuffer.wrap(manifestBytes));
    }

    public ManifestParser(@NonNull ByteBuffer manifestBytes) {
        mManifestBytes = manifestBytes;
    }

    public List<ManifestComponent> parseComponents() throws IOException {
        try (BlockReader reader = new BlockReader(mManifestBytes.array())) {
            ResXmlDocument xmlBlock = new ResXmlDocument();
            xmlBlock.readBytes(reader);
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            // manifest
            if (!TAG_MANIFEST.equals(resManifestElement.getName())) {
                throw new IOException("\"manifest\" tag not found.");
            }
            String packageName = getAttributeValue(resManifestElement, ATTR_MANIFEST_PACKAGE);
            if (packageName == null) {
                throw new IOException("\"manifest\" does not have required attribute \"package\".");
            }
            mPackageName = packageName;
            // manifest -> application
            ResXmlElement resApplicationElement = null;
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements(TAG_APPLICATION);
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next();
            }
            if (resXmlElementIt.hasNext()) {
                throw new IOException("\"manifest\" has duplicate \"application\" tags.");
            }
            if (resApplicationElement == null) {
                Log.i(TAG, "package %s does not have \"application\" tag.", mPackageName);
                return Collections.emptyList();
            }
            // manifest -> application -> component
            List<ManifestComponent> componentIfList = new ArrayList<>(resApplicationElement.getElementsCount());
            String tagName;
            resXmlElementIt = resApplicationElement.getElements();
            while (resXmlElementIt.hasNext()) {
                ResXmlElement elem = resXmlElementIt.next();
                tagName = elem.getName();
                if (tagName != null) {
                    switch (tagName) {
                        case TAG_ACTIVITY:
                        case TAG_ACTIVITY_ALIAS:
                        case TAG_SERVICE:
                        case TAG_RECEIVER:
                        case TAG_PROVIDER:
                            componentIfList.add(parseComponentInfo(elem));
                            break;
                    }
                }
            }
            return componentIfList;
        }
    }

    @NonNull
    private ManifestComponent parseComponentInfo(@NonNull ResXmlElement componentElement) throws IOException {
        String componentName = getAttributeValue(componentElement, ATTR_NAME);
        if (componentName == null) {
            throw new IOException("\"" + componentElement.getName() + "\" does not have  required attribute \"android:name\".");
        }
        ManifestComponent componentIf = new ManifestComponent(new ComponentName(mPackageName, componentName));
        // manifest -> application -> component -> intent-filter
        Iterator<ResXmlElement> resXmlElementIt = componentElement.getElements(TAG_INTENT_FILTER);
        while (resXmlElementIt.hasNext()) {
            ResXmlElement elem = resXmlElementIt.next();
            componentIf.intentFilters.add(parseIntentFilter(elem));
        }
        return componentIf;
    }

    @NonNull
    private ManifestIntentFilter parseIntentFilter(@NonNull ResXmlElement intentFilterElement) {
        ManifestIntentFilter intentFilter = new ManifestIntentFilter();
        String priorityString = getAttributeValue(intentFilterElement, ATTR_PRIORITY);
        if (priorityString != null) {
            intentFilter.priority = Integer.parseInt(priorityString);
        }
        // manifest -> application -> component -> intent-filter -> action|category|data
        Iterator<ResXmlElement> resXmlElementIt = intentFilterElement.getElements();
        String tagName;
        while (resXmlElementIt.hasNext()) {
            ResXmlElement elem = resXmlElementIt.next();
            tagName = elem.getName();
            if (tagName != null) {
                switch (tagName) {
                    case TAG_ACTION:
                        intentFilter.actions.add(Objects.requireNonNull(getAttributeValue(elem, ATTR_NAME)));
                        break;
                    case TAG_CATEGORY:
                        intentFilter.categories.add(Objects.requireNonNull(getAttributeValue(elem, ATTR_NAME)));
                        break;
                    case TAG_DATA:
                        intentFilter.data.add(parseData(elem));
                        break;
                }
            }
        }
        return intentFilter;
    }

    @NonNull
    private ManifestIntentFilter.ManifestData parseData(@NonNull ResXmlElement dataElement) {
        ManifestIntentFilter.ManifestData data = new ManifestIntentFilter.ManifestData();
        ResXmlAttribute attribute;
        for (int i = 0; i < dataElement.getAttributeCount(); ++i) {
            attribute = dataElement.getAttributeAt(i);
            if (attribute.equalsName("scheme")) {
                data.scheme = attribute.getValueAsString();
            } else if (attribute.equalsName("host")) {
                data.host = attribute.getValueAsString();
            } else if (attribute.equalsName("port")) {
                data.port = attribute.getValueAsString();
            } else if (attribute.equalsName("path")) {
                data.path = attribute.getValueAsString();
            } else if (attribute.equalsName("pathPrefix")) {
                data.pathPrefix = attribute.getValueAsString();
            } else if (attribute.equalsName("pathSuffix")) {
                data.pathSuffix = attribute.getValueAsString();
            } else if (attribute.equalsName("pathPattern")) {
                data.pathPattern = attribute.getValueAsString();
            } else if (attribute.equalsName("pathAdvancedPattern")) {
                data.pathAdvancedPattern = attribute.getValueAsString();
            } else if (attribute.equalsName("mimeType")) {
                data.mimeType = attribute.getValueAsString();
            } else {
                Log.i(TAG, "Unknown intent-filter > data attribute %s", attribute.getName());
            }
        }
        return data;
    }

    @Nullable
    private String getAttributeValue(@NonNull ResXmlElement element, @NonNull String attrName) {
        ResXmlAttribute attribute;
        for (int i = 0; i < element.getAttributeCount(); ++i) {
            attribute = element.getAttributeAt(i);
            if (attribute.equalsName(attrName)) {
                return attribute.getValueAsString();
            }
        }
        return null;
    }
}
