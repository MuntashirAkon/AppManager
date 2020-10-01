/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.backup;


import android.text.TextUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class BackupMode {
    @IntDef(value = {
            MODE_NO_ENCRYPTION,
            MODE_OPEN_PGP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final int MODE_NO_ENCRYPTION = 0;
    public static final int MODE_OPEN_PGP = 1;

    @Mode
    public static int getMode() {
        String keyIds = (String) AppPref.get(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR);
        if (!TextUtils.isEmpty(keyIds)) {
            // FIXME(1/10/20): Check for the availability of the provider
            return MODE_OPEN_PGP;
        }
        return MODE_NO_ENCRYPTION;
    }

    @Nullable
    public static Crypto getCrypto(@Mode int mode) {
        switch (mode) {
            case MODE_OPEN_PGP:
                return new OpenPGPCrypto();
            case MODE_NO_ENCRYPTION:
            default:
                return null;
        }
    }
}
