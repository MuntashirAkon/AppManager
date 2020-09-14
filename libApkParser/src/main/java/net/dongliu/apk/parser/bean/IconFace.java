package net.dongliu.apk.parser.bean;

import java.io.Serializable;

/**
 * The icon interface
 */
public interface IconFace extends Serializable {

    /**
     * If icon is file resource
     */
    boolean isFile();

    /**
     * Return the icon file as bytes. This method is valid only when {@link #isFile()} return true.
     * Otherwise, {@link UnsupportedOperationException} should be thrown.
     */
    byte[] getData();


    /**
     * Return the icon file path in apk file. This method is valid only when {@link #isFile()} return true.
     * Otherwise, {@link UnsupportedOperationException} should be thrown.
     */
    String getPath();
}
