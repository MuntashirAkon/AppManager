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

package io.github.muntashirakon.AppManager.scanner;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getUnderlinedString;

public class ScannerActivity extends BaseActivity {
    private static final String APP_DEX = "app_dex";
    private static final String SIG_TO_IGNORE = "^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w(\\.\\w)+)\\..*$";

    /* package */ static List<String> classListAll;
    /* package */ static List<String> trackerClassList = new ArrayList<>();
    /* package */ static List<String> libClassList = new ArrayList<>();
    /* package */ static DexClasses dexClasses;

    private CharSequence mAppName;
    private ActionBar mActionBar;
    private ProgressIndicator mProgressIndicator;
    private String mPackageName;
    private ParcelFileDescriptor fd;
    private File apkFile;

    @Override
    protected void onDestroy() {
        IOUtils.deleteDir(new File(getCacheDir().getParent(), APP_DEX));
        IOUtils.deleteDir(getCodeCacheDir());
        IOUtils.closeQuietly(fd);
        if (apkFile != null && !apkFile.getAbsolutePath().startsWith("/data/app/")) {
            // Only attempt to delete the apk file if it's cached
            IOUtils.deleteSilently(apkFile);
        }
        IOUtils.closeQuietly(dexClasses);
        // Empty static vars
        // This works because ClassListingActivity opens on top of ScannerActivity
        classListAll = null;
        trackerClassList.clear();
        libClassList.clear();
        dexClasses = null;
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        setSupportActionBar(findViewById(R.id.toolbar));
        mActionBar = getSupportActionBar();
        Intent intent = getIntent();

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgress(true);

        final Uri apkUri = intent.getData();
        if (apkUri == null) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            try {
                fd = getContentResolver().openFileDescriptor(apkUri, "r");
                if (fd == null) {
                    throw new FileNotFoundException("FileDescription cannot be null");
                }
                apkFile = IOUtils.getFileFromFd(fd);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            String path = apkUri.getPath();
            if (path != null) apkFile = new File(path);
        }
        if (apkFile == null) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // A new thread to load all summary
        new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                // Test if this path is readable
                if (!apkFile.exists() || !apkFile.canRead()) {
                    // Not readable, cache the file
                    try (InputStream uriStream = getContentResolver().openInputStream(apkUri)) {
                        apkFile = IOUtils.getCachedFile(uriStream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // Generate apk checksums
                new Thread(() -> {
                    try {
                        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                        SpannableStringBuilder sb = new SpannableStringBuilder(apkUri.toString()).append("\n");
                        sb.append(getBoldString(getString(R.string.checksums))).append("\n")
                                .append(getBoldString(DigestUtils.MD5 + ": "))
                                .append(DigestUtils.getHexDigest(DigestUtils.MD5, apkFile)).append("\n")
                                .append(getBoldString(DigestUtils.SHA_1 + ": "))
                                .append(DigestUtils.getHexDigest(DigestUtils.SHA_1, apkFile)).append("\n")
                                .append(getBoldString(DigestUtils.SHA_256 + ": "))
                                .append(DigestUtils.getHexDigest(DigestUtils.SHA_256, apkFile)).append("\n")
                                .append(getBoldString(DigestUtils.SHA_384 + ": "))
                                .append(DigestUtils.getHexDigest(DigestUtils.SHA_384, apkFile)).append("\n")
                                .append(getBoldString(DigestUtils.SHA_512 + ": "))
                                .append(DigestUtils.getHexDigest(DigestUtils.SHA_512, apkFile));
                        runOnUiThread(() -> {
                            ((TextView) findViewById(R.id.apk_title)).setText(R.string.source_dir);
                            ((TextView) findViewById(R.id.apk_description)).setText(sb);
                        });
                    } catch (Exception e) {
                        // ODEX, need to see how to handle
                        e.printStackTrace();
                    }
                }).start();
                final PackageManager pm = getApplicationContext().getPackageManager();
                final String archiveFilePath = apkFile.getAbsolutePath();
                @SuppressLint("WrongConstant") final PackageInfo packageInfo = pm.getPackageArchiveInfo(archiveFilePath, 64);
                // Fetch signature
                new Thread(() -> {
                    if (packageInfo != null) {
                        Spannable certInfo = getCertificateInfo(packageInfo);
                        runOnUiThread(() -> ((TextView) findViewById(R.id.checksum_description)).setText(certInfo));
                        mPackageName = packageInfo.packageName;
                        final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                        applicationInfo.publicSourceDir = archiveFilePath;
                        applicationInfo.sourceDir = archiveFilePath;
                        mAppName = applicationInfo.loadLabel(pm);
                        runOnUiThread(() -> {
                            if (mActionBar != null) {
                                mActionBar.setTitle(mAppName);
                                mActionBar.setSubtitle(R.string.scanner);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.failed_to_fetch_package_info, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }).start();
                // Load classes
                dexClasses = new DexClasses(this, apkFile);
                classListAll = dexClasses.getClassNames();
                runOnUiThread(() -> {
                    ((TextView) findViewById(R.id.classes_title)).setText(getResources().getQuantityString(R.plurals.classes, classListAll.size(), classListAll.size()));
                    findViewById(R.id.classes).setOnClickListener(v -> {
                        Intent intent1 = new Intent(this, ClassListingActivity.class);
                        intent1.putExtra(ClassListingActivity.EXTRA_APP_NAME, mAppName);
                        startActivity(intent1);
                    });
                });
                // Fetch tracker info
                new Thread(() -> {
                    try {
                        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                        setTrackerInfo();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                // Fetch library info
                new Thread(() -> {
                    try {
                        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                        setLibraryInfo();
                        // Progress is dismissed here because this will take the largest time
                        runOnUiThread(() -> showProgress(false));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
                finishAffinity();
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showProgress(boolean willShow) {
        if (willShow) {
            mProgressIndicator.show();
        } else {
            mProgressIndicator.hide();
        }
    }

    private void setTrackerInfo() {
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        int[] signatureCount = new int[trackerSignatures.length];
        SpannableStringBuilder found = new SpannableStringBuilder();
        boolean[] signaturesFound = new boolean[trackerSignatures.length];
        int totalIteration = 0;
        int totalTrackersFound = 0;
        long t_start, t_end;
        t_start = System.currentTimeMillis();
        for (String className : classListAll) {
            if (className.length() > 8 && className.contains(".")) {
                for (int i = 0; i < trackerSignatures.length; i++) {
                    totalIteration++;
                    if (className.contains(trackerSignatures[i])) {
                        trackerClassList.add(className);
                        signatureCount[i]++;
                        signaturesFound[i] = true;
                        if (found.toString().contains(trackerNames[i])) break;
                        else {
                            ++totalTrackersFound;
                            if (found.length() > 0) found.append(", ");
                            found.append(trackerNames[i]);
                        }
                        break;
                    }
                }
            }
        }
        t_end = System.currentTimeMillis();
        long totalTimeTaken = t_end - t_start;
        SpannableStringBuilder foundTrackerList = new SpannableStringBuilder();
        if (totalTrackersFound > 0) {
            foundTrackerList.append(getString(R.string.found_trackers)).append(" ").append(found);
        }
        SpannableStringBuilder foundTrackersInfo = new SpannableStringBuilder();
        for (int i = 0, j = 0; i < trackerSignatures.length; i++) {
            if (signaturesFound[i]) {
                if (!foundTrackersInfo.toString().contains(trackerNames[i])) {
                    foundTrackersInfo.append(getUnderlinedString(getBoldString((++j) + ". " + trackerNames[i]))).append("\n");
                }
                foundTrackersInfo.append("    ").append(trackerSignatures[i]).append(" (").append(String.valueOf(signatureCount[i])).append(")\n");
            }
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(
                getString(R.string.tested_signatures_on_classes_and_time_taken,
                        trackerSignatures.length, classListAll.size(), totalTimeTaken, totalIteration));
        if (foundTrackerList.length() > 0) {
            builder.append("\n").append(foundTrackerList);
        }

        String summary;
        if (totalTrackersFound == 0) {
            summary = getString(R.string.no_tracker_found);
        } else if (totalTrackersFound == 1) {
            summary = getResources().getQuantityString(R.plurals.tracker_and_classes, trackerClassList.size(), trackerClassList.size());
        } else if (totalTrackersFound == 2) {
            summary = getResources().getQuantityString(R.plurals.two_trackers_and_classes, trackerClassList.size(), trackerClassList.size());
        } else {
            summary = getResources().getQuantityString(R.plurals.other_trackers_and_classes, totalTrackersFound, totalTrackersFound, trackerClassList.size());
        }

        int finalTotalTrackersFound = totalTrackersFound;
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.tracker_title)).setText(summary);
            ((TextView) findViewById(R.id.tracker_description)).setText(builder);
            if (finalTotalTrackersFound == 0) return;
            findViewById(R.id.tracker).setOnClickListener(v ->
                    UIUtils.getDialogWithScrollableTextView(this, foundTrackersInfo, false)
                            .setTitle(R.string.tracker_details)
                            .setPositiveButton(R.string.ok, null)
                            .setNegativeButton(R.string.copy, (dialog, which) -> {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.signatures), foundTrackersInfo);
                                clipboard.setPrimaryClip(clip);
                            })
                            .setNeutralButton(R.string.exodus_link, (dialog, which) -> {
                                Uri exodus_link = Uri.parse(String.format("https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", mPackageName));
                                Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            })
                            .show());
        });
    }

    private void setLibraryInfo() {
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getResources().getStringArray(R.array.lib_types);
        int[] signatureCount = new int[libSignatures.length];
        SpannableStringBuilder found = new SpannableStringBuilder();
        boolean[] signaturesFound = new boolean[libSignatures.length];
        @IntRange(from = 0)
        int totalLibsFound = 0;
        for (String className : classListAll) {
            if (className.length() > 8 && className.contains(".")) {
                boolean matched = false;
                for (int i = 0; i < libSignatures.length; i++) {
                    if (className.contains(libSignatures[i])) {
                        matched = true;
                        libClassList.add(className);
                        signatureCount[i]++;
                        signaturesFound[i] = true;
                        if (found.toString().contains(libNames[i])) break;
                        else {
                            ++totalLibsFound;
                            if (found.length() > 0) found.append(", ");
                            found.append(libNames[i]);
                        }
                        break;
                    }
                }
                if (!matched && !className.startsWith(mPackageName) && !className.matches(SIG_TO_IGNORE)) {
                    missingLibs.add(className);
                }
            }
        }
        SpannableStringBuilder foundLibsInfo = new SpannableStringBuilder();
        for (int i = 0, j = 0; i < libSignatures.length; i++) {
            if (signaturesFound[i]) {
                if (!foundLibsInfo.toString().contains(libNames[i])) {
                    foundLibsInfo.append(getUnderlinedString(getBoldString((++j) + ". " + libNames[i])));
                    foundLibsInfo.append(" (").append(libTypes[i]).append(")\n");
                }
                foundLibsInfo.append("    ").append(libSignatures[i]).append(" (").append(String.valueOf(signatureCount[i])).append(")\n");
            }
        }
        String summary;
        if (totalLibsFound == 0) {
            summary = getString(R.string.no_libs);
        } else {
            summary = getResources().getQuantityString(R.plurals.libraries, totalLibsFound, totalLibsFound);
        }

        int finalTotalLibsFound = totalLibsFound;
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.libs_title)).setText(summary);
            ((TextView) findViewById(R.id.libs_description)).setText(found);
            if (finalTotalLibsFound == 0) return;
            findViewById(R.id.libs).setOnClickListener(v ->
                    UIUtils.getDialogWithScrollableTextView(this, foundLibsInfo, false)
                            .setTitle(R.string.lib_details)
                            .setNegativeButton(R.string.ok, null)
                            .setNeutralButton(R.string.copy, (dialog, which) -> {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.signatures), foundLibsInfo);
                                clipboard.setPrimaryClip(clip);
                            })
                            .show());
            // Missing libs
            if (missingLibs.size() > 0) {
                ((TextView) findViewById(R.id.missing_libs_title)).setText(getResources().getQuantityString(R.plurals.missing_signatures, missingLibs.size(), missingLibs.size()));
                View view = findViewById(R.id.missing_libs);
                view.setVisibility(View.VISIBLE);
                view.setOnClickListener(v -> new SearchableMultiChoiceDialogBuilder(this,
                        missingLibs, ArrayUtils.toCharSequence(missingLibs))
                        .setTitle(R.string.signatures)
                        .setNegativeButton(R.string.ok, null)
                        .setNeutralButton(R.string.send_selected, (dialog, which, selectedItems) -> {
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("message/rfc822");
                            i.putExtra(Intent.EXTRA_EMAIL, new String[]{"muntashirakon@riseup.net"});
                            i.putExtra(Intent.EXTRA_SUBJECT, "App Manager: Missing signatures");
                            i.putExtra(Intent.EXTRA_TEXT, selectedItems.toString());
                            startActivity(Intent.createChooser(i, getText(R.string.signatures)));
                        })
                        .show());
            }
        });
    }

    @NonNull
    private Spannable getCertificateInfo(@NonNull PackageInfo p) {
        Signature[] signatures = p.signatures;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        X509Certificate cert;
        byte[] certBytes;
        try {
            for (Signature signature : signatures) {
                certBytes = signature.toByteArray();
                cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(certBytes));
                if (builder.length() > 0) builder.append("\n\n");
                builder.append(getBoldString(getString(R.string.issuer) + ": "))
                        .append(cert.getIssuerX500Principal().getName()).append("\n")
                        .append(getBoldString(getString(R.string.algorithm) + ": "))
                        .append(cert.getSigAlgName()).append("\n");
                // Checksums
                builder.append(getBoldString(getString(R.string.checksums)))
                        .append("\n")
                        .append(getBoldString(DigestUtils.MD5 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.MD5, certBytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_1 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_1, certBytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_256 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_256, certBytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_384 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_384, certBytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_512 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_512, certBytes));
            }
        } catch (CertificateException ignored) {
        }
        return builder;
    }
}
