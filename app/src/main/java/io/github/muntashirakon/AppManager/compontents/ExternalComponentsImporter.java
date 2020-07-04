package io.github.muntashirakon.AppManager.compontents;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;

import com.google.classysharkandroid.utils.IOUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.storage.StorageManager;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Import components from external apps like Blocker, MyAndroidTools, Watt
 */
public class ExternalComponentsImporter {
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
                String packageName = applyFromWatt(context, uri);
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(context, packageName)) {
                    cb.applyRules(true);
                }
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
     * FIXME: Breaks in v2.5.6
     * @param context Application context
     * @param fileUri File URI
     */
    @NonNull
    private static String applyFromWatt(@NonNull Context context, Uri fileUri) throws FileNotFoundException {
        String filename = Utils.getName(context.getContentResolver(), fileUri);
        if (filename == null) throw new FileNotFoundException("The requested content is not found.");
        File amFile = new File(ComponentsBlocker.provideLocalIfwRulesPath(context) + "/" + filename);
        InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
        if (inputStream == null) throw new FileNotFoundException("The requested content is not found.");
        OutputStream outputStream = new FileOutputStream(amFile);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                FileUtils.copy(inputStream, outputStream);
            } else {
                IOUtils.copy(inputStream, outputStream);
            }
            inputStream.close();
            outputStream.close();
            return Utils.trimExtension(filename);
        } catch (IOException e) {
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
            HashMap<String, HashMap<String, StorageManager.Type>> packageComponents = new HashMap<>();
            HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
            PackageManager packageManager = context.getPackageManager();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray components = jsonObject.getJSONArray("components");
            for(int i = 0; i<components.length(); ++i) {
                JSONObject component = (JSONObject) components.get(i);
                String packageName = component.getString("packageName");
                if (!packageInfoList.containsKey(packageName))
                    packageInfoList.put(packageName, packageManager.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES));
                String componentName = component.getString("name");
                if (!packageComponents.containsKey(packageName))
                    packageComponents.put(packageName, new HashMap<>());
                //noinspection ConstantConditions
                packageComponents.get(packageName).put(componentName, getType(componentName, packageInfoList.get(packageName)));
            }
            if (packageComponents.size() > 0) {
                for (String packageName: packageComponents.keySet()) {
                    HashMap<String, StorageManager.Type> disabledComponents = packageComponents.get(packageName);
                    //noinspection ConstantConditions
                    if (disabledComponents.size() > 0) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getInstance(context, packageName)){
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

    private static StorageManager.Type getType(@NonNull String name, @NonNull PackageInfo packageInfo) {
        for (ActivityInfo activityInfo: packageInfo.activities)
            if (activityInfo.name.equals(name)) return StorageManager.Type.ACTIVITY;
        for (ProviderInfo providerInfo: packageInfo.providers)
            if (providerInfo.name.equals(name)) return StorageManager.Type.PROVIDER;
        for (ActivityInfo receiverInfo: packageInfo.receivers)
            if (receiverInfo.name.equals(name)) return StorageManager.Type.RECEIVER;
        for (ServiceInfo serviceInfo: packageInfo.services)
            if (serviceInfo.name.equals(name)) return StorageManager.Type.SERVICE;
        return StorageManager.Type.UNKNOWN;
    }
}
