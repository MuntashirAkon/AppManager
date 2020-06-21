package io.github.muntashirakon.AppManager.compontents;

import android.content.Context;
import android.util.Xml;

import com.jaredrummler.android.shell.Shell;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Block application components: activities, broadcasts, services and providers.
 * <br><br>
 * Activities, broadcasts and services are blocked via Intent Firewall (which is superior to the
 * <code>pm disable <b>component</b></code> method). Rules for each package is saved as a separate
 * xml file which is named after the package name and saved to /data/system/ifw and
 * /sdcard/Android/data/io.github.muntashirakon.AppManager/files/ifw. By default, data is read from
 * both directories but written only to the latter directory unless
 * {@link ComponentsApplier#applyRules(boolean)} is called in which case data is
 * saved to the former directory (rules are applied automatically once they're copied there).
 * <br>
 * Providers are blocked via <code>pm disable <b>component</b></code> method since there's no way to
 * block them via intent firewall. blocked providers are only kept in the /sdcard/Android/data/io.github.muntashirakon.AppManager/files/ifw
 * directory. The file name is the same as above but with .txt extension instead of .xml and each
 * provider is saved in a new line.
 *
 * @see <a href="https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/firewall/IntentFirewall.java">IntentFirewall.java</a>
 * @see ComponentType
 */
public class ComponentsApplier {
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_RECEIVER = "broadcast";
    private static final String TAG_SERVICE = "service";

    private static final String SYSTEM_RULES_PATH = "/data/system/ifw/";
    private static ComponentsApplier componentsApplier = null;

    /**
     * Component types: activity, broadcast receiver, service, provider
     */
    public enum ComponentType {
        ACTIVITY,
        SERVICE,
        RECEIVER,
        PROVIDER,
        UNKNOWN
    }

    @NonNull
    public static ComponentsApplier getInstance(@NonNull Context context, @NonNull String packageName) {
        if (componentsApplier == null) {
            try {
                String localIfwRulesPath = provideLocalIfwRulesPath(context);
                componentsApplier = new ComponentsApplier(localIfwRulesPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        }
        componentsApplier.setPackageName(packageName);
        return componentsApplier;
    }

    @NonNull
    private static String provideLocalIfwRulesPath(@NonNull Context context) throws FileNotFoundException {
        File file = context.getExternalFilesDir("ifw");
        if (file == null || (!file.exists() && !file.mkdirs())) {
            file = new File(context.getFilesDir(), "ifw");
            if (!file.exists() && !file.mkdirs()) {
                throw new FileNotFoundException("Can not get correct path to save ifw rules");
            }
        }
        return file.getAbsolutePath();
    }

    private final String LOCAL_RULES_PATH;
    private File localRulesFile;
    private File localProvidersFile;
    private HashMap<String, ComponentType> disabledComponents;
    private Set<String> removedProviders;
    private String packageName;

    private ComponentsApplier(@NonNull String localIfwRulesPath) {
        this.LOCAL_RULES_PATH = localIfwRulesPath;
    }

    /**
     * Alternative to constructor
     * @param packageName Name of the package to handle
     */
    private void setPackageName(String packageName) {
        this.packageName = packageName;
        this.localRulesFile = new File(LOCAL_RULES_PATH, packageName + ".xml");
        this.localProvidersFile = new File(LOCAL_RULES_PATH, packageName + ".txt");
        removedProviders = new HashSet<>();
        retrieveDisabledComponents();
    }

    public Boolean hasComponent(String componentName) {
        return disabledComponents.containsKey(componentName);
    }

    public int componentCount() {
        return disabledComponents.size();
    }

    public void addComponent(String componentName, ComponentType componentType) {
        disabledComponents.put(componentName, componentType);
    }

    public void removeComponent(String componentName) {
        if (hasComponent(componentName)) {
            if (disabledComponents.get(componentName) == ComponentType.PROVIDER) {
                removedProviders.add(componentName);
            }
            disabledComponents.remove(componentName);
        }
    }

    /**
     * Save the disabled components locally (not applied to the system)
     * @throws IOException If it fails to write to the destination file
     */
    public void saveDisabledComponents() throws IOException {
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
        StringBuilder providers = new StringBuilder();
        for (String component : disabledComponents.keySet()) {
            String componentFilter = "<component-filter name=\"" + packageName + "/" + component + "\"/>\n";
            ComponentType componentType = disabledComponents.get(component);
            if (componentType != null) {
                switch (componentType) {
                    case ACTIVITY: activities.append(componentFilter); break;
                    case RECEIVER: receivers.append(componentFilter); break;
                    case SERVICE: services.append(componentFilter); break;
                    case PROVIDER: providers.append(component).append("\n"); break;
                }
            }
        }

        String rules = "<rules>\n" +
                ((activities.length() == 0) ? "" : "<activity block=\"true\" log=\"false\">\n" + activities + "</activity>\n") +
                ((services.length() == 0) ? "" : "<service block=\"true\" log=\"false\">\n" + services + "</service>\n") +
                ((receivers.length() == 0) ? "" : "<broadcast block=\"true\" log=\"false\">\n" + receivers + "</broadcast>\n") +
                "</rules>";
        // Save rules
        FileOutputStream rulesStream = new FileOutputStream(localRulesFile);
        rulesStream.write(rules.getBytes());
        rulesStream.close();
        // Save providers
        FileOutputStream providersStream = new FileOutputStream(localProvidersFile);
        providersStream.write(providers.toString().getBytes());
        providersStream.close();
    }

    /**
     * Check whether rules are applied successfully
     * @return True if applied, false otherwise
     */
    public boolean isRulesApplied() {
        // FIXME
        return Shell.SU.run(String.format("test -e '%s%s.xml' && echo 1 || echo 0", SYSTEM_RULES_PATH, packageName)).getStdout().equals("1");
    }

    /**
     * Apply rules, ie. save them in the system directory
     * @param apply Whether to apply the rules or remove them altogether
     */
    public void applyRules(boolean apply) {
        try {
            // Save disabled components once again
            saveDisabledComponents();
            // Apply/Remove rules
            if (apply && localRulesFile.exists()) {
                // Apply rules
                Shell.SU.run(String.format("cp '%s' %s && chmod 0666 %s%s.xml && am force-stop %s",
                        localRulesFile.getAbsolutePath(), SYSTEM_RULES_PATH, SYSTEM_RULES_PATH,
                        packageName, packageName));
            } else {
                // Remove rules
                Shell.SU.run(String.format("test -e '%s%s.xml' && rm -rf %s%s.xml && am force-stop %s",
                        SYSTEM_RULES_PATH, packageName, SYSTEM_RULES_PATH, packageName, packageName));
            }
            // Enable removed providers
            for (String provider : removedProviders) {
                Shell.SU.run(String.format("pm enable %s/%s", packageName, provider));
            }
            // Fetch providers from file once again
            Set<String> providers = new HashSet<>();
            if (localProvidersFile.exists()) {
                try {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader(localProvidersFile));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        if (!line.equals("")) {
                            providers.add(line.trim());
                        }
                    }
                    bufferedReader.close();
                } catch (IOException ignored) {}
            }
            // Enable/disable components
            if (apply) {
                // Disable providers
                for (String provider : providers) {
                    Shell.SU.run(String.format("pm disable %s/%s", packageName, provider));
                }
            } else {
                // Enable providers
                for (String provider : providers) {
                    Shell.SU.run(String.format("pm enable %s/%s", packageName, provider));
                }
            }
        } catch (IOException ignored) {}
    }

    /**
     * Retrieve a set of disabled components from local source
     *
     * If it's available in the system, save a copy to the local source and then retrieve the components
     */
    private void retrieveDisabledComponents() {
        disabledComponents = new HashMap<>();
        int packageNameLength = packageName.length()+1;
        if (!localRulesFile.exists()) {
            // Rules doesn't exist in locally
            if (isRulesApplied()) {
                // But there are rules in the system
                // Copy system rules to make them accessible locally
                Shell.SU.run(String.format("cp %s%s.xml '%s'", SYSTEM_RULES_PATH, packageName, LOCAL_RULES_PATH));
            } else return;
        }
        if (localProvidersFile.exists()) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(localProvidersFile));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!line.equals("")) {
                        disabledComponents.put(line.trim(), ComponentType.PROVIDER);
                    }
                }
                bufferedReader.close();
            } catch (IOException ignored) {}
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
                            disabledComponents.put(fullKey.substring(packageNameLength), componentType);
                        }
                }
                event = parser.nextTag();
            }
            rulesStream.close();
        } catch (IOException | XmlPullParserException ignored) {}
    }

    /**
     * Get component type from TAG_* constants
     * @param componentName Name of the constant: one of the TAG_*
     * @return One of the {@link ComponentType}
     */
    private ComponentType getComponentType(@NonNull String componentName) {
        switch (componentName) {
            case TAG_ACTIVITY:
                return ComponentType.ACTIVITY;
            case TAG_RECEIVER:
                return ComponentType.RECEIVER;
            case TAG_SERVICE:
                return ComponentType.SERVICE;
            default:
                return ComponentType.UNKNOWN;
        }
    }
}
