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

package io.github.muntashirakon.AppManager.rules.compontents;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;

/**
 * Import components from external apps like Blocker, MyAndroidTools, Watt
 */
public class ExternalComponentsImporter {
    @NonNull
    public static List<UserPackagePair> setModeToFilteredAppOps(@NonNull Collection<UserPackagePair> userPackagePairs, int[] appOps, @AppOpsManager.Mode int mode) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        Collection<Integer> appOpList;
        AppOpsService appOpsService = new AppOpsService();
        for (UserPackagePair pair : userPackagePairs) {
            appOpList = PackageUtils.getFilteredAppOps(pair.getPackageName(), pair.getUserHandle(), appOps, mode);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(), pair.getUserHandle())) {
                for (int appOp : appOpList) {
                    try {
                        appOpsService.setMode(appOp, PackageUtils.getAppUid(pair), pair.getPackageName(), mode);
                        cb.setAppOp(String.valueOf(appOp), mode);
                    } catch (Exception ignore) {
                    }
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> applyFromExistingBlockList(@NonNull List<String> packageNames, int userHandle) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName : packageNames) {
            components = PackageUtils.getUserDisabledComponentsForPackage(packageName, userHandle);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                for (String componentName : components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                // Remove IFW blocking rules if exists
                Runner.runCommand(Runner.getRootInstance(), String.format("rm %s/%s*.xml", ComponentsBlocker.SYSTEM_RULES_PATH, packageName));
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static Pair<Boolean, Integer> applyFromBlocker(@NonNull Context context, @NonNull List<Uri> uriList, int[] userHandles) {
        boolean failed = false;
        Integer failedCount = 0;
        for (Uri uri : uriList) {
            for (int userHandle : userHandles) {
                try {
                    applyFromBlocker(context, uri, userHandle);
                } catch (Exception e) {
                    e.printStackTrace();
                    failed = true;
                    ++failedCount;
                }
            }
        }
        return new Pair<>(failed, failedCount);
    }

    @NonNull
    public static Pair<Boolean, Integer> applyFromWatt(@NonNull Context context, @NonNull List<Uri> uriList, int[] userHandles) {
        boolean failed = false;
        Integer failedCount = 0;
        for (Uri uri : uriList) {
            for (int userHandle : userHandles) {
                try {
                    applyFromWatt(context, uri, userHandle);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    failed = true;
                    ++failedCount;
                }
            }
        }
        return new Pair<>(failed, failedCount);
    }

    /**
     * Watt only supports IFW, so copy them directly
     *
     * @param context Application context
     * @param fileUri File URI
     */
    private static void applyFromWatt(@NonNull Context context, Uri fileUri, int userHandle) throws FileNotFoundException {
        String filename = IOUtils.getFileName(context.getContentResolver(), fileUri);
        if (filename == null) {
            throw new FileNotFoundException("The requested content is not found.");
        }
        try {
            try (InputStream rulesStream = context.getContentResolver().openInputStream(fileUri)) {
                if (rulesStream == null) throw new IOException("Failed to open input stream.");
                String packageName = IOUtils.trimExtension(filename);
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                    HashMap<String, RulesStorageManager.Type> components = ComponentUtils.readIFWRules(rulesStream, packageName);
                    for (String componentName : components.keySet()) {
                        // Overwrite rules if exists
                        cb.addComponent(componentName, components.get(componentName));
                    }
                    cb.applyRules(true);
                }
            }
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    /**
     * Apply from blocker
     *
     * @param context Application context
     * @param uri     File URI
     */
    @SuppressLint("WrongConstant")
    private static void applyFromBlocker(@NonNull Context context, Uri uri, int userHandle)
            throws Exception {
        try {
            String jsonString = IOUtils.getFileContent(context.getContentResolver(), uri);
            HashMap<String, HashMap<String, RulesStorageManager.Type>> packageComponents = new HashMap<>();
            HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray components = jsonObject.getJSONArray("components");
            for (int i = 0; i < components.length(); ++i) {
                JSONObject component = (JSONObject) components.get(i);
                String packageName = component.getString("packageName");
                if (!packageInfoList.containsKey(packageName)) {
                    packageInfoList.put(packageName, PackageManagerCompat.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                    | flagDisabledComponents, userHandle));
                }
                String componentName = component.getString("name");
                if (!packageComponents.containsKey(packageName)) {
                    packageComponents.put(packageName, new HashMap<>());
                }
                // Fetch package components using PackageInfo since the type used in Blocker can be wrong
                packageComponents.get(packageName).put(componentName, getType(componentName, packageInfoList.get(packageName)));
            }
            if (packageComponents.size() > 0) {
                for (String packageName : packageComponents.keySet()) {
                    HashMap<String, RulesStorageManager.Type> disabledComponents = packageComponents.get(packageName);
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
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    private static RulesStorageManager.Type getType(@NonNull String name, @NonNull PackageInfo packageInfo) {
        for (ActivityInfo activityInfo : packageInfo.activities)
            if (activityInfo.name.equals(name)) return RulesStorageManager.Type.ACTIVITY;
        for (ProviderInfo providerInfo : packageInfo.providers)
            if (providerInfo.name.equals(name)) return RulesStorageManager.Type.PROVIDER;
        for (ActivityInfo receiverInfo : packageInfo.receivers)
            if (receiverInfo.name.equals(name)) return RulesStorageManager.Type.RECEIVER;
        for (ServiceInfo serviceInfo : packageInfo.services)
            if (serviceInfo.name.equals(name)) return RulesStorageManager.Type.SERVICE;
        return RulesStorageManager.Type.UNKNOWN;
    }
}
