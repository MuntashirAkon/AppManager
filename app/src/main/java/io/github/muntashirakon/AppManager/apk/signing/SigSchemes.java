// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import java.util.Arrays;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.AppPref;

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

    @SignatureScheme
    private int flags;

    @NonNull
    public static SigSchemes fromPref() {
        SigSchemes sigSchemes = new SigSchemes((Integer) AppPref.get(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT));
        if (sigSchemes.isEmpty()) {
            // Use default if no flag is set
            return new SigSchemes(sigSchemes.getDefaultFlags());
        }
        return sigSchemes;
    }

    public SigSchemes(@SignatureScheme int flags) {
        this.flags = flags;
    }

    public boolean isEmpty() {
        return flags == 0;
    }

    public int getFlags() {
        return flags;
    }

    public int getDefaultFlags() {
        return (int) AppPref.getInstance().getDefaultValue(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT);
    }

    public void addFlag(int index) {
        this.flags |= (1 << index);
    }

    public void removeFlag(int index) {
        this.flags &= ~(1 << index);
    }

    @NonNull
    public boolean[] flagsToCheckedItems() {
        boolean[] checkedItems = new boolean[TOTAL_SIG_SCHEME];
        Arrays.fill(checkedItems, false);
        for (int i = 0; i < TOTAL_SIG_SCHEME; ++i) {
            if ((flags & (1 << i)) != 0) checkedItems[i] = true;
        }
        return checkedItems;
    }

    public boolean v1SchemeEnabled() {
        return (flags & SIG_SCHEME_V1) != 0;
    }
    public boolean v2SchemeEnabled() {
        return (flags & SIG_SCHEME_V2) != 0;
    }
    public boolean v3SchemeEnabled() {
        return (flags & SIG_SCHEME_V3) != 0;
    }
    public boolean v4SchemeEnabled() {
        return (flags & SIG_SCHEME_V4) != 0;
    }
}
