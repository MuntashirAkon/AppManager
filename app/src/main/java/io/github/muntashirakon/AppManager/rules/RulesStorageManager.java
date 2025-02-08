// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.BatteryOptimizationRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.FreezeRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskDenyListRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.NotificationListenerRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.Paths;

public class RulesStorageManager implements Closeable {
    @NonNull
    private final ArrayList<RuleEntry> mEntries;

    @GuardedBy("entries")
    @NonNull
    protected String packageName;
    @GuardedBy("entries")
    protected boolean readOnly = true;
    @UserIdInt
    protected int userId;

    protected RulesStorageManager(@NonNull String packageName, @UserIdInt int userId) {
        this.packageName = packageName;
        this.userId = userId;
        mEntries = new ArrayList<>();
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
        synchronized (mEntries) {
            List<T> newEntries = new ArrayList<>();
            for (RuleEntry entry : mEntries) if (type.isInstance(entry)) newEntries.add(type.cast(entry));
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<RuleEntry> getAll(List<RuleType> types) {
        synchronized (mEntries) {
            List<RuleEntry> newEntries = new ArrayList<>();
            for (RuleEntry entry : mEntries) if (types.contains(entry.type)) newEntries.add(entry);
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<ComponentRule> getAllComponents() {
        return getAll(ComponentRule.class);
    }

    @GuardedBy("entries")
    public List<RuleEntry> getAll() {
        synchronized (mEntries) {
            return mEntries;
        }
    }

    @GuardedBy("entries")
    public int entryCount() {
        synchronized (mEntries) {
            return mEntries.size();
        }
    }

    @GuardedBy("entries")
    public void removeEntry(RuleEntry entry) {
        synchronized (mEntries) {
            mEntries.remove(entry);
        }
    }

    @GuardedBy("entries")
    @Nullable
    protected RuleEntry removeEntries(String name, RuleType type) {
        synchronized (mEntries) {
            Iterator<RuleEntry> entryIterator = mEntries.iterator();
            RuleEntry entry = null;
            while (entryIterator.hasNext()) {
                entry = entryIterator.next();
                if (entry.name.equals(name) && entry.type.equals(type)) {
                    entryIterator.remove();
                }
            }
            return entry;
        }
    }

    protected void setComponent(String name, RuleType componentType, @ComponentRule.ComponentStatus String componentStatus) {
        ComponentRule newRule = new ComponentRule(packageName, name, componentType, componentStatus);
        RuleEntry oldRule = addUniqueEntry(newRule);
        if (oldRule instanceof ComponentRule) {
            newRule.setLastComponentStatus(((ComponentRule) oldRule).getComponentStatus());
        }
    }

    public void setAppOp(int op, @AppOpsManagerCompat.Mode int mode) {
        addUniqueEntry(new AppOpRule(packageName, op, mode));
    }

    public void setPermission(String name, boolean isGranted, @PermissionCompat.PermissionFlags int flags) {
        addUniqueEntry(new PermissionRule(packageName, name, isGranted, flags));
    }

    public void setNotificationListener(String name, boolean isGranted) {
        addUniqueEntry(new NotificationListenerRule(packageName, name, isGranted));
    }

    public void setMagiskHide(@NonNull MagiskProcess magiskProcess) {
        addUniqueEntry(new MagiskHideRule(magiskProcess));
    }

    public void setMagiskDenyList(MagiskProcess magiskProcess) {
        addUniqueEntry(new MagiskDenyListRule(magiskProcess));
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

    public void setFreezeType(@FreezeUtils.FreezeType int freezeType) {
        addUniqueEntry(new FreezeRule(packageName, freezeType));
    }

    /**
     * Add entry, remove old entries depending on entry {@link RuleType}.
     */
    @GuardedBy("entries")
    public void addEntry(@NonNull RuleEntry entry) {
        synchronized (mEntries) {
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
        synchronized (mEntries) {
            removeEntry(entry);
            mEntries.add(entry);
        }
    }

    /**
     * Remove all entries of the given name and type before adding the entry.
     */
    @GuardedBy("entries")
    @Nullable
    private RuleEntry addUniqueEntry(@NonNull RuleEntry entry) {
        synchronized (mEntries) {
            RuleEntry previousEntry = removeEntries(entry.name, entry.type);
            mEntries.add(entry);
            return previousEntry;
        }
    }

    @GuardedBy("entries")
    protected void loadEntries(Path file, boolean isExternal) throws IOException {
        String dataRow;
        try (BufferedReader TSVFile = new BufferedReader(new PathReader(file))) {
            while ((dataRow = TSVFile.readLine()) != null) {
                RuleEntry entry = RuleEntry.unflattenFromString(packageName, dataRow, isExternal);
                synchronized (mEntries) {
                    mEntries.add(entry);
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
        synchronized (mEntries) {
            if (mEntries.isEmpty()) {
                tsvRulesFile.delete();
                return;
            }
            try (OutputStream TSVFile = tsvRulesFile.openOutputStream()) {
                ComponentUtils.storeRules(TSVFile, mEntries, isExternal);
            }
        }
    }

    @NonNull
    public static Path getConfDir(@NonNull Context context) {
        return Objects.requireNonNull(Paths.build(context.getFilesDir(), "conf"));
    }

    @NonNull
    protected Path getDesiredFile(boolean create) throws IOException {
        Path confDir = getConfDir(ContextUtils.getContext());
        if (!confDir.exists()) {
            confDir.mkdirs();
        }
        if (create) {
            return confDir.findOrCreateFile(packageName + ".tsv", null);
        }
        return confDir.findFile(packageName + ".tsv");
    }
}
