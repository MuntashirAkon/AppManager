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
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;

public class StorageManager implements Closeable {
    @Override
    public void close() {
        if (!readOnly) commit();
    }

    public enum Type {
        ACTIVITY,
        PROVIDER,
        RECEIVER,
        SERVICE,
        APP_OP,
        PERMISSION,
        UNKNOWN
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

    protected StorageManager(Context context, String packageName) {
        this.context = context;
        this.packageName = packageName;
        loadEntries();
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

    public void removeEntry(Entry entry) {
        entries.remove(entry);
    }

    public void removeEntry(String name) {
        Entry removableEntry = null;
        for (Entry entry: entries) if (entry.name.equals(name)) removableEntry = entry;
        entries.remove(removableEntry);
    }

    protected void setComponent(String name, Type componentType, Boolean isApplied) {
        Entry entry = new Entry();
        entry.name = name;
        entry.type = componentType;
        entry.extra = isApplied;
        addEntry(entry); // FIXME
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

    private void addEntry(@NonNull Entry entry) {
        removeEntry(entry.name);
        entries.add(entry);
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
                if (tokenizer.hasMoreElements()) entry.type = getType(tokenizer.nextElement().toString());
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
            stringBuilder.append(entry.name).append("\t").append(entry.type).append("\t").
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

    private Type getType(@NonNull String strType) {
        switch (strType) {
            case "ACTIVITY": return Type.ACTIVITY;
            case "PROVIDER": return Type.PROVIDER;
            case "RECEIVER": return Type.RECEIVER;
            case "SERVICE": return Type.SERVICE;
            case "APP_OP": return Type.APP_OP;
            case "PERMISSION": return Type.PERMISSION;
            default: return Type.UNKNOWN;
        }
    }

    @Nullable
    private Object getExtra(@NonNull Type type, @NonNull String strExtra) {
        switch (type) {
            case ACTIVITY:
            case PROVIDER:
            case RECEIVER:
            case SERVICE:
            case PERMISSION:
                return Boolean.valueOf(strExtra);
            case APP_OP: return Integer.valueOf(strExtra);
            default: return null;
        }
    }
}
