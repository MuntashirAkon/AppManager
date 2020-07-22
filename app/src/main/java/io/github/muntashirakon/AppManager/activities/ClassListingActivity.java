package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
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
import com.google.classysharkandroid.dex.DexLoaderBuilder;
import com.google.classysharkandroid.reflector.Reflector;
import com.google.classysharkandroid.utils.UriUtils;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import dalvik.system.DexClassLoader;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static com.google.classysharkandroid.utils.PackageUtils.apkCert;
import static com.google.classysharkandroid.utils.PackageUtils.convertS;
import static io.github.muntashirakon.AppManager.utils.IOUtils.deleteDir;
import static io.github.muntashirakon.AppManager.utils.IOUtils.readFully;

public class ClassListingActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    private Intent intent;
    private List<String> classList;
    private List<String> classListAll;
    private ListView mListView;
    private int totalTrackersFound = 0;
    private int totalClassesScanned = 0;
    private String foundTrackerList = "";
    private String[] signatures;
    private int[] signatureCount;
    private boolean[] signaturesFound;
    private String[] tracker_names;
    private boolean trackerClassesOnly;
    private int totalIteration = 0;
    private long totalTimeTaken = 0;
    private ClassListingAdapter mClassListingAdapter;
    private String packageInfo = "";
    private CharSequence mAppName;
    private ActionBar mActionBar;
    private ProgressIndicator mProgressIndicator;
    private static String mConstraint;
    private String mPackageName;

    @Override
    protected void onDestroy() {
        deleteCache(this);
        clearApplicationData();
        super.onDestroy();
    }

    private void clearApplicationData() {
        String appCacheDirStr = getCacheDir().getParent();
        if (appCacheDirStr == null) return;
        File appCacheDir = new File(appCacheDirStr);
        if (appCacheDir.exists()) {
            String[] children = appCacheDir.list();
            if (children == null) return;
            for (String s : children) {
                if (!s.equals("lib")) {
                    deleteDir(new File(appCacheDir, s));
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_listing);
        setSupportActionBar(findViewById(R.id.toolbar));
        mActionBar = getSupportActionBar();
        intent = getIntent();
        if (mActionBar != null) {
            mActionBar.setDisplayShowCustomEnabled(true);

            SearchView searchView = new SearchView(mActionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            mActionBar.setCustomView(searchView, layoutParams);
        }

        trackerClassesOnly = false;

        mListView = findViewById(android.R.id.list);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);
        mListView.setEmptyView(findViewById(android.R.id.empty));

        mProgressIndicator = findViewById(R.id.progress_linear);

        new Thread(() -> {
            final Uri uriFromIntent = intent.getData();
            classList = new ArrayList<>();
            if (uriFromIntent != null) packageInfo = "<b>" + getString(R.string.source_dir) + ": </b>" + uriFromIntent.toString() + "\n";
            else packageInfo = "";
            tracker_names = StaticDataset.getTrackerNames();
            signatures = StaticDataset.getTrackerCodeSignatures();
            try {
                InputStream uriStream = UriUtils.getStreamFromUri(ClassListingActivity.this, uriFromIntent);
                final byte[] bytes = readFully(uriStream, -1, true);
                uriStream.close();
                new Thread(() -> {
                    try {
                        packageInfo += "\n<b>MD5sum:</b> " + convertS(MessageDigest.getInstance("md5").digest(bytes))
                                + "\n<b>SHA1sum:</b> " + convertS(MessageDigest.getInstance("sha1").digest(bytes))
                                + "\n<b>SHA256sum:</b> " + convertS(MessageDigest.getInstance("sha256").digest(bytes));
                    } catch (NoSuchAlgorithmException ignored) {}

                    final PackageManager pm = getApplicationContext().getPackageManager();
                    PackageInfo mPackageInfo = null;
                    if (intent != null && intent.getAction() != null) {
                        if (intent.getAction().equals(Intent.ACTION_VIEW)) {
                            String archiveFilePath = UriUtils.pathUriCache(getApplicationContext(),
                                    uriFromIntent, "cache.apk");
                            if (archiveFilePath != null)
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, 64);  // PackageManager.GET_SIGNATURES (Android Bug)
                        }
                    } else {
                        if (uriFromIntent != null) {
                            String archiveFilePath = uriFromIntent.getPath();
                            if (archiveFilePath != null)
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, 64);  // PackageManager.GET_SIGNATURES (Android Bug)
                        }
                    }
                    if (mPackageInfo != null) {
                        packageInfo += apkCert(mPackageInfo);
                        mPackageName = mPackageInfo.packageName;
                        mAppName = mPackageInfo.applicationInfo.loadLabel(pm);
                        runOnUiThread(() -> {
                            if (mActionBar != null) {
                                mActionBar.setTitle(mAppName);
                                mActionBar.setSubtitle(getString(R.string.tracker_classes));
                            }
                        });
                    } else packageInfo += "\n<i><b>FAILED to retrieve PackageInfo!</b></i>";
                }).start();

                new FillClassesNamesThread(bytes).start();
                new StartDexLoaderThread(bytes).start();
            } catch (Exception e) {
                e.printStackTrace();
                ActivityCompat.finishAffinity(this);
            }
        }).start();
    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
            if (Build.VERSION.SDK_INT >= 21) {
                dir = context.getCodeCacheDir();
                deleteDir(dir);
            }
        } catch (Exception e) { e.printStackTrace();}
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
            mClassListingAdapter.getFilter().filter(newText);
        return true;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_class_listing_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
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
                    statsMsg.append(tracker_names[i]).append("\n"); j++;
                }
                new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                        .setTitle(String.format(getString(R.string.trackers_and_classes), j, tracker_names.length))
                        .setNegativeButton(android.R.string.ok, null)
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
        StringBuilder foundTrackersInfo = new StringBuilder();
        if (totalTrackersFound > 0)
            foundTrackersInfo.append("\n").append(getString(R.string.tracker_details)).append(":");
        for (int i = 0, j = 0; i < signatures.length; i++) {
            if (signaturesFound[i]) {
                if (!foundTrackersInfo.toString().contains(tracker_names[i]))
                    foundTrackersInfo.append("\n<b><u>").append(++j).append(". ").append(tracker_names[i]).append("</b></u>\n");
                foundTrackersInfo.append("  ").append(signatures[i]).append(" (").append(signatureCount[i]).append(")\n");
            }
        }
        if (totalTrackersFound > 0)
            foundTrackersInfo.append("\n");

        // Display as dialog
        TextView showText = new TextView(this);
        int paddingSize = getResources().getDimensionPixelSize(R.dimen.padding_medium);
        showText.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);
        showText.setText(HtmlCompat.fromHtml(String.format(getString(R.string.tested_signatures_on_classes_and_time_taken),
                signatures.length, totalClassesScanned, totalTimeTaken, totalIteration, foundTrackerList + foundTrackersInfo + packageInfo)
                .replaceAll(" ", "&nbsp;").replaceAll("\n", "<br/>"), HtmlCompat.FROM_HTML_MODE_LEGACY));
        showText.setMovementMethod(new ScrollingMovementMethod());
        showText.setTextIsSelectable(true);
        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setTitle(String.format(getString(R.string.trackers_and_classes),
                        totalTrackersFound, classList.size()))
                .setView(showText)
                .setIcon(R.drawable.ic_frost_classysharkexodus_black_24dp)
                .setNegativeButton(android.R.string.ok, null)
                .setNeutralButton(R.string.exodus_link, (dialog, which) -> {
                    Uri exodus_link = Uri.parse(String.format("https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", mPackageName));
                    Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    }
                })
                .show();
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
                classListAll = PackageUtils.getClassNames(bytes);
                totalClassesScanned = classListAll.size();
                StringBuilder found = new StringBuilder();
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
                                else found.append("<b>").append(++totalTrackersFound)
                                        .append(". ").append(tracker_names[i]).append("</b>\n");
                                break;
                            }
                        }
                    }
                }
                t_end = System.currentTimeMillis();
                totalTimeTaken = t_end - t_start;
                if (totalTrackersFound > 0) foundTrackerList = getString(R.string.found_trackers) + "\n" + found;
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
                mProgressIndicator.hide();
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
            if(ClassListingActivity.mConstraint != null
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
                textView.setText(Utils.getHighlightedText(className, mConstraint, mColorRed));
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
}
