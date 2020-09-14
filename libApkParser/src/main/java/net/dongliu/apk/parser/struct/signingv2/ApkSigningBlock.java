package net.dongliu.apk.parser.struct.signingv2;

import java.util.List;

/**
 * For read apk signing block
 *
 * @see <a href="https://source.android.com/security/apksigning/v2">apksigning v2 scheme</a>
 */
public class ApkSigningBlock {
    public static final int SIGNING_V2_ID = 0x7109871a;

    public static final String MAGIC = "APK Sig Block 42";

    private List<SignerBlock> signerBlocks;

    public ApkSigningBlock(List<SignerBlock> signerBlocks) {
        this.signerBlocks = signerBlocks;
    }

    public List<SignerBlock> getSignerBlocks() {
        return signerBlocks;
    }
}
