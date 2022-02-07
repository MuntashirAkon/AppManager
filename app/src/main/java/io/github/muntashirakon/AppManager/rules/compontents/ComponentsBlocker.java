// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.AtomicProxyFile;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyOutputStream;

import static io.github.muntashirakon.AppManager.appops.AppOpsManager.OP_NONE;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;

/**
 * Block application components: activities, broadcasts, services and providers.
 * <p>
 * Activities, broadcasts and services are blocked via Intent Firewall (which is superior to
 * <code>pm disable <b>component</b></code>). Rules for each package is saved as a separate tsv file
 * named after its package name and saved to {@code /data/data/${applicationId}/files/conf}. In case
 * of activities, broadcasts and services, the rules are finally saved to {@link #SYSTEM_RULES_PATH}.
 * <p>
 * Providers are blocked via {@link PackageManager#setComponentEnabledSetting(ComponentName, int, int)}
 * since there's no way to block them via Intent Firewall. Blocked providers are only kept in the
 * {@code /data/data/${applicationId}/files/conf} directory.
 *
 * @see <a href="https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/firewall/IntentFirewall.java">IntentFirewall.java</a>
 */
public final class ComponentsBlocker extends RulesStorageManager {
    public static final String TAG = "ComponentBlocker";

    static final ProxyFile SYSTEM_RULES_PATH;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            SYSTEM_RULES_PATH = new ProxyFile("/data/secure/system/ifw");
        } else {
            SYSTEM_RULES_PATH = new ProxyFile("/data/system/ifw");
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static ComponentsBlocker sInstance;

    /**
     * Get a new or existing IMMUTABLE instance of {@link ComponentsBlocker}. The existing instance
     * will only be returned if the existing instance has the same package name as the original.
     * This reads rules from the {@link #SYSTEM_RULES_PATH}. If reading rules is necessary, use
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
     * @param packageName      The package whose instance is to be returned
     * @param userHandle       The user to whom the rules belong
     * @param noReloadFromDisk Whether not to load rules from the {@link #SYSTEM_RULES_PATH}
     * @return New or existing immutable instance for the package
     * @see #getInstance(String, int)
     * @see #getMutableInstance(String, int)
     */
    @NonNull
    public static ComponentsBlocker getInstance(@NonNull String packageName, int userHandle, boolean noReloadFromDisk) {
        if (sInstance == null) {
            sInstance = new ComponentsBlocker(packageName, userHandle);
        } else if (!noReloadFromDisk || !sInstance.packageName.equals(packageName)) {
            sInstance.close();
            sInstance = new ComponentsBlocker(packageName, userHandle);
        }
        if (!noReloadFromDisk && Ops.isRoot()) {
            sInstance.retrieveDisabledComponents();
        }
        sInstance.readOnly = true;
        return sInstance;
    }

    @NonNull
    private final AtomicProxyFile mRulesFile;
    @NonNull
    private Set<String> mComponents;
    @Nullable
    private PackageInfo mPackageInfo;

    private ComponentsBlocker(String packageName, int userHandle) {
        super(packageName, userHandle);
        mRulesFile = new AtomicProxyFile(new ProxyFile(SYSTEM_RULES_PATH, packageName + ".xml"));
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | flagDisabledComponents
                    | flagMatchUninstalled | PackageManager.GET_SERVICES, userHandle);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        mComponents = PackageUtils.collectComponentClassNames(mPackageInfo).keySet();
    }

    /**
     * Reload package components
     */
    public void reloadComponents() {
        mComponents = PackageUtils.collectComponentClassNames(mPackageInfo).keySet();
    }

    /**
     * Apply all rules configured within App Manager. This also includes {@link #SYSTEM_RULES_PATH}.
     *
     * @param context    Application Context
     * @param userHandle The user to apply rules
     * @return {@code true} iff all rules are applied correctly.
     */
    @WorkerThread
    public static boolean applyAllRules(@NonNull Context context, int userHandle) {
        // Apply all rules from conf folder
        File confPath = new File(context.getFilesDir(), "conf");
        String[] packageNamesWithTSVExt = confPath.list((dir, name) -> name.endsWith(".tsv"));
        boolean isSuccessful = true;
        if (packageNamesWithTSVExt != null) {
            // Apply rules
            for (String packageNameWithTSVExt : packageNamesWithTSVExt) {
                try (ComponentsBlocker cb = getMutableInstance(FileUtils.trimExtension(packageNameWithTSVExt), userHandle)) {
                    isSuccessful &= cb.applyRules(true);
                }
            }
        }
        return isSuccessful;
    }

    /**
     * Whether the given component is blocked.
     *
     * @param componentName The component name to check
     * @return {@code true} if blocked, {@code false} otherwise
     */
    public boolean isComponentBlocked(String componentName) {
        ComponentRule cr = getComponent(componentName);
        return cr != null && cr.isBlocked();
    }

    /**
     * Check if the given component exists in the rules. It does not necessarily mean that the
     * component is being blocked.
     *
     * @param componentName The component name to check
     * @return {@code true} if exists, {@code false} otherwise
     * @see ComponentsBlocker#isComponentBlocked(String)
     */
    @GuardedBy("entries")
    public boolean hasComponentName(String componentName) {
        for (ComponentRule entry : getAllComponents()) if (entry.name.equals(componentName)) return true;
        return false;
    }

    /**
     * Get number of components among other rules.
     *
     * @return Number of components
     */
    public int componentCount() {
        int count = 0;
        for (ComponentRule entry : getAllComponents()) {
            if (!entry.toBeRemoved()) ++count;
        }
        return count;
    }

    @Nullable
    public ComponentRule getComponent(String componentName) {
        for (ComponentRule rule : getAllComponents()) {
            if (rule.name.equals(componentName)) return rule;
        }
        return null;
    }

    /**
     * Add the given component to the rules list with user preferred component status, does nothing if the instance is
     * immutable.
     *
     * @param componentName The component to add
     * @param componentType Component type
     * @see #addEntry(RuleEntry)
     */
    public void addComponent(String componentName, RuleType componentType) {
        if (!readOnly) setComponent(componentName, componentType, AppPref.getDefaultComponentStatus());
    }

    /**
     * Add the given component to the rules list, does nothing if the instance is immutable.
     *
     * @param componentName   The component to add
     * @param componentType   Component type
     * @param componentStatus Component status
     * @see #addEntry(RuleEntry)
     */
    public void addComponent(String componentName,
                             RuleType componentType,
                             @ComponentRule.ComponentStatus String componentStatus) {
        if (!readOnly) setComponent(componentName, componentType, componentStatus);
    }

    /**
     * Suggest removal of the given component from the rules, does nothing if the instance is
     * immutable or the component does not exist. The rules are only applied when {@link #commit()}
     * is called.
     *
     * @param componentName The component to remove
     * @see #removeEntry(RuleEntry)
     * @see #deleteComponent(String)
     */
    public void removeComponent(String componentName) {
        if (readOnly) return;
        ComponentRule cr = getComponent(componentName);
        if (cr != null) {
            setComponent(componentName, cr.type, ComponentRule.COMPONENT_TO_BE_DEFAULTED);
        }
    }

    /**
     * Remove component from the list rules without triggering a component removal request, does nothing
     * if the instance is immutable or the component does not exist. The rules are only applied when
     * {@link #commit()} is called.
     *
     * @param componentName The component to remove
     * @see #removeEntry(RuleEntry)
     * @see #removeComponent(String)
     */
    public void deleteComponent(String componentName) {
        if (readOnly) return;
        ComponentRule cr = getComponent(componentName);
        if (cr != null) {
            removeEntries(componentName, cr.type);
        }
    }

    /**
     * Save the disabled components in the {@link #SYSTEM_RULES_PATH}.
     *
     * @return {@code true} iff the components could be saved.
     */
    private boolean saveDisabledComponents(boolean apply) {
        if (readOnly) {
            Log.e(TAG, "Read-only instance.");
            return false;
        }
        if (!apply || componentCount() == 0) {
            // No components set, delete if already exists
            mRulesFile.delete();
            return true;
        }
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (ComponentRule component : getAllComponents()) {
            // Ignore components that needs unblocking
            if (!component.isIfw()) continue;
            String componentFilter = "  <component-filter name=\"" + packageName + "/" + component.name + "\"/>\n";
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
            }
        }

        String rules = "<rules>\n" +
                ((activities.length() == 0) ? "" : "<activity block=\"true\" log=\"false\">\n" + activities + "</activity>\n") +
                ((services.length() == 0) ? "" : "<service block=\"true\" log=\"false\">\n" + services + "</service>\n") +
                ((receivers.length() == 0) ? "" : "<broadcast block=\"true\" log=\"false\">\n" + receivers + "</broadcast>\n") +
                "</rules>";
        // Save rules
        ProxyOutputStream rulesStream = null;
        try {
            rulesStream = mRulesFile.startWrite();
            Log.d(TAG, "Rules: " + rules);
            rulesStream.write(rules.getBytes());
            mRulesFile.finishWrite(rulesStream);
            Runner.runCommand(new String[]{"chmod", "0666", mRulesFile.getBaseFile().getAbsolutePath()});
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write rules for package " + packageName, e);
            mRulesFile.failWrite(rulesStream);
            return false;
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
        List<ComponentRule> entries = getAllComponents();
        for (ComponentRule entry : entries) {
            if (!entry.isApplied()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply the currently modified rules if the argument apply is true. Since IFW is used, when
     * apply is true, the IFW rules are saved to {@link #SYSTEM_RULES_PATH} and components that are
     * set to be removed or unblocked will be removed. If apply is set to false, all rules will be
     * removed but before that all components will be set to their default state (ie., the state
     * described in the app manifest).
     *
     * @param apply Whether to apply the rules or remove them altogether.
     * @return {@code true} iff all rules are applied correctly.
     */
    @WorkerThread
    public boolean applyRules(boolean apply) {
        // Check root. If no root is present, check if the app is test-only.
        if (!Ops.isRoot() && !(Ops.isPrivileged() && mPackageInfo != null
                && PackageUtils.isTestOnlyApp(mPackageInfo.applicationInfo))) {
            return false;
        }
        // Validate components
        validateComponents();
        // Save blocked IFW components or remove them based on the value of apply
        if (Ops.isRoot() && !saveDisabledComponents(apply)) {
            return false;
        }
        // Enable/disable components
        List<ComponentRule> allEntries = getAllComponents();
        Log.d(TAG, "All: " + allEntries.toString());
        boolean isSuccessful = true;
        if (apply) {
            for (ComponentRule entry : allEntries) {
                switch (entry.getComponentStatus()) {
                    case ComponentRule.COMPONENT_TO_BE_DEFAULTED:
                        // Set component state to default and remove it
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.getComponentName(),
                                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0, userHandle);
                            removeEntry(entry);
                        } catch (Throwable e) {
                            isSuccessful = false;
                            Log.e(TAG, "Could not enable component: " + packageName + "/" + entry.name, e);
                        }
                        break;
                    case ComponentRule.COMPONENT_TO_BE_ENABLED:
                        // Enable components
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.getComponentName(),
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userHandle);
                            setComponent(entry.name, entry.type, ComponentRule.COMPONENT_ENABLED);
                        } catch (Throwable e) {
                            isSuccessful = false;
                            Log.e(TAG, "Could not disable component: " + packageName + "/" + entry.name, e);
                        }
                        break;
                    case ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW:
                        setComponent(entry.name, entry.type, ComponentRule.COMPONENT_BLOCKED_IFW);
                        break;
                    case ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE:
                    case ComponentRule.COMPONENT_TO_BE_DISABLED:
                        // Disable components
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.getComponentName(),
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0, userHandle);
                            setComponent(entry.name, entry.type, entry.getCounterpartOfToBe());
                        } catch (Throwable e) {
                            isSuccessful = false;
                            Log.e(TAG, "Could not disable component: " + packageName + "/" + entry.name, e);
                        }
                        break;
                    default:
                        setComponent(entry.name, entry.type, entry.getCounterpartOfToBe());
                }
            }
        } else {
            // Enable all, remove to be removed components and set others to be blocked
            for (ComponentRule entry : allEntries) {
                // Enable components if they're disabled by other methods.
                // IFW rules are already removed above.
                try {
                    PackageManagerCompat.setComponentEnabledSetting(entry.getComponentName(),
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0, userHandle);
                    if (entry.toBeRemoved()) {
                        removeEntry(entry);
                    } else setComponent(entry.name, entry.type, entry.getToBe());
                } catch (Throwable e) {
                    isSuccessful = false;
                    Log.e(TAG, "Could not enable component: " + packageName + "/" + entry.name, e);
                }
            }
        }
        return isSuccessful;
    }

    /**
     * Apply all configured app ops and permissions.
     *
     * @return {@code true} iff all the rules are applied correctly.
     */
    public boolean applyAppOpsAndPerms() {
        if (mPackageInfo == null) {
            return false;
        }
        boolean isSuccessful = true;
        int uid = mPackageInfo.applicationInfo.uid;
        AppOpsService appOpsService = new AppOpsService();
        // Apply all app ops
        for (AppOpRule appOp : getAll(AppOpRule.class)) {
            try {
                appOpsService.setMode(appOp.getOp(), uid, packageName, appOp.getMode());
            } catch (Throwable e) {
                isSuccessful = false;
                Log.e(TAG, "Could not set mode " + appOp.getMode() + " for app op " + appOp.getOp(), e);
            }
        }
        // Apply all permissions
        for (PermissionRule permissionRule : getAll(PermissionRule.class)) {
            Permission permission = permissionRule.getPermission(true);
            try {
                permission.setAppOpAllowed(permission.getAppOp() != OP_NONE && appOpsService
                        .checkOperation(permission.getAppOp(), uid, packageName) == AppOpsManager.MODE_ALLOWED);
                if (permission.isGranted()) {
                    PermUtils.grantPermission(mPackageInfo, permission, appOpsService, true, true);
                } else {
                    PermUtils.revokePermission(mPackageInfo, permission, appOpsService, true);
                }
            } catch (Throwable e) {
                isSuccessful = false;
                Log.e(TAG, "Could not " + (permission.isGranted() ? "grant" : "revoke") + " " + permissionRule.name, e);
            }
        }
        return isSuccessful;
    }

    /**
     * Check if the components are up-to-date and remove the ones that are not up-to-date.
     */
    private void validateComponents() {
        // Validate components
        List<ComponentRule> allEntries = getAllComponents();
        for (ComponentRule entry : allEntries) {
            if (!mComponents.contains(entry.name)) {
                // Remove non-existent components
                removeEntry(entry);
            }
        }
    }

    /**
     * Retrieve a set of disabled components from the {@link #SYSTEM_RULES_PATH}. If they are
     * available add them to the rules, overridden if necessary.
     */
    private void retrieveDisabledComponents() {
        if (!Ops.isRoot()) return;
        Log.d(TAG, "Retrieving disabled components for package " + packageName);
        if (!mRulesFile.exists() || mRulesFile.getBaseFile().length() == 0) {
            // System doesn't have any rules.
            // Load the rules saved inside App Manager
            for (ComponentRule entry : getAllComponents()) {
                setComponent(entry.name, entry.type, entry.getToBe());
            }
            return;
        }
        try (InputStream rulesStream = mRulesFile.openRead()) {
            HashMap<String, RuleType> components = ComponentUtils.readIFWRules(rulesStream, packageName);
            for (String componentName : components.keySet()) {
                // Override existing rule for the component if it exists
                setComponent(componentName, components.get(componentName), ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE);
            }
            Log.d(TAG, "Retrieved components for package " + packageName);
        } catch (IOException | RemoteException ignored) {
        }
    }
}
