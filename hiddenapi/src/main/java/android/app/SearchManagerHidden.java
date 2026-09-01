// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package android.app;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(SearchManager.class)
@RequiresApi(Build.VERSION_CODES.M)
public class SearchManagerHidden {
    public void launchAssist(@Nullable Bundle args) {
        HiddenUtil.throwUOE(args);
    }
}
