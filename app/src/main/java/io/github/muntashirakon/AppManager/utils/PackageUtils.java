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

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.DeadObjectException;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import com.android.apksig.ApkVerifier;
import com.android.internal.util.TextUtils;

import java.io.File;
import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public final class PackageUtils {
    public static final File PACKAGE_STAGING_DIRECTORY = new File("/data/local/tmp");

    public static final int flagSigningInfo;
    public static final int flagDisabledComponents;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            flagDisabledComponents = PackageManager.MATCH_DISABLED_COMPONENTS;
        else flagDisabledComponents = PackageManager.GET_DISABLED_COMPONENTS;
    }

    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern SERVICE_REGEX = Pattern.compile("ServiceRecord\\{.*/([^\\}]+)\\}");
    private static final String SERVICE_NOTHING = "(nothing)";

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> collectComponentClassNames(String packageName) {
        try {
            @SuppressLint("WrongConstant")
            PackageInfo packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS | flagDisabledComponents
                            | PackageManager.GET_URI_PERMISSION_PATTERNS
                            | PackageManager.GET_SERVICES);
            return collectComponentClassNames(packageInfo);
        } catch (PackageManager.NameNotFoundException ignore) {
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof DeadObjectException)) {
                throw e;
            }
        }
        return new HashMap<>();
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> collectComponentClassNames(@NonNull PackageInfo packageInfo) {
        HashMap<String, RulesStorageManager.Type> componentClasses = new HashMap<>();
        // Add activities
        if (packageInfo.activities != null) {
            for (ActivityInfo activityInfo : packageInfo.activities) {
                if (activityInfo.targetActivity != null)
                    componentClasses.put(activityInfo.targetActivity, RulesStorageManager.Type.ACTIVITY);
                else componentClasses.put(activityInfo.name, RulesStorageManager.Type.ACTIVITY);
            }
        }
        // Add others
        if (packageInfo.services != null) {
            for (ComponentInfo componentInfo : packageInfo.services)
                componentClasses.put(componentInfo.name, RulesStorageManager.Type.SERVICE);
        }
        if (packageInfo.receivers != null) {
            for (ComponentInfo componentInfo : packageInfo.receivers)
                componentClasses.put(componentInfo.name, RulesStorageManager.Type.RECEIVER);
        }
        if (packageInfo.providers != null) {
            for (ComponentInfo componentInfo : packageInfo.providers)
                componentClasses.put(componentInfo.name, RulesStorageManager.Type.PROVIDER);
        }
        return componentClasses;
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getFilteredComponents(String packageName, String[] signatures) {
        HashMap<String, RulesStorageManager.Type> filteredComponents = new HashMap<>();
        HashMap<String, RulesStorageManager.Type> components = collectComponentClassNames(packageName);
        for (String componentName : components.keySet()) {
            for (String signature : signatures) {
                if (componentName.startsWith(signature) || componentName.contains(signature)) {
                    filteredComponents.put(componentName, components.get(componentName));
                }
            }
        }
        return filteredComponents;
    }

    @NonNull
    public static Collection<Integer> getFilteredAppOps(String packageName, @NonNull int[] appOps) {
        List<Integer> filteredAppOps = new ArrayList<>();
        AppOpsService appOpsService = new AppOpsService();
        for (int appOp : appOps) {
            try {
                if (appOpsService.checkOperation(appOp, -1, packageName, Users.getCurrentUserHandle()) != AppOpsManager.MODE_IGNORED) {
                    filteredAppOps.add(appOp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filteredAppOps;
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getUserDisabledComponentsForPackage(String packageName) {
        HashMap<String, RulesStorageManager.Type> componentClasses = collectComponentClassNames(packageName);
        HashMap<String, RulesStorageManager.Type> disabledComponents = new HashMap<>();
        PackageManager pm = AppManager.getContext().getPackageManager();
        for (String componentName : componentClasses.keySet()) {
            if (isComponentDisabledByUser(pm, packageName, componentName))
                disabledComponents.put(componentName, componentClasses.get(componentName));
        }
        disabledComponents.putAll(ComponentUtils.getIFWRulesForPackage(packageName));
        return disabledComponents;
    }

    public static boolean isComponentDisabledByUser(@NonNull PackageManager pm, @NonNull String packageName, @NonNull String componentClassName) {
        ComponentName componentName = new ComponentName(packageName, componentClassName);
        switch (pm.getComponentEnabledSetting(componentName)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                return false;
        }
    }

    @NonNull
    public static List<String> getRunningServicesForPackage(String packageName) {
        List<String> runningServices = new ArrayList<>();
        if (!PermissionUtils.hasDumpPermission()) return runningServices;
        Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "activity", "services", "-p", packageName});
        if (result.isSuccessful()) {
            List<String> serviceDump = result.getOutputAsList(1);
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

    public static boolean hasRunningServices(String packageName) {
        if (!PermissionUtils.hasDumpPermission()) return false;
        Runner.Result result = Runner.runCommand(Runner.getUserInstance(), new String[]{"dumpsys", "activity", "services", "-p", packageName});
        if (result.isSuccessful()) {
            List<String> serviceDump = result.getOutputAsList(1);
            if (serviceDump.size() == 1 && SERVICE_NOTHING.equals(serviceDump.get(0).trim())) {
                return false;
            }
            Matcher matcher;
            String service, line;
            ListIterator<String> it = serviceDump.listIterator();
            if (it.hasNext()) {
                line = it.next();
                if ("Last ANR service:".equals(line.trim())) return false;
                matcher = SERVICE_REGEX.matcher(line);
                while (it.hasNext()) {
                    if (matcher.find(0)) {
                        service = matcher.group(1);
                        line = it.next();
                        if ("Last ANR service:".equals(line.trim())) break;
                        matcher = SERVICE_REGEX.matcher(line);
                        while (it.hasNext()) {
                            if (matcher.find(0)) break;
                            if (line.contains("app=ProcessRecord{")) {
                                if (service != null) return true;
                            }
                            line = it.next();
                            matcher = SERVICE_REGEX.matcher(line);
                        }
                    } else matcher = SERVICE_REGEX.matcher(it.next());
                }
            }
        }
        return false;
    }

    @NonNull
    public static String getPackageLabel(@NonNull PackageManager pm, String packageName) {
        try {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return packageName;
    }

    @NonNull
    public static CharSequence getPackageLabel(@NonNull PackageManager pm, String packageName, int userHandle) {
        try {
            ApplicationInfo applicationInfo = ApiSupporter.getInstance(LocalServer.getInstance()).getApplicationInfo(packageName, 0, userHandle);
            return applicationInfo.loadLabel(pm);
        } catch (Exception ignore) {
        }
        return packageName;
    }

    @Nullable
    public static ArrayList<String> packagesToAppLabels(@NonNull PackageManager pm, @Nullable List<String> packages, List<Integer> userHandles) {
        if (packages == null) return null;
        ArrayList<String> appLabels = new ArrayList<>();
        int i = 0;
        for (String packageName : packages) {
            appLabels.add(PackageUtils.getPackageLabel(pm, packageName, userHandles.get(i)).toString());
            ++i;
        }
        return appLabels;
    }

    public static boolean isInstalled(@NonNull PackageManager packageManager, String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return applicationInfo != null;
    }

    public static int getAppUid(@NonNull PackageManager packageManager, String packageName) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return 0;
    }

    @NonNull
    public static String getSourceDir(@NonNull ApplicationInfo applicationInfo) {
        String sourceDir = new File(applicationInfo.publicSourceDir).getParent(); // or applicationInfo.sourceDir
        if (sourceDir == null) {
            throw new RuntimeException("Application source directory cannot be empty");
        }
        return sourceDir;
    }

    @NonNull
    public static String[] getDataDirs(@NonNull ApplicationInfo applicationInfo, boolean loadExternal, boolean loadMediaObb) {
        ArrayList<String> dataDirs = new ArrayList<>();
        if (applicationInfo.dataDir == null) {
            throw new RuntimeException("Data directory cannot be empty.");
        }
        dataDirs.add(applicationInfo.dataDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !applicationInfo.dataDir.equals(applicationInfo.deviceProtectedDataDir)) {
            dataDirs.add(applicationInfo.deviceProtectedDataDir);
        }
        if (loadExternal) {
            List<PrivilegedFile> externalFiles = new ArrayList<>(Arrays.asList(OsEnvironment
                    .buildExternalStorageAppDataDirs(applicationInfo.packageName)));
            for (PrivilegedFile externalFile : externalFiles) {
                if (externalFile != null && externalFile.exists())
                    dataDirs.add(externalFile.getAbsolutePath());
            }
        }
        if (loadMediaObb) {
            List<PrivilegedFile> externalFiles = new ArrayList<>();
            externalFiles.addAll(Arrays.asList(OsEnvironment.buildExternalStorageAppMediaDirs(applicationInfo.packageName)));
            externalFiles.addAll(Arrays.asList(OsEnvironment.buildExternalStorageAppObbDirs(applicationInfo.packageName)));
            for (PrivilegedFile externalFile : externalFiles) {
                if (externalFile != null && externalFile.exists())
                    dataDirs.add(externalFile.getAbsolutePath());
            }
        }
        return dataDirs.toArray(new String[0]);
    }

    public static String getHiddenCodePathOrDefault(String packageName, String defaultPath) {
        Runner.Result result = Runner.runCommand(RunnerUtils.CMD_PM + " dump " + packageName + " | "
                + Runner.TOYBOX + " grep codePath");
        if (result.isSuccessful()) {
            List<String> paths = result.getOutputAsList();
            if (paths.size() > 0) {
                // Get only the last path
                String codePath = paths.get(paths.size() - 1);
                int start = codePath.indexOf('=');
                if (start != -1) return codePath.substring(start + 1);
            }
        }
        return new File(defaultPath).getParent();
    }

    @Nullable
    public static Signature[] getSigningInfo(@NonNull PackageInfo packageInfo, boolean isExternal) {
        if (!isExternal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = packageInfo.signingInfo;
            if (signingInfo == null) return null;
            return signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else return packageInfo.signatures;
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(@NonNull PackageInfo packageInfo) {
        return getSigningCertSha256Checksum(packageInfo, false);
    }

    public static boolean isSignatureDifferent(PackageInfo newPkgInfo, PackageInfo oldPkgInfo) {
        String[] newChecksums = getSigningCertSha256Checksum(newPkgInfo, true);
        List<String> oldChecksums = new ArrayList<>(Arrays.asList(getSigningCertSha256Checksum(oldPkgInfo)));
        // Signature is different if the number of signatures don't match
        if (newChecksums.length != oldChecksums.size()) return true;
        for (String newChecksum : newChecksums) {
            oldChecksums.remove(newChecksum);
        }
        // Old checksums should contain no values if the checksums are the same
        return oldChecksums.size() != 0;
    }

    @NonNull
    public static String[] getSigningCertSha256Checksum(PackageInfo packageInfo, boolean isExternal) {
        return getSigningCertChecksums(DigestUtils.SHA_256, packageInfo, isExternal);
    }

    @NonNull
    public static String[] getSigningCertChecksums(@DigestUtils.Algorithm String algo,
                                                   PackageInfo packageInfo, boolean isExternal) {
        Signature[] signatureArray = getSigningInfo(packageInfo, isExternal);
        ArrayList<String> checksums = new ArrayList<>();
        if (signatureArray != null) {
            for (Signature signature : signatureArray) {
                checksums.add(DigestUtils.getHexDigest(algo, signature.toByteArray()));
            }
        }
        return checksums.toArray(new String[0]);
    }

    @NonNull
    public static Spannable getSigningCertificateInfo(@NonNull Context ctx, @Nullable X509Certificate certificate) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (certificate == null) return builder;
        byte[] certBytes = certificate.getSignature();
        builder.append(getPrimaryText(ctx, ctx.getString(R.string.subject) + ": "))
                .append(certificate.getSubjectX500Principal().getName()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.issuer) + ": "))
                .append(certificate.getIssuerX500Principal().getName()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.issued_date) + ": "))
                .append(certificate.getNotBefore().toString()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.expiry_date) + ": "))
                .append(certificate.getNotAfter().toString()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.type) + ": "))
                .append(certificate.getType()).append(", ")
                .append(getPrimaryText(ctx, ctx.getString(R.string.version) + ": "))
                .append(String.valueOf(certificate.getVersion())).append(", ")
                .append(getPrimaryText(ctx, ctx.getString(R.string.validity) + ": "));
        try {
            certificate.checkValidity();
            builder.append(ctx.getString(R.string.valid));
        } catch (CertificateExpiredException e) {
            builder.append(ctx.getString(R.string.expired));
        } catch (CertificateNotYetValidException e) {
            builder.append(ctx.getString(R.string.not_yet_valid));
        }
        builder.append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.serial_no) + ": "))
                .append(Utils.bytesToHex(certificate.getSerialNumber().toByteArray())).append("\n");
        // Checksums
        builder.append(getTitleText(ctx, ctx.getString(R.string.checksums))).append("\n");
        Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
        for (Pair<String, String> digest : digests) {
            builder.append(getPrimaryText(ctx, digest.first + ": ")).append(digest.second).append("\n");
        }
        // Signature
        builder.append(getTitleText(ctx, ctx.getString(R.string.signature)))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.algorithm) + ": "))
                .append(certificate.getSigAlgName()).append("\n")
                .append(getPrimaryText(ctx, "OID: "))
                .append(certificate.getSigAlgOID()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.signature) + ": "))
                .append(Utils.bytesToHex(certificate.getSignature())).append("\n");
        // Public key used by Google: https://github.com/google/conscrypt
        // 1. X509PublicKey (PublicKey)
        // 2. OpenSSLRSAPublicKey (RSAPublicKey)
        // 3. OpenSSLECPublicKey (ECPublicKey)
        PublicKey publicKey = certificate.getPublicKey();
        builder.append(getTitleText(ctx, ctx.getString(R.string.public_key)))
                .append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.algorithm) + ": "))
                .append(publicKey.getAlgorithm()).append("\n")
                .append(getPrimaryText(ctx, ctx.getString(R.string.format) + ": "))
                .append(publicKey.getFormat());
        if (publicKey instanceof RSAPublicKey) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            builder.append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_exponent) + ": "))
                    .append(rsaPublicKey.getPublicExponent().toString()).append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_modulus) + ": "))
                    .append(Utils.bytesToHex(rsaPublicKey.getModulus().toByteArray()));
        } else if (publicKey instanceof ECPublicKey) {
            ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
            builder.append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.dsa_affine_x) + ": "))
                    .append(ecPublicKey.getW().getAffineX().toString()).append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.dsa_affine_y) + ": "))
                    .append(ecPublicKey.getW().getAffineY().toString());
        }
        // TODO(5/10/20): Add description for each extensions
        Set<String> critSet = certificate.getCriticalExtensionOIDs();
        if (critSet != null && !critSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.critical_exts)));
            for (String oid : critSet) {
                builder.append("\n- ")
                        .append(getPrimaryText(ctx, oid + ": "))
                        .append(Utils.bytesToHex(certificate.getExtensionValue(oid)));
            }
        }
        Set<String> nonCritSet = certificate.getNonCriticalExtensionOIDs();
        if (nonCritSet != null && !nonCritSet.isEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.non_critical_exts)));
            for (String oid : nonCritSet) {
                builder.append("\n- ")
                        .append(getPrimaryText(ctx,oid + ": "))
                        .append(Utils.bytesToHex(certificate.getExtensionValue(oid)));
            }
        }
        return builder;
    }

    @NonNull
    public static Spannable getApkVerifierInfo(@Nullable ApkVerifier.Result result, Context ctx) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (result == null) return builder;
        int colorRed = ContextCompat.getColor(ctx, R.color.red);
        int colorGreen = ContextCompat.getColor(ctx, R.color.stopped);
        int warnCount = 0;
        List<CharSequence> errors = new ArrayList<>();
        for (ApkVerifier.IssueWithParams err : result.getErrors()) {
            errors.add(getColoredText(err.toString(), colorRed));
        }
        warnCount += result.getWarnings().size();
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeIgnoredSigners()) {
            String name = signer.getName();
            for (ApkVerifier.IssueWithParams err : signer.getErrors()) {
                errors.add(getColoredText(new SpannableStringBuilder(getBoldString(name + ": ")).append(err.toString()), colorRed));
            }
            warnCount += signer.getWarnings().size();
        }
        if (result.isVerified()) {
            if (warnCount == 0) {
                builder.append(getColoredText(getTitleText(ctx, "\u2714 " +
                        ctx.getString(R.string.verified)), colorGreen));
            } else {
                builder.append(getColoredText(getTitleText(ctx, "\u2714 " + ctx.getResources()
                        .getQuantityString(R.plurals.verified_with_warning, warnCount, warnCount)), colorGreen));
            }
            if (result.isSourceStampVerified()) {
                builder.append("\n\u2714 ").append(ctx.getString(R.string.source_stamp_verified));
            }
            List<CharSequence> sigSchemes = new LinkedList<>();
            if (result.isVerifiedUsingV1Scheme()) sigSchemes.add("v1");
            if (result.isVerifiedUsingV2Scheme()) sigSchemes.add("v2");
            if (result.isVerifiedUsingV3Scheme()) sigSchemes.add("v3");
            if (result.isVerifiedUsingV4Scheme()) sigSchemes.add("v4");
            builder.append("\n").append(getPrimaryText(ctx, ctx.getResources()
                    .getQuantityString(R.plurals.signature_schemes_pl, sigSchemes.size()) + ": "));
            builder.append(TextUtils.joinSpannable(", ", sigSchemes));
        } else {
            builder.append(getColoredText(getTitleText(ctx, "\u2718 " + ctx.getString(R.string.not_verified)), colorRed));
        }
        builder.append("\n");
        // If there are errors, no certificate info will be loaded
        builder.append(TextUtils.joinSpannable("\n", errors)).append("\n");
        return builder;
    }
}
