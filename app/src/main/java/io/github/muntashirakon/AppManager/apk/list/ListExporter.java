// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.csv.CsvWriter;

public final class ListExporter {
    public static final int EXPORT_TYPE_CSV = 0;
    public static final int EXPORT_TYPE_JSON = 1;
    public static final int EXPORT_TYPE_XML = 2;
    public static final int EXPORT_TYPE_MARKDOWN = 3;

    @IntDef({EXPORT_TYPE_CSV, EXPORT_TYPE_JSON, EXPORT_TYPE_XML, EXPORT_TYPE_MARKDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExportType {
    }

    public static void export(@NonNull Context context,
                              @NonNull Writer writer,
                              @ExportType int exportType,
                              @NonNull List<PackageInfo> packageInfoList) throws IOException {
        List<AppListItem> appListItems = getAppListItems(context, packageInfoList);
        switch (exportType) {
            case EXPORT_TYPE_CSV:
                exportCsv(writer, appListItems);
                return;
            case EXPORT_TYPE_JSON:
                try {
                    exportJson(writer, appListItems);
                } catch (JSONException e) {
                    ExUtils.rethrowAsIOException(e);
                }
                return;
            case EXPORT_TYPE_XML:
                exportXml(writer, appListItems);
                return;
            case EXPORT_TYPE_MARKDOWN:
                exportMarkdown(context, writer, appListItems);
                return;
        }
        throw new IllegalArgumentException("Invalid export type: " + exportType);
    }

    private static void exportXml(@NonNull Writer writer,
                                  @NonNull List<AppListItem> appListItems) throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();
        xmlSerializer.setOutput(writer);
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.docdecl("packages SYSTEM \"https://raw.githubusercontent.com/MuntashirAkon/AppManager/master/schema/packages.dtd\"");
        xmlSerializer.startTag("", "packages");
        xmlSerializer.attribute("", "version", String.valueOf(1));
        for (AppListItem appListItem : appListItems) {
            xmlSerializer.startTag("", "package");
            xmlSerializer.attribute("", "name", appListItem.packageName);
            xmlSerializer.attribute("", "label", appListItem.getPackageLabel());
            xmlSerializer.attribute("", "versionCode", String.valueOf(appListItem.getVersionCode()));
            xmlSerializer.attribute("", "versionName", appListItem.getVersionName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                xmlSerializer.attribute("", "minSdk", String.valueOf(appListItem.getMinSdk()));
            }
            xmlSerializer.attribute("", "targetSdk", String.valueOf(appListItem.getTargetSdk()));
            xmlSerializer.attribute("", "signature", appListItem.getSignatureSha256());
            xmlSerializer.attribute("", "firstInstallTime", String.valueOf(appListItem.getFirstInstallTime()));
            xmlSerializer.attribute("", "lastUpdateTime", String.valueOf(appListItem.getLastUpdateTime()));
            if (appListItem.getInstallerPackageName() != null) {
                xmlSerializer.attribute("", "installerPackageName", appListItem.getInstallerPackageName());
                if (appListItem.getInstallerPackageLabel() != null) {
                    xmlSerializer.attribute("", "installerPackageLabel", appListItem.getInstallerPackageLabel());
                }
            }
            xmlSerializer.endTag("", "package");
        }
        xmlSerializer.endTag("", "packages");
        xmlSerializer.endDocument();
        xmlSerializer.flush();
    }

    private static void exportCsv(@NonNull Writer writer,
                                  @NonNull List<AppListItem> appListItems) throws IOException {
        CsvWriter csvWriter = new CsvWriter(writer);
        // Add header
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            csvWriter.addLine(new String[]{"name", "label", "versionCode", "versionName", "minSdk",
                    "targetSdk", "signature", "firstInstallTime", "lastUpdateTime",
                    "installerPackageName", "installerPackageLabel"});
        } else {
            csvWriter.addLine(new String[]{"name", "label", "versionCode", "versionName",
                    "targetSdk", "signature", "firstInstallTime", "lastUpdateTime",
                    "installerPackageName", "installerPackageLabel"});
        }
        for (AppListItem item : appListItems) {
            String installerPackage = item.getInstallerPackageName() != null ? item.getInstallerPackageName() : "";
            String installerLabel = item.getInstallerPackageLabel() != null ? item.getInstallerPackageLabel() : "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                csvWriter.addLine(new String[]{item.packageName, item.getPackageLabel(),
                        String.valueOf(item.getVersionCode()), item.getVersionName(),
                        String.valueOf(item.getMinSdk()), String.valueOf(item.getTargetSdk()),
                        item.getSignatureSha256(), String.valueOf(item.getFirstInstallTime()),
                        String.valueOf(item.getLastUpdateTime()),
                        installerPackage, installerLabel});
            } else {
                csvWriter.addLine(new String[]{item.packageName, item.getPackageLabel(),
                        String.valueOf(item.getVersionCode()), item.getVersionName(),
                        String.valueOf(item.getTargetSdk()), item.getSignatureSha256(),
                        String.valueOf(item.getFirstInstallTime()),
                        String.valueOf(item.getLastUpdateTime()),
                        installerPackage, installerLabel});
            }
        }
    }

    private static void exportJson(@NonNull Writer writer,
                                   @NonNull List<AppListItem> appListItems)
            throws JSONException, IOException {
        // Should reflect packages.dtd
        JSONArray array = new JSONArray();
        for (AppListItem item : appListItems) {
            JSONObject object = new JSONObject();
            object.put("name", item.packageName);
            object.put("label", item.getPackageLabel());
            object.put("versionCode", item.getVersionCode());
            object.put("versionName", item.getVersionName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                object.put("minSdk", item.getMinSdk());
            }
            object.put("targetSdk", item.getTargetSdk());
            object.put("signature", item.getSignatureSha256());
            object.put("firstInstallTime", item.getFirstInstallTime());
            object.put("lastUpdateTime", item.getLastUpdateTime());
            if (item.getInstallerPackageName() != null) {
                object.put("installerPackageName", item.getInstallerPackageName());
                if (item.getInstallerPackageLabel() != null) {
                    object.put("installerPackageLabel", item.getInstallerPackageLabel());
                }
            }
            array.put(object);
        }
        writer.write(array.toString(4));
    }

    private static void exportMarkdown(@NonNull Context context, @NonNull Writer writer,
                                       @NonNull List<AppListItem> appListItems) throws IOException {
        writer.write("# Package Info\n\n");
        for (AppListItem appListItem : appListItems) {
            writer.append("## ").append(appListItem.getPackageLabel()).append("\n\n")
                    .append("**Package name:** ").append(appListItem.packageName).append("\n")
                    .append("**Version:** ").append(appListItem.getVersionName()).append(" (")
                    .append(String.valueOf(appListItem.getVersionCode())).append(")\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                writer.append("**Min SDK:** ").append(String.valueOf(appListItem.getMinSdk()))
                        .append(", ");
            }
            writer.append("**Target SDK:** ").append(String.valueOf(appListItem.getTargetSdk()))
                    .append("\n")
                    .append("**Date installed:** ")
                    .append(DateUtils.formatDateTime(context, appListItem.getFirstInstallTime()))
                    .append(", **Date updated:** ")
                    .append(DateUtils.formatDateTime(context, appListItem.getLastUpdateTime()))
                    .append("\n");
            if (appListItem.getInstallerPackageName() != null) {
                writer.append("**Installer:** ");
                if (appListItem.getInstallerPackageLabel() != null) {
                    writer.append(appListItem.getInstallerPackageLabel()).append(" (");
                }
                writer.append(appListItem.getInstallerPackageName());
                if (appListItem.getInstallerPackageLabel() != null) {
                    writer.append(")");
                }
            }
            writer.append("\n\n");
        }
    }

    @NonNull
    private static List<AppListItem> getAppListItems(@NonNull Context context,
                                                     @NonNull List<PackageInfo> packageInfoList) {
        List<AppListItem> appListItems = new ArrayList<>(packageInfoList.size());
        PackageManager pm = context.getPackageManager();
        for (PackageInfo packageInfo : packageInfoList) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            AppListItem item = new AppListItem(packageInfo.packageName);
            appListItems.add(item);
            item.setIcon(UIUtils.getBitmapFromDrawable(applicationInfo.loadIcon(pm)));
            item.setPackageLabel(applicationInfo.loadLabel(pm).toString());
            item.setVersionCode(PackageInfoCompat.getLongVersionCode(packageInfo));
            item.setVersionName(packageInfo.versionName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                item.setMinSdk(applicationInfo.minSdkVersion);
            }
            item.setTargetSdk(applicationInfo.targetSdkVersion);
            String[] signatureSha256 = PackageUtils.getSigningCertSha256Checksum(packageInfo, false);
            item.setSignatureSha256(TextUtils.join(",", signatureSha256));
            item.setFirstInstallTime(packageInfo.firstInstallTime);
            item.setLastUpdateTime(packageInfo.lastUpdateTime);
            String installerPackageName = PackageManagerCompat.getInstallerPackageName(
                    packageInfo.packageName, UserHandleHidden.getUserId(applicationInfo.uid));
            if (installerPackageName != null) {
                item.setInstallerPackageName(installerPackageName);
                String installerPackageLabel;
                try {
                    installerPackageLabel = pm.getApplicationInfo(installerPackageName, 0)
                            .loadLabel(pm).toString();
                    if (!installerPackageLabel.equals(installerPackageName)) {
                        item.setInstallerPackageLabel(installerPackageLabel);
                    }
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
        }
        return appListItems;
    }
}
