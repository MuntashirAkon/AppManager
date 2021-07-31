// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.android.internal.util.TextUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.EmptySpan;
import io.github.muntashirakon.AppManager.types.NumericSpan;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

// Copyright 2015 Google, Inc.
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
    @Nullable
    private String mPackageName;
    private ParcelFileDescriptor fd;
    private File apkFile;
    private Uri apkUri;
    private boolean isExternalApk;
    private ScannerViewModel model;

    @Override
    protected void onDestroy() {
        IOUtils.deleteDir(new File(getCacheDir().getParent(), APP_DEX));
        IOUtils.deleteDir(getCodeCacheDir());
        IOUtils.closeQuietly(fd);
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
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_scanner);
        setSupportActionBar(findViewById(R.id.toolbar));
        model = new ViewModelProvider(this).get(ScannerViewModel.class);
        mActionBar = getSupportActionBar();
        Intent intent = getIntent();
        isExternalApk = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, true);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgress(true);

        apkUri = IntentCompat.getDataUri(intent);
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

        model.loadSummary(apkFile, apkUri);

        model.getApkChecksums().observe(this, checksums -> {
            SpannableStringBuilder sb = new SpannableStringBuilder(apkUri.toString()).append("\n");
            sb.append(getPrimaryText(this, getString(R.string.checksums)));
            for (Pair<String, String> digest : checksums) {
                sb.append("\n").append(getPrimaryText(this, digest.first + ": ")).append(digest.second);
            }
            ((TextView) findViewById(R.id.apk_title)).setText(R.string.source_dir);
            ((TextView) findViewById(R.id.apk_description)).setText(sb);
        });
        model.getPackageInfo().observe(this, packageInfo -> {
            if (packageInfo != null) {
                String archiveFilePath = model.getApkFile().getAbsolutePath();
                mPackageName = packageInfo.packageName;
                final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                applicationInfo.publicSourceDir = archiveFilePath;
                applicationInfo.sourceDir = archiveFilePath;
                mAppName = applicationInfo.loadLabel(getPackageManager());
                if (mActionBar != null) {
                    mActionBar.setTitle(mAppName);
                    mActionBar.setSubtitle(R.string.scanner);
                }
            } else {
                Toast.makeText(this, R.string.failed_to_fetch_package_info, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        model.getApkVerifierResult().observe(this, result -> {
            TextView checksumDescription = findViewById(R.id.checksum_description);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(PackageUtils.getApkVerifierInfo(result, this));
            List<X509Certificate> certificates = result.getSignerCertificates();
            if (certificates != null && certificates.size() > 0) {
                builder.append(getCertificateInfo(certificates));
            }
            checksumDescription.setText(builder);
        });
        model.getAllClasses().observe(this, allClasses -> {
            dexClasses = model.getDexClasses();
            classListAll = allClasses;
            ((TextView) findViewById(R.id.classes_title)).setText(getResources().getQuantityString(R.plurals.classes,
                    classListAll.size(), classListAll.size()));
            findViewById(R.id.classes).setOnClickListener(v -> {
                Intent intent1 = new Intent(this, ClassListingActivity.class);
                intent1.putExtra(ClassListingActivity.EXTRA_APP_NAME, mAppName);
                startActivity(intent1);
            });
            // Fetch tracker info
            new Thread(() -> {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                try {
                    setTrackerInfo();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            // Fetch library info
            new Thread(() -> {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                try {
                    setLibraryInfo();
                    // Progress is dismissed here because this will take the largest time
                    runOnUiThread(() -> showProgress(false));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_scanner, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_install).setVisible(isExternalApk && FeatureController.isInstallerEnabled());
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
        for (String className : model.getClassListAll()) {
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
            findViewById(R.id.tracker).setOnClickListener(v -> {
                ScrollableDialogBuilder builder1 = new ScrollableDialogBuilder(this, getOrderedList(foundTrackerInfo))
                        .setTitle(R.string.tracker_details)
                        .setPositiveButton(R.string.ok, null)
                        .setNegativeButton(R.string.copy, (dialog, which, isChecked) -> {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText(getString(R.string.signatures), TextUtils.join("\n", foundTrackerInfo));
                            clipboard.setPrimaryClip(clip);
                        });
                if (mPackageName != null) {
                    builder1.setNeutralButton(R.string.exodus_link, (dialog, which, isChecked) -> {
                        Uri exodus_link = Uri.parse(String.format(
                                "https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", mPackageName));
                        Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        }
                    });
                }
                builder1.show();
            });
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
        for (String className : model.getClassListAll()) {
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
                if (!matched && (mPackageName != null && !className.startsWith(mPackageName)) && !className.matches(SIG_TO_IGNORE)) {
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
                        .showSelectAll(false)
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
    private Spannable getCertificateInfo(@NonNull List<X509Certificate> certificates) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (X509Certificate cert : certificates) {
            try {
                if (builder.length() > 0) builder.append("\n\n");
                builder.append(getPrimaryText(this, getString(R.string.issuer) + ": "))
                        .append(cert.getIssuerX500Principal().getName()).append("\n")
                        .append(getPrimaryText(this, getString(R.string.algorithm) + ": "))
                        .append(cert.getSigAlgName()).append("\n");
                // Checksums
                builder.append(getPrimaryText(this, getString(R.string.checksums)));
                Pair<String, String>[] digests = DigestUtils.getDigests(cert.getEncoded());
                for (Pair<String, String> digest : digests) {
                    builder.append("\n").append(getPrimaryText(this, digest.first + ": ")).append(digest.second);
                }
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
            }
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
