package net.dongliu.apk.parser;

import net.dongliu.apk.parser.bean.*;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.*;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.resource.Densities;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.signingv2.ApkSigningBlock;
import net.dongliu.apk.parser.struct.signingv2.SignerBlock;
import net.dongliu.apk.parser.struct.zip.EOCD;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Unsigned;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import static java.lang.System.arraycopy;

/**
 * Common Apk Parser methods.
 * This Class is not thread-safe.
 *
 * @author Liu Dong
 */
public abstract class AbstractApkFile implements Closeable {
    private DexClass[] dexClasses;

    private boolean resourceTableParsed;
    private ResourceTable resourceTable;
    private Set<Locale> locales;

    private boolean manifestParsed;
    private String manifestXml;
    private ApkMeta apkMeta;
    private List<IconPath> iconPaths;

    private List<ApkSigner> apkSigners;
    private List<ApkV2Signer> apkV2Signers;

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
     * @throws IOException
     */
    public Set<Locale> getLocales() throws IOException {
        parseResourceTable();
        return this.locales;
    }

    /**
     * Get the apk's certificate meta. If have multi signer, return the certificate the first signer used.
     *
     * @deprecated use {{@link #getApkSingers()}} instead
     */
    @Deprecated
    public List<CertificateMeta> getCertificateMetaList() throws IOException, CertificateException {
        if (apkSigners == null) {
            parseCertificates();
        }
        if (apkSigners.isEmpty()) {
            throw new ParserException("ApkFile certificate not found");
        }
        return apkSigners.get(0).getCertificateMetas();
    }

    /**
     * Get the apk's all certificates.
     * For each entry, the key is certificate file path in apk file, the value is the certificates info of the certificate file.
     *
     * @deprecated use {{@link #getApkSingers()}} instead
     */
    @Deprecated
    public Map<String, List<CertificateMeta>> getAllCertificateMetas() throws IOException, CertificateException {
        List<ApkSigner> apkSigners = getApkSingers();
        Map<String, List<CertificateMeta>> map = new LinkedHashMap<>();
        for (ApkSigner apkSigner : apkSigners) {
            map.put(apkSigner.getPath(), apkSigner.getCertificateMetas());
        }
        return map;
    }

    /**
     * Get the apk's all cert file info, of apk v1 signing.
     * If cert faile not exist, return empty list.
     */
    public List<ApkSigner> getApkSingers() throws IOException, CertificateException {
        if (apkSigners == null) {
            parseCertificates();
        }
        return this.apkSigners;
    }

    private void parseCertificates() throws IOException, CertificateException {
        this.apkSigners = new ArrayList<>();
        for (CertificateFile file : getAllCertificateData()) {
            CertificateParser parser = CertificateParser.getInstance(file.getData());
            List<CertificateMeta> certificateMetas = parser.parse();
            apkSigners.add(new ApkSigner(file.getPath(), certificateMetas));
        }
    }

    /**
     * Get the apk's all signer in apk sign block, using apk singing v2 scheme.
     * If apk v2 signing block not exists, return empty list.
     */
    public List<ApkV2Signer> getApkV2Singers() throws IOException, CertificateException {
        if (apkV2Signers == null) {
            parseApkSigningBlock();
        }
        return this.apkV2Signers;
    }

    private void parseApkSigningBlock() throws IOException, CertificateException {
        List<ApkV2Signer> list = new ArrayList<>();
        ByteBuffer apkSignBlockBuf = findApkSignBlock();
        if (apkSignBlockBuf != null) {
            ApkSignBlockParser parser = new ApkSignBlockParser(apkSignBlockBuf);
            ApkSigningBlock apkSigningBlock = parser.parse();
            for (SignerBlock signerBlock : apkSigningBlock.getSignerBlocks()) {
                List<X509Certificate> certificates = signerBlock.getCertificates();
                List<CertificateMeta> certificateMetas = CertificateMetas.from(certificates);
                ApkV2Signer apkV2Signer = new ApkV2Signer(certificateMetas);
                list.add(apkV2Signer);
            }
        }
        this.apkV2Signers = list;
    }


