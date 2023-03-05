package io.github.muntashirakon.AppManager.magisk;

import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class MagiskModule {
    public static final String MODULE_NAME = "AppManager";
    public static final String MODULE_PROP = "module.prop";
    private static final String SCHEDULED_APK_FILE = "sched_apk.txt";

    private static MagiskModule instance;
    public static MagiskModule getInstance() throws IOException {
        if (instance == null) {
            try {
                instance = new MagiskModule();
            } catch (ErrnoException e) {
                ExUtils.rethrowAsIOException(e);
            }
        }
        return instance;
    }

    private static class PackageInfo {
        public String packageName;
        public String path;
        public int type;
    }

    @NonNull
    private final Path modulePath;

    private MagiskModule() throws IOException, ErrnoException {
        Path magiskModuleDir = MagiskUtils.getModDir();
        if (magiskModuleDir.hasFile(MODULE_NAME)) {
            modulePath = magiskModuleDir.findFile(MODULE_NAME);
        } else {
            // Module does not yet exist
            modulePath = magiskModuleDir.createNewDirectory(MODULE_NAME);
            Paths.chmod(modulePath, 0755);
        }
        if (!modulePath.hasFile(MODULE_PROP)) {
            Path moduleProp = modulePath.createNewFile(MODULE_PROP, null);
            try (OutputStream os = moduleProp.openOutputStream()) {
                generateModuleProp().store(os, null);
            }
            Paths.chmod(moduleProp, 0644);
        }
    }

    /**
     * Install the APK file to the given destination. If the destination already exists, it attempts to replace it by
     * calling {@link #replace(String, Path, String)}. This does not check whether the app is already installed.
     * It is up to the caller to ensure that the app has already been installed.
     *
     * @param apkFile     The APK file to put in the destination
     * @param destination Destination with the APK name
     */
    public void install(@NonNull String packageName, @NonNull Path apkFile, @NonNull String destination)
            throws IOException {
        destination = getSupportedPathOrFail(destination);
        Path destPath = Paths.get(destination);
        if (destPath.exists()) {
            replace(packageName, apkFile, destination);
            return;
        }
        // This works because it's a Linux FS
        Path moduleDest = Objects.requireNonNull(Paths.build(modulePath, destination));
        if (moduleDest.exists()) {
            throw new IOException(moduleDest + " exists");
        }
        Path parent = moduleDest.getParentFile();
        if (parent == null) {
            throw new FileNotFoundException("Parent not found");
        }
        parent.mkdirs();
        Path newPath = apkFile.copyTo(parent);
        if (newPath == null) {
            throw new IOException("Could not copy " + apkFile + " into " + parent);
        }
        newPath.renameTo(moduleDest.getName());
        if (!Objects.equals(moduleDest, newPath)) {
            newPath.delete();
            throw new IOException("Copy failed");
        }
        // TODO: 3/3/23 Update module database to add this path
    }

    /**
     * Reinstall the APK file existed in the given destination. This only deletes the folder used as a placeholder in
     * this module. It is up to the caller to reinstall the app using a given method.
     *
     * @param destination Destination with the APK name
     */
    public void reinstall(@NonNull String packageName, @NonNull String destination) throws IOException {
        destination = getSupportedPathOrFail(destination);
        // This works because it's a Linux FS
        Path moduleDest = Objects.requireNonNull(Paths.build(modulePath, destination));
        if (moduleDest.exists()) {
            // The app is installed systemless-ly via App Manager, simply delete up to the last empty path
            if (!deleteUntilNonEmpty(moduleDest)) {
                throw new IOException("Could not delete " + moduleDest);
            }
            // TODO: 3/3/23 Update module database to remove this path
            return;
        }
        throw new FileNotFoundException(destination + " not installed via App Manager");
    }

    /**
     * Replace the old APK file with the given APK. If App Manager is already configured to use it, it simply replaces
     * the APK with the new one. Otherwise, it overwrites the destination. A replaced APK is needed to be installed
     * again after a reboot.
     *
     * @param apkFile     The APK file to put in the destination
     * @param destination Destination with the APK name
     */
    public void replace(@NonNull String packageName, @NonNull Path apkFile, @NonNull String destination) throws IOException {
        destination = getSupportedPathOrFail(destination);
        Path realDest = Paths.get(destination);
        if (!realDest.exists()) {
            throw new FileNotFoundException(realDest + " must exist");
        }
        // This works because it's a Linux FS
        Path moduleDest = Objects.requireNonNull(Paths.build(modulePath, destination));
        Path parent = moduleDest.getParentFile();
        if (parent == null) {
            throw new FileNotFoundException("Parent not found");
        }
        if (!moduleDest.exists()) {
            parent.mkdirs();
        }
        Path newPath = apkFile.copyTo(parent);
        if (newPath == null) {
            throw new IOException("Could not copy " + apkFile + " into " + parent);
        }
        newPath.renameTo(moduleDest.getName());
        if (!Objects.equals(moduleDest, newPath)) {
            newPath.delete();
            throw new IOException("Copy failed");
        }
        // Schedule installation upon boot
        Set<Path> paths = readScheduledApkFiles();
        paths.add(moduleDest);
        writeScheduledApkFiles(paths);
        // TODO: 3/3/23 Update module database to add this path
    }

    /**
     * If the app is installed systemless-ly via App Manager, the path is simply deleted. Otherwise, it overwrites
     * the destination with an empty folder so that the APK appears to be unavailable. If the system app is updated,
     * it might appear as a user app after a reboot. So, it is up to the caller to uninstall the app before calling
     * this method.
     *
     * @param destination Destination without the APK name
     */
    public void uninstall(@NonNull String packageName, @NonNull String destination) throws IOException {
        destination = getSupportedPathOrFail(destination);
        Path realDest = Paths.get(destination);
        // This works because it's a Linux FS
        Path moduleDest = Objects.requireNonNull(Paths.build(modulePath, destination));
        if (moduleDest.exists()) {
            // The app is installed systemless-ly via App Manager, simply delete up to the last empty path
            if (!deleteUntilNonEmpty(moduleDest)) {
                throw new IOException("Could not delete " + moduleDest);
            }
            // TODO: 3/3/23 Update module database to remove this path
            return;
        }
        // The app is not installed systemless-ly
        if (realDest.exists()) {
            // The destination exists, overwrite it in the module
            if (!moduleDest.mkdirs()) {
                throw new IOException("Could not create " + moduleDest);
            }
            moduleDest.createNewFile(".replace", null);
            // TODO: 3/3/23 Update module database to add this path
            return;
        }
        throw new FileNotFoundException(destination + " is already uninstalled");
    }

    @NonNull
    private static String getSupportedPathOrFail(@NonNull String path) throws FileNotFoundException {
        String newPath = MagiskUtils.getValidSystemLocation(path);
        if (newPath == null) {
            throw new FileNotFoundException("Invalid path " + path);
        }
        return newPath;
    }

    private static boolean deleteUntilNonEmpty(@NonNull Path pathToDelete) {
        if (!pathToDelete.exists()) {
            return false;
        }
        Path parent = pathToDelete.getParentFile();
        // Delete the path first, regardless of whether it's a file or directory
        boolean success = pathToDelete.delete();
        while (parent != null && parent.listFiles().length == 0) {
            Path tmp = parent;
            parent = parent.getParentFile();
            tmp.delete();
        }
        return success;
    }

    @NonNull
    private static Properties generateModuleProp() {
        Properties moduleProp = new Properties();
        moduleProp.put("id", MODULE_NAME);
        moduleProp.put("name", "App Manager");
        moduleProp.put("version", "v1.0.0");
        moduleProp.put("versionCode", "1");
        moduleProp.put("author", "Muntashir Al-Islam");
        moduleProp.put("description", "Companion Magisk module for App Manager");
        return moduleProp;
    }

    @NonNull
    private static Set<Path> readScheduledApkFiles() {
        File scheduledApkFile = new File(ContextUtils.getContext().getFilesDir(), SCHEDULED_APK_FILE);
        Set<Path> paths = new ArraySet<>();
        if (!scheduledApkFile.exists()) {
            return paths;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(scheduledApkFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                paths.add(Paths.get(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return paths;
    }

    private static void writeScheduledApkFiles(@NonNull Set<Path> paths) throws IOException {
        File scheduledApkFile = new File(ContextUtils.getContext().getFilesDir(), SCHEDULED_APK_FILE);
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(scheduledApkFile))) {
            for (Path path : paths) {
                writer.println(path);
            }
            if (writer.checkError()) {
                throw new IOException("Error occured while writing to the file");
            }
        }
    }
}
