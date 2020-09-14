package net.dongliu.apk.parser.struct;

/**
 * android system file.
 *
 * @author dongiu
 */
public class AndroidConstants {

    public static final String RESOURCE_FILE = "resources.arsc";

    public static final String MANIFEST_FILE = "AndroidManifest.xml";

    public static final String DEX_FILE = "classes.dex";

    public static final String DEX_ADDITIONAL = "classes%d.dex";

    public static final String RES_PREFIX = "res/";

    public static final String ASSETS_PREFIX = "assets/";

    public static final String LIB_PREFIX = "lib/";

    public static final String META_PREFIX = "META-INF/";

    public static final String ARCH_ARMEABI = "";
    /**
     * the binary xml file used system attr id.
     */
    public static final int ATTR_ID_START = 0x01010000;

    /**
     * start offset for system android.R.style
     */
    public static final int SYS_STYLE_ID_START = 0x01030000;

    /**
     * end offset for system android.R.style
     */
    public static final int SYS_STYLE_ID_END = 0x01031000;


}
