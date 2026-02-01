package io.github.muntashirakon.AppManager.dpc;

import static io.github.muntashirakon.AppManager.compat.DevicePolicyManagerCompat.DPC_ADMIN;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;

public class DpcReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        DevicePolicyManager dpc = getManager(context);
        if (!dpc.isDeviceOwnerApp(BuildConfig.APPLICATION_ID)) {
            throw new RuntimeException("Not Device Owner but onEnabled was called");
        }
        //Setup DPC, and reset restrictions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpc.setOrganizationName(DPC_ADMIN, context.getString(R.string.app_name));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dpc.setOrganizationId(BuildConfig.APPLICATION_ID);
            }
            dpc.setShortSupportMessage(DPC_ADMIN, context.getText(R.string.dpc_short_support_message));
            dpc.setLongSupportMessage(DPC_ADMIN, context.getText(R.string.dpc_long_support_message));
            for (String s : dpc.getUserRestrictions(DPC_ADMIN).keySet()) {
                dpc.clearUserRestriction(DPC_ADMIN, s);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpc.setUserControlDisabledPackages(DPC_ADMIN, List.of());
        }
        Toast.makeText(context, R.string.dpc_on_enabled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        Toast.makeText(context, R.string.dpc_on_disabled, Toast.LENGTH_SHORT).show();

    }
}
