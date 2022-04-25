// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.internal.util.TextUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReportScanItem;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class ScannerFragment extends Fragment {
    private static final String SIG_TO_IGNORE = "^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w\\d?(\\.\\w\\d?)+)\\..*$";

    private int totalClasses;
    private final List<String> mTrackerClassList = new ArrayList<>();
    private final List<String> mLibClassList = new ArrayList<>();

    private CharSequence mAppName;
    @Nullable
    private String mPackageName;
    private ScannerViewModel mViewModel;
    private ScannerActivity mActivity;

    private View vtContainerView;
    private TextView vtTitleView;
    private TextView vtDescriptionView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(ScannerViewModel.class);
        mActivity = (ScannerActivity) requireActivity();
        // Checksum
        mViewModel.apkChecksumsLiveData().observe(getViewLifecycleOwner(), checksums -> {
            SpannableStringBuilder sb = new SpannableStringBuilder(mViewModel.getApkUri().toString()).append("\n");
            sb.append(getPrimaryText(mActivity, getString(R.string.checksums)));
            for (Pair<String, String> digest : checksums) {
                sb.append("\n").append(getPrimaryText(mActivity, digest.first + ": "))
                        .append(getMonospacedText(digest.second));
            }
            ((TextView) view.findViewById(R.id.apk_title)).setText(R.string.source_dir);
            ((TextView) view.findViewById(R.id.apk_description)).setText(sb);
        });
        // Package info: Title & subtitle
        mViewModel.packageInfoLiveData().observe(getViewLifecycleOwner(), packageInfo -> {
            if (packageInfo != null) {
                String archiveFilePath = mViewModel.getApkFile().getAbsolutePath();
                mPackageName = packageInfo.packageName;
                final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                applicationInfo.publicSourceDir = archiveFilePath;
                applicationInfo.sourceDir = archiveFilePath;
                mAppName = applicationInfo.loadLabel(mActivity.getPackageManager());
            } else {
                File apkFile = mViewModel.getApkFile();
                mAppName = apkFile != null ? apkFile.getName() : mViewModel.getApkUri().getLastPathSegment();
            }
            mActivity.setTitle(mAppName);
            mActivity.setSubtitle(R.string.scanner);
        });
        // APK verifier result
        mViewModel.apkVerifierResultLiveData().observe(getViewLifecycleOwner(), result -> {
            TextView checksumDescription = view.findViewById(R.id.checksum_description);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(PackageUtils.getApkVerifierInfo(result, mActivity));
            List<X509Certificate> certificates = result.getSignerCertificates();
            if (certificates != null && certificates.size() > 0) {
                builder.append(getCertificateInfo(mActivity, certificates));
            }
            checksumDescription.setText(builder);
        });
        // List all classes
        mViewModel.allClassesLiveData().observe(getViewLifecycleOwner(), allClasses -> {
            totalClasses = allClasses.size();
            ((TextView) view.findViewById(R.id.classes_title)).setText(getResources().getQuantityString(R.plurals.classes,
                    allClasses.size(), allClasses.size()));
            view.findViewById(R.id.classes).setOnClickListener(v ->
                    mActivity.loadNewFragment(new ClassListingFragment()));

            // Fetch tracker info
            new Thread(() -> {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                try {
                    setTrackerInfo(view);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            // Fetch library info
            new Thread(() -> {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                try {
                    setLibraryInfo(view);
                    // Progress is dismissed here because this will take the largest time
                    mActivity.runOnUiThread(() -> mActivity.showProgress(false));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        });
        // List tracker classes
        mViewModel.trackerClassesLiveData().observe(getViewLifecycleOwner(), trackerClasses -> {
            // TODO: 26/3/22
        });
        // List library classes
        mViewModel.libraryClassesLiveData().observe(getViewLifecycleOwner(), libraryClasses -> {
            // TODO: 26/3/22
        });
        // VirusTotal
        vtContainerView = view.findViewById(R.id.vt);
        if (!FeatureController.isInternetEnabled() || AppPref.getVtApiKey() == null) {
            vtContainerView.setVisibility(View.GONE);
            view.findViewById(R.id.vt_disclaimer).setVisibility(View.GONE);
        }
        vtTitleView = view.findViewById(R.id.vt_title);
        vtDescriptionView = view.findViewById(R.id.vt_description);
        mViewModel.vtFileScanMetaLiveData().observe(getViewLifecycleOwner(), vtFileScanMeta -> {
            if (vtFileScanMeta == null) {
                // Uploading
                vtTitleView.setText(R.string.vt_uploading);
            } else {
                // Upload completed and queued
                vtTitleView.setText(R.string.vt_queued);
                vtDescriptionView.setText(vtFileScanMeta.getPermalink());
            }
        });
        mViewModel.vtFileReportLiveData().observe(getViewLifecycleOwner(), vtFileReport -> {
            if (vtFileReport == null) {
                // Failed
                vtTitleView.setText(R.string.vt_failed);
                vtDescriptionView.setText(null);
                vtContainerView.setOnClickListener(null);
            } else if (vtFileReport.getPositives() == null) {
                // Still queued
                vtTitleView.setText(R.string.vt_queued);
                vtDescriptionView.setText(vtFileReport.getPermalink());
            } else {
                // Successful
                publishVirusTotalReport(vtFileReport);
            }
        });
        // Load summary for the APK file
        mViewModel.loadSummary();
    }

    private void publishVirusTotalReport(@NonNull VtFileReport vtFileReport) {
        int positives = Objects.requireNonNull(vtFileReport.getPositives());
        CharSequence resultSummary = getString(R.string.vt_success, positives, vtFileReport.getTotal());
        @ColorRes
        int color;
        if (positives <= 3) {
            color = R.color.stopped;
        } else if (positives <= 12) {
            color = R.color.tracker;
        } else color = R.color.electric_red;
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(mActivity)
                .setTitle(getString(R.string.vt_success, positives, vtFileReport.getTotal()))
                .setSubtitle(getString(R.string.vt_scan_date, vtFileReport.getScanDate()))
                .setEndIcon(R.drawable.ic_vt, v -> {
                    Uri vtPermalink = Uri.parse(vtFileReport.getPermalink());
                    Intent linkIntent = new Intent(Intent.ACTION_VIEW, vtPermalink);
                    if (linkIntent.resolveActivity(mActivity.getPackageManager()) != null) {
                        startActivity(linkIntent);
                    }
                })
                .setEndIconContentDescription(R.string.vt_permalink);
        Spanned result;
        Map<String, VtFileReportScanItem> vtFileReportScanItems = vtFileReport.getScans();
        if (vtFileReportScanItems != null) {
            @ColorInt int colorRed = ContextCompat.getColor(mActivity, R.color.electric_red);
            @ColorInt int colorGreen = ContextCompat.getColor(mActivity, R.color.stopped);
            ArrayList<Spannable> detectedList = new ArrayList<>();
            ArrayList<Spannable> undetectedList = new ArrayList<>();
            for (String avName : vtFileReportScanItems.keySet()) {
                VtFileReportScanItem item = Objects.requireNonNull(vtFileReportScanItems.get(avName));
                if (item.isDetected()) {
                    detectedList.add(new SpannableStringBuilder(getColoredText(getPrimaryText(mActivity, avName),
                            colorRed)).append(getSmallerText(" (" + item.getVersion() + ")"))
                            .append("\n").append(item.getMalware()));
                } else {
                    undetectedList.add(new SpannableStringBuilder(getColoredText(getPrimaryText(mActivity, avName),
                            colorGreen)).append(getSmallerText(" (" + item.getVersion() + ")")));
                }
            }
            detectedList.addAll(undetectedList);
            result = UiUtils.getOrderedList(detectedList);
        } else result = null;
        vtTitleView.setText(getColoredText(resultSummary, ContextCompat.getColor(mActivity, color)));
        if (result != null) {
            vtDescriptionView.setText(R.string.tap_to_see_details);
            vtContainerView.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                    .setCustomTitle(titleBuilder.build())
                    .setMessage(result)
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.copy, (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(getString(R.string.scan_in_vt), result);
                        clipboard.setPrimaryClip(clip);
                        Snackbar.make(vtContainerView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
                    })
                    .show());
        }
    }

    @NonNull
    private Map<String, SpannableStringBuilder> getNativeLibraryInfo(boolean trackerOnly) {
        Collection<String> nativeLibsInApk = mViewModel.getNativeLibraries();
        if (nativeLibsInApk.size() == 0) return new HashMap<>();
        String[] libNames = getResources().getStringArray(R.array.lib_native_names);
        String[] libSignatures = getResources().getStringArray(R.array.lib_native_signatures);
        int[] isTracker = getResources().getIntArray(R.array.lib_native_is_tracker);
        // The following array is directly mapped to the arrays above
        @SuppressWarnings("unchecked")
        List<String>[] matchedLibs = new List[libSignatures.length];
        Map<String, SpannableStringBuilder> foundNativeLibInfoMap = new ArrayMap<>();
        for (int i = 0; i < libSignatures.length; ++i) {
            if (trackerOnly && isTracker[i] == 0) continue;
            Pattern pattern = Pattern.compile(libSignatures[i]);
            for (String lib : nativeLibsInApk) {
                if (pattern.matcher(lib).find()) {
                    if (matchedLibs[i] == null) {
                        matchedLibs[i] = new ArrayList<>();
                    }
                    matchedLibs[i].add(lib);
                }
            }
            if (matchedLibs[i] == null) continue;
            SpannableStringBuilder builder = foundNativeLibInfoMap.get(libNames[i]);
            if (builder == null) {
                builder = new SpannableStringBuilder(getPrimaryText(mActivity, libNames[i]));
                foundNativeLibInfoMap.put(libNames[i], builder);
            }
            for (String lib : matchedLibs[i]) {
                builder.append("\n").append(getMonospacedText(lib));
            }
        }
        return foundNativeLibInfoMap;
    }

    private void setTrackerInfo(View view) {
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        int[] signatureCount = new int[trackerSignatures.length];
        int totalIteration = 0;
        long t_start, t_end;
        t_start = System.currentTimeMillis();
        // Iterate over all classes
        mTrackerClassList.clear();
        for (String className : mViewModel.getAllClasses()) {
            if (className.length() > 8 && className.contains(".")) {
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < trackerSignatures.length; i++) {
                    totalIteration++;
                    if (className.contains(trackerSignatures[i])) {
                        mTrackerClassList.add(className);
                        signatureCount[i]++;
                        break;
                    }
                }
            }
        }
        t_end = System.currentTimeMillis();
        long totalTimeTaken = t_end - t_start;
        mViewModel.setTrackerClasses(mTrackerClassList);
        Map<String, SpannableStringBuilder> foundTrackerInfoMap = new ArrayMap<>();
        foundTrackerInfoMap.putAll(getNativeLibraryInfo(true));
        final boolean[] hasSecondDegree = new boolean[]{false};
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < trackerSignatures.length; i++) {
            if (signatureCount[i] == 0) continue;
            if (foundTrackerInfoMap.get(trackerNames[i]) == null) {
                foundTrackerInfoMap.put(trackerNames[i], new SpannableStringBuilder()
                        .append(getPrimaryText(mActivity, trackerNames[i])));
            }
            //noinspection ConstantConditions Never null here
            foundTrackerInfoMap.get(trackerNames[i])
                    .append("\n")
                    .append(getMonospacedText(trackerSignatures[i]))
                    .append(getSmallerText(" (" + signatureCount[i] + ")"));
            if (!hasSecondDegree[0]) {
                hasSecondDegree[0] = trackerNames[i].startsWith("²");
            }
        }
        Set<String> foundTrackerNames = foundTrackerInfoMap.keySet();
        List<Spannable> foundTrackerInfo = new ArrayList<>(foundTrackerInfoMap.values());
        Collections.sort(foundTrackerInfo, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        Spanned trackerList = UiUtils.getOrderedList(foundTrackerInfo);
        SpannableStringBuilder foundTrackerList = new SpannableStringBuilder();
        int totalTrackersFound = foundTrackerInfoMap.size();
        if (totalTrackersFound > 0) {
            foundTrackerList.append(getString(R.string.found_trackers)).append(" ").append(
                    TextUtils.joinSpannable(", ", foundTrackerNames));
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(
                getString(R.string.tested_signatures_on_classes_and_time_taken, trackerSignatures.length, totalClasses,
                        totalTimeTaken, totalIteration));
        if (foundTrackerList.length() > 0) {
            builder.append("\n").append(foundTrackerList);
        }
        // Get summary
        CharSequence summary;
        if (totalTrackersFound == 0) {
            summary = getString(R.string.no_tracker_found);
        } else if (totalTrackersFound == 1) {
            summary = getResources().getQuantityString(R.plurals.tracker_and_classes, mTrackerClassList.size(), mTrackerClassList.size());
        } else if (totalTrackersFound == 2) {
            summary = getResources().getQuantityString(R.plurals.two_trackers_and_classes, mTrackerClassList.size(), mTrackerClassList.size());
        } else {
            summary = getResources().getQuantityString(R.plurals.other_trackers_and_classes, totalTrackersFound, totalTrackersFound, mTrackerClassList.size());
        }
        // Add colours
        CharSequence coloredSummary;
        if (totalTrackersFound == 0) {
            coloredSummary = getColoredText(summary, ContextCompat.getColor(mActivity, R.color.stopped));
        } else {
            coloredSummary = getColoredText(summary, ContextCompat.getColor(mActivity, R.color.electric_red));
        }

        mActivity.runOnUiThread(() -> {
            ((TextView) view.findViewById(R.id.tracker_title)).setText(coloredSummary);
            ((TextView) view.findViewById(R.id.tracker_description)).setText(builder);
            if (totalTrackersFound == 0) return;
            MaterialCardView trackersView = view.findViewById(R.id.tracker);
            trackersView.setOnClickListener(v -> {
                DialogTitleBuilder titleBuilder = new DialogTitleBuilder(mActivity)
                        .setTitle(R.string.tracker_details)
                        .setSubtitle(summary);
                if (mPackageName != null) {
                    titleBuilder.setEndIcon(R.drawable.ic_exodusprivacy, v1 -> {
                                Uri exodus_link = Uri.parse(String.format(
                                        "https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", mPackageName));
                                Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                                if (intent.resolveActivity(mActivity.getPackageManager()) != null) {
                                    startActivity(intent);
                                }
                            })
                            .setEndIconContentDescription(R.string.exodus_link);
                }
                new ScrollableDialogBuilder(mActivity, hasSecondDegree[0] ?
                        new SpannableStringBuilder(trackerList)
                                .append("\n\n")
                                .append(getSmallerText(getText(R.string.second_degree_tracker_note)))
                        : trackerList)
                        .setTitle(titleBuilder.build())
                        .enableAnchors()
                        .setPositiveButton(R.string.ok, null)
                        .setNeutralButton(R.string.copy, (dialog, which, isChecked) -> {
                            ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText(getString(R.string.signatures), trackerList);
                            clipboard.setPrimaryClip(clip);
                            Snackbar.make(trackersView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
                        })
                        .show();
            });
        });
    }

    private void setLibraryInfo(View view) {
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getResources().getStringArray(R.array.lib_types);
        // The following array is directly mapped to the arrays above
        int[] signatureCount = new int[libSignatures.length];
        // Iterate over all classes
        mLibClassList.clear();
        for (String className : mViewModel.getAllClasses()) {
            if (className.length() > 8 && className.contains(".")) {
                boolean matched = false;
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < libSignatures.length; i++) {
                    if (className.contains(libSignatures[i])) {
                        matched = true;
                        // Add to found classes
                        mLibClassList.add(className);
                        // Increment this signature match count
                        signatureCount[i]++;
                        break;
                    }
                }
                // Add the class to the missing libs list if it doesn't match the filters
                if (!matched
                        && (mPackageName != null && !className.startsWith(mPackageName))
                        && !className.matches(SIG_TO_IGNORE)) {
                    missingLibs.add(className);
                }
            }
        }
        Map<String, SpannableStringBuilder> foundLibInfoMap = new ArrayMap<>();
        foundLibInfoMap.putAll(getNativeLibraryInfo(false));
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < libSignatures.length; i++) {
            if (signatureCount[i] == 0) continue;
            if (foundLibInfoMap.get(libNames[i]) == null) {
                // Add the lib info since it isn't added already
                foundLibInfoMap.put(libNames[i], new SpannableStringBuilder()
                        .append(getPrimaryText(mActivity, libNames[i]))
                        .append(getSmallerText(" (" + libTypes[i] + ")")));
            }
            //noinspection ConstantConditions Never null here
            foundLibInfoMap.get(libNames[i])
                    .append("\n")
                    .append(getMonospacedText(libSignatures[i]))
                    .append(getSmallerText(" (" + signatureCount[i] + ")"));
        }
        Set<String> foundLibNames = foundLibInfoMap.keySet();
        List<Spannable> foundLibInfoList = new ArrayList<>(foundLibInfoMap.values());
        int totalLibsFound = foundLibInfoList.size();
        Collections.sort(foundLibInfoList, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        Spanned foundLibsInfo = UiUtils.getOrderedList(foundLibInfoList);
        String summary;
        if (totalLibsFound == 0) {
            summary = getString(R.string.no_libs);
        } else {
            summary = getResources().getQuantityString(R.plurals.libraries, totalLibsFound, totalLibsFound);
        }

        mActivity.runOnUiThread(() -> {
            ((TextView) view.findViewById(R.id.libs_title)).setText(summary);
            ((TextView) view.findViewById(R.id.libs_description)).setText(TextUtils.join(", ", foundLibNames));
            if (totalLibsFound == 0) return;
            MaterialCardView libsView = view.findViewById(R.id.libs);
            libsView.setOnClickListener(v ->
                    new ScrollableDialogBuilder(mActivity, foundLibsInfo)
                            .setTitle(new DialogTitleBuilder(mActivity)
                                    .setTitle(R.string.lib_details)
                                    .setSubtitle(summary)
                                    .build())
                            .setNegativeButton(R.string.ok, null)
                            .setNeutralButton(R.string.copy, (dialog, which, isChecked) -> {
                                ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText(getString(R.string.signatures), foundLibsInfo);
                                clipboard.setPrimaryClip(clip);
                                Snackbar.make(libsView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
                            })
                            .show());
            // Missing libs
            if (missingLibs.size() > 0) {
                ((TextView) view.findViewById(R.id.missing_libs_title)).setText(getResources().getQuantityString(R.plurals.missing_signatures, missingLibs.size(), missingLibs.size()));
                View v = view.findViewById(R.id.missing_libs);
                v.setVisibility(View.VISIBLE);
                v.setOnClickListener(v2 -> new SearchableMultiChoiceDialogBuilder<>(mActivity, missingLibs,
                        ArrayUtils.toCharSequence(missingLibs))
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
    private static Spannable getCertificateInfo(@NonNull Context context, @NonNull List<X509Certificate> certificates) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (X509Certificate cert : certificates) {
            try {
                if (builder.length() > 0) builder.append("\n\n");
                builder.append(getPrimaryText(context, context.getString(R.string.issuer) + ": "))
                        .append(cert.getIssuerX500Principal().getName()).append("\n")
                        .append(getPrimaryText(context, context.getString(R.string.algorithm) + ": "))
                        .append(cert.getSigAlgName()).append("\n");
                // Checksums
                builder.append(getPrimaryText(context, context.getString(R.string.checksums)));
                Pair<String, String>[] digests = DigestUtils.getDigests(cert.getEncoded());
                for (Pair<String, String> digest : digests) {
                    builder.append("\n")
                            .append(getPrimaryText(context, digest.first + ": "))
                            .append(getMonospacedText(digest.second));
                }
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
            }
        }
        return builder;
    }
}
