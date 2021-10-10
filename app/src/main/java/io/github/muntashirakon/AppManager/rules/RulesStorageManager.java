// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.BatteryOptimizationRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.NotificationListenerRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFileReader;

public class RulesStorageManager implements Closeable {
    @NonNull
    private final ArrayList<RuleEntry> entries;
    @GuardedBy("entries")
    @NonNull
    protected String packageName;
    @GuardedBy("entries")
    protected boolean readOnly = true;
    protected int userHandle;

    protected RulesStorageManager(@NonNull String packageName, int userHandle) {
        this.packageName = packageName;
        this.userHandle = userHandle;
        this.entries = new ArrayList<>();
        try {
            loadEntries(getDesiredFile(false), false);
        } catch (Throwable ignored) {
        }
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public void setMutable() {
        this.readOnly = false;
    }

    @Override
    public void close() {
        if (!readOnly) commit();
    }

    @GuardedBy("entries")
    public <T extends RuleEntry> List<T> getAll(Class<T> type) {
        synchronized (entries) {
            List<T> newEntries = new ArrayList<>();
            for (RuleEntry entry : entries) if (type.isInstance(entry)) newEntries.add(type.cast(entry));
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<RuleEntry> getAll(List<RuleType> types) {
        synchronized (entries) {
            List<RuleEntry> newEntries = new ArrayList<>();
            for (RuleEntry entry : entries) if (types.contains(entry.type)) newEntries.add(entry);
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<ComponentRule> getAllComponents() {
        return getAll(ComponentRule.class);
    }

    @GuardedBy("entries")
    public List<RuleEntry> getAll() {
        synchronized (entries) {
            return entries;
        }
    }

    @GuardedBy("entries")
    public int entryCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    @GuardedBy("entries")
    public void removeEntry(RuleEntry entry) {
        synchronized (entries) {
            entries.remove(entry);
        }
    }

    @GuardedBy("entries")
    protected void removeEntries(String name, RuleType type) {
        synchronized (entries) {
            Iterator<RuleEntry> entryIterator = entries.iterator();
            RuleEntry entry;
            while (entryIterator.hasNext()) {
                entry = entryIterator.next();
                if (entry.name.equals(name) && entry.type.equals(type)) {
                    entryIterator.remove();
                }
            }
        }
    }

    protected void setComponent(String name, RuleType componentType, @ComponentRule.ComponentStatus String componentStatus) {
        addUniqueEntry(new ComponentRule(packageName, name, componentType, componentStatus));
    }

    public void setAppOp(int op, @AppOpsManager.Mode int mode) {
        addUniqueEntry(new AppOpRule(packageName, op, mode));
    }

    public void setPermission(String name, boolean isGranted, @PermissionCompat.PermissionFlags int flags) {
        addUniqueEntry(new PermissionRule(packageName, name, isGranted, flags));
    }

    public void setNotificationListener(String name, boolean isGranted) {
        addUniqueEntry(new NotificationListenerRule(packageName, name, isGranted));
    }

    public void setMagiskHide(boolean isHide) {
        addUniqueEntry(new MagiskHideRule(packageName, isHide));
    }

    public void setBatteryOptimization(boolean willOptimize) {
        addUniqueEntry(new BatteryOptimizationRule(packageName, willOptimize));
    }

    public void setNetPolicy(@NetworkPolicyManagerCompat.NetPolicy int netPolicy) {
        addUniqueEntry(new NetPolicyRule(packageName, netPolicy));
    }

    public void setUriGrant(@NonNull UriManager.UriGrant uriGrant) {
        // There could be many UriGrants
        addEntryInternal(new UriGrantRule(packageName, uriGrant));
    }

    public void setSsaid(@NonNull String ssaid) {
        addUniqueEntry(new SsaidRule(packageName, ssaid));
    }

    /**
     * Add entry, remove old entries depending on entry {@link RuleType}.
     */
    @GuardedBy("entries")
    public void addEntry(@NonNull RuleEntry entry) {
        synchronized (entries) {
            if (entry.type.equals(RuleType.URI_GRANT)) {
                // UriGrant is not unique
                addEntryInternal(entry);
            } else addUniqueEntry(entry);
        }
    }

    /**
     * Remove the exact entry if exists before adding it.
     */
    @GuardedBy("entries")
    private void addEntryInternal(@NonNull RuleEntry entry) {
        synchronized (entries) {
            removeEntry(entry);
            entries.add(entry);
        }
    }

    /**
     * Remove all entries of the given name and type before adding the entry.
     */
    @GuardedBy("entries")
    private void addUniqueEntry(@NonNull RuleEntry entry) {
        synchronized (entries) {
            removeEntries(entry.name, entry.type);
            entries.add(entry);
        }
    }

    public void applyAppOpsAndPerms(boolean apply) {
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        AppOpsService appOpsService = new AppOpsService();
        if (apply) {
            // Apply all app ops
            for (AppOpRule appOp : getAll(AppOpRule.class)) {
                try {
                    appOpsService.setMode(appOp.getOp(), uid, packageName, appOp.getMode());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            // Apply all permissions
            for (PermissionRule permission : getAll(PermissionRule.class)) {
                try {
                    if (permission.isGranted()) {
                        // grant permission
                        PermissionCompat.grantPermission(packageName, permission.name, userHandle);
                    } else {
                        PermissionCompat.revokePermission(packageName, permission.name, userHandle);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // Reset all app ops
            try {
                appOpsService.resetAllModes(userHandle, packageName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            // Revoke all permissions
            for (PermissionRule permission : getAll(PermissionRule.class)) {
                try {
                    PermissionCompat.revokePermission(packageName, permission.name, userHandle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @GuardedBy("entries")
    protected void loadEntries(Path file, boolean isExternal) throws IOException, RemoteException {
        String dataRow;
        try (BufferedReader TSVFile = new BufferedReader(new ProxyFileReader(file))) {
            while ((dataRow = TSVFile.readLine()) != null) {
                RuleEntry entry = RuleEntry.unflattenFromString(packageName, dataRow, isExternal);
                synchronized (entries) {
                    entries.add(entry);
                }
            }
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    public void commit() {
        try {
            saveEntries(getDesiredFile(true), false);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    public void commitExternal(Path tsvRulesFile) {
        try {
            saveEntries(tsvRulesFile, true);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    protected void saveEntries(Path tsvRulesFile, boolean isExternal) throws IOException, RemoteException {
        synchronized (entries) {
            if (entries.size() == 0) {
                tsvRulesFile.delete();
                return;
            }
            try (OutputStream TSVFile = tsvRulesFile.openOutputStream()) {
                ComponentUtils.storeRules(TSVFile, entries, isExternal);
            }
        }
    }

    @NonNull
    public static Path getConfDir() {
        Context ctx = AppManager.getContext();
        return new Path(ctx, new File(ctx.getFilesDir(), "conf"));
    }

    @NonNull
    protected Path getDesiredFile(boolean create) throws IOException {
        Path confDir = getConfDir();
        if (!confDir.exists()) {
            confDir.mkdirs();
        }
        if (create) {
            return confDir.findOrCreateFile(packageName + ".tsv", null);
        }
        return confDir.findFile(packageName + ".tsv");
    }
}
