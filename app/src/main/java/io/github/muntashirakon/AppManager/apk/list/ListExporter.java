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

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public final class ListExporter {
    public static final int EXPORT_TYPE_XML = 1;
    public static final int EXPORT_TYPE_MARKDOWN = 2;

    @IntDef({EXPORT_TYPE_XML, EXPORT_TYPE_MARKDOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExportType {
    }

    @NonNull
    public static String export(@NonNull Context context, @ExportType int exportType,
                                @NonNull List<PackageInfo> packageInfoList) throws IOException {
        List<AppListItem> appListItems = new ArrayList<>(packageInfoList.size());
        PackageManager pm = context.getPackageManager();
        for (PackageInfo packageInfo : packageInfoList) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            AppListItem item = new AppListItem(packageInfo.packageName);
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
            String installerPackageName = PackageManagerCompat.getInstallerPackageName(packageInfo.packageName,
                    UserHandleHidden.getUserId(applicationInfo.uid));
            if (installerPackageName != null) {
                item.setInstallerPackageName(installerPackageName);
                String installerPackageLabel;
                try {
                    installerPackageLabel = pm.getApplicationInfo(installerPackageName, 0).loadLabel(pm).toString();
                    if (!installerPackageLabel.equals(installerPackageName)) {
                        item.setInstallerPackageLabel(installerPackageLabel);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
            appListItems.add(item);
        }
        if (exportType == EXPORT_TYPE_XML) {
            return exportXml(appListItems);
        } else if (exportType == EXPORT_TYPE_MARKDOWN) {
            return exportMarkdown(context, appListItems);
        }
        throw new IllegalArgumentException("Invalid export type: " + exportType);
    }

    @NonNull
    private static String exportXml(@NonNull List<AppListItem> appListItems) throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();
        StringWriter stringWriter = new StringWriter();
        xmlSerializer.setOutput(stringWriter);
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
        return stringWriter.toString();
    }

    @NonNull
    private static String exportMarkdown(@NonNull Context context, @NonNull List<AppListItem> appListItems) {
        StringBuilder sb = new StringBuilder("# Package Info\n\n");
        for (AppListItem appListItem : appListItems) {
            sb.append("## ").append(appListItem.getPackageLabel()).append("\n\n")
                    .append("**Package name:** ").append(appListItem.packageName).append("\n")
                    .append("**Version:** ").append(appListItem.getVersionName()).append(" (")
                    .append(appListItem.getVersionCode()).append(")\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sb.append("**Min SDK:** ").append(appListItem.getMinSdk()).append(", ");
            }
            sb.append("**Target SDK:** ").append(appListItem.getTargetSdk()).append("\n")
                    .append("**Date installed:** ").append(DateUtils.formatDateTime(context, appListItem.getFirstInstallTime()))
                    .append(", **Date updated:** ").append(DateUtils.formatDateTime(context, appListItem.getLastUpdateTime()))
                    .append("\n");
            if (appListItem.getInstallerPackageName() != null) {
                sb.append("**Installer:** ");
                if (appListItem.getInstallerPackageLabel() != null) {
                    sb.append(appListItem.getInstallerPackageLabel()).append(" (");
                }
                sb.append(appListItem.getInstallerPackageName());
                if (appListItem.getInstallerPackageLabel() != null) {
                    sb.append(")");
                }
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
