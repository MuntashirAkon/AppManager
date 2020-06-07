package io.github.muntashirakon.AppManager.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.classysharkandroid.dex.DexLoaderBuilder;
import com.google.classysharkandroid.reflector.ClassesNamesList;
import com.google.classysharkandroid.reflector.Reflector;
import com.google.classysharkandroid.utils.IOUtils;
import com.google.classysharkandroid.utils.UriUtils;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.R;

import static com.google.classysharkandroid.utils.PackageUtils.apkCert;
import static com.google.classysharkandroid.utils.PackageUtils.convertS;
import static io.github.muntashirakon.AppManager.utils.IOUtils.readFully;

public class ClassListingActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private Intent inIntent;
    private ClassesNamesList classesList;
    private ClassesNamesList classesListAll;
    private ListView mListView;
    private int totalTrackersFound = 0;
    private int totalClassesScanned = 0;
    private String foundTrackerList = "";
    private String[] signatures;
    private int[] signatureCount;
    private boolean[] signaturesFound;
    private String[] Names;
    private boolean trackerClassesOnly;
    private int totalTimeTaken = 0;
    private ClassListingAdapter mClassListingAdapter;
    private String packageInfo = "";
    private CharSequence mAppName;
    private ActionBar mActionBar;
    private ProgressBar mProgressBar;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_listing);
        mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
            PackageManager pm = getPackageManager();
            try {
                assert packageName != null;
                mAppName = pm.getApplicationInfo(packageName, 0).loadLabel(pm);
                mActionBar.setTitle(mAppName);
                mActionBar.setSubtitle(getString(R.string.tracker_classes));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
                finish();
            }
            mActionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);

            SearchView searchView = new SearchView(mActionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mActionBar.setCustomView(searchView, layoutParams);
        }

        inIntent = getIntent();

        trackerClassesOnly = false;

        mListView = findViewById(R.id.listView);
        mListView.setTextFilterEnabled(true);
        mListView.setDividerHeight(0);

        mProgressBar = findViewById(R.id.progress_horizontal);

        final Uri uriFromIntent = inIntent.getData();
        classesList = new ClassesNamesList();
        classesListAll  = new ClassesNamesList();

        if (inIntent.getData() != null)
            packageInfo = "<b>" + getString(R.string.apk_path) + ": </b>"
                    + inIntent.getData().toString() + "\n";
        else packageInfo = "";

        Names = getResources().getStringArray(R.array.tracker_names);
        InputStream uriStream;
        try {
            uriStream = UriUtils.getStreamFromUri(ClassListingActivity.this, uriFromIntent);
            final byte[] bytes = readFully(uriStream, -1, true);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        packageInfo += "\n<b>MD5sum:</b> " + convertS(MessageDigest.getInstance("md5").digest(bytes))
                                + "\n<b>SHA1sum:</b> " + convertS(MessageDigest.getInstance("sha1").digest(bytes))
                                + "\n<b>SHA256sum:</b> " + convertS(MessageDigest.getInstance("sha256").digest(bytes));
                    } catch (NoSuchAlgorithmException ignored) {}

                    PackageManager pm = getApplicationContext().getPackageManager();
                    PackageInfo mPackageInfo = null;
                    final int signingCertFlag;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        signingCertFlag = PackageManager.GET_SIGNING_CERTIFICATES;
                    } else {
                        signingCertFlag = PackageManager.GET_SIGNATURES;
                    }
                    if (inIntent != null && inIntent.getAction() != null) {
                        if (inIntent.getAction().equals(Intent.ACTION_VIEW)) {
                            String archiveFilePath = UriUtils.pathUriCache(getApplicationContext(),
                                    uriFromIntent, "cache.apk");
                            if (archiveFilePath != null) {
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_SIGNATURES);
                            }
                        }
                    } else {
                        if (uriFromIntent != null) {
                            String archiveFilePath = uriFromIntent.getPath();
                            if (archiveFilePath != null) {
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_SIGNATURES);
                            }
                        }
                    }

                    if (mPackageInfo != null) packageInfo += apkCert(mPackageInfo);
                    else packageInfo += "\n<i><b>FAILED to retrieve PackageInfo!</b></i>";
                }
            }).start();

            new FillClassesNamesThread(bytes).start();
            new StartDexLoaderThread(bytes).start();
        } catch (Exception e) {
            e.printStackTrace();
            ActivityCompat.finishAffinity(this);
        }
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

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null) return false;
            for (String child : children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if(dir!= null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mClassListingAdapter != null)
            mClassListingAdapter.getFilter().filter(newText);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_class_listing_actions, menu);
        return true;
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
                StringBuilder statsMsg = new StringBuilder(Names[0] + "\n");
                int i, j;
                j = 1;
                for (i = 1; i < Names.length; i++) {
                    if (Names[i - 1].equals(Names[i])) continue;
                    statsMsg.append(Names[i]).append("\n"); j++;
                }
                new AlertDialog.Builder(this, R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                        .setTitle(String.format(getString(R.string.trackers_and_classes),
                                j, Names.length))
                        .setNegativeButton(android.R.string.ok, null)
                        .setMessage(statsMsg.toString()).show();
                return true;
            case R.id.action_toggle_class_listing:
                mClassListingAdapter.setDefaultList((trackerClassesOnly ? classesList : classesListAll).getClassNames());
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
                if (!foundTrackersInfo.toString().contains(Names[i]))
                    foundTrackersInfo.append("\n<b><u>").append(++j).append(". ").append(Names[i]).append("</b></u>\n");
                foundTrackersInfo.append("  ").append(signatures[i]).append(" (").append(signatureCount[i]).append(")\n");
            }
        }
        if (totalTrackersFound > 0)
            foundTrackersInfo.append("\n");

        // Display as dialog
        TextView showText = new TextView(this);
        int paddingSize = getResources().getDimensionPixelSize(R.dimen.padding_medium);
        showText.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);
        showText.setText(Html.fromHtml(String.format(getString(R.string.tested_signatures_on_classes_and_time_taken),
                signatures.length, totalClassesScanned, totalTimeTaken, foundTrackerList + foundTrackersInfo + packageInfo)
                .replaceAll(" ", "&nbsp;").replaceAll("\n", "<br/>")));
        showText.setMovementMethod(new ScrollingMovementMethod());
        showText.setTextIsSelectable(true);
        new AlertDialog.Builder(this, R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle(String.format(getString(R.string.trackers_and_classes),
                        totalTrackersFound, classesList.size()))
                .setView(showText)
                .setIcon(R.drawable.ic_frost_classysharkexodus_black_24dp)
                .setNegativeButton(android.R.string.ok, null)
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
                long threadId = Thread.currentThread().getId();
                File incomeFile = File.createTempFile("classes" + threadId, ".dex", getCacheDir());
                IOUtils.bytesToFile(bytes, incomeFile);
                File optimizedFile = File.createTempFile("opt" + threadId, ".dex", getCacheDir());
                DexFile dx = DexFile.loadDex(incomeFile.getPath(), optimizedFile.getPath(), 0);

                StringBuilder found = new StringBuilder();
                signatures = getResources().getStringArray(R.array.tracker_signatures);
                signatureCount = new int[signatures.length];
                signaturesFound = new boolean[signatures.length];
                for (Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements(); ) {
                    String className = classNames.nextElement();
                    classesListAll.add(className);
                    totalClassesScanned++;
                    if (className.length() > 8) {
                        if (className.contains(".")) {
                            for (int i = 0; i < signatures.length; i++) {
                                totalTimeTaken++; // Total enumerations
                                if (className.contains(signatures[i])) {
                                    classesList.add(className);
                                    signatureCount[i]++;
                                    signaturesFound[i] = true;
                                    if (found.toString().contains(Names[i])) break;
                                    else found.append("<b>").append(++totalTrackersFound)
                                            .append(". ").append(Names[i]).append("</b>\n");
                                    break;
                                }
                            }
                        }
                    }
                }

                if (totalTrackersFound > 0)
                    foundTrackerList = getString(R.string.found_trackers) + "\n" + found;

                ClassListingActivity.this.deleteFile("*");
                //noinspection ResultOfMethodCallIgnored
                incomeFile.delete();
                //noinspection ResultOfMethodCallIgnored
                optimizedFile.delete();
            } catch (Exception e) {
                // ODEX, need to see how to handle
                e.printStackTrace();
            }

            ClassListingActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mClassListingAdapter = new ClassListingAdapter(ClassListingActivity.this);
                    if (!trackerClassesOnly) {
                        mClassListingAdapter.setDefaultList(classesList.getClassNames());
                        mActionBar.setSubtitle(getString(R.string.tracker_classes));
                    } else {
                        mClassListingAdapter.setDefaultList(classesListAll.getClassNames());
                        mActionBar.setSubtitle(getString(R.string.all_classes));
                    }
