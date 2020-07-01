package io.github.muntashirakon.AppManager.storage;

import android.annotation.SuppressLint;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;

public class StorageManager {
    @SuppressLint("StaticFieldLeak")
    private static StorageManager storageManager;
    public static StorageManager getInstance(Context context, String packageName) {
        if (storageManager == null) storageManager = new StorageManager(context.getApplicationContext());
        storageManager.setPackageName(packageName);
        return storageManager;
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

    private Context context;
    private String packageName;
    private List<Entry> entries;

    private StorageManager(Context context) {
        this.context = context;
    }

    private void setPackageName(String packageName) {
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

    public List<Entry> getAllComponents() {
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

    public boolean hasEntry(Entry entry) {
        for (Entry _entry: entries) if (_entry.equals(entry)) return true;
        return false;
    }

    public void removeEntry(Entry entry) {
        entries.remove(entry);
        commit();
    }

    public void removeEntry(String name) {
        removeEntry(name, true);
    }

    public void setComponent(String name, Type componentType, Boolean isApplied) {
        Entry entry = new Entry();
        entry.name = name;
        entry.type = componentType;
        entry.extra = isApplied;
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

    private void addEntry(@NonNull Entry entry) {
        removeEntry(entry.name, false);
        entries.add(entry);
        commit();
    }

    private void removeEntry(String name, Boolean isCommit) {
        for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext();)
            if (iterator.next().name.equals(name)) iterator.remove();
        if (isCommit) commit();
    }

    private void loadEntries() {
        try {
            entries = new ArrayList<>();
            StringTokenizer tokenizer;
            BufferedReader TSVFile = new BufferedReader(new FileReader(getDesiredFile()));
            String dataRow;
            while ((dataRow = TSVFile.readLine()) != null){
                tokenizer = new StringTokenizer(dataRow,"\t");
                Entry entry = new Entry();
                if (tokenizer.hasMoreElements()) entry.name = tokenizer.nextElement().toString();
                if (tokenizer.hasMoreElements()) entry.type = getType(tokenizer.nextElement().toString());
                if (tokenizer.hasMoreElements()) entry.extra = getExtra(entry.type, tokenizer.nextElement().toString());
                entries.add(entry);
            }
            TSVFile.close();
        } catch (IOException ignore) {}
    }

    private void commit() {
        new Thread(() -> saveEntries(new ArrayList<>(entries))).start();
    }

    private void saveEntries(List<Entry> finalEntries) {
        try {
            if (finalEntries.size() == 0) {
                //noinspection ResultOfMethodCallIgnored
                getDesiredFile().delete();
                return;
            }
            StringBuilder stringBuilder = new StringBuilder();
            for(Entry entry: finalEntries) {
                stringBuilder.append(entry.name).append("\t").append(entry.type).append("\t").append(entry.extra).append("\n");
            }
            FileOutputStream TSVFile = new FileOutputStream(getDesiredFile());
            TSVFile.write(stringBuilder.toString().getBytes());
            TSVFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    private File getDesiredFile() throws FileNotFoundException {
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
