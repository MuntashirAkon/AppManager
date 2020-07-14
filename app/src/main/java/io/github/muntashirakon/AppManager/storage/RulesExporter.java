package io.github.muntashirakon.AppManager.storage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;

/**
 * Export rules to external directory either for a single package or multiple packages.
 *
 * @see RulesImporter
 */
public class RulesExporter {
    private Context mContext;
    private @Nullable List<String> mPackagesToExport;
    private @NonNull List<RulesStorageManager.Type> mTypesToExport;

    public RulesExporter(@NonNull List<RulesStorageManager.Type> typesToExport, @Nullable List<String> packagesToExport) {
        mContext = AppManager.getContext();
        mPackagesToExport = packagesToExport;
        mTypesToExport = typesToExport;
    }

    public void saveRules(Uri uri) throws IOException {
        if (mPackagesToExport == null) mPackagesToExport = getAllPackages();
        try (OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri)) {
            for (String packageName: mPackagesToExport) {
                // Get a read-only instance
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mContext, packageName)) {
                    for (RulesStorageManager.Entry entry: cb.getAll()) {
                        if (mTypesToExport.contains(entry.type)) {
                            Objects.requireNonNull(outputStream).write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name, entry.type.name(), entry.extra).getBytes());
                        }
                    }
                }
            }
        }
    }

    @NonNull
    private List<String> getAllPackages() {
        List<ApplicationInfo> applicationInfoList = mContext.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        List<String> packageNames = new ArrayList<>();
        for (ApplicationInfo applicationInfo: applicationInfoList)
            packageNames.add(applicationInfo.packageName);
        return packageNames;
    }
}
