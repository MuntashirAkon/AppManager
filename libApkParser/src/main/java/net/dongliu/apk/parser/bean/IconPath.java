package net.dongliu.apk.parser.bean;

/**
 * Icon path, and density
 */
public class IconPath {
    private String path;
    private int density;

    public IconPath(String path, int density) {
        this.path = path;
        this.density = density;
    }

    /**
     * The icon path in apk file
     */
    public String getPath() {
        return path;
    }

    /**
     * Return the density this icon for. 0 means default icon.
     * see {@link net.dongliu.apk.parser.struct.resource.Densities} for more density values.
     */
    public int getDensity() {
        return density;
    }

    @Override
    public String toString() {
        return "IconPath{" +
                "path='" + path + '\'' +
                ", density=" + density +
                '}';
    }
}