//                    mListView.setAdapter(mAdapter);
                    mListView.setAdapter(mClassListingAdapter);
                    mProgressBar.setVisibility(View.GONE);
                    if (classesList.getClassNames().isEmpty() && totalClassesScanned == 0) {
                        Toast.makeText(ClassListingActivity.this,
                                "Sorry don't support /system ODEX", Toast.LENGTH_LONG).show();
                    } else {
                        viewScanSummary();
                    }
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

                ClassListingActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view,
                                                    int position, long id) {
                                Class<?> loadClass;
                                try {
                                    loadClass = loader.loadClass((!trackerClassesOnly ? classesList
                                            : classesListAll).getClassName((int) (parent
                                            .getAdapter()).getItemId(position)));

                                    Reflector reflector = new Reflector(loadClass);

                                    Toast.makeText(ClassListingActivity.this,
                                            reflector.generateClassData(), Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(ClassListingActivity.this,
                                            ClassViewerActivity.class);
                                    intent.putExtra(ClassViewerActivity.EXTRA_CLASS_NAME,
                                            (!trackerClassesOnly ? classesList : classesListAll)
                                                    .getClassName((int) (parent.getAdapter())
                                                            .getItemId(position)));
                                    intent.putExtra(ClassViewerActivity.EXTRA_CLASS_DUMP, reflector.toString());
                                    intent.putExtra(ClassViewerActivity.EXTRA_APP_NAME, mAppName);
                                    startActivity(intent);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(ClassListingActivity.this, e.toString(),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class ClassListingAdapter extends BaseAdapter implements Filterable {
        static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

        private LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        private List<String> mDefaultList;
        private List mAdapterList;

        private int mColorTransparent;
        private int mColorSemiTransparent;

        ClassListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = activity.getResources().getColor(R.color.SEMI_TRANSPARENT);
        }

        void setDefaultList(List<String> list) {
            mDefaultList = list;
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public Object getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            String className = (String) mAdapterList.get(position);
            TextView textView = (TextView) convertView;
            if (mConstraint != null && className.toLowerCase().contains(mConstraint)) {
                // Highlight searched query
                textView.setText(getHighlightedText(className));
            } else {
                textView.setText(className);
            }
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        Spannable getHighlightedText(String s) {
            Spannable spannable = sSpannableFactory.newSpannable(s);
            int start = s.toLowerCase().indexOf(mConstraint);
            int end = start + mConstraint.length();
            spannable.setSpan(new BackgroundColorSpan(0xFFB7B7B7), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase();
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.size());
                        for (String item : mDefaultList) {
                            if (item.toLowerCase().contains(constraint))
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
                            mAdapterList = (List) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}
