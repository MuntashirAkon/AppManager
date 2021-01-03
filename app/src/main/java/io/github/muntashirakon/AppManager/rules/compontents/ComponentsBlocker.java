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
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;

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
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;

/**
 * Block application components: activities, broadcasts, services and providers.
 * <p>
 * Activities, broadcasts and services are blocked via Intent Firewall (which is superior to
 * <code>pm disable <b>component</b></code>). Rules for each package is saved as a separate tsv file
 * named after its package name and saved to {@code /data/data/${applicationId}/files/conf}. In case
 * of activities, broadcasts and services, the rules are finally saved to {@link #SYSTEM_RULES_PATH}
 * via {@link #LOCAL_RULES_PATH}.
 * <p>
 * Providers are blocked via <code>pm disable <b>provider</b></code> since there's no way to block
 * them via Intent Firewall. Blocked providers are only kept in the
 * {@code /data/data/${applicationId}/files/conf} directory.
 *
 * @see <a href="https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/firewall/IntentFirewall.java">IntentFirewall.java</a>
 */
public final class ComponentsBlocker extends RulesStorageManager {
    public static final String TAG = "ComponentBlocker";

    private static String LOCAL_RULES_PATH;
    static final String SYSTEM_RULES_PATH;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            SYSTEM_RULES_PATH = "/data/secure/system/ifw";
        } else {
            SYSTEM_RULES_PATH = "/data/system/ifw/";
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static ComponentsBlocker INSTANCE;

    /**
     * Get a new or existing IMMUTABLE instance of {@link ComponentsBlocker}. The existing instance
     * will only be returned if the existing instance has the same package name as the original.
     * This read rules from the {@link #SYSTEM_RULES_PATH}. If reading rules is necessary, use
     * {@link #getInstance(String, int, boolean)} with the last argument set to true. It is also
     * possible to make this instance mutable by calling {@link #setMutable()} and once set mutable,
     * closing this instance will commit the changes automatically. To prevent this,
     * {@link #setReadOnly()} should be called before closing the instance.
     *
     * @param packageName The package whose instance is to be returned
     * @param userHandle  The user to whom the rules belong
     * @return New or existing immutable instance for the package
     * @see #getInstance(String, int, boolean)
     * @see #getMutableInstance(String, int)
     */
    @NonNull
    public static ComponentsBlocker getInstance(@NonNull String packageName, int userHandle) {
        return getInstance(packageName, userHandle, false);
    }

    /**
     * Get a new or existing MUTABLE instance of {@link ComponentsBlocker}. This DOES NOT read rules
     * from the {@link #SYSTEM_RULES_PATH}. This is essentially the same as calling
     * {@link #getInstance(String, int, boolean)} with the last argument set to {@code true} and
     * calling {@link #setMutable()} after that. Closing this instance will commit the changes
     * automatically. To prevent this, {@link #setReadOnly()} should be called before closing
     * the instance.
     *
     * @param packageName The package whose instance is to be returned
     * @param userHandle  The user to whom the rules belong
     * @return New or existing mutable instance for the package
     * @see #getInstance(String, int)
     * @see #getInstance(String, int, boolean)
     */
    @NonNull
    public static ComponentsBlocker getMutableInstance(@NonNull String packageName, int userHandle) {
        ComponentsBlocker componentsBlocker = getInstance(packageName, userHandle, true);
        componentsBlocker.readOnly = false;
        return componentsBlocker;
    }

    /**
     * Get a new or existing IMMUTABLE instance of {@link ComponentsBlocker}. The existing instance
     * will only be returned if the existing instance has the same package name as the original. It
     * is also possible to make this instance mutable by calling {@link #setMutable()} and once set
     * mutable, closing this instance will commit the changes automatically. To prevent this,
     * {@link #setReadOnly()} should be called before closing the instance.
     *
     * @param packageName    The package whose instance is to be returned
     * @param userHandle     The user to whom the rules belong
     * @param noLoadFromDisk Whether not to load rules from the {@link #SYSTEM_RULES_PATH}
     * @return New or existing immutable instance for the package
     * @see #getInstance(String, int)
     * @see #getMutableInstance(String, int)
     */
    @NonNull
    public static ComponentsBlocker getInstance(@NonNull String packageName, int userHandle, boolean noLoadFromDisk) {
        // TODO(3/12/20): Handle multiple users
        if (INSTANCE == null) {
            try {
                getLocalIfwRulesPath();
                INSTANCE = new ComponentsBlocker(AppManager.getContext(), packageName, userHandle);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        } else if (!INSTANCE.packageName.equals(packageName)) {
            INSTANCE.close();
            INSTANCE = null;
            INSTANCE = new ComponentsBlocker(AppManager.getContext(), packageName, userHandle);
        }
        if (!noLoadFromDisk && AppPref.isRootEnabled())
            INSTANCE.retrieveDisabledComponents();
        INSTANCE.readOnly = true;
        return INSTANCE;
    }

    /**
     * Get locally stored IFW rules path. Currently set to
     * {@code /sdcard/Android/data/${applicationId}/files/ifw} or
     * {@code /data/data/${applicationId}/files/ifw} depending on the availability of the external
     * storage. This path is only used briefly, before writing to the {@link #SYSTEM_RULES_PATH}.
     *
     * @throws FileNotFoundException If there's no path available where rules can be stored locally
     */
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

    private final File localRulesFile;

    protected ComponentsBlocker(Context context, String packageName, int userHandle) {
        super(context, packageName, userHandle);
        this.localRulesFile = new File(LOCAL_RULES_PATH, packageName + ".xml");
    }

    /**
     * Apply all rules configured within App Manager. This also includes {@link #SYSTEM_RULES_PATH}.
     *
     * @param context    Application Context
     * @param userHandle The user to apply rules
     */
    public static void applyAllRules(@NonNull Context context, int userHandle) {
        // Apply all rules from conf folder
        File confPath = new File(context.getFilesDir(), "conf");
        String[] packageNamesWithTSVExt = confPath.list((dir, name) -> name.endsWith(".tsv"));
        if (packageNamesWithTSVExt != null) {
            // Apply rules
            for (String packageNameWithTSVExt : packageNamesWithTSVExt) {
                try (ComponentsBlocker cb = getMutableInstance(IOUtils.trimExtension(packageNameWithTSVExt), userHandle)) {
                    cb.applyRules(true);
                }
            }
        }
    }

    /**
     * Check if the given component exists in the rules. It does not necessarily mean that the
     * component is being blocked.
     *
     * @param componentName The component name to check
     * @return {@code true} if exists, {@code false} otherwise
     *
     * @see #isComponentBlocked(String)
     */
    public boolean hasComponent(String componentName) {
        return hasName(componentName);
    }

    /**
     * Whether the given component is blocked.
     * @param componentName The component name to check
     * @return {@code true} if blocked, {@code false} otherwise
     */
    public boolean isComponentBlocked(String componentName) {
        return hasComponent(componentName) && get(componentName).extra == COMPONENT_BLOCKED;
    }

    /**
     * Get number of components among other rules.
     *
     * @return Number of components
     */
    public int componentCount() {
        int count = 0;
        for (Entry entry : getAll()) {
            if (isComponent(entry) && entry.extra != COMPONENT_TO_BE_UNBLOCKED)
                ++count;
        }
        return count;
    }

    /**
     * Add the given component to the rules list, does nothing if the instance is immutable.
     *
     * @param componentName The component to add
     * @param componentType Component type
     *
     * @see #addEntry(Entry)
     */
    public void addComponent(String componentName, RulesStorageManager.Type componentType) {
        if (!readOnly) setComponent(componentName, componentType, COMPONENT_TO_BE_BLOCKED);
    }

    /**
     * Suggest removal of the given component from the rules, does nothing if the instance is
     * immutable or the component does not exist. The rules are only applied when {@link #commit()}
     * is called.
     *
     * @param componentName The component to remove
     * @see #removeEntry(Entry)
     * @see #removeEntry(String)
     */
    public void removeComponent(String componentName) {
        if (readOnly) return;
        if (hasName(componentName)) {
            setComponent(componentName, get(componentName).type, COMPONENT_TO_BE_UNBLOCKED);
        }
    }

    /**
     * Save the disabled components in the {@link #LOCAL_RULES_PATH}.
     *
     * @throws IOException If it fails to write to the destination file
     */
    private void saveDisabledComponents() throws IOException {
        if (readOnly) throw new IOException("Saving disabled components in read only mode.");
        if (componentCount() == 0) {
            // No components set, delete if already exists
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            return;
        }
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (RulesStorageManager.Entry component : getAllComponents()) {
            // Ignore components that needs unblocking
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
            Log.d(TAG, "Rules: " + rules);
            rulesStream.write(rules.getBytes());
        }
    }

    /**
     * Find if there is any component that needs blocking. Previous implementations checked for
     * rules file in the system IFW directory as well, but since all controls are now inside the app
     * itself, it's no longer deemed necessary to check the existence of the file. Besides, previous
     * implementation (which was similar to Watt's) did not take providers into account, which are
     * blocked via {@code pm}.
     *
     * @return {@code true} if there's no pending rules, {@code false} otherwise
     */
    public boolean isRulesApplied() {
        List<RulesStorageManager.Entry> entries = getAllComponents();
        for (RulesStorageManager.Entry entry : entries)
            if (entry.extra == COMPONENT_TO_BE_BLOCKED) return false;
        return true;
    }

    /**
     * Apply the currently modified rules if the the argument apply is true. Since IFW is used, when
     * apply is true, the IFW rules are saved to {@link #SYSTEM_RULES_PATH} and components that are
     * set to be removed or unblocked will be removed. If apply is set to false, all rules will be
     * removed but before that all components will be set to their default state (ie., the state
     * described in the app manifest).
     *
     * @param apply Whether to apply the rules or remove them altogether
     */
    public void applyRules(boolean apply) {
        try {
            // Save blocked IFW components
            if (apply) saveDisabledComponents();
            // Apply/Remove IFW rules
            if (apply && localRulesFile.exists()) {
                // Apply rules
                Runner.runCommand(String.format(Runner.TOYBOX + " cp \"%s\" %s && "
                                + Runner.TOYBOX + " chmod 0666 %s%s.xml && "
                                + RunnerUtils.CMD_FORCE_STOP_PACKAGE,
                        localRulesFile.getAbsolutePath(), SYSTEM_RULES_PATH, SYSTEM_RULES_PATH,
                        packageName, RunnerUtils.userHandleToUser(userHandle), packageName));
            } else {
                // Remove rules if remove is called or applied with no rules
                Runner.runCommand(String.format(Runner.TOYBOX + " test -e '%s%s.xml' && "
                                + Runner.TOYBOX + " rm -rf %s%s.xml && "
                                + RunnerUtils.CMD_FORCE_STOP_PACKAGE,
                        SYSTEM_RULES_PATH, packageName, SYSTEM_RULES_PATH, packageName,
                        RunnerUtils.userHandleToUser(userHandle), packageName));
            }
            if (localRulesFile.exists()) //noinspection ResultOfMethodCallIgnored
                localRulesFile.delete();
            // Enable/disable components
            List<RulesStorageManager.Entry> allEntries = getAllComponents();
            Log.d(TAG, "All: " + allEntries.toString());
            if (apply) {
                // Enable the components that need removal and disable requested components
                for (RulesStorageManager.Entry entry : allEntries) {
                    if (entry.extra == COMPONENT_TO_BE_UNBLOCKED) {
                        // Enable components that are removed
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(new ComponentName(packageName, entry.name), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0, userHandle);
                            removeEntry(entry);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not enable component: " + packageName + "/" + entry.name);
                        }
                    } else if (isComponent(entry)) {
                        // Disable components
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(new ComponentName(packageName, entry.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, userHandle);
                            setComponent(entry.name, entry.type, COMPONENT_BLOCKED);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Could not disable component: " + packageName + "/" + entry.name);
                        }
                    }
                }
            } else {
                // Enable all, remove to be removed components and set others to be blocked
                for (RulesStorageManager.Entry entry : allEntries) {
                    // Enable components if they're disabled by other methods.
                    // IFW rules are already removed above.
                    try {
                        PackageManagerCompat.setComponentEnabledSetting(new ComponentName(packageName, entry.name), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0, userHandle);
                        if (entry.extra == COMPONENT_TO_BE_UNBLOCKED) removeEntry(entry);
                        else setComponent(entry.name, entry.type, COMPONENT_TO_BE_BLOCKED);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Could not enable component: " + packageName + "/" + entry.name);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isComponent(@NonNull Entry entry) {
        return entry.type.equals(Type.ACTIVITY)
                || entry.type.equals(Type.PROVIDER)
                || entry.type.equals(Type.RECEIVER)
                || entry.type.equals(Type.SERVICE);
    }

    /**
     * Retrieve a set of disabled components from the {@link #SYSTEM_RULES_PATH}. If they are
     * available add them to the rules, overridden if necessary.
     */
    private void retrieveDisabledComponents() {
        if (!AppPref.isRootEnabled()) return;
        Log.d(TAG, "Retrieving disabled components for package " + packageName);
        PrivilegedFile rulesFile = new PrivilegedFile(SYSTEM_RULES_PATH, packageName + ".xml");
        String ruleXmlString;
        if (rulesFile.exists()) {
            // Read system rules
            ruleXmlString = IOUtils.getFileContent(rulesFile);
            Log.d(TAG, "IFW: Retrieved components for package " + packageName + "\n" + ruleXmlString);
        } else ruleXmlString = null;
        if (TextUtils.isEmpty(ruleXmlString)) {
            // System doesn't have any rules.
            // Load the rules saved inside App Manager
            for (RulesStorageManager.Entry entry : getAllComponents()) {
                setComponent(entry.name, entry.type, COMPONENT_TO_BE_BLOCKED);
            }
            return;
        }
        try {
            //noinspection ConstantConditions ruleXmlString is never null here
            try (InputStream rulesStream = new ByteArrayInputStream(ruleXmlString.getBytes())) {
                HashMap<String, Type> components = ComponentUtils.readIFWRules(rulesStream, packageName);
                for (String componentName : components.keySet()) {
                    // Override existing rule for the component if it exists
                    setComponent(componentName, components.get(componentName), COMPONENT_BLOCKED);
                }
            }
        } catch (IOException ignored) {
        }
    }
}
