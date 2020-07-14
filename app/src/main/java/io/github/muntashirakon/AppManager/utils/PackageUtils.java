package io.github.muntashirakon.AppManager.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.runner.Runner;

public final class PackageUtils {
    private static final Pattern SERVICE_REGEX = Pattern.compile("ServiceRecord\\{.*/([^\\}]+)\\}");

    @NonNull
    public static List<String> getClassNames(byte[] bytes) {
        ArrayList<String> classNames = new ArrayList<>();
        File incomeFile = null;
        File optimizedFile = null;
        try {
            File cacheDir = AppManager.getContext().getCacheDir();
            incomeFile = File.createTempFile("classes_" + System.currentTimeMillis(), ".dex", cacheDir);
            IOUtils.bytesToFile(bytes, incomeFile);
            optimizedFile = File.createTempFile("opt_" + System.currentTimeMillis(), ".dex", cacheDir);
            DexFile dexFile = DexFile.loadDex(incomeFile.getPath(), optimizedFile.getPath(), 0);
            classNames = Collections.list(dexFile.entries());
            dexFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (incomeFile != null) //noinspection ResultOfMethodCallIgnored
                incomeFile.delete();
            if (optimizedFile != null) //noinspection ResultOfMethodCallIgnored
                optimizedFile.delete();
        }
        return classNames;
    }

    @NonNull
    public static List<String> getRunningServicesForPackage(String packageName) {
        List<String> runningServices = new ArrayList<>();
        Runner.run(AppManager.getContext(), "dumpsys activity services -p " + packageName);
        if (Runner.getLastResult().isSuccessful()) {
            List<String> serviceDump = Runner.getLastResult().getOutputAsList();
            Matcher matcher;
            String service, line;
            ListIterator<String> it = serviceDump.listIterator();
            if (it.hasNext()) {
                matcher = SERVICE_REGEX.matcher(it.next());
                while (it.hasNext()) {
                    if (matcher.find(0)) {
                        service = matcher.group(1);
                        line = it.next();
                        matcher = SERVICE_REGEX.matcher(line);
                        while (it.hasNext()) {
                            if (matcher.find(0)) break;
                            if (line.contains("app=ProcessRecord{")) {
                                if (service != null) {
                                    runningServices.add(service.startsWith(".") ? packageName + service : service);
                                }
                                break;
                            }
                            line = it.next();
                            matcher = SERVICE_REGEX.matcher(line);
                        }
                    } else matcher = SERVICE_REGEX.matcher(it.next());
                }
            }
        }
        return runningServices;
    }
}
