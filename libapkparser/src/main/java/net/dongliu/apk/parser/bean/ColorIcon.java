// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.bean;

import java.io.Serializable;

/**
 * The plain icon, using color drawable resource.
 */
// Copyright 2018 Liu Dong
//to be implemented
public class ColorIcon implements IconFace, Serializable {
    private static final long serialVersionUID = -7913024425268466186L;

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public byte[] getData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPath() {
        throw new UnsupportedOperationException();
    }

}
