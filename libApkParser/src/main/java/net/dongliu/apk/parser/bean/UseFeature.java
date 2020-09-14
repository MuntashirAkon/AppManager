package net.dongliu.apk.parser.bean;

/**
 * the permission used by apk
 *
 * @author dongliu
 */
public class UseFeature {
    private final String name;
    private final boolean required;

    public UseFeature(String name, boolean required) {
        this.name = name;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
