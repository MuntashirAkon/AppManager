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

package io.github.muntashirakon.AppManager.rules;

import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.ProxyFileReader;
import io.github.muntashirakon.io.ProxyOutputStream;

public class RulesStorageManager implements Closeable {
    @StringDef(value = {
            COMPONENT_BLOCKED,
            COMPONENT_TO_BE_BLOCKED,
            COMPONENT_TO_BE_UNBLOCKED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComponentStatus {
    }

    public static final String COMPONENT_BLOCKED = "true";  // To preserve compatibility
    public static final String COMPONENT_TO_BE_BLOCKED = "false";  // To preserve compatibility
    public static final String COMPONENT_TO_BE_UNBLOCKED = "unblocked";

    // Keep in sync with #getExtra(Type, String)
    public enum Type {
        ACTIVITY,
        PROVIDER,
        RECEIVER,
        SERVICE,
        APP_OP,  // int
        PERMISSION,  // boolean
        MAGISK_HIDE,  // boolean
        BATTERY_OPT,  // boolean (battery optimization)
        NET_POLICY,  // int (whitelist|blacklist|none)
        NOTIFICATION,  // string (component name)
        URI_GRANT,  // string (flattened by UriGrant)
        SSAID,  // string
        UNKNOWN;

        public static final String[] names = new String[values().length];

        static {
            Type[] values = values();
            for (int i = 0; i < values.length; ++i) names[i] = values[i].name();
        }
    }

    public static class Entry {
        public static final String STUB = "STUB";

        /**
         * Name of the entry, unique for {@link Type#ACTIVITY}, {@link Type#PROVIDER}, {@link Type#RECEIVER},
         * {@link Type#SERVICE} and {@link Type#PERMISSION} but not others. In other cases, they can be {@link #STUB}.
         */
        @NonNull
        public String name;
        /**
         * Type of the entry.
         */
        public Type type;
        /**
         * Extra data.
         *
         * @see #getExtra(Type, String)
         */
        public Object extra; // mode, is_applied, is_granted

        public Entry() {
            this.name = STUB;
        }

        public Entry(@NonNull String name, Type type, Object extra) {
            this.name = name;
            this.type = type;
            this.extra = extra;
        }

        @NonNull
        @Override
        public String toString() {
            return "Entry{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    ", extra=" + extra +
                    '}';
        }
    }

    @NonNull
    protected final Context context;
    @NonNull
    private final ArrayList<Entry> entries;
    @GuardedBy("entries")
    @NonNull
    protected String packageName;
    @GuardedBy("entries")
    protected boolean readOnly = true;
    protected int userHandle;

    protected RulesStorageManager(@NonNull Context context, @NonNull String packageName, int userHandle) {
        this.context = context;
        this.packageName = packageName;
        this.userHandle = userHandle;
        this.entries = new ArrayList<>();
        try {
            loadEntries(getDesiredFile(), false);
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
    protected Entry get(String name) {
        synchronized (entries) {
            for (Entry entry : entries) if (entry.name.equals(name)) return entry;
            return null;
        }
    }

    @GuardedBy("entries")
    public List<Entry> getAll(Type type) {
        synchronized (entries) {
            List<Entry> newEntries = new ArrayList<>();
            for (Entry entry : entries) if (entry.type.equals(type)) newEntries.add(entry);
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<Entry> getAllComponents() {
        synchronized (entries) {
            List<Entry> newEntries = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.type.equals(Type.ACTIVITY)
                        || entry.type.equals(Type.PROVIDER)
                        || entry.type.equals(Type.RECEIVER)
                        || entry.type.equals(Type.SERVICE))
                    newEntries.add(entry);
            }
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<Entry> getAll() {
        synchronized (entries) {
            return entries;
        }
    }

    @GuardedBy("entries")
    protected boolean hasName(String name) {
        synchronized (entries) {
            for (Entry entry : entries) if (entry.name.equals(name)) return true;
            return false;
        }
    }

    @GuardedBy("entries")
    public int entryCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    @GuardedBy("entries")
    public void removeEntry(Entry entry) {
        synchronized (entries) {
            entries.remove(entry);
        }
    }

    @GuardedBy("entries")
    protected void removeEntries(String name, Type type) {
        synchronized (entries) {
            Iterator<Entry> entryIterator = entries.iterator();
            Entry entry;
            while (entryIterator.hasNext()) {
                entry = entryIterator.next();
                if (entry.name.equals(name) && entry.type.equals(type)) {
                    entryIterator.remove();
                }
            }
        }
    }

    protected void setComponent(String name, Type componentType, @ComponentStatus String componentStatus) {
        addUniqueEntry(new Entry(name, componentType, componentStatus));
    }

    public void setAppOp(String name, @AppOpsManager.Mode int mode) {
        addUniqueEntry(new Entry(name, Type.APP_OP, mode));
    }

    public void setPermission(String name, Boolean isGranted) {
        addUniqueEntry(new Entry(name, Type.PERMISSION, isGranted));
    }

    public void setNotificationListener(String name, Boolean isGranted) {
        addUniqueEntry(new Entry(name, Type.NOTIFICATION, isGranted));
    }

    public void setMagiskHide(Boolean isHide) {
        Entry entry = new Entry();
        entry.type = Type.MAGISK_HIDE;
        entry.extra = isHide;
        addEntryInternal(entry);
    }

    public void setBatteryOptimization(Boolean willOptimize) {
        Entry entry = new Entry();
        entry.type = Type.BATTERY_OPT;
        entry.extra = willOptimize;
        addEntryInternal(entry);
    }

    public void setNetPolicy(@NetworkPolicyManagerCompat.NetPolicy int netPolicy) {
        Entry entry = new Entry();
        entry.type = Type.BATTERY_OPT;
        entry.extra = netPolicy;
        addEntryInternal(entry);
    }

    public void setUriGrant(@NonNull UriManager.UriGrant uriGrant) {
        Entry entry = new Entry();
        entry.type = Type.URI_GRANT;
        entry.extra = uriGrant;
        addEntryInternal(entry);
    }

    public void setSsaid(@NonNull String ssaid) {
        Entry entry = new Entry();
        entry.type = Type.SSAID;
        entry.extra = ssaid;
        addEntryInternal(entry);
    }

    /**
     * Add entry, remove old entries depending on entry {@link Type}.
     */
    @GuardedBy("entries")
    public void addEntry(@NonNull Entry entry) {
        synchronized (entries) {
            switch (entry.type) {
                case ACTIVITY:
                case PROVIDER:
                case RECEIVER:
                case SERVICE:
                case PERMISSION:
                case APP_OP:
                case NOTIFICATION:
                    addUniqueEntry(entry);
                    break;
                case SSAID:
                case URI_GRANT:
                case NET_POLICY:
                case BATTERY_OPT:
                case MAGISK_HIDE:
                case UNKNOWN:
                default:
                    addEntryInternal(entry);
            }
        }
    }

    /**
     * Remove the exact entry if exists before adding it.
     */
    @GuardedBy("entries")
    private void addEntryInternal(@NonNull Entry entry) {
        synchronized (entries) {
            removeEntry(entry);
            entries.add(entry);
        }
    }

    /**
     * Remove all entries of the given name and type before adding the entry.
     */
    @GuardedBy("entries")
    private void addUniqueEntry(@NonNull Entry entry) {
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
            List<Entry> appOps = getAll(Type.APP_OP);
            for (Entry appOp : appOps) {
                try {
                    appOpsService.setMode(Integer.parseInt(appOp.name), uid, packageName, (int) appOp.extra);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            // Apply all permissions
            List<Entry> permissions = getAll(Type.PERMISSION);
            for (Entry permission : permissions) {
                try {
                    if ((Boolean) permission.extra) {
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
            List<Entry> permissions = getAll(Type.PERMISSION);
            for (Entry permission : permissions) {
                try {
                    PermissionCompat.revokePermission(packageName, permission.name, userHandle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @GuardedBy("entries")
    protected void loadEntries(File file, boolean isExternal) throws IOException, RemoteException {
        StringTokenizer tokenizer;
        String dataRow;
        String packageName;
        try (BufferedReader TSVFile = new BufferedReader(new ProxyFileReader(file))) {
            while ((dataRow = TSVFile.readLine()) != null) {
                tokenizer = new StringTokenizer(dataRow, "\t");
                Entry entry = new Entry();
                if (isExternal) {
                    if (tokenizer.hasMoreElements()) {
                        // Match package name
                        packageName = tokenizer.nextElement().toString();
                        if (!this.packageName.equals(packageName)) {
                            // Skip this line and load the next one
                            continue;
                        }
                    } else throw new IOException("Invalid format: packageName not found");
                }
                if (tokenizer.hasMoreElements()) {
                    entry.name = tokenizer.nextElement().toString();
                } else throw new IOException("Invalid format: name not found");
                if (tokenizer.hasMoreElements()) {
                    try {
                        entry.type = Type.valueOf(tokenizer.nextElement().toString());
                    } catch (Exception e) {
                        entry.type = Type.UNKNOWN;
                    }
                } else throw new IOException("Invalid format: entryType not found");
                if (tokenizer.hasMoreElements()) {
                    entry.extra = getExtra(entry.type, tokenizer.nextElement().toString());
                } else throw new IOException("Invalid format: extra not found");
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
            saveEntries(getDesiredFile(), false);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    public void commitExternal(File tsvRulesFile) {
        try {
            saveEntries(tsvRulesFile, true);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    protected void saveEntries(File tsvRulesFile, boolean isExternal) throws IOException, RemoteException {
        synchronized (entries) {
            if (entries.size() == 0) {
                //noinspection ResultOfMethodCallIgnored
                tsvRulesFile.delete();
                return;
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (Entry entry : entries) {
                if (isExternal) stringBuilder.append(packageName).append("\t");
                stringBuilder.append(entry.name).append("\t")
                        .append(entry.type.name()).append("\t")
                        .append(entry.extra).append("\n");
            }
            try (OutputStream TSVFile = new ProxyOutputStream(tsvRulesFile)) {
                TSVFile.write(stringBuilder.toString().getBytes());
            }
        }
    }

    @NonNull
    public static File getConfDir() {
        return new File(AppManager.getContext().getFilesDir(), "conf");
    }

    @NonNull
    protected File getDesiredFile() throws FileNotFoundException {
        File confDir = getConfDir();
        if (!confDir.exists() && !confDir.mkdirs()) {
            throw new FileNotFoundException("Can not get correct path to save ifw rules");
        }
        return new File(confDir, packageName + ".tsv");
    }

    @Nullable
    static Object getExtra(@NonNull Type type, @NonNull String strExtra) {
        switch (type) {
            case ACTIVITY:
            case PROVIDER:
            case RECEIVER:
            case SERVICE:
            case NOTIFICATION:
            case SSAID:
                return strExtra;
            case PERMISSION:
            case MAGISK_HIDE:
            case BATTERY_OPT:
                return Boolean.valueOf(strExtra);
            case APP_OP:
            case NET_POLICY:
                return Integer.valueOf(strExtra);
            case URI_GRANT:
                return UriManager.UriGrant.unflattenFromString(strExtra);
            default:
                return null;
        }
    }
}
