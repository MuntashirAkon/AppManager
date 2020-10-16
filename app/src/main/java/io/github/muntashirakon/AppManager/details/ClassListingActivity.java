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

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.google.classysharkandroid.dex.DexLoaderBuilder;
import com.google.classysharkandroid.reflector.Reflector;

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
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import dalvik.system.DexClassLoader;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBiggerText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getUnderlinedString;

public class ClassListingActivity extends BaseActivity implements SearchView.OnQueryTextListener {
    private static final String APP_DEX = "app_dex";

    private List<String> classList;
    private List<String> classListAll;
    private ListView mListView;
    private TextView mEmptyView;
    private int totalTrackersFound = 0;
    private int totalClassesScanned = 0;
    private SpannableStringBuilder foundTrackerList = new SpannableStringBuilder();
    private String[] signatures;
    private int[] signatureCount;
    private boolean[] signaturesFound;
    private String[] tracker_names;
    private boolean trackerClassesOnly;
    private int totalIteration = 0;
    private long totalTimeTaken = 0;
    private ClassListingAdapter mClassListingAdapter;
    private SpannableStringBuilder packageInfo = new SpannableStringBuilder();
    private CharSequence mAppName;
    private ActionBar mActionBar;
    private ProgressIndicator mProgressIndicator;
    private static String mConstraint;
    private String mPackageName;
    private ParcelFileDescriptor fd;
    private File apkFile;

    @Override
    protected void onDestroy() {
        IOUtils.deleteDir(new File(getCacheDir().getParent(), APP_DEX));
        IOUtils.deleteDir(getCodeCacheDir());
        IOUtils.closeQuietly(fd);
        if (!apkFile.getAbsolutePath().startsWith("/data/app/")) {
            // Only attempt to delete the apk file if it's cached
            IOUtils.deleteSilently(apkFile);
        }
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_listing);
        setSupportActionBar(findViewById(R.id.toolbar));
        mActionBar = getSupportActionBar();
        Intent intent = getIntent();
        if (mActionBar != null) {
            mActionBar.setDisplayShowCustomEnabled(true);

            SearchView searchView = new SearchView(mActionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(UIUtils.getAccentColor(this));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(UIUtils.getAccentColor(this));

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            mActionBar.setCustomView(searchView, layoutParams);
        }

        trackerClassesOnly = false;

        mListView = findViewById(android.R.id.list);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);
        mEmptyView = findViewById(android.R.id.empty);
        mListView.setEmptyView(mEmptyView);

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

