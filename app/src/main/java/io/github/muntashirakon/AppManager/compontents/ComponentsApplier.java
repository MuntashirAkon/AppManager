package io.github.muntashirakon.AppManager.compontents;

import android.content.Context;
import android.util.Xml;

import com.jaredrummler.android.shell.Shell;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import androidx.annotation.NonNull;

import static io.github.muntashirakon.AppManager.compontents.ComponentType.ACTIVITY;
import static io.github.muntashirakon.AppManager.compontents.ComponentType.RECEIVER;
import static io.github.muntashirakon.AppManager.compontents.ComponentType.SERVICE;

public class ComponentsApplier {
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_RECEIVER = "broadcast";
    private static final String TAG_SERVICE = "service";

    private final String localIfwRulesPath;
    private static final String IFW_DATA = "/data/system/ifw/";
    private static ComponentsApplier componentsApplier = null;

    @NonNull
    public static ComponentsApplier getInstance(Context context) {
        if (componentsApplier == null) {
            try {
                String localIfwRulesPath = provideLocalIfwRulesPath(context);
                componentsApplier = new ComponentsApplier(localIfwRulesPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        }
        return componentsApplier;
    }

    private static String provideLocalIfwRulesPath(Context context) throws FileNotFoundException {
        File file = context.getExternalFilesDir("ifw");
        if (file == null || (!file.exists() && !file.mkdirs())) {
            file = new File(context.getFilesDir(), "ifw");
            if (!file.exists() && !file.mkdirs()) {
                throw new FileNotFoundException("Can not get correct path to save ifw rules");
            }
        }
        return file.getAbsolutePath();
    }

    private ComponentsApplier(@NonNull String localIfwRulesPath) {
        this.localIfwRulesPath = localIfwRulesPath;
    }

    /**
     * Retrieve a set of disabled components from local source
     *
     * If it's available in the system, save a copy to the local source and then retrieve the components
     * @param packageName The package name whose components are to be retrieved
     * @return A hash set of disabled components
     */
    public HashMap<String, ComponentType> getDisabledComponentNamesForPackage(String packageName) {
        File localRulesFile = localRulesFile(packageName);
        HashMap<String, ComponentType> components = new HashMap<>();
        if (!localRulesFile.exists()) {
            // Rules doesn't exist in locally
            if (isRulesApplied(packageName)) {
                // But there are rules in the system
                // Copy system rules to make them accessible locally
                Shell.SU.run(String.format("cp %s%s.xml '%s'", IFW_DATA, packageName, localIfwRulesPath));
            } else return components;
        }
        try {
            FileInputStream rulesStream = new FileInputStream(localRulesFile);
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(rulesStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "rules");
            int event = parser.nextTag();
            ComponentType componentType = ComponentType.UNKNOWN;
            int packageNameLength = packageName.length()+1;
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals(TAG_ACTIVITY) || name.equals(TAG_RECEIVER) || name.equals(TAG_SERVICE)) {
                            componentType = getComponentType(name);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("component-filter")) {
                            String fullKey = parser.getAttributeValue(null, "name");
//                            Log.d("Components", componentType + " - " + fullKey);
                            components.put(fullKey.substring(packageNameLength), componentType);
                        }
                }
                event = parser.nextTag();
            }
            rulesStream.close();
        } catch (IOException | XmlPullParserException ignored) {}
        return components;
    }

    /**
     * Save the disabled components locally (not applied to the system)
     * @param packageName The package name whose disabled components are to be saved
     * @param disabledComponents A list of disabled components
     * @throws IOException If it fails to write to the destination file
     */
    public void saveDisabledComponentsForPackage(String packageName, @NonNull HashMap<String, ComponentType> disabledComponents) throws IOException {
        File localRulesFile = localRulesFile(packageName);
        if (disabledComponents.isEmpty()) {
            if (localRulesFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            }
            return;
        }
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (String component : disabledComponents.keySet()) {
            String componentFilter = "<component-filter name=\"" + packageName + "/" + component + "\"/>\n";
            ComponentType componentType = disabledComponents.get(component);
            if (componentType != null) {
                switch (componentType) {
                    case ACTIVITY: activities.append(componentFilter); break;
                    case RECEIVER: receivers.append(componentFilter); break;
                    case SERVICE: services.append(componentFilter); break;
                }
            }
        }

        String rules = "<rules>\n" +
                ((activities.length() == 0) ? "" : "<activity block=\"true\" log=\"false\">\n" + activities + "</activity>\n") +
                ((services.length() == 0) ? "" : "<service block=\"true\" log=\"false\">\n" + services + "</service>\n") +
                ((receivers.length() == 0) ? "" : "<broadcast block=\"true\" log=\"false\">\n" + receivers + "</broadcast>\n") +
                "</rules>";
        FileOutputStream outputStream = new FileOutputStream(localRulesFile);
        outputStream.write(rules.getBytes());
        outputStream.close();
    }

    /**
     * Apply rules, ie. save them in the system directory
     * @param packageName The package name whose rules are to be saved
     * @param apply Whether to apply the rules or remove them altogether
     */
    public void applyRulesForPackage(String packageName, boolean apply) {
        String command;
        File localRulesFile = localRulesFile(packageName);
        if (apply && localRulesFile.exists()) {
            command = String.format("cp '%s' %s && chmod 0666 %s%s.xml && am force-stop %s",
                    localRulesFile.getAbsolutePath(), IFW_DATA, IFW_DATA, packageName, packageName);
        } else {
            command = String.format("test -e '%s%s.xml' && rm -rf %s%s.xml && am force-stop %s",
                    IFW_DATA, packageName, IFW_DATA, packageName, packageName);
        }
        Shell.SU.run(command);
    }

    /**
     * Check whether rules are applied successfully
     * @param packageName Name of the package whose rules have to be checked
     * @return True if applied, false otherwise
     */
    public boolean isRulesApplied(String packageName) {
        return Shell.SU.run(String.format("test -e '%s%s.xml' && echo 1 || echo 0", IFW_DATA, packageName)).getStdout().equals("1");
    }

    /**
     * Get component type from TAG_* constants
     * @param componentName Name of the constant: one of the TAG_*
     * @return One of the {@link ComponentType}
     */
    private ComponentType getComponentType(@NonNull String componentName) {
        switch (componentName) {
            case TAG_ACTIVITY:
                return ACTIVITY;
            case TAG_RECEIVER:
                return RECEIVER;
            case TAG_SERVICE:
                return SERVICE;
            default:
                return ComponentType.UNKNOWN;
        }
    }

    /**
     * Get local rules file
     * @param packageName Name of the package whose rules are requested
     * @return Local file location
     */
    private File localRulesFile(String packageName) {
        return new File(localIfwRulesPath, packageName + ".xml");
    }
}
