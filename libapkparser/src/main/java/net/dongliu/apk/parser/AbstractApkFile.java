// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.bean.AdaptiveIcon;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.Icon;
import net.dongliu.apk.parser.bean.IconFace;
import net.dongliu.apk.parser.bean.IconPath;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.AdaptiveIconParser;
import net.dongliu.apk.parser.parser.ApkMetaTranslator;
import net.dongliu.apk.parser.parser.BinaryXmlParser;
import net.dongliu.apk.parser.parser.CompositeXmlStreamer;
import net.dongliu.apk.parser.parser.ResourceTableParser;
import net.dongliu.apk.parser.parser.XmlStreamer;
import net.dongliu.apk.parser.parser.XmlTranslator;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceTable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Common Apk Parser methods.
 * This Class is not thread-safe.
 */
// Copyright 2016 Liu Dong
public abstract class AbstractApkFile implements Closeable {
    private boolean resourceTableParsed;
    private ResourceTable resourceTable;
    private Set<Locale> locales;

    private boolean manifestParsed;
    private String manifestXml;
    private ApkMeta apkMeta;
    private List<IconPath> iconPaths;

    private static final Locale DEFAULT_LOCALE = Locale.US;

    /**
     * default use empty locale
     */
    private Locale preferredLocale = DEFAULT_LOCALE;

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public String getManifestXml() throws IOException {
        parseManifest();
        return this.manifestXml;
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public ApkMeta getApkMeta() throws IOException {
        parseManifest();
        return this.apkMeta;
    }

    /**
     * get locales supported from resource file
     *
     * @return decoded AndroidManifest.xml
     */
    public Set<Locale> getLocales() throws IOException {
        parseResourceTable();
        return this.locales;
    }

    private void parseManifest() throws IOException {
        if (manifestParsed) {
            return;
        }
        parseResourceTable();
        XmlTranslator xmlTranslator = new XmlTranslator();
        ApkMetaTranslator apkTranslator = new ApkMetaTranslator(this.resourceTable, this.preferredLocale);
        XmlStreamer xmlStreamer = new CompositeXmlStreamer(xmlTranslator, apkTranslator);

        byte[] data = getFileData(AndroidConstants.MANIFEST_FILE);
        if (data == null) {
            throw new ParserException("Manifest file not found");
        }
        transBinaryXml(data, xmlStreamer);
        this.manifestXml = xmlTranslator.getXml();
        this.apkMeta = apkTranslator.getApkMeta();
        this.iconPaths = apkTranslator.getIconPaths();
        manifestParsed = true;
    }

    /**
     * read file in apk into bytes
     */
    public abstract byte[] getFileData(String path) throws IOException;

    /**
     * return the whole apk file as ByteBuffer
     */
    protected abstract ByteBuffer fileData() throws IOException;

    /**
     * trans binary xml file to text xml file.
     *
     * @param path the xml file path in apk file
     * @return the text. null if file not exists
     */
    public String transBinaryXml(String path) throws IOException {
        byte[] data = getFileData(path);
        if (data == null) {
            return null;
        }
        parseResourceTable();

        XmlTranslator xmlTranslator = new XmlTranslator();
        transBinaryXml(data, xmlTranslator);
        return xmlTranslator.getXml();
    }

    private void transBinaryXml(byte[] data, XmlStreamer xmlStreamer) throws IOException {
        parseResourceTable();

        ByteBuffer buffer = ByteBuffer.wrap(data);
        BinaryXmlParser binaryXmlParser = new BinaryXmlParser(buffer, resourceTable);
        binaryXmlParser.setLocale(preferredLocale);
        binaryXmlParser.setXmlStreamer(xmlStreamer);
        binaryXmlParser.parse();
    }

    /**
     * This method return icons specified in android manifest file, application.
     * The icons could be file icon, color icon, or adaptive icon, etc.
     *
     * @return icon files.
     */
    public List<IconFace> getAllIcons() throws IOException {
        List<IconPath> iconPaths = getIconPaths();
        if (iconPaths.isEmpty()) {
            return Collections.emptyList();
        }
        List<IconFace> iconFaces = new ArrayList<>(iconPaths.size());
        for (IconPath iconPath : iconPaths) {
            String filePath = iconPath.getPath();
            if (filePath.endsWith(".xml")) {
                // adaptive icon?
                byte[] data = getFileData(filePath);
                if (data == null) {
                    continue;
                }
                parseResourceTable();

                AdaptiveIconParser iconParser = new AdaptiveIconParser();
                transBinaryXml(data, iconParser);
                Icon backgroundIcon = null;
                if (iconParser.getBackground() != null) {
                    backgroundIcon = newFileIcon(iconParser.getBackground(), iconPath.getDensity());
                }
                Icon foregroundIcon = null;
                if (iconParser.getForeground() != null) {
                    foregroundIcon = newFileIcon(iconParser.getForeground(), iconPath.getDensity());
                }
                AdaptiveIcon icon = new AdaptiveIcon(foregroundIcon, backgroundIcon);
                iconFaces.add(icon);
            } else {
                Icon icon = newFileIcon(filePath, iconPath.getDensity());
                iconFaces.add(icon);
            }
        }
        return iconFaces;
    }

    @NonNull
    private Icon newFileIcon(String filePath, int density) throws IOException {
        return new Icon(filePath, density, getFileData(filePath));
    }

    /**
     * Get the default apk icon file.
     *
     * @deprecated use {@link #getAllIcons()}
     */
    @Deprecated
    public Icon getIconFile() throws IOException {
        ApkMeta apkMeta = getApkMeta();
        String iconPath = apkMeta.getIcon();
        if (iconPath == null) {
            return null;
        }
        return new Icon(iconPath, Densities.DEFAULT, getFileData(iconPath));
    }

    /**
     * Get all the icon paths, for different densities.
     *
     * @deprecated using {@link #getAllIcons()} instead
     */
    @Deprecated
    public List<IconPath> getIconPaths() throws IOException {
        parseManifest();
        return this.iconPaths;
    }

    /**
     * Get all the icons, for different densities.
     *
     * @deprecated using {@link #getAllIcons()} instead
     */
    @Deprecated
    public List<Icon> getIconFiles() throws IOException {
        List<IconPath> iconPaths = getIconPaths();
        List<Icon> icons = new ArrayList<>(iconPaths.size());
        for (IconPath iconPath : iconPaths) {
            Icon icon = newFileIcon(iconPath.getPath(), iconPath.getDensity());
            icons.add(icon);
        }
        return icons;
    }

    /**
     * parse resource table.
     */
    private void parseResourceTable() throws IOException {
        if (resourceTableParsed) {
            return;
        }
        resourceTableParsed = true;
        byte[] data = getFileData(AndroidConstants.RESOURCE_FILE);
        if (data == null) {
            // if no resource entry has been found, we assume it is not needed by this APK
            this.resourceTable = new ResourceTable();
            this.locales = Collections.emptySet();
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        ResourceTableParser resourceTableParser = new ResourceTableParser(buffer);
        resourceTableParser.parse();
        this.resourceTable = resourceTableParser.getResourceTable();
        this.locales = resourceTableParser.getLocales();
    }

    @Override
    public void close() throws IOException {
        this.resourceTable = null;
        this.iconPaths = null;
    }

    /**
     * The local used to parse apk
     */
    public Locale getPreferredLocale() {
        return preferredLocale;
    }


    /**
     * The locale preferred. Will cause getManifestXml / getApkMeta to return different values.
     * The default value is from os default locale setting.
     */
    public void setPreferredLocale(Locale preferredLocale) {
        if (!Objects.equals(this.preferredLocale, preferredLocale)) {
            this.preferredLocale = preferredLocale;
            this.manifestXml = null;
            this.apkMeta = null;
            this.manifestParsed = false;
        }
    }

}
