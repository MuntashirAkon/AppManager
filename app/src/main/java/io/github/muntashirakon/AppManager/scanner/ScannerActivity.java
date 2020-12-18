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
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.TextUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.types.EmptySpan;
import io.github.muntashirakon.AppManager.types.NumericSpan;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class ScannerActivity extends BaseActivity {
    public static final String EXTRA_IS_EXTERNAL = "is_external";

    private static final String APP_DEX = "app_dex";
    private static final String SIG_TO_IGNORE = "^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w\\d?(\\.\\w\\d?)+)\\..*$";

    /* package */ static List<String> classListAll;
    /* package */ static List<String> trackerClassList = new ArrayList<>();
    /* package */ static List<String> libClassList = new ArrayList<>();
    /* package */ static DexClasses dexClasses;

    private CharSequence mAppName;
    private ActionBar mActionBar;
    private LinearProgressIndicator mProgressIndicator;
    private String mPackageName;
    private ParcelFileDescriptor fd;
    private File apkFile;
    private Uri apkUri;
    private boolean isExternalApk;

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
        isExternalApk = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, true);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgress(true);

        apkUri = intent.getData();
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
                        Pair<String, String>[] digests = DigestUtils.getDigests(apkFile);
                        SpannableStringBuilder sb = new SpannableStringBuilder(apkUri.toString()).append("\n");
                        sb.append(getPrimaryText(this, getString(R.string.checksums)));
                        for (Pair<String, String> digest : digests) {
                            sb.append("\n").append(getPrimaryText(this, digest.first + ": ")).append(digest.second);
                        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_scanner, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_install).setVisible(isExternalApk);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_install) {
            Intent openApk = new Intent(getBaseContext(), PackageInstallerActivity.class);
            openApk.setData(apkUri);
            startActivity(openApk);
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
        boolean[] signaturesFound = new boolean[trackerSignatures.length];
        int totalIteration = 0;
        int totalTrackersFound = 0;
        long t_start, t_end;
        t_start = System.currentTimeMillis();
        // Iterate over all classes
        for (String className : classListAll) {
            if (className.length() > 8 && className.contains(".")) {
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < trackerSignatures.length; i++) {
                    totalIteration++;
                    if (className.contains(trackerSignatures[i])) {
                        trackerClassList.add(className);
                        signatureCount[i]++;
                        signaturesFound[i] = true;
                        break;
                    }
                }
            }
        }
        t_end = System.currentTimeMillis();
        long totalTimeTaken = t_end - t_start;
        Map<String, SpannableStringBuilder> foundTrackerInfoMap = new ArrayMap<>();
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < trackerSignatures.length; i++) {
            if (signaturesFound[i]) {
                if (foundTrackerInfoMap.get(trackerNames[i]) == null) {
                    ++totalTrackersFound;
                    foundTrackerInfoMap.put(trackerNames[i], new SpannableStringBuilder()
                            .append(getPrimaryText(this, trackerNames[i])));
                }
                //noinspection ConstantConditions Never null here
                foundTrackerInfoMap.get(trackerNames[i]).append("\n").append(trackerSignatures[i])
                        .append(getSmallerText(" (" + signatureCount[i] + ")"));
            }
        }
        Set<String> foundTrackerNames = foundTrackerInfoMap.keySet();
        List<Spannable> foundTrackerInfo = new ArrayList<>(foundTrackerInfoMap.values());
        Collections.sort(foundTrackerInfo, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        SpannableStringBuilder foundTrackerList = new SpannableStringBuilder();
        if (totalTrackersFound > 0) {
            foundTrackerList.append(getString(R.string.found_trackers)).append(" ").append(
                    TextUtils.joinSpannable(", ", foundTrackerNames));
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(
                getString(R.string.tested_signatures_on_classes_and_time_taken,
                        trackerSignatures.length, classListAll.size(), totalTimeTaken, totalIteration));
        if (foundTrackerList.length() > 0) {
            builder.append("\n").append(foundTrackerList);
        }

        CharSequence summary;
        if (totalTrackersFound == 0) {
            summary = getString(R.string.no_tracker_found);
        } else if (totalTrackersFound == 1) {
            summary = getResources().getQuantityString(R.plurals.tracker_and_classes, trackerClassList.size(), trackerClassList.size());
        } else if (totalTrackersFound == 2) {
            summary = getResources().getQuantityString(R.plurals.two_trackers_and_classes, trackerClassList.size(), trackerClassList.size());
        } else {
            summary = getResources().getQuantityString(R.plurals.other_trackers_and_classes, totalTrackersFound, totalTrackersFound, trackerClassList.size());
        }
        // Add colours
        CharSequence coloredSummary;
        if (totalTrackersFound == 0) {
            coloredSummary = UIUtils.getColoredText(summary, ContextCompat.getColor(this, R.color.stopped));
        } else {
            coloredSummary = UIUtils.getColoredText(summary, ContextCompat.getColor(this, R.color.electric_red));
        }

        int finalTotalTrackersFound = totalTrackersFound;
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.tracker_title)).setText(coloredSummary);
            ((TextView) findViewById(R.id.tracker_description)).setText(builder);
            if (finalTotalTrackersFound == 0) return;
            findViewById(R.id.tracker).setOnClickListener(v ->
                    new ScrollableDialogBuilder(this, getOrderedList(foundTrackerInfo))
                            .setTitle(R.string.tracker_details)
                            .setPositiveButton(R.string.ok, null)
                            .setNegativeButton(R.string.copy, (dialog, which, isChecked) -> {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.signatures), TextUtils.join("\n", foundTrackerInfo));
                                clipboard.setPrimaryClip(clip);
                            })
                            .setNeutralButton(R.string.exodus_link, (dialog, which, isChecked) -> {
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
        // The following two arrays are directly mapped to the arrays above
        int[] signatureCount = new int[libSignatures.length];
        boolean[] signaturesFound = new boolean[libSignatures.length];
        @IntRange(from = 0)
        int totalLibsFound = 0;
        // Iterate over all classes
        for (String className : classListAll) {
            if (className.length() > 8 && className.contains(".")) {
                boolean matched = false;
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < libSignatures.length; i++) {
                    if (className.contains(libSignatures[i])) {
                        matched = true;
                        // Add to found classes
                        libClassList.add(className);
                        // Increment this signature match count
                        signatureCount[i]++;
                        // Set this signature as matched
                        signaturesFound[i] = true;
                        break;
                    }
                }
                // Add the class to the missing libs list if it doesn't match the filters
                if (!matched && !className.startsWith(mPackageName) && !className.matches(SIG_TO_IGNORE)) {
                    missingLibs.add(className);
                }
            }
        }
        Map<String, SpannableStringBuilder> foundLibInfoMap = new ArrayMap<>();
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < libSignatures.length; i++) {
            if (signaturesFound[i]) {
                if (foundLibInfoMap.get(libNames[i]) == null) {
                    // Add the lib info since it isn't added already
                    ++totalLibsFound;
                    foundLibInfoMap.put(libNames[i], new SpannableStringBuilder()
                            .append(getPrimaryText(this, libNames[i]))
                            .append(getSmallerText(" (" + libTypes[i] + ")")));
                }
                //noinspection ConstantConditions Never null here
                foundLibInfoMap.get(libNames[i]).append("\n").append(libSignatures[i])
                        .append(getSmallerText(" (" + signatureCount[i] + ")"));
            }
        }
        Set<String> foundLibNames = foundLibInfoMap.keySet();
        List<Spannable> foundLibInfo = new ArrayList<>(foundLibInfoMap.values());
        Collections.sort(foundLibInfo, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        String summary;
        if (totalLibsFound == 0) {
            summary = getString(R.string.no_libs);
        } else {
            summary = getResources().getQuantityString(R.plurals.libraries, totalLibsFound, totalLibsFound);
        }

        int finalTotalLibsFound = totalLibsFound;
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.libs_title)).setText(summary);
            ((TextView) findViewById(R.id.libs_description)).setText(TextUtils.join(", ", foundLibNames));
            if (finalTotalLibsFound == 0) return;
            findViewById(R.id.libs).setOnClickListener(v ->
                    new ScrollableDialogBuilder(this, getOrderedList(foundLibInfo))
                            .setTitle(R.string.lib_details)
                            .setNegativeButton(R.string.ok, null)
                            .setNeutralButton(R.string.copy, (dialog, which, isChecked) -> {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.signatures), TextUtils.join("\n", foundLibInfo));
                                clipboard.setPrimaryClip(clip);
                            })
                            .show());
            // Missing libs
            if (missingLibs.size() > 0) {
                ((TextView) findViewById(R.id.missing_libs_title)).setText(getResources().getQuantityString(R.plurals.missing_signatures, missingLibs.size(), missingLibs.size()));
                View view = findViewById(R.id.missing_libs);
                view.setVisibility(View.VISIBLE);
                view.setOnClickListener(v -> new SearchableMultiChoiceDialogBuilder<>(this,
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
                builder.append(getPrimaryText(this, getString(R.string.issuer) + ": "))
                        .append(cert.getIssuerX500Principal().getName()).append("\n")
                        .append(getPrimaryText(this, getString(R.string.algorithm) + ": "))
                        .append(cert.getSigAlgName()).append("\n");
                // Checksums
                builder.append(getPrimaryText(this, getString(R.string.checksums)));
                Pair<String, String>[] digests = DigestUtils.getDigests(certBytes);
                for (Pair<String, String> digest : digests) {
                    builder.append("\n").append(getPrimaryText(this, digest.first + ": ")).append(digest.second);
                }
            }
        } catch (CertificateException ignored) {
        }
        return builder;
    }

    @NonNull
    public Spanned getOrderedList(@NonNull final Iterable<Spannable> spannableList) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        Spannable tmpSpannable;
        int j = 0;
        for (Spannable spannable : spannableList) {
            tmpSpannable = new SpannableString(spannable);
            int finish = tmpSpannable.toString().indexOf("\n");
            tmpSpannable.setSpan(new NumericSpan(40, 30, ++j), 0,
                    (finish == -1 ? tmpSpannable.length() : finish), 0);
            if (finish != -1) {
                tmpSpannable.setSpan(new EmptySpan(40, 30), finish + 1,
                        tmpSpannable.length(), 0);
            }
            spannableStringBuilder.append(tmpSpannable).append("\n");
        }
        return spannableStringBuilder;
    }
}
