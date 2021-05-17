// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct.signingv2;

// Copyright 2018 hsiafan
public class Digest {
    private int algorithmID;
    private byte[] value;

    public Digest(int algorithmID, byte[] value) {
        this.algorithmID = algorithmID;
        this.value = value;
    }

    public int getAlgorithmID() {
        return algorithmID;
    }

    public byte[] getValue() {
        return value;
    }
}
