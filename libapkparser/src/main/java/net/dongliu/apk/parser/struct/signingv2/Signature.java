// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct.signingv2;

// Copyright 2018 hsiafan
public class Signature {
    private int algorithmID;
    private byte[] data;

    public Signature(int algorithmID, byte[] data) {
        this.algorithmID = algorithmID;
        this.data = data;
    }

    public int getAlgorithmID() {
        return algorithmID;
    }

    public byte[] getData() {
        return data;
    }
}
