// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class SignatureInfo {
    public final String signature;
    public final String label;
    public final String type;
    public final List<String> classes;

    private int mCount;

    public SignatureInfo(@NonNull String signature, @NonNull String label) {
        this.signature = signature;
        this.label = label;
        this.type = "Tracker"; // Arbitrary type
        this.classes = new ArrayList<>();
    }

    public SignatureInfo(@NonNull String signature, @NonNull String label, @NonNull String type) {
        this.signature = signature;
        this.label = label;
        this.type = type;
        this.classes = new ArrayList<>();
    }

    public void setCount(int count) {
        mCount = count;
    }

    public int getCount() {
        return mCount;
    }

    public void addClass(@NonNull String className) {
        classes.add(className);
    }

    @NonNull
    @Override
    public String toString() {
        return "SignatureInfo{" +
                "signature='" + signature + '\'' +
                ", label='" + label + '\'' +
                ", type='" + type + '\'' +
                ", classes=" + classes +
                ", mCount=" + mCount +
                '}';
    }
}
