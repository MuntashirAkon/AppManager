package io.github.muntashirakon.AppManager.storage.compontents;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

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
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * Block application components: activities, broadcasts, services and providers.
 * <br><br>
 * Activities, broadcasts and services are blocked via Intent Firewall (which is superior to the
 * <code>pm disable <b>component</b></code> method). Rules for each package is saved as a separate
 * xml file which is named after the package name and saved to /data/system/ifw and
 * /sdcard/Android/data/io.github.muntashirakon.AppManager/files/ifw. By default, data is read from
 * both directories but written only to the latter directory unless
 * {@link ComponentsBlocker#applyRules(boolean)} is called in which case data is
 * saved to the former directory (rules are applied automatically once they're copied there).
 * <br>
 * Providers are blocked via <code>pm disable <b>component</b></code> method since there's no way to
 * block them via intent firewall. blocked providers are only kept in the /sdcard/Android/data/io.github.muntashirakon.AppManager/files/ifw
 * directory. The file name is the same as above but with .txt extension instead of .xml and each
 * provider is saved in a new line.
 *
 * @see <a href="https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/firewall/IntentFirewall.java">IntentFirewall.java</a>
 */
public class ComponentsBlocker extends RulesStorageManager {
    public static final String TAG_ACTIVITY = "activity";
    public static final String TAG_RECEIVER = "broadcast";
    public static final String TAG_SERVICE = "service";

    private static final String SYSTEM_RULES_PATH = "/data/system/ifw/";
    private static String LOCAL_RULES_PATH;

    private static @NonNull HashMap<String, ComponentsBlocker> componentsBlockers = new HashMap<>();

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull Context context, @NonNull String packageName) {
        return getInstance(context, packageName, false);
    }

    @NonNull
    public static ComponentsBlocker getMutableInstance(@NonNull Context context, @NonNull String packageName) {
        ComponentsBlocker componentsBlocker = getInstance(context, packageName, true);
        componentsBlocker.readOnly = false;
        return componentsBlocker;
    }

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull Context context, @NonNull String packageName, boolean noLoadFromDisk) {
        if (!componentsBlockers.containsKey(packageName)) {
            try {
                getLocalIfwRulesPath(context);
                componentsBlockers.put(packageName, new ComponentsBlocker(context, packageName));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        }
        ComponentsBlocker componentsBlocker = componentsBlockers.get(packageName);
        if (!noLoadFromDisk && AppPref.isRootEnabled()) //noinspection ConstantConditions
            componentsBlocker.retrieveDisabledComponents();
        //noinspection ConstantConditions
        componentsBlocker.readOnly = true;
        return componentsBlocker;
    }

    @NonNull
    public static String getLocalIfwRulesPath(@NonNull Context context)
            throws FileNotFoundException {
        // FIXME: Move from getExternalFilesDir to getCacheDir
        if (LOCAL_RULES_PATH == null) {
            File file = context.getExternalFilesDir("ifw");
            if (file == null || (!file.exists() && !file.mkdirs())) {
                file = new File(context.getFilesDir(), "ifw");
                if (!file.exists() && !file.mkdirs()) {
                    throw new FileNotFoundException("Can not get correct path to save ifw rules");
                }
            }
            LOCAL_RULES_PATH = file.getAbsolutePath();
        }
        return LOCAL_RULES_PATH;
    }

    protected ComponentsBlocker(Context context, String packageName) {
        super(context, packageName);
        this.localRulesFile = new File(LOCAL_RULES_PATH, packageName + ".xml");
        this.localProvidersFile = new File(LOCAL_RULES_PATH, packageName + ".txt");
        removedProviders = new HashSet<>();
    }

    /**
     * Apply all rules configured within App Manager. This includes the external IFW path as well as
     * the internal conf path. In v2.6, the former path will be removed.
     * @param context Application Context
     */
    public static void applyAllRules(@NonNull Context context) {
        // Add all rules from the local IFW folder
        addAllLocalRules(context);
        // Apply all rules from conf folder
        File confPath = new File(context.getFilesDir(), "conf");
        Runner.run(context, String.format("ls %s/*.tsv", confPath.getAbsolutePath()));
        if (Runner.getLastResult().isSuccessful()) {
            // Get packages
            List<String> packageNames = Runner.getLastResult().getOutputAsList();
            for (int i = 0; i<packageNames.size(); ++i) {
                String s = new File(packageNames.get(i)).getName();
                packageNames.set(i, s.substring(0, s.lastIndexOf(".tsv")));
            }
            // Apply rules for each package
            for (String packageName: packageNames) {
                try (ComponentsBlocker cb = getMutableInstance(context, packageName)) {
                    cb.applyRules(true);
                }
            }
        }
    }

    /**
     * Add all rules from the external IFW path
     * @param context Application context
     * @deprecated Due to the fact that any application can edit or delete these files, this method
     *      is not secured and will be removed in v2.6
     */
    @Deprecated
    public static void addAllLocalRules(@NonNull Context context) {
        try {
            String ifwPath = getLocalIfwRulesPath(context);
            Runner.run(context, String.format("ls %s/*.xml", ifwPath));
            if (Runner.getLastResult().isSuccessful()) {
                // Get packages
                List<String> packageNames = Runner.getLastResult().getOutputAsList();
                for (int i = 0; i<packageNames.size(); ++i) {
                    String s = new File(packageNames.get(i)).getName();
                    packageNames.set(i, s.substring(0, s.lastIndexOf(".xml")));
                }
                // Apply rules for each package
                for (String packageName: packageNames) {
                    try (ComponentsBlocker cb = getInstance(context, packageName)) {
                        // Make the instance mutable
                        cb.readOnly = false;
                    }
                }
            }
        } catch (FileNotFoundException ignore) {}
    }

    private File localRulesFile;
    private File localProvidersFile;
    private Set<String> removedProviders;

    public Boolean hasComponent(String componentName) {
        return hasName(componentName);
    }

    public int componentCount() {
        int count = 0;
        for (Entry entry: getAll()) {
            if (entry.type.equals(Type.ACTIVITY)
                    || entry.type.equals(Type.PROVIDER)
                    || entry.type.equals(Type.RECEIVER)
                    || entry.type.equals(Type.SERVICE))
                ++count;
        }
        return count;
    }

    public void addComponent(String componentName, RulesStorageManager.Type componentType) {
        if (!readOnly) setComponent(componentName, componentType, false);
    }

    public void removeComponent(String componentName) {
        if (readOnly) return;
        if (hasName(componentName)) {
            if (get(componentName).type == RulesStorageManager.Type.PROVIDER)
                removedProviders.add(componentName);
            removeEntry(componentName);
        }
    }

    /**
     * Save the disabled components locally (not applied to the system)
     * @throws IOException If it fails to write to the destination file
     */
    private void saveDisabledComponents() throws IOException {
        if (readOnly) throw new IOException("Saving disabled components in read only mode.");
        if (componentCount() == 0) {
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            return;
        }
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (RulesStorageManager.Entry component : getAllComponents()) {
            String componentFilter = "  <component-filter name=\"" + packageName + "/" + component.name + "\"/>\n";
            RulesStorageManager.Type componentType = component.type;
            switch (component.type) {
                case ACTIVITY: activities.append(componentFilter); break;
                case RECEIVER: receivers.append(componentFilter); break;
                case SERVICE: services.append(componentFilter); break;
            }
            setComponent(component.name, componentType, true);
        }

        String rules = "<rules>\n" +
                ((activities.length() == 0) ? "" : "<activity block=\"true\" log=\"false\">\n" + activities + "</activity>\n") +
                ((services.length() == 0) ? "" : "<service block=\"true\" log=\"false\">\n" + services + "</service>\n") +
                ((receivers.length() == 0) ? "" : "<broadcast block=\"true\" log=\"false\">\n" + receivers + "</broadcast>\n") +
                "</rules>";
        // Save rules
        try (FileOutputStream rulesStream = new FileOutputStream(localRulesFile)) {
            Log.d("Rules", rules);
            rulesStream.write(rules.getBytes());
        }
    }

    /**
     * Check whether previous rules are applied successfully
     * @return True if applied, false otherwise
     */
    public boolean isRulesApplied() {
        List<RulesStorageManager.Entry> entries = getAllComponents();
        if (AppPref.isRootEnabled() && Runner.run(context, String.format("test -e '%s%s.xml'",
                SYSTEM_RULES_PATH, packageName)).isSuccessful()) return true;
        for (RulesStorageManager.Entry entry: entries) if (!((Boolean) entry.extra)) return false;
        return true;
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
                Runner.run(context, String.format("cp '%s' %s && chmod 0666 %s%s.xml && am force-stop %s",
                        localRulesFile.getAbsolutePath(), SYSTEM_RULES_PATH, SYSTEM_RULES_PATH,
                        packageName, packageName));
            } else {
                // Remove rules
                Runner.run(context, String.format("test -e '%s%s.xml' && rm -rf %s%s.xml && am force-stop %s",
                        SYSTEM_RULES_PATH, packageName, SYSTEM_RULES_PATH, packageName, packageName));
            }
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            // Enable removed providers
            for (String provider : removedProviders) {
                Runner.run(context, String.format("pm enable %s/%s", packageName, provider));
            }
            // Read from storage manager
            List<RulesStorageManager.Entry> disabledProviders = getAll(RulesStorageManager.Type.PROVIDER);
            // Enable/disable components
            if (apply) {
                // Disable providers
                for (RulesStorageManager.Entry provider: disabledProviders) {
                    Runner.run(context, String.format("pm disable %s/%s", packageName, provider.name));
                    setComponent(provider.name, provider.type, true);
                }
            } else {
                // Enable providers
                for (RulesStorageManager.Entry provider: disabledProviders) {
                    Runner.run(context, String.format("pm enable %s/%s", packageName, provider.name));
                    removeEntry(provider);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve a set of disabled components from local source. If it's available in the system,
     * save a copy to the local source and then retrieve the components
     */
    private void retrieveDisabledComponents() {
        Log.d("ComponentBlocker", "Retrieving disabled components for package " + packageName);
        if (AppPref.isRootEnabled() && Runner.run(context, String.format("test -e '%s%s.xml'",
                SYSTEM_RULES_PATH, packageName)).isSuccessful()) {
            // Copy system rules to access them locally
            Log.d("ComponentBlocker - IFW", "Copying disabled components for package " + packageName);
            // FIXME: Read all files instead of just one for greater compatibility
            // FIXME: In v2.6, file contents will be copied instead of copying the file itself
            Runner.run(context, String.format("cp %s%s.xml '%s' && chmod 0666 '%s/%s.xml'",
                    SYSTEM_RULES_PATH, packageName, LOCAL_RULES_PATH, LOCAL_RULES_PATH, packageName));
        }
        retrieveDisabledProviders();
        try {
            if (!localRulesFile.exists()) {
                for (RulesStorageManager.Entry entry: getAllComponents()) {
                    setComponent(entry.name, entry.type, false);
                }
                return;
            }
            try (FileInputStream rulesStream = new FileInputStream(localRulesFile)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(rulesStream, null);
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, null, "rules");
                int event = parser.nextTag();
                RulesStorageManager.Type componentType = RulesStorageManager.Type.UNKNOWN;
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
                                int divider = fullKey.indexOf('/');
                                String pkgName = fullKey.substring(0, divider);
                                String componentName = fullKey.substring(divider + 1);
                                if (pkgName.equals(packageName)) {
                                    // Overwrite rules if exists
                                    setComponent(componentName, componentType, true);
                                }
                            }
                    }
                    event = parser.nextTag();
                }
            }
            //noinspection ResultOfMethodCallIgnored
            localRulesFile.delete();
        } catch (IOException | XmlPullParserException ignored) {}
    }

    /**
     * Retrieve disabled providers from the local IFW path.
     * @deprecated Due to the fact that any application can edit or delete these files, this method
     *      is not secured and will be removed in v2.6
     */
    @Deprecated
    private void retrieveDisabledProviders() {
        // Read from external provider file if exists and delete it
        if (localProvidersFile.exists()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(localProvidersFile))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!TextUtils.isEmpty(line))
                        setComponent(line.trim(), RulesStorageManager.Type.PROVIDER, true);
                }
            } catch (IOException ignored) {
            } finally {
                //noinspection ResultOfMethodCallIgnored
                localProvidersFile.delete();
            }
        }
    }

    /**
     * Get component type from TAG_* constants
     * @param componentName Name of the constant: one of the TAG_*
     * @return One of the {@link RulesStorageManager.Type}
     */
    RulesStorageManager.Type getComponentType(@NonNull String componentName) {
        switch (componentName) {
            case TAG_ACTIVITY: return RulesStorageManager.Type.ACTIVITY;
            case TAG_RECEIVER: return RulesStorageManager.Type.RECEIVER;
            case TAG_SERVICE: return RulesStorageManager.Type.SERVICE;
            default: return RulesStorageManager.Type.UNKNOWN;
        }
    }
}
