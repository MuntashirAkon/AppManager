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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

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

    public enum Type {
        ACTIVITY,
        PROVIDER,
        RECEIVER,
        SERVICE,
        APP_OP,
        PERMISSION,
        UNKNOWN;

        public static final String[] names = new String[values().length];

        static {
            Type[] values = values();
            for (int i = 0; i < values.length; ++i) names[i] = values[i].name();
        }
    }

    public static class Entry {
        public String name; // pk
        public Type type;
        public Object extra; // mode, is_applied, is_granted

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

    protected RulesStorageManager(@NonNull Context context, @NonNull String packageName) {
        this.context = context;
        this.packageName = packageName;
        this.entries = new ArrayList<>();
        loadEntries();
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
        Entry entry = new Entry();
        entry.name = name;
        entry.type = componentType;
        entry.extra = componentStatus;
        addEntry(entry);
    }

    public void setAppOp(String name, @AppOpsManager.Mode int mode) {
        Entry entry = new Entry();
        entry.name = name;
        entry.type = Type.APP_OP;
        entry.extra = mode;
        addEntry(entry);
    }

    public void setPermission(String name, Boolean isGranted) {
        Entry entry = new Entry();
        entry.name = name;
        entry.type = Type.PERMISSION;
        entry.extra = isGranted;
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
        String user = RunnerUtils.userHandleToUser(Users.getCurrentUserHandle());
        if (apply) {
            // Apply all app ops
            List<Entry> appOps = getAll(Type.APP_OP);
            for (Entry appOp : appOps) {
                try {
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_SET, packageName, Integer.parseInt(appOp.name), appOp.extra));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Apply all permissions
            List<Entry> permissions = getAll(Type.PERMISSION);
            for (Entry permission : permissions) {
                if ((Boolean) permission.extra) {
                    // grant permission
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_GRANT, user, packageName, permission.name));
                } else {
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_REVOKE, user, packageName, permission.name));
                }
            }
        } else {
            // Reset all app ops
            try {
                runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_RESET, packageName));
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Revoke all permissions
            List<Entry> permissions = getAll(Type.PERMISSION);
            for (Entry permission : permissions) {
                runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_REVOKE, user, packageName, permission.name));
            }
        }
        // Run all commands
        runner.runCommand();
    }

    @GuardedBy("entries")
    private void loadEntries() {
        StringTokenizer tokenizer;
        String dataRow;
        try (BufferedReader TSVFile = new BufferedReader(new FileReader(getDesiredFile()))) {
            while ((dataRow = TSVFile.readLine()) != null) {
                tokenizer = new StringTokenizer(dataRow, "\t");
                Entry entry = new Entry();
                if (tokenizer.hasMoreElements()) entry.name = tokenizer.nextElement().toString();
                if (tokenizer.hasMoreElements()) {
                    try {
                        entry.type = Type.valueOf(tokenizer.nextElement().toString());
                    } catch (Exception e) {
                        entry.type = Type.UNKNOWN;
                    }
                }
                if (tokenizer.hasMoreElements())
                    entry.extra = getExtra(entry.type, tokenizer.nextElement().toString());
                synchronized (entries) {
                    entries.add(entry);
                }
            }
        } catch (IOException ignore) {
        }
    }

    @GuardedBy("entries")
    public void commit() {
        try {
            saveEntries();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @GuardedBy("entries")
    private void saveEntries() throws IOException {
        synchronized (entries) {
            File tsvRulesFile = getDesiredFile();
            if (entries.size() == 0) {
                //noinspection ResultOfMethodCallIgnored
                tsvRulesFile.delete();
                return;
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (Entry entry : entries) {
                stringBuilder.append(entry.name).append("\t").append(entry.type.name()).append("\t").
                        append(entry.extra).append("\n");
            }
            try (FileOutputStream TSVFile = new FileOutputStream(tsvRulesFile)) {
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
                return strExtra;
            case PERMISSION:
                return Boolean.valueOf(strExtra);
            case APP_OP:
                return Integer.valueOf(strExtra);
            default:
                return null;
        }
    }
}
