package io.github.muntashirakon.AppManager.storage;

import android.content.Context;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.utils.RunnerUtils;

public class RulesStorageManager implements Closeable {
    @StringDef(value = {
            COMPONENT_BLOCKED,
            COMPONENT_TO_BE_BLOCKED,
            COMPONENT_TO_BE_UNBLOCKED
    })
    public @interface ComponentStatus {}
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
            for (int i = 0; i<values.length; ++i) names[i] = values[i].name();
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

    protected Context context;
    protected String packageName;
    protected boolean readOnly = true;
    private CopyOnWriteArrayList<Entry> entries;

    protected RulesStorageManager(Context context, String packageName) {
        this.context = context;
        this.packageName = packageName;
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

    public Entry get(String name) {
        for (Entry entry: entries) if (entry.name.equals(name)) return entry;
        return null;
    }

    public List<Entry> getAll(Type type) {
        List<Entry> newEntries = new ArrayList<>();
        for (Entry entry: entries) if (entry.type.equals(type)) newEntries.add(entry);
        return newEntries;
    }

    protected List<Entry> getAllComponents() {
        List<Entry> newEntries = new ArrayList<>();
        for (Entry entry: entries) {
            if (entry.type.equals(Type.ACTIVITY)
                    || entry.type.equals(Type.PROVIDER)
                    || entry.type.equals(Type.RECEIVER)
                    || entry.type.equals(Type.SERVICE))
                newEntries.add(entry);
        }
        return newEntries;
    }

    public List<Entry> getAll() {
        return entries;
    }

    public boolean hasName(String name) {
        for (Entry entry: entries) if (entry.name.equals(name)) return true;
        return false;
    }

    public int entryCount() {
        return entries.size();
    }

    public void removeEntry(Entry entry) {
        entries.remove(entry);
    }

    public void removeEntry(String name) {
        Entry removableEntry = null;
        for (Entry entry: entries) if (entry.name.equals(name)) removableEntry = entry;
        entries.remove(removableEntry);
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

    public void addEntry(@NonNull Entry entry) {
        removeEntry(entry.name);
        entries.add(entry);
    }

    public void applyAppOpsAndPerms(boolean apply) {
        Runner runner = Runner.getInstance();
        if (apply) {
            // Apply all app ops
            List<Entry> appOps = getAll(Type.APP_OP);
            for (Entry appOp: appOps) {
                try {
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_SET, packageName, Integer.parseInt(appOp.name), appOp.extra));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Apply all permissions
            List<Entry> permissions = getAll(Type.PERMISSION);
            for (Entry permission: permissions) {
                if ((Boolean) permission.extra) {
                    // grant permission
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_GRANT, packageName, permission.name));
                } else {
                    runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_REVOKE, packageName, permission.name));
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
            for (Entry permission: permissions) {
                runner.addCommand(String.format(Locale.ROOT, RunnerUtils.CMD_PERMISSION_REVOKE, packageName, permission.name));
            }
        }
        // Run all commands
        runner.runCommand();
    }

    private void loadEntries() {
        entries = new CopyOnWriteArrayList<>();
        StringTokenizer tokenizer;
        String dataRow;
        try (BufferedReader TSVFile = new BufferedReader(new FileReader(getDesiredFile()))) {
            while ((dataRow = TSVFile.readLine()) != null){
                tokenizer = new StringTokenizer(dataRow,"\t");
                Entry entry = new Entry();
                if (tokenizer.hasMoreElements()) entry.name = tokenizer.nextElement().toString();
                if (tokenizer.hasMoreElements()) {
                    try {
                        entry.type = Type.valueOf(tokenizer.nextElement().toString());
                    } catch (Exception e) {
                        entry.type = Type.UNKNOWN;
                    }
                }
                if (tokenizer.hasMoreElements()) entry.extra = getExtra(entry.type, tokenizer.nextElement().toString());
                entries.add(entry);
            }
        } catch (IOException ignore) {}
    }

    public void commit() {
        new Thread(() -> {
            try {
                saveEntries(new ArrayList<>(entries));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    synchronized private void saveEntries(@NonNull List<Entry> finalEntries) throws IOException {
        if (finalEntries.size() == 0) {
            //noinspection ResultOfMethodCallIgnored
            getDesiredFile().delete();
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for(Entry entry: finalEntries) {
            stringBuilder.append(entry.name).append("\t").append(entry.type.name()).append("\t").
                    append(entry.extra).append("\n");
        }
        try (FileOutputStream TSVFile = new FileOutputStream(getDesiredFile())) {
            TSVFile.write(stringBuilder.toString().getBytes());
        }
    }

    @NonNull
    protected File getDesiredFile() throws FileNotFoundException {
        File file = new File(context.getFilesDir(), "conf");
        if (!file.exists() && !file.mkdirs()) {
            throw new FileNotFoundException("Can not get correct path to save ifw rules");
        }
        return new File(file, packageName + ".tsv");
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
            case APP_OP: return Integer.valueOf(strExtra);
            default: return null;
        }
    }
}
