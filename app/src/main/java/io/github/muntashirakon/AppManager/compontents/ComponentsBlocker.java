package io.github.muntashirakon.AppManager.compontents;

import android.content.Context;
import android.text.TextUtils;
import android.util.Xml;

import com.jaredrummler.android.shell.CommandResult;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.storage.StorageManager;

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
 * @see StorageManager
 */
public class ComponentsBlocker {
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_RECEIVER = "broadcast";
    private static final String TAG_SERVICE = "service";

    private static final String SYSTEM_RULES_PATH = "/data/system/ifw/";
    private static ComponentsBlocker componentsBlocker = null;

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull Context context, @NonNull String packageName) {
        return getInstance(context, packageName, false);
    }

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull Context context, @NonNull String packageName, boolean noLoadFromDisk) {
        if (componentsBlocker == null) {
            try {
                String localIfwRulesPath = provideLocalIfwRulesPath(context);
                componentsBlocker = new ComponentsBlocker(localIfwRulesPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        }
        if (ComponentsBlocker.packageName == null || !ComponentsBlocker.packageName.equals(packageName))
            componentsBlocker.setPackageName(context, packageName, noLoadFromDisk);
        return componentsBlocker;
    }

    @NonNull
    public static String provideLocalIfwRulesPath(@NonNull Context context) throws FileNotFoundException {
        // FIXME: Move from getExternalFilesDir to getCacheDir
        File file = context.getExternalFilesDir("ifw");
        if (file == null || (!file.exists() && !file.mkdirs())) {
            file = new File(context.getFilesDir(), "ifw");
            if (!file.exists() && !file.mkdirs()) {
                throw new FileNotFoundException("Can not get correct path to save ifw rules");
            }
        }
        return file.getAbsolutePath();
    }

    public static void applyAllRules(@NonNull Context context) {
        try {
            String ifwPath = provideLocalIfwRulesPath(context);
            CommandResult result = Shell.SU.run(String.format("ls %s/*.xml", ifwPath));
            if (result.isSuccessful()) {
                // Get packages
                List<String> packageNames = result.stdout;
                for(int i = 0; i<packageNames.size(); ++i) {
                    String s = new File(packageNames.get(i)).getName();
                    packageNames.set(i, s.substring(0, s.lastIndexOf(".xml")));
                }
                // Apply rules for each package
                for(String packageName: packageNames) {
                    getInstance(context, packageName).applyRules(true);
                }
            }
        } catch (FileNotFoundException ignore) {}
    }

    private final String LOCAL_RULES_PATH;
    private File localRulesFile;
    private File localProvidersFile;
    private Set<String> removedProviders;
    private static String packageName;
    private StorageManager storageManager;

    private ComponentsBlocker(@NonNull String localIfwRulesPath) {
        this.LOCAL_RULES_PATH = localIfwRulesPath;
    }

    /**
     * Alternative to constructor
     * @param packageName Name of the package to handle
     */
    private void setPackageName(Context context, String packageName, boolean noLoadFromDisk) {
        ComponentsBlocker.packageName = packageName;
        this.storageManager = StorageManager.getInstance(context, packageName);
        this.localRulesFile = new File(LOCAL_RULES_PATH, packageName + ".xml");
        this.localProvidersFile = new File(LOCAL_RULES_PATH, packageName + ".txt");
        removedProviders = new HashSet<>();
        if (!noLoadFromDisk) retrieveDisabledComponents();
    }

    public Boolean hasComponent(String componentName) {
        return storageManager.hasName(componentName);
    }

    public int componentCount() {
        return storageManager.getAllComponents().size();
    }

    public void addComponent(String componentName, StorageManager.Type componentType) {
        storageManager.setComponent(componentName, componentType, false);
    }

    public void removeComponent(String componentName) {
        if (storageManager.hasName(componentName)) {
            if (storageManager.get(componentName).type == StorageManager.Type.PROVIDER)
                removedProviders.add(componentName);
            storageManager.removeEntry(componentName);
        }
    }

    /**
     * Save the disabled components locally (not applied to the system)
     * @throws IOException If it fails to write to the destination file
     */
    private void saveDisabledComponents() throws IOException {
        if (componentCount() == 0) {
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            return;
        }
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (StorageManager.Entry component : storageManager.getAllComponents()) {
            String componentFilter = "  <component-filter name=\"" + packageName + "/" + component.name + "\"/>\n";
            StorageManager.Type componentType = component.type;
            switch (component.type) {
                case ACTIVITY: activities.append(componentFilter); break;
                case RECEIVER: receivers.append(componentFilter); break;
                case SERVICE: services.append(componentFilter); break;
            }
            storageManager.setComponent(component.name, componentType, true);
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
    }

    /**
     * Check whether rules are applied successfully
     * @return True if applied, false otherwise
     */
    public boolean isRulesApplied() {
        List<StorageManager.Entry> entries = storageManager.getAllComponents();
        for (StorageManager.Entry entry: entries) if (!((Boolean) entry.extra)) return false;
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
                Shell.SU.run(String.format("cp '%s' %s && chmod 0666 %s%s.xml && am force-stop %s",
                        localRulesFile.getAbsolutePath(), SYSTEM_RULES_PATH, SYSTEM_RULES_PATH,
                        packageName, packageName));
            } else {
                // Remove rules
                Shell.SU.run(String.format("test -e '%s%s.xml' && rm -rf %s%s.xml && am force-stop %s",
                        SYSTEM_RULES_PATH, packageName, SYSTEM_RULES_PATH, packageName, packageName));
            }
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            // Enable removed providers
            for (String provider : removedProviders) {
                Shell.SU.run(String.format("pm enable %s/%s", packageName, provider));
            }
            // Read from storage manager
            List<StorageManager.Entry> disabledProviders = storageManager.getAll(StorageManager.Type.PROVIDER);
            // Enable/disable components
            if (apply) {
                // Disable providers
                for (StorageManager.Entry provider: disabledProviders) {
                    Shell.SU.run(String.format("pm disable %s/%s", packageName, provider.name));
                    storageManager.setComponent(provider.name, provider.type, true);
                }
            } else {
                // Enable providers
                for (StorageManager.Entry provider: disabledProviders) {
                    Shell.SU.run(String.format("pm enable %s/%s", packageName, provider.name));
                    storageManager.removeEntry(provider);
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
        int packageNameLength = packageName.length()+1;
        if (isRulesApplied()) {
            // Copy system rules to access them locally
            // FIXME: Read all files instead of just one for greater compatibility
            // FIXME: In v2.5.7, file contents will be copied instead of copying the file itself
            Shell.SU.run(String.format("cp %s%s.xml '%s' && chmod 0666 '%s/%s.xml'",
                    SYSTEM_RULES_PATH, packageName, LOCAL_RULES_PATH, LOCAL_RULES_PATH, packageName));
        }
        retrieveDisabledProviders();
        try {
            if (!localRulesFile.exists()) {
                for (StorageManager.Entry entry: storageManager.getAllComponents()) {
                    storageManager.setComponent(entry.name, entry.type, false);
                }
                return;
            }
            FileInputStream rulesStream = new FileInputStream(localRulesFile);
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(rulesStream, null);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "rules");
            int event = parser.nextTag();
            StorageManager.Type componentType = StorageManager.Type.UNKNOWN;
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
                            // FIXME: Verify package name
                            // Overwrite rules if exists
                            storageManager.setComponent(fullKey.substring(packageNameLength), componentType, true);
                        }
                }
                event = parser.nextTag();
            }
            rulesStream.close();
            //noinspection ResultOfMethodCallIgnored
            localRulesFile.delete();
        } catch (IOException | XmlPullParserException ignored) {}
    }

    // FIXME: Remove this in v2.5.7
    @Deprecated
    private void retrieveDisabledProviders() {
        // Read from external provider file if exists and delete it
        if (localProvidersFile.exists()) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(localProvidersFile));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (!TextUtils.isEmpty(line))
                        storageManager.setComponent(line.trim(), StorageManager.Type.PROVIDER, true);
                }
                bufferedReader.close();
                //noinspection ResultOfMethodCallIgnored
                localProvidersFile.delete();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Get component type from TAG_* constants
     * @param componentName Name of the constant: one of the TAG_*
     * @return One of the {@link StorageManager.Type}
     */
    private StorageManager.Type getComponentType(@NonNull String componentName) {
        switch (componentName) {
            case TAG_ACTIVITY: return StorageManager.Type.ACTIVITY;
            case TAG_RECEIVER: return StorageManager.Type.RECEIVER;
            case TAG_SERVICE: return StorageManager.Type.SERVICE;
            default: return StorageManager.Type.UNKNOWN;
        }
    }
}
