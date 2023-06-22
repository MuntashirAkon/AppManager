// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class SigSchemes {
    @IntDef(flag = true, value = {
            SIG_SCHEME_V1,
            SIG_SCHEME_V2,
            SIG_SCHEME_V3,
            SIG_SCHEME_V4,
    })
    public @interface SignatureScheme {
    }

    public static final int SIG_SCHEME_V1 = 1 << 0;
    public static final int SIG_SCHEME_V2 = 1 << 1;
    public static final int SIG_SCHEME_V3 = 1 << 2;
    public static final int SIG_SCHEME_V4 = 1 << 3;

    public static final int TOTAL_SIG_SCHEME = 4;

    public static final int DEFAULT_SCHEMES = SIG_SCHEME_V1 | SIG_SCHEME_V2;

    @SignatureScheme
    private int mFlags;

    public SigSchemes(@SignatureScheme int flags) {
        this.mFlags = flags;
    }

    public boolean isEmpty() {
        return mFlags == 0;
    }

    public int getFlags() {
        return mFlags;
    }

    public void setFlags(int flags) {
        this.mFlags = flags;
    }

    @NonNull
    public List<Integer> getAllItems() {
        List<Integer> allItems = new ArrayList<>();
        for (int i = 0; i < TOTAL_SIG_SCHEME; ++i) {
            allItems.add(1 << i);
        }
        return allItems;
    }

    public boolean v1SchemeEnabled() {
        return (mFlags & SIG_SCHEME_V1) != 0;
    }
    public boolean v2SchemeEnabled() {
        return (mFlags & SIG_SCHEME_V2) != 0;
    }
    public boolean v3SchemeEnabled() {
        return (mFlags & SIG_SCHEME_V3) != 0;
    }
    public boolean v4SchemeEnabled() {
        return (mFlags & SIG_SCHEME_V4) != 0;
    }
}
