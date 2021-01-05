package io.github.muntashirakon.AppManager.intercept;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

public class IntentCompat {
    public static void removeFlags(@NonNull Intent intent, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.removeFlags(flags);
        } else {
            int _flags = intent.getFlags();
            _flags &= ~flags;
            intent.setFlags(_flags);
        }
    }
}
