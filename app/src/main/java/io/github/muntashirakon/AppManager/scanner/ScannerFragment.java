// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtAvEngineResult;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class ScannerFragment extends Fragment {
    private CharSequence mAppName;
    private ScannerViewModel mViewModel;
    private ScannerActivity mActivity;

    private MaterialCardView mVtContainerView;
    private TextView mVtTitleView;
    private TextView mVtDescriptionView;
    private TextView pithusDescriptionView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(ScannerViewModel.class);
        mActivity = (ScannerActivity) requireActivity();
        int cardColor = ColorCodes.getListItemColor1(mActivity);
        MaterialCardView classesView = view.findViewById(R.id.classes);
        classesView.setCardBackgroundColor(cardColor);
        MaterialCardView trackersView = view.findViewById(R.id.tracker);
        trackersView.setCardBackgroundColor(cardColor);
        mVtContainerView = view.findViewById(R.id.vt);
        mVtContainerView.setCardBackgroundColor(cardColor);
        mVtTitleView = view.findViewById(R.id.vt_title);
        mVtDescriptionView = view.findViewById(R.id.vt_description);
        MaterialCardView pithusContainerView = view.findViewById(R.id.pithus);
        pithusContainerView.setCardBackgroundColor(cardColor);
        pithusDescriptionView = view.findViewById(R.id.pithus_description);
        MaterialCardView libsView = view.findViewById(R.id.libs);
        libsView.setCardBackgroundColor(cardColor);
        MaterialCardView apkInfoView = view.findViewById(R.id.apk);
        apkInfoView.setCardBackgroundColor(cardColor);
        MaterialCardView signaturesView = view.findViewById(R.id.signatures);
        signaturesView.setCardBackgroundColor(cardColor);
        MaterialCardView missingLibsView = view.findViewById(R.id.missing_libs);
        missingLibsView.setCardBackgroundColor(cardColor);
        // VirusTotal
        if (!FeatureController.isVirusTotalEnabled() || Prefs.VirusTotal.getApiKey() == null) {
            mVtContainerView.setVisibility(View.GONE);
            view.findViewById(R.id.vt_disclaimer).setVisibility(View.GONE);
        }
        // Pithus
        if (!FeatureController.isInternetEnabled()) {
            pithusContainerView.setVisibility(View.GONE);
        }
        // Checksum
        mViewModel.apkChecksumsLiveData().observe(getViewLifecycleOwner(), checksums -> {
            if (checksums == null) {
                return;
            }
            List<CharSequence> lines = new ArrayList<>();
            for (Pair<String, String> digest : checksums) {
                lines.add(new SpannableStringBuilder()
                        .append(getPrimaryText(mActivity, digest.first + LangUtils.getSeparatorString()))
                        .append(getMonospacedText(digest.second)));
            }
            ((TextView) view.findViewById(R.id.apk_title)).setText(R.string.apk_checksums);
            ((TextView) view.findViewById(R.id.apk_description)).setText(TextUtilsCompat.joinSpannable("\n", lines));
        });
        // Package info: Title & subtitle
        mViewModel.packageInfoLiveData().observe(getViewLifecycleOwner(), packageInfo -> {
            if (packageInfo != null) {
                String archiveFilePath = mViewModel.getApkFile().getAbsolutePath();
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
            if (certificates != null && !certificates.isEmpty()) {
                builder.append(getCertificateInfo(mActivity, certificates));
            }
            checksumDescription.setText(builder);
        });
        // List all classes
        mViewModel.allClassesLiveData().observe(getViewLifecycleOwner(), allClasses -> {
            ((TextView) view.findViewById(R.id.classes_title)).setText(getResources().getQuantityString(R.plurals.classes,
                    allClasses.size(), allClasses.size()));
            classesView.setOnClickListener(v -> mActivity.loadNewFragment(new ClassListingFragment()));
        });
        // List tracker classes
        mViewModel.trackerClassesLiveData().observe(getViewLifecycleOwner(), trackerClasses ->
                setTrackerInfo(trackerClasses, view));
        // List library classes
        mViewModel.libraryClassesLiveData().observe(getViewLifecycleOwner(), libraryClasses -> {
            setLibraryInfo(libraryClasses, view);
            // Progress is dismissed here because this will take the largest time
            mActivity.showProgress(false);
        });
        // List missing classes
        mViewModel.missingClassesLiveData().observe(getViewLifecycleOwner(), missingClasses -> {
            if (!missingClasses.isEmpty()) {
                ((TextView) view.findViewById(R.id.missing_libs_title)).setText(getResources().getQuantityString(R.plurals.missing_signatures, missingClasses.size(), missingClasses.size()));
                missingLibsView.setVisibility(View.VISIBLE);
                missingLibsView.setOnClickListener(v2 -> new SearchableMultiChoiceDialogBuilder<>(mActivity, missingClasses,
                        ArrayUtils.toCharSequence(missingClasses))
                        .setTitle(R.string.signatures)
                        .showSelectAll(false)
                        .setNegativeButton(R.string.ok, null)
                        .setNeutralButton(R.string.send_selected, (dialog, which, selectedItems) -> {
                            String message = "Package: " + mViewModel.getPackageName() + "\n" +
                                    "Signatures: " + selectedItems;
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("message/rfc822");
                            i.putExtra(Intent.EXTRA_EMAIL, new String[]{"am4android@riseup.net"});
                            i.putExtra(Intent.EXTRA_SUBJECT, "App Manager: Missing signatures");
                            i.putExtra(Intent.EXTRA_TEXT, message);
                            startActivity(Intent.createChooser(i, getText(R.string.signatures)));
                        })
                        .show());
            }
        });
        mViewModel.vtFileUploadLiveData().observe(getViewLifecycleOwner(), permalink -> {
            if (permalink == null) {
                // Uploading
                mVtTitleView.setText(R.string.vt_uploading);
                if (Prefs.VirusTotal.promptBeforeUpload()) {
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.scan_in_vt)
                            .setMessage(R.string.vt_confirm_uploading_file)
                            .setCancelable(false)
                            .setPositiveButton(R.string.vt_confirm_upload_and_scan, (dialog, which) -> mViewModel.enableUploading())
                            .setNegativeButton(R.string.no, (dialog, which) -> mViewModel.disableUploading())
                            .show();
                } else mViewModel.enableUploading();
            } else {
                // Upload completed and queued
                mVtTitleView.setText(R.string.vt_queued);
                mVtDescriptionView.setText(permalink);
            }
        });
        mViewModel.vtFileReportLiveData().observe(getViewLifecycleOwner(), vtFileReport -> {
            if (vtFileReport == null) {
                // Failed
                mVtTitleView.setText(R.string.vt_failed);
                mVtDescriptionView.setText(null);
                mVtContainerView.setOnClickListener(null);
            } else {
                // Successful
                publishVirusTotalReport(vtFileReport);
            }
        });
        mViewModel.getPithusReportLiveData().observe(getViewLifecycleOwner(), url -> {
            if (url != null) {
                // Report available
                pithusDescriptionView.setText(url);
            } else {
                // Report unavailable
                pithusDescriptionView.setText(R.string.report_not_available);
            }
        });
        // Load summary for the APK file
        mViewModel.loadSummary();
    }

    private void publishVirusTotalReport(@NonNull VtFileReport vtFileReport) {
        int positives = Objects.requireNonNull(vtFileReport.getPositives());
        CharSequence resultSummary = getString(R.string.vt_success, positives, vtFileReport.getTotal());
        @ColorInt
        int color;
        if (positives <= 3) {
            color = ColorCodes.getVirusTotalSafeIndicatorColor(mActivity);
        } else if (positives <= 12) {
            color = ColorCodes.getVirusTotalUnsafeIndicatorColor(mActivity);
        } else color = ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(mActivity);
        CharSequence scanDate = getString(R.string.vt_scan_date, DateUtils.formatDateTime(mActivity, vtFileReport.scanDate));
        String permalink = vtFileReport.permalink;
        Spanned result;
        List<VtAvEngineResult> vtFileReportScanItems = vtFileReport.results;
        if (!vtFileReportScanItems.isEmpty()) {
            int colorUnsafe = ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(mActivity);
            int colorSafe = ColorCodes.getVirusTotalSafeIndicatorColor(mActivity);
            ArrayList<Spannable> detectedList = new ArrayList<>();
            ArrayList<Spannable> suspiciousList = new ArrayList<>();
            ArrayList<Spannable> undetectedList = new ArrayList<>();
            ArrayList<Spannable> neutralList = new ArrayList<>();
            for (VtAvEngineResult item : vtFileReportScanItems) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                Spannable title = getPrimaryText(mActivity, item.engineName);
                if (item.category < VtAvEngineResult.CAT_UNDETECTED) {
                    sb.append(title);
                    neutralList.add(sb);
                } else if (item.category < VtAvEngineResult.CAT_SUSPICIOUS) {
                    sb.append(getColoredText(title, colorSafe));
                    undetectedList.add(sb);
                } else if (item.category == VtAvEngineResult.CAT_SUSPICIOUS) {
                    sb.append(getColoredText(title, colorUnsafe));
                    suspiciousList.add(sb);
                } else { // malicious
                    sb.append(getColoredText(title, colorUnsafe));
                    detectedList.add(sb);
                }
                sb.append(getSmallerText(" (" + item.engineVersion + ")"));
                if (item.result != null) {
                    sb.append("\n").append(item.result);
                }
            }
            detectedList.addAll(suspiciousList);
            detectedList.addAll(undetectedList);
            detectedList.addAll(neutralList);
            result = UiUtils.getOrderedList(detectedList);
        } else result = null;
        mVtTitleView.setText(getColoredText(resultSummary, color));
        if (result != null) {
            mVtDescriptionView.setText(R.string.tap_to_see_details);
            mVtContainerView.setOnClickListener(v -> {
                VirusTotalDialog fragment = VirusTotalDialog.getInstance(resultSummary, scanDate, result, permalink);
                fragment.show(getParentFragmentManager(), VirusTotalDialog.TAG);
            });
        }
    }

    @NonNull
    private Map<String, SpannableStringBuilder> getNativeLibraryInfo(boolean trackerOnly) {
        Collection<String> nativeLibsInApk = mViewModel.getNativeLibraries();
        if (nativeLibsInApk.isEmpty()) return new HashMap<>();
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

    private void setTrackerInfo(@NonNull List<SignatureInfo> trackerInfoList, @NonNull View view) {
        Map<String, SpannableStringBuilder> foundTrackerInfoMap = new ArrayMap<>();
        foundTrackerInfoMap.putAll(getNativeLibraryInfo(true));
        boolean hasSecondDegree = false;
        // Iterate over signatures again but this time list only the found ones.
        for (SignatureInfo trackerInfo : trackerInfoList) {
            if (foundTrackerInfoMap.get(trackerInfo.label) == null) {
                foundTrackerInfoMap.put(trackerInfo.label, new SpannableStringBuilder()
                        .append(getPrimaryText(mActivity, trackerInfo.label)));
            }
            //noinspection ConstantConditions Never null here
            foundTrackerInfoMap.get(trackerInfo.label)
                    .append("\n")
                    .append(getMonospacedText(trackerInfo.signature))
                    .append(getSmallerText(" (" + trackerInfo.getCount() + ")"));
            if (!hasSecondDegree) {
                hasSecondDegree = trackerInfo.label.startsWith("²");
            }
        }
        Set<String> foundTrackerNames = foundTrackerInfoMap.keySet();
        List<Spannable> foundTrackerInfo = new ArrayList<>(foundTrackerInfoMap.values());
        Collections.sort(foundTrackerInfo, (o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
        SpannableStringBuilder trackerList = new SpannableStringBuilder(UiUtils.getOrderedList(foundTrackerInfo));
        SpannableStringBuilder foundTrackerList = new SpannableStringBuilder();
        int totalTrackersFound = foundTrackerInfoMap.size();
        if (totalTrackersFound > 0) {
            foundTrackerList.append(getString(R.string.found_trackers)).append(" ").append(
                    TextUtilsCompat.joinSpannable(", ", foundTrackerNames));
        }
        int totalTrackerClasses = mViewModel.getTrackerClasses().size();
        // Get summary
        CharSequence summary;
        if (totalTrackersFound == 0) {
            summary = getString(R.string.no_tracker_found);
        } else if (totalTrackersFound == 1) {
            summary = getResources().getQuantityString(R.plurals.tracker_and_classes, totalTrackerClasses, totalTrackerClasses);
        } else if (totalTrackersFound == 2) {
            summary = getResources().getQuantityString(R.plurals.two_trackers_and_classes, totalTrackerClasses, totalTrackerClasses);
        } else {
            summary = getResources().getQuantityString(R.plurals.other_trackers_and_classes, totalTrackersFound, totalTrackersFound, totalTrackerClasses);
        }
        // Add colours
        CharSequence coloredSummary;
        if (totalTrackersFound == 0) {
            coloredSummary = getColoredText(summary, ColorCodes.getScannerNoTrackerIndicatorColor(mActivity));
        } else {
            coloredSummary = getColoredText(summary, ColorCodes.getScannerTrackerIndicatorColor(mActivity));
        }

        TextView trackerInfoTitle = view.findViewById(R.id.tracker_title);
        TextView trackerInfoDescription = view.findViewById(R.id.tracker_description);
        trackerInfoTitle.setText(coloredSummary);
        if (totalTrackersFound == 0) {
            trackerInfoDescription.setVisibility(View.GONE);
            return;
        }
        trackerInfoDescription.setVisibility(View.VISIBLE);
        trackerInfoDescription.setText(foundTrackerList);
        MaterialCardView trackersView = view.findViewById(R.id.tracker);
        boolean finalHasSecondDegree = hasSecondDegree;
        trackersView.setOnClickListener(v -> {
            TrackerInfoDialog fragment = TrackerInfoDialog.getInstance(coloredSummary, trackerList, finalHasSecondDegree);
            fragment.show(getParentFragmentManager(), TrackerInfoDialog.TAG);
        });
    }

    private void setLibraryInfo(@NonNull List<SignatureInfo> libraryInfoList, @NonNull View view) {
        Map<String, SpannableStringBuilder> foundLibInfoMap = new ArrayMap<>();
        foundLibInfoMap.putAll(getNativeLibraryInfo(false));
        // Iterate over signatures again but this time list only the found ones.
        for (SignatureInfo libraryInfo : libraryInfoList) {
            if (foundLibInfoMap.get(libraryInfo.label) == null) {
                // Add the lib info since it isn't added already
                foundLibInfoMap.put(libraryInfo.label, new SpannableStringBuilder()
                        .append(getPrimaryText(mActivity, libraryInfo.label))
                        .append(getSmallerText(" (" + libraryInfo.type + ")")));
            }
            //noinspection ConstantConditions Never null here
            foundLibInfoMap.get(libraryInfo.label)
                    .append("\n")
                    .append(getMonospacedText(libraryInfo.signature))
                    .append(getSmallerText(" (" + libraryInfo.getCount() + ")"));
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

        ((TextView) view.findViewById(R.id.libs_title)).setText(summary);
        ((TextView) view.findViewById(R.id.libs_description)).setText(TextUtils.join(", ", foundLibNames));
        if (totalLibsFound == 0) return;
        MaterialCardView libsView = view.findViewById(R.id.libs);
        libsView.setOnClickListener(v -> {
            LibraryInfoDialog fragment = LibraryInfoDialog.getInstance(summary, foundLibsInfo);
            fragment.show(getParentFragmentManager(), LibraryInfoDialog.TAG);
        });

    }

    @NonNull
    private static Spannable getCertificateInfo(@NonNull Context context, @NonNull List<X509Certificate> certificates) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (X509Certificate cert : certificates) {
            try {
                if (builder.length() > 0) builder.append("\n\n");
                builder.append(getPrimaryText(context, context.getString(R.string.issuer) + LangUtils.getSeparatorString()))
                        .append(cert.getIssuerX500Principal().getName()).append("\n")
                        .append(getPrimaryText(context, context.getString(R.string.algorithm) + LangUtils.getSeparatorString()))
                        .append(cert.getSigAlgName()).append("\n");
                // Checksums
                builder.append(getPrimaryText(context, context.getString(R.string.checksums)));
                Pair<String, String>[] digests = DigestUtils.getDigests(cert.getEncoded());
                for (Pair<String, String> digest : digests) {
                    builder.append("\n")
                            .append(getPrimaryText(context, digest.first + LangUtils.getSeparatorString()))
                            .append(getMonospacedText(digest.second));
                }
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
            }
        }
        return builder;
    }
}
