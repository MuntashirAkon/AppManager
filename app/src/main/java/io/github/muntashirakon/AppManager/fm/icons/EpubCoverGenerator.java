// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.compat.xml.Xml;

final class EpubCoverGenerator {
    @Nullable
    public static Bitmap generateFromFile(@NonNull File file) {
        try(ZipFile zipFile = new ZipFile(file)) {
            String opfFile = getOpfFileLocation(zipFile, zipFile.getEntry("META-INF/container.xml"));
            if (opfFile == null) {
                return null;
            }
            String coverId = getCoverId(zipFile, zipFile.getEntry(opfFile));
            if (coverId == null) {
                return null;
            }
            String coverImage = getCover(zipFile, zipFile.getEntry(opfFile), coverId);
            if (coverImage == null) {
                return null;
            }
            String parent = new File(opfFile).getParent();
            String coverImageLocation;
            if (parent != null) {
                coverImageLocation = parent + File.separator + coverImage;
            } else coverImageLocation = coverImage;
            ZipEntry coverEntry = zipFile.getEntry(coverImageLocation);
            if (coverEntry != null) {
                return BitmapFactory.decodeStream(zipFile.getInputStream(coverEntry));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    private static String getOpfFileLocation(@NonNull ZipFile zipFile, @Nullable ZipEntry zipEntry) {
        if (zipEntry == null) {
            return null;
        }
        try {
            XmlPullParser parser = Xml.newFastPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name());
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "container");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("rootfile".equals(tagName)) {
                        return parser.getAttributeValue(null, "full-path");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }

    @Nullable
    private static String getCoverId(@NonNull ZipFile zipFile, @Nullable ZipEntry zipEntry) {
        if (zipEntry == null) {
            return null;
        }
        try {
            XmlPullParser parser = Xml.newFastPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name());
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "package");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("meta".equals(tagName) && "cover".equals(parser.getAttributeValue(null, "name"))) {
                        return parser.getAttributeValue(null, "content");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }

    @Nullable
    private static String getCover(@NonNull ZipFile zipFile, @Nullable ZipEntry zipEntry, @NonNull String coverId) {
        if (zipEntry == null) {
            return null;
        }
        try {
            XmlPullParser parser = Xml.newFastPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(zipFile.getInputStream(zipEntry), StandardCharsets.UTF_8.name());
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "package");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    String id = parser.getAttributeValue(null, "id");
                    if ("item".equals(tagName) && id == null) {
                        event = parser.next();
                        continue;
                    }
                    String properties = parser.getAttributeValue(null, "properties");
                    if (coverId.equals(id)) {
                        return parser.getAttributeValue(null, "href");
                    } else if ("cover-image".equals(properties)) {
                        return parser.getAttributeValue(null, "href");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }
}
