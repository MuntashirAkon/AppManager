package io.github.muntashirakon.AppManager.compontents;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.FileUtils;
import android.util.Log;

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
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.compontents.ComponentsBlocker.ComponentType;

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
                ComponentsBlocker.getInstance(context, packageName).applyRules(true);
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
     * FIXME: Retrieve component types using PackageInfo instead of json file
     * @param context Application context
     * @param uri File URI
     */
    public static void applyFromBlocker(@NonNull Context context, Uri uri)
            throws Exception {
        try {
            String jsonString = Utils.getFileContent(context.getContentResolver(), uri);
            HashMap<String, HashMap<String, ComponentType>> packageComponents = new HashMap<>();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray components = jsonObject.getJSONArray("components");
            for(int i = 0; i<components.length(); ++i) {
                JSONObject component = (JSONObject) components.get(i);
                String packageName = component.getString("packageName");
                String componentName = component.getString("name");
                String type = component.getString("type");
                if (!packageComponents.containsKey(packageName))
                    packageComponents.put(packageName, new HashMap<>());
                //noinspection ConstantConditions
                packageComponents.get(packageName).put(componentName, getTypeFromString(type));
            }
            if (packageComponents.size() > 0) {
                ComponentsBlocker blocker;
                for (String packageName: packageComponents.keySet()) {
                    HashMap<String, ComponentType> disabledComponents = packageComponents.get(packageName);
                    //noinspection ConstantConditions
                    if (disabledComponents.size() > 0) {
                        blocker = ComponentsBlocker.getInstance(context, packageName);
                        for (String component: disabledComponents.keySet()) {
                            blocker.addComponent(component, disabledComponents.get(component));
                        }
                        blocker.applyRules(true);
                        if (!blocker.isRulesApplied()) throw new Exception("Rules not applied for package " + packageName);
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @NonNull
    private static ComponentType getTypeFromString(@NonNull String strType) {
        switch (strType) {
            case "ACTIVITY": return ComponentType.ACTIVITY;
            case "PROVIDER": return ComponentType.PROVIDER;
            case "RECEIVER": return ComponentType.RECEIVER;
            case "SERVICE": return ComponentType.SERVICE;
        }
        return ComponentType.UNKNOWN;
    }
}
