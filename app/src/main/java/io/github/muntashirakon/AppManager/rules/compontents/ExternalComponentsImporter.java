package io.github.muntashirakon.AppManager.rules.compontents;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

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
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Import components from external apps like Blocker, MyAndroidTools, Watt
 */
public class ExternalComponentsImporter {
    @NonNull
    public static List<String> applyFromTrackingComponents(@NonNull Context context, @NonNull Collection<String> packageNames) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = TrackerComponentUtils.getTrackerComponentsForPackage(packageName);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                for (String componentName: components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> applyFilteredComponents(@NonNull Context context, @NonNull Collection<String> packageNames, String[] signatures) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = PackageUtils.getFilteredComponents(packageName, signatures);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                for (String componentName: components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> applyFilteredAppOps(@NonNull Context context, @NonNull Collection<String> packageNames, int[] appOps) {
        List<String> failedPkgList = new ArrayList<>();
        Collection<Integer> appOpList;
        AppOpsService appOpsService = new AppOpsService(context);
        for (String packageName: packageNames) {
            appOpList = PackageUtils.getFilteredAppOps(packageName, appOps);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                for (int appOp: appOpList) {
                    try {
                        appOpsService.setMode(appOp, -1, packageName, AppOpsManager.MODE_IGNORED);
                        cb.setAppOp(String.valueOf(appOp), AppOpsManager.MODE_IGNORED);
                    } catch (Exception ignore) {}
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static List<String> applyFromExistingBlockList(@NonNull Context context, @NonNull List<String> packageNames) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName: packageNames) {
            components = PackageUtils.getUserDisabledComponentsForPackage(packageName);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                for (String componentName: components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @NonNull
    public static Tuple<Boolean, Integer> applyFromBlocker(@NonNull Context context, @NonNull List<Uri> uriList) {
        boolean failed = false;
        Integer failedCount = 0;
        for(Uri uri: uriList) {
            try {
                applyFromBlocker(context, uri);
            } catch (Exception e) {
                e.printStackTrace();
                failed = true;
                ++failedCount;
            }
        }
        return new Tuple<>(failed, failedCount);
    }

    @NonNull
    public static Tuple<Boolean, Integer> applyFromWatt(@NonNull Context context, @NonNull List<Uri> uriList) {
        boolean failed = false;
        Integer failedCount = 0;
        for(Uri uri: uriList) {
            try {
                applyFromWatt(context, uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                failed = true;
                ++failedCount;
            }
        }
        return new Tuple<>(failed, failedCount);
    }

    /**
     * Watt only supports IFW, so copy them directly
     *
     * @param context Application context
     * @param fileUri File URI
     */
    private static void applyFromWatt(@NonNull Context context, Uri fileUri) throws FileNotFoundException {
        String filename = Utils.getName(context.getContentResolver(), fileUri);
        if (filename == null) throw new FileNotFoundException("The requested content is not found.");
        try {
            try (InputStream rulesStream = context.getContentResolver().openInputStream(fileUri)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(rulesStream, null);
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, null, "rules");
                int event = parser.nextTag();
                RulesStorageManager.Type componentType = RulesStorageManager.Type.UNKNOWN;
                String packageName = Utils.trimExtension(filename);
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                    String name;
                    while (!(name = parser.getName()).equals("rules")) {
                        switch (event) {
                            case XmlPullParser.START_TAG:
                                if (name.equals(ComponentsBlocker.TAG_ACTIVITY)
                                        || name.equals(ComponentsBlocker.TAG_RECEIVER)
                                        || name.equals(ComponentsBlocker.TAG_SERVICE)) {
                                    componentType = cb.getComponentType(name);
                                }
                                break;
                            case XmlPullParser.END_TAG:
                                if (name.equals("component-filter")) {
                                    String fullKey = parser.getAttributeValue(null, "name");
                                    int divider = fullKey.indexOf('/');
                                    String pkgName = fullKey.substring(0, divider);
                                    String componentName = fullKey.substring(divider + 1);
                                    if (pkgName.equals(packageName)) {
                                        // Overwrite rules if exists
                                        cb.addComponent(componentName, componentType);
                                    }
                                }
                        }
                        event = parser.nextTag();
                    }
                    cb.applyRules(true);
                }
            }
        } catch (IOException|XmlPullParserException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    /**
     * Apply from blocker
     * @param context Application context
     * @param uri File URI
     */
    public static void applyFromBlocker(@NonNull Context context, Uri uri)
            throws Exception {
        try {
            String jsonString = Utils.getFileContent(context.getContentResolver(), uri);
            HashMap<String, HashMap<String, RulesStorageManager.Type>> packageComponents = new HashMap<>();
            HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
            PackageManager packageManager = context.getPackageManager();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray components = jsonObject.getJSONArray("components");
            for (int i = 0; i<components.length(); ++i) {
                JSONObject component = (JSONObject) components.get(i);
                String packageName = component.getString("packageName");
                if (!packageInfoList.containsKey(packageName)) {
                    int apiCompatFlags;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        apiCompatFlags = PackageManager.MATCH_DISABLED_COMPONENTS;
                    else apiCompatFlags = PackageManager.GET_DISABLED_COMPONENTS;
                    packageInfoList.put(packageName, packageManager.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                    | apiCompatFlags));
                }
                String componentName = component.getString("name");
                if (!packageComponents.containsKey(packageName))
                    packageComponents.put(packageName, new HashMap<>());
                //noinspection ConstantConditions
                packageComponents.get(packageName).put(componentName, getType(componentName, packageInfoList.get(packageName)));
            }
            if (packageComponents.size() > 0) {
                for (String packageName: packageComponents.keySet()) {
                    HashMap<String, RulesStorageManager.Type> disabledComponents = packageComponents.get(packageName);
                    //noinspection ConstantConditions
                    if (disabledComponents.size() > 0) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)){
                            for (String component: disabledComponents.keySet()) {
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
        for (ActivityInfo activityInfo: packageInfo.activities)
            if (activityInfo.name.equals(name)) return RulesStorageManager.Type.ACTIVITY;
        for (ProviderInfo providerInfo: packageInfo.providers)
            if (providerInfo.name.equals(name)) return RulesStorageManager.Type.PROVIDER;
        for (ActivityInfo receiverInfo: packageInfo.receivers)
            if (receiverInfo.name.equals(name)) return RulesStorageManager.Type.RECEIVER;
        for (ServiceInfo serviceInfo: packageInfo.services)
            if (serviceInfo.name.equals(name)) return RulesStorageManager.Type.SERVICE;
        return RulesStorageManager.Type.UNKNOWN;
    }
}
