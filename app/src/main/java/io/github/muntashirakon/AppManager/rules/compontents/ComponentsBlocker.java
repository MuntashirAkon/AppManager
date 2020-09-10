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
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

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
public final class ComponentsBlocker extends RulesStorageManager {
    private static String LOCAL_RULES_PATH;
    static final String SYSTEM_RULES_PATH = "/data/system/ifw/";

    @SuppressLint("StaticFieldLeak")
    private static ComponentsBlocker INSTANCE;

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull String packageName) {
        return getInstance(packageName, false);
    }

    @NonNull
    public static ComponentsBlocker getMutableInstance(@NonNull String packageName) {
        ComponentsBlocker componentsBlocker = getInstance(packageName, true);
        componentsBlocker.readOnly = false;
        return componentsBlocker;
    }

    @NonNull
    public static ComponentsBlocker getInstance(@NonNull String packageName, boolean noLoadFromDisk) {
        if (INSTANCE == null) {
            try {
                getLocalIfwRulesPath();
                INSTANCE = new ComponentsBlocker(AppManager.getContext(), packageName);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        } else if (!INSTANCE.packageName.equals(packageName)) {
            INSTANCE.close();
            INSTANCE = null;
            INSTANCE = new ComponentsBlocker(AppManager.getContext(), packageName);
        }
        if (!noLoadFromDisk && AppPref.isRootEnabled())
            INSTANCE.retrieveDisabledComponents();
        INSTANCE.readOnly = true;
        return INSTANCE;
    }

    public static void getLocalIfwRulesPath() throws FileNotFoundException {
        Context context = AppManager.getContext();
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
    }

    private File localRulesFile;

    protected ComponentsBlocker(Context context, String packageName) {
        super(context, packageName);
        this.localRulesFile = new File(LOCAL_RULES_PATH, packageName + ".xml");
    }

    /**
     * Apply all rules configured within App Manager. This includes the external IFW path as well as
     * the internal conf path. In v2.6, the former path will be removed.
     *
     * @param context Application Context
     */
    public static void applyAllRules(@NonNull Context context) {
        // Apply all rules from conf folder
        PrivilegedFile confPath = new PrivilegedFile(context.getFilesDir(), "conf");
        String[] packageNamesWithTSV = confPath.list((dir, name) -> name.endsWith(".tsv"));
        if (packageNamesWithTSV != null) {
            // Apply rules
            String packageName;
            for (String s : packageNamesWithTSV) {
                packageName = s.substring(0, s.lastIndexOf(".tsv"));
                try (ComponentsBlocker cb = getMutableInstance(packageName)) {
                    cb.applyRules(true);
                }
            }
        }
    }

    public boolean hasComponent(String componentName) {
        return hasName(componentName);
    }

    public int componentCount() {
        int count = 0;
        for (Entry entry : getAll()) {
            if ((entry.type.equals(Type.ACTIVITY)
                    || entry.type.equals(Type.PROVIDER)
                    || entry.type.equals(Type.RECEIVER)
                    || entry.type.equals(Type.SERVICE))
                    && entry.extra != COMPONENT_TO_BE_UNBLOCKED)
                ++count;
        }
        return count;
    }

    public void addComponent(String componentName, RulesStorageManager.Type componentType) {
        if (!readOnly) setComponent(componentName, componentType, COMPONENT_TO_BE_BLOCKED);
    }

    public void removeComponent(String componentName) {
        if (readOnly) return;
        if (hasName(componentName)) {
            if (get(componentName).type == Type.PROVIDER)  // Preserve for later
                setComponent(componentName, Type.PROVIDER, COMPONENT_TO_BE_UNBLOCKED);
            else removeEntry(componentName);
        }
    }

    /**
     * Save the disabled components locally (not applied to the system)
     *
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
            if (component.extra == COMPONENT_TO_BE_UNBLOCKED) continue;
            String componentFilter = "  <component-filter name=\"" + packageName + "/" + component.name + "\"/>\n";
            RulesStorageManager.Type componentType = component.type;
            switch (component.type) {
                case ACTIVITY:
                    activities.append(componentFilter);
                    break;
                case RECEIVER:
                    receivers.append(componentFilter);
                    break;
                case SERVICE:
                    services.append(componentFilter);
                    break;
                case PROVIDER:
                    continue;
            }
            setComponent(component.name, componentType, COMPONENT_BLOCKED);
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
     * Find if there is any component that needs blocking. Previous implementations checked for
     * rules file in the system IFW directory as well, but since all controls are now inside the app
     * itself, it's no longer deemed necessary to check the existence of the file. Besides, previous
     * implementation (which was similar to Watt's) did not take providers into account, which are
     * blocked via <code>pm</code>.
     *
     * @return True if applied, false otherwise
     */
    public boolean isRulesApplied() {
        List<RulesStorageManager.Entry> entries = getAllComponents();
        for (RulesStorageManager.Entry entry : entries)
            if (entry.extra == COMPONENT_TO_BE_BLOCKED) return false;
        return true;
    }

    /**
     * Apply the currently modified rules if the the argument apply is true. Since IFW is used, when
     * apply is true, the IFW rules are saved to the system directory and components that are set to
     * be removed or unblocked will be removed (or for providers, enabled and removed). If apply is
     * set to false, all rules will be removed but before that all components will be set to their
     * default state (ie., the state described in the app manifest).
     *
     * @param apply Whether to apply the rules or remove them altogether
     */
    public void applyRules(boolean apply) {
        try {
            // Save disabled components
            if (apply) saveDisabledComponents();
            // Apply/Remove rules
            if (apply && localRulesFile.exists()) {
                // Apply rules
                Runner.runCommand(String.format(Runner.TOYBOX + " cp \"%s\" %s && " + Runner.TOYBOX + " chmod 0666 %s%s.xml && am force-stop %s",
                        localRulesFile.getAbsolutePath(), SYSTEM_RULES_PATH, SYSTEM_RULES_PATH,
                        packageName, packageName));
            } else {
                // Remove rules if remove is called or applied with no rules
                Runner.runCommand(String.format(Runner.TOYBOX + " test -e '%s%s.xml' && " + Runner.TOYBOX + " rm -rf %s%s.xml && am force-stop %s",
                        SYSTEM_RULES_PATH, packageName, SYSTEM_RULES_PATH, packageName, packageName));
            }
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            // Enable/disable components
            if (apply) {
                // Disable providers
                List<RulesStorageManager.Entry> disabledProviders = getAll(RulesStorageManager.Type.PROVIDER);
                Log.d("ComponentBlocker", "Providers: " + disabledProviders.toString());
                for (RulesStorageManager.Entry provider : disabledProviders) {
                    if (provider.extra == COMPONENT_TO_BE_UNBLOCKED) {  // Enable components that are removed
                        RunnerUtils.enableComponent(packageName, provider.name, Users.getCurrentUserHandle());
                        removeEntry(provider);
                    } else {
                        RunnerUtils.disableComponent(packageName, provider.name, Users.getCurrentUserHandle());
                        setComponent(provider.name, provider.type, COMPONENT_BLOCKED);
                    }
                }
            } else {
                // Enable all, remove to be removed components and set others to be blocked
                List<RulesStorageManager.Entry> allEntries = getAllComponents();
                Log.d("ComponentBlocker", "All: " + allEntries.toString());
                for (RulesStorageManager.Entry entry : allEntries) {
                    RunnerUtils.enableComponent(packageName, entry.name, Users.getCurrentUserHandle());  // Enable components if they're disabled by other methods
                    if (entry.extra == COMPONENT_TO_BE_UNBLOCKED) removeEntry(entry);
                    else setComponent(entry.name, entry.type, COMPONENT_TO_BE_BLOCKED);
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
        if (!AppPref.isRootEnabled()) return;
        Log.d("ComponentBlocker", "Retrieving disabled components for package " + packageName);
        PrivilegedFile rulesFile = new PrivilegedFile(SYSTEM_RULES_PATH, packageName + ".xml");
        String ruleXmlString = null;
        if (rulesFile.exists()) {
            // Copy system rules to access them locally
            ruleXmlString = Utils.getFileContent(rulesFile);
            Log.d("ComponentBlocker - IFW", "Retrieved components for package " + packageName + "\n" + ruleXmlString);
        }
        if (TextUtils.isEmpty(ruleXmlString)) {
            // Load from App Manager's saved rules
            for (RulesStorageManager.Entry entry : getAllComponents()) {
                setComponent(entry.name, entry.type, COMPONENT_TO_BE_BLOCKED);
            }
            return;
        }
        try {
            //noinspection ConstantConditions ruleXmlString is never null
            try (InputStream rulesStream = new ByteArrayInputStream(ruleXmlString.getBytes())) {
                HashMap<String, Type> components = ComponentUtils.readIFWRules(rulesStream, packageName);
                for (String componentName : components.keySet()) {
                    setComponent(componentName, components.get(componentName), COMPONENT_BLOCKED);
                }
            }
        } catch (IOException ignored) {
        }
    }
}
