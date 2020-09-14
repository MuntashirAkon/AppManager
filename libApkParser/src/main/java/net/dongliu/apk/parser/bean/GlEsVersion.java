package net.dongliu.apk.parser.bean;

/**
 * the glEsVersion apk used.
 *
 * @author dongliu
 */
public class GlEsVersion {
    private final int major;
    private final int minor;
    private final boolean required;

    public GlEsVersion(int major, int minor, boolean required) {
        this.major = major;
        this.minor = minor;
        this.required = required;
    }


    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public String toString() {
        return this.major + "." + this.minor;
    }

}
