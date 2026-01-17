package io.github.muntashirakon.AppManager.dpc;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.R;

public class DpcReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        Toast.makeText(context, R.string.dpc_on_enabled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        Toast.makeText(context, R.string.dpc_on_disabled, Toast.LENGTH_SHORT).show();

    }
}
