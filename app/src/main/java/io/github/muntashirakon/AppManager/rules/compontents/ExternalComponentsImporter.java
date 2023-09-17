// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Import components from external apps like Blocker, Watt
 */
public class ExternalComponentsImporter {
    public static void setModeToFilteredAppOps(@NonNull AppOpsManagerCompat appOpsManager,
                                               @NonNull UserPackagePair pair,
                                               int[] appOps,
                                               @AppOpsManagerCompat.Mode int mode) throws RemoteException {
        Collection<Integer> appOpList;
        appOpList = PackageUtils.getFilteredAppOps(pair.getPackageName(), pair.getUserId(), appOps, mode);
        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserId())) {
            for (int appOp : appOpList) {
                appOpsManager.setMode(appOp, PackageUtils.getAppUid(pair), pair.getPackageName(), mode);
                cb.setAppOp(appOp, mode);
            }
            cb.applyRules(true);
        }
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromExistingBlockList(@NonNull List<String> packageNames, int userHandle) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RuleType> components;
        Path rulesPath = Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH);
        for (String packageName : packageNames) {
            components = PackageUtils.getUserDisabledComponentsForPackage(packageName, userHandle);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                for (String componentName : components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                // Remove IFW blocking rules if exists
                Path[] rulesFiles = rulesPath.listFiles((dir, name) -> name.startsWith(packageName) && name.endsWith("xml"));
                for (Path rulesFile : rulesFiles) {
                    rulesFile.delete();
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromBlocker(@NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            String filename = Paths.get(uri).getName();
            try {
                for (int userHandle : userHandles) {
                    applyFromBlocker(uri, userHandle);
                }
            } catch (Exception e) {
                failedFiles.add(filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromWatt(@NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            Path path = Paths.get(uri);
            String filename = path.getName();
            try {
                for (int userHandle : userHandles) {
                    applyFromWatt(Paths.trimPathExtension(filename), path, userHandle);
                }
            } catch (IOException e) {
                failedFiles.add(filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    /**
     * Watt only supports IFW, so copy them directly
     */
    @WorkerThread
    private static void applyFromWatt(String packageName, Path path, int userHandle)
            throws IOException {
        try (InputStream rulesStream = path.openInputStream()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                HashMap<String, RuleType> components = ComponentUtils.readIFWRules(rulesStream,
                        packageName);
                for (String componentName : components.keySet()) {
                    // Overwrite rules if exists
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            }
        }
    }

    /**
     * Apply from blocker
     *
     * @param uri File URI
     */
    @WorkerThread
    @SuppressLint("WrongConstant")
    private static void applyFromBlocker(Uri uri, int userHandle) throws Exception {
        String jsonString = Paths.get(uri).getContentAsString();
        HashMap<String, HashMap<String, RuleType>> packageComponents = new HashMap<>();
        HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray components = jsonObject.getJSONArray("components");
        List<String> uninstalledApps = new ArrayList<>();
        for (int i = 0; i < components.length(); ++i) {
            JSONObject component = (JSONObject) components.get(i);
            String packageName = component.getString("packageName");
            if (uninstalledApps.contains(packageName)) continue;
            if (!packageInfoList.containsKey(packageName)) {
                try {
                    packageInfoList.put(packageName, PackageManagerCompat.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                    | MATCH_DISABLED_COMPONENTS
                                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle));
                } catch (Exception e) {
                    // App not installed
                    uninstalledApps.add(packageName);
                    continue;
                }
            }
            String componentName = component.getString("name");
            if (!packageComponents.containsKey(packageName)) {
                packageComponents.put(packageName, new HashMap<>());
            }
            // Fetch package components using PackageInfo since the type used in Blocker can be wrong
            //noinspection ConstantConditions
            packageComponents.get(packageName).put(componentName, getType(componentName,
                    packageInfoList.get(packageName)));
        }
        if (packageComponents.size() > 0) {
            for (String packageName : packageComponents.keySet()) {
                HashMap<String, RuleType> disabledComponents = packageComponents.get(packageName);
                //noinspection ConstantConditions
                if (disabledComponents.size() > 0) {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                        for (String component : disabledComponents.keySet()) {
                            cb.addComponent(component, disabledComponents.get(component));
                        }
                        cb.applyRules(true);
                        if (!cb.isRulesApplied())
                            throw new Exception("Rules not applied for package " + packageName);
                    }
                }
            }
        }
    }

    @Nullable
    private static RuleType getType(@NonNull String name, @NonNull PackageInfo packageInfo) {
        for (ActivityInfo activityInfo : packageInfo.activities)
            if (activityInfo.name.equals(name)) return RuleType.ACTIVITY;
        for (ProviderInfo providerInfo : packageInfo.providers)
            if (providerInfo.name.equals(name)) return RuleType.PROVIDER;
        for (ActivityInfo receiverInfo : packageInfo.receivers)
            if (receiverInfo.name.equals(name)) return RuleType.RECEIVER;
        for (ServiceInfo serviceInfo : packageInfo.services)
            if (serviceInfo.name.equals(name)) return RuleType.SERVICE;
        return null;
    }
}