    protected abstract List<CertificateFile> getAllCertificateData() throws IOException;

    protected static class CertificateFile {
        private String path;
        private byte[] data;

        public CertificateFile(String path, byte[] data) {
            this.path = path;
            this.data = data;
        }

        public String getPath() {
            return path;
        }

        public byte[] getData() {
            return data;
        }
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
     * @throws IOException
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
     * get class infos form dex file. currently only class name
     */
    public DexClass[] getDexClasses() throws IOException {
        if (this.dexClasses == null) {
            parseDexFiles();
        }
        return this.dexClasses;
    }

    private DexClass[] mergeDexClasses(DexClass[] first, DexClass[] second) {
        DexClass[] result = new DexClass[first.length + second.length];
        arraycopy(first, 0, result, 0, first.length);
        arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private DexClass[] parseDexFile(String path) throws IOException {
        byte[] data = getFileData(path);
        if (data == null) {
            String msg = String.format("Dex file %s not found", path);
            throw new ParserException(msg);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        DexParser dexParser = new DexParser(buffer);
        return dexParser.parse();
    }

    private void parseDexFiles() throws IOException {
        this.dexClasses = parseDexFile(AndroidConstants.DEX_FILE);
        for (int i = 2; i < 1000; i++) {
            String path = String.format(AndroidConstants.DEX_ADDITIONAL, i);
            try {
                DexClass[] classes = parseDexFile(path);
                this.dexClasses = mergeDexClasses(this.dexClasses, classes);
            } catch (ParserException e) {
                break;
            }
        }
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

    /**
     * Check apk sign. This method only use apk v1 scheme verifier
     *
     * @deprecated using google official ApkVerifier of apksig lib instead.
     */
    @Deprecated
    public abstract ApkSignStatus verifyApk() throws IOException;

    @Override
    public void close() throws IOException {
        this.apkSigners = null;
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

    /**
     * Create ApkSignBlockParser for this apk file.
     *
     * @return null if do not have sign block
     */
    protected ByteBuffer findApkSignBlock() throws IOException {
        ByteBuffer buffer = fileData().order(ByteOrder.LITTLE_ENDIAN);
        int len = buffer.limit();

        // first find zip end of central directory entry
        if (len < 22) {
            // should not happen
            throw new RuntimeException("Not zip file");
        }
        int maxEOCDSize = 1024 * 100;
        EOCD eocd = null;
        for (int i = len - 22; i > Math.max(0, len - maxEOCDSize); i--) {
            int v = buffer.getInt(i);
            if (v == EOCD.SIGNATURE) {
                Buffers.position(buffer, i + 4);
                eocd = new EOCD();
                eocd.setDiskNum(Buffers.readUShort(buffer));
                eocd.setCdStartDisk(Buffers.readUShort(buffer));
                eocd.setCdRecordNum(Buffers.readUShort(buffer));
                eocd.setTotalCDRecordNum(Buffers.readUShort(buffer));
                eocd.setCdSize(Buffers.readUInt(buffer));
                eocd.setCdStart(Buffers.readUInt(buffer));
                eocd.setCommentLen(Buffers.readUShort(buffer));
            }
        }

        if (eocd == null) {
            return null;
        }

        int magicStrLen = 16;
        long cdStart = eocd.getCdStart();
        // find apk sign block
        Buffers.position(buffer, cdStart - magicStrLen);
        String magic = Buffers.readAsciiString(buffer, magicStrLen);
        if (!magic.equals(ApkSigningBlock.MAGIC)) {
            return null;
        }
        Buffers.position(buffer, cdStart - 24);
        int blockSize = Unsigned.ensureUInt(buffer.getLong());
        Buffers.position(buffer, cdStart - blockSize - 8);
        long size2 = Unsigned.ensureULong(buffer.getLong());
        if (blockSize != size2) {
            return null;
        }
        // now at the start of signing block
        return Buffers.sliceAndSkip(buffer, blockSize - magicStrLen);
    }

}
