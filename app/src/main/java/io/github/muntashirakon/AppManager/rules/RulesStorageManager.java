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
import android.net.NetworkPolicyManager;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
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

    @IntDef(flag = true, value = {
            NetworkPolicyManager.POLICY_NONE,
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetPolicy {}

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
        UNKNOWN;

        public static final String[] names = new String[values().length];

        static {
            Type[] values = values();
            for (int i = 0; i < values.length; ++i) names[i] = values[i].name();
        }
    }

    public static class Entry {
        public static final String STUB = "STUB";

        @NonNull
        public String name; // pk
        public Type type;
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
        } catch (IOException e) {
            e.printStackTrace();
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
    public Entry get(String name) {
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
    public boolean hasName(String name) {
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
    public void removeEntry(String name) {
        synchronized (entries) {
            Entry removableEntry = null;
            for (Entry entry : entries) if (entry.name.equals(name)) removableEntry = entry;
            entries.remove(removableEntry);
        }
    }

    protected void setComponent(String name, Type componentType, @ComponentStatus String componentStatus) {
        addEntry(new Entry(name, componentType, componentStatus));
    }

    public void setAppOp(String name, @AppOpsManager.Mode int mode) {
        addEntry(new Entry(name, Type.APP_OP, mode));
    }

    public void setPermission(String name, Boolean isGranted) {
        addEntry(new Entry(name, Type.PERMISSION, isGranted));
    }

    public void setNotificationListener(String name, Boolean isGranted) {
        addEntry(new Entry(name, Type.NOTIFICATION, isGranted));
    }

    public void setMagiskHide(Boolean isHide) {
        Entry entry = new Entry();
        entry.type = Type.MAGISK_HIDE;
        entry.extra = isHide;
        addEntry(entry);
    }

    public void setBatteryOptimization(Boolean willOptimize) {
        Entry entry = new Entry();
        entry.type = Type.BATTERY_OPT;
        entry.extra = willOptimize;
        addEntry(entry);
    }

    public void setNetPolicy(@NetPolicy int netPolicy) {
        Entry entry = new Entry();
        entry.type = Type.BATTERY_OPT;
        entry.extra = netPolicy;
        addEntry(entry);
    }

    public void setUriGrant(@NonNull UriManager.UriGrant uriGrant) {
        Entry entry = new Entry();
        entry.type = Type.URI_GRANT;
        entry.extra = uriGrant;
        addEntry(entry);
    }

    @GuardedBy("entries")
    public void addEntry(@NonNull Entry entry) {
        synchronized (entries) {
            removeEntry(entry.name);
            entries.add(entry);
        }
    }

    public void applyAppOpsAndPerms(boolean apply) {
        Runner runner = Runner.getInstance();
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
                        PackageManagerCompat.grantPermission(packageName, permission.name, userHandle);
                    } else {
                        PackageManagerCompat.revokePermission(packageName, permission.name, userHandle);
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
                    PackageManagerCompat.revokePermission(packageName, permission.name, userHandle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        // Run all commands
        runner.runCommand();
    }

    @GuardedBy("entries")
    protected void loadEntries(File file, boolean isExternal) throws IOException {
        StringTokenizer tokenizer;
        String dataRow;
        String packageName;
        try (BufferedReader TSVFile = new BufferedReader(new FileReader(file))) {
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

    @GuardedBy("entries")
    public void commit() {
        try {
            saveEntries(getDesiredFile(), false);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @GuardedBy("entries")
    public void commitExternal(File tsvRulesFile) {
        try {
            saveEntries(tsvRulesFile, true);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

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
                stringBuilder.append(entry.name).append("\t").append(entry.type.name()).append("\t").
                        append(entry.extra).append("\n");
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