        new Thread(() -> {
            classList = new ArrayList<>();
            packageInfo.append(getBiggerText(getBoldString("\u27a4 " + getString(R.string.source_dir))))
                    .append("\n").append(apkUri.toString()).append("\n");
            tracker_names = StaticDataset.getTrackerNames();
            signatures = StaticDataset.getTrackerCodeSignatures();
            try {
                final byte[] bytes;
                try (InputStream uriStream = getContentResolver().openInputStream(apkUri)) {
                    bytes = IOUtils.readFully(uriStream, -1, true);
                }
                new FillClassesNamesThread(bytes).start();
                new StartDexLoaderThread(bytes).start();
            } catch (Exception e) {
                e.printStackTrace();
                finishAffinity();
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mClassListingAdapter != null && mConstraint != null && !mConstraint.equals("")) {
            mClassListingAdapter.getFilter().filter(mConstraint);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mConstraint = newText;
        if (mClassListingAdapter != null)
            mClassListingAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_class_listing_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_view_summary:
                viewScanSummary();
                return true;
            case R.id.action_view_trackers:
                StringBuilder statsMsg = new StringBuilder(tracker_names[0] + "\n");
                int i, j;
                j = 1;
                for (i = 1; i < tracker_names.length; i++) {
                    if (tracker_names[i - 1].equals(tracker_names[i])) continue;
                    statsMsg.append(tracker_names[i]).append("\n");
                    j++;
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.trackers_and_classes, j, tracker_names.length))
                        .setNegativeButton(R.string.ok, null)
                        .setMessage(statsMsg.toString()).show();
                return true;
            case R.id.action_toggle_class_listing:
                mClassListingAdapter.setDefaultList(trackerClassesOnly ? classList : classListAll);
                mActionBar.setSubtitle(getString((trackerClassesOnly ? R.string.tracker_classes : R.string.all_classes)));
                trackerClassesOnly = !trackerClassesOnly;
                mListView.setAdapter(mClassListingAdapter);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void viewScanSummary() {
        if (mProgressIndicator.isShown()) {
            Toast.makeText(this, R.string.scanning_is_still_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        SpannableStringBuilder foundTrackersInfo = new SpannableStringBuilder();
        if (totalTrackersFound > 0)
            foundTrackersInfo.append(getBiggerText(getBoldString("\u27a4 " + getString(R.string.tracker_details)))).append("\n");
        for (int i = 0, j = 0; i < signatures.length; i++) {
            if (signaturesFound[i]) {
                if (!foundTrackersInfo.toString().contains(tracker_names[i])) {
                    foundTrackersInfo.append(getUnderlinedString(getBoldString((++j) + ". " + tracker_names[i]))).append("\n");
                }
                foundTrackersInfo.append("  ").append(signatures[i]).append(" (").append(String.valueOf(signatureCount[i])).append(")\n");
            }
        }
        // Display as dialog
        View view = getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
        MaterialTextView summaryTV = view.findViewById(R.id.content);
        SpannableStringBuilder builder = new SpannableStringBuilder()
                .append(getString(R.string.tested_signatures_on_classes_and_time_taken,
                        signatures.length, totalClassesScanned, totalTimeTaken, totalIteration))
                .append("\n")
                .append(foundTrackerList)
                .append(foundTrackersInfo)
                .append(packageInfo);
        summaryTV.setText(builder);
        summaryTV.setTextIsSelectable(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.trackers_and_classes, totalTrackersFound, classList.size()))
                .setView(view)
                .setIcon(R.drawable.ic_frost_classysharkexodus_black_24dp)
                .setNegativeButton(R.string.ok, null)
                .setNeutralButton(R.string.exodus_link, (dialog, which) -> {
                    Uri exodus_link = Uri.parse(String.format("https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", mPackageName));
                    Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void showProgress(boolean willShow) {
        if (willShow) {
            mProgressIndicator.show();
            mEmptyView.setText(R.string.loading);
        } else {
            mProgressIndicator.hide();
            mEmptyView.setText(R.string.no_tracker_class);
        }
    }

    private class FillClassesNamesThread extends Thread {
        private final byte[] bytes;

        FillClassesNamesThread(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                packageInfo.append(getBoldString(getString(R.string.checksums))).append("\n")
                        .append(getBoldString(DigestUtils.MD5 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.MD5, bytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_1 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_1, bytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_256 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_256, bytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_384 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_384, bytes)).append("\n")
                        .append(getBoldString(DigestUtils.SHA_512 + ": "))
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_512, bytes)).append("\n");
                // Test if this path is readable
                if (!apkFile.exists() || !apkFile.canRead()) {
                    try {
                        apkFile = IOUtils.getCachedFile(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                final PackageManager pm = getApplicationContext().getPackageManager();
                final String archiveFilePath = apkFile.getAbsolutePath();
                @SuppressLint("WrongConstant")
                PackageInfo packageInfo = pm.getPackageArchiveInfo(archiveFilePath, 64);  // PackageManager.GET_SIGNATURES (Android Bug)
                if (packageInfo != null) {
                    ClassListingActivity.this.packageInfo.append(getCertificateInfo(packageInfo));
                    mPackageName = packageInfo.packageName;
                    final ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    applicationInfo.publicSourceDir = archiveFilePath;
                    applicationInfo.sourceDir = archiveFilePath;
                    mAppName = applicationInfo.loadLabel(pm);
                    runOnUiThread(() -> {
                        if (mActionBar != null) {
                            mActionBar.setTitle(mAppName);
                            mActionBar.setSubtitle(getString(R.string.tracker_classes));
                        }
                    });
                } else {
                    ClassListingActivity.this.packageInfo.append("\n").append(UIUtils.getBoldString(getString(R.string.failed_to_fetch_package_info)));
                }
                classListAll = PackageUtils.getClassNames(apkFile);
                totalClassesScanned = classListAll.size();
                SpannableStringBuilder found = new SpannableStringBuilder();
                signatureCount = new int[signatures.length];
                signaturesFound = new boolean[signatures.length];
                long t_start, t_end;
                t_start = System.currentTimeMillis();
                for (String className : classListAll) {
                    if (className.length() > 8 && className.contains(".")) {
                        for (int i = 0; i < signatures.length; i++) {
                            totalIteration++;
                            if (className.contains(signatures[i])) {
                                classList.add(className);
                                signatureCount[i]++;
                                signaturesFound[i] = true;
                                if (found.toString().contains(tracker_names[i])) break;
                                else {
                                    found.append(getBoldString(++totalTrackersFound + ". " + tracker_names[i])).append("\n");
                                }
                                break;
                            }
                        }
                    }
                }
                t_end = System.currentTimeMillis();
                totalTimeTaken = t_end - t_start;
                if (totalTrackersFound > 0)
                    foundTrackerList.append(getString(R.string.found_trackers)).append("\n").append(found);
            } catch (Exception e) {
                // ODEX, need to see how to handle
                e.printStackTrace();
            }

            ClassListingActivity.this.runOnUiThread(() -> {
                mClassListingAdapter = new ClassListingAdapter(ClassListingActivity.this);
                if (!trackerClassesOnly) {
                    mClassListingAdapter.setDefaultList(classList);
                    mActionBar.setSubtitle(getString(R.string.tracker_classes));
                } else {
                    mClassListingAdapter.setDefaultList(classListAll);
                    mActionBar.setSubtitle(getString(R.string.all_classes));
                }
                mListView.setAdapter(mClassListingAdapter);
                showProgress(false);
                if (classList.isEmpty() && totalClassesScanned == 0) {
                    // FIXME: Add support for odex (using root)
                    Toast.makeText(ClassListingActivity.this, R.string.system_odex_not_supported, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    viewScanSummary();
                }
            });
        }
    }

    private class StartDexLoaderThread extends Thread {
        private final byte[] bytes;

        StartDexLoaderThread(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

                final DexClassLoader loader = DexLoaderBuilder.fromBytes(ClassListingActivity.this, bytes);

                ClassListingActivity.this.runOnUiThread(
                        () -> mListView.setOnItemClickListener((parent, view, position, id) -> {
                            Class<?> loadClass;
                            try {
                                loadClass = loader.loadClass((!trackerClassesOnly ? classList
                                        : classListAll).get((int) (parent.getAdapter())
                                        .getItemId(position)));

                                Reflector reflector = new Reflector(loadClass);

                                Toast.makeText(ClassListingActivity.this,
                                        reflector.generateClassData(), Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(ClassListingActivity.this,
                                        ClassViewerActivity.class);
                                intent.putExtra(ClassViewerActivity.EXTRA_CLASS_NAME,
                                        (!trackerClassesOnly ? classList : classListAll).get((int)
                                                (parent.getAdapter()).getItemId(position)));
                                intent.putExtra(ClassViewerActivity.EXTRA_CLASS_DUMP, reflector.toString());
                                intent.putExtra(ClassViewerActivity.EXTRA_APP_NAME, mAppName);
                                startActivity(intent);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(ClassListingActivity.this, e.toString(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class ClassListingAdapter extends BaseAdapter implements Filterable {
        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private List<String> mDefaultList;
        private List<String> mAdapterList;

        private int mColorTransparent;
        private int mColorSemiTransparent;
        private int mColorRed;

        ClassListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(List<String> list) {
            mDefaultList = list;
            mAdapterList = list;
            if (ClassListingActivity.mConstraint != null
                    && !ClassListingActivity.mConstraint.equals("")) {
                getFilter().filter(ClassListingActivity.mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public String getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            String className = mAdapterList.get(position);
            TextView textView = (TextView) convertView;
            if (mConstraint != null && className.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                textView.setText(UIUtils.getHighlightedText(className, mConstraint, mColorRed));
            } else {
                textView.setText(className);
            }
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.size());
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<String>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
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
                builder.append(getBiggerText(getBoldString("\u27a4 " + getString(R.string.signature))))
                        .append("\n")
                        .append(getBoldString(getString(R.string.issuer) + ": "))
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
                        .append(DigestUtils.getHexDigest(DigestUtils.SHA_512, certBytes)).append("\n");
            }
        } catch (CertificateException ignored) {
        }
        return builder;
    }
}
