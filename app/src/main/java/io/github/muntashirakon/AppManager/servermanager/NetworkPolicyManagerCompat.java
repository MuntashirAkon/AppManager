package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.net.INetworkPolicyManager;
import android.net.NetworkPolicyManager;
import android.os.RemoteException;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class NetworkPolicyManagerCompat {
    @IntDef(flag = true, value = {
            NetworkPolicyManager.POLICY_NONE,
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetPolicy {}

    @NetPolicy
    public static int getUidPolicy(int uid) {
        try {
            return getNetPolicyManager().getUidPolicy(uid);
        } catch (Throwable e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void setUidPolicy(int uid, int policies) throws RemoteException {
        getNetPolicyManager().setUidPolicy(uid, policies);
    }

    @NonNull
    public static ArrayMap<Integer, String> getReadablePolicies(@NonNull Context context, int policies) {
        ArrayMap<Integer, String> readablePolicies = new ArrayMap<>();
        if (policies > 0) {
            if ((policies & NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND) != 0) {
                readablePolicies.put(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND, context.getString(R.string.reject_background));
            }
            if ((policies & NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND) != 0) {
                readablePolicies.put(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND, context.getString(R.string.allow_background));
            }
        } else {
            readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none));
        }
        return readablePolicies;
    }

    @NonNull
    public static ArrayMap<Integer, String> getAllReadablePolicies(@NonNull Context context) {
        ArrayMap<Integer, String> readablePolicies = new ArrayMap<>();
        readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none));
        readablePolicies.put(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND, context.getString(R.string.reject_background));
        readablePolicies.put(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND, context.getString(R.string.allow_background));
        return readablePolicies;
    }

    public static INetworkPolicyManager getNetPolicyManager() {
        return INetworkPolicyManager.Stub.asInterface(ProxyBinder.getService("netpolicy"));
    }
}
