package net.dongliu.apk.parser.bean;

import java.io.Serializable;

/**
 * Android adaptive icon, from android 8.0
 */
public class AdaptiveIcon implements IconFace, Serializable {
    private static final long serialVersionUID = 4185750290211529320L;
    private final Icon foreground;
    private final Icon background;

    public AdaptiveIcon(Icon foreground, Icon background) {
        this.foreground = foreground;
        this.background = background;
    }


    /**
     * The foreground icon
     */
    public Icon getForeground() {
        return foreground;
    }

    /**
     * The background icon
     */
    public Icon getBackground() {
        return background;
    }

    @Override
    public String toString() {
        return "AdaptiveIcon{" +
                "foreground=" + foreground +
                ", background=" + background +
                '}';
    }

    @Override
    public boolean isFile() {
        return foreground.isFile();
    }

    @Override
    public byte[] getData() {
        return foreground.getData();
    }

    @Override
    public String getPath() {
        return foreground.getPath();
    }
}
