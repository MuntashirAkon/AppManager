package io.github.muntashirakon.AppManager.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.classysharkandroid.adapters.StableArrayAdapter;
import com.google.classysharkandroid.dex.DexLoaderBuilder;
import com.google.classysharkandroid.reflector.ClassesNamesList;
import com.google.classysharkandroid.reflector.Reflector;
import com.google.classysharkandroid.utils.IOUtils;
import com.google.classysharkandroid.utils.UriUtils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;

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
    private ProgressDialog mProgressDialog;
    private int totalTrackersFound = 0;
    private int totalClassesScanned = 0;
    private String foundTrackerList = "";
    private String[] signatures;
    private int[] signatureCount;
    private boolean[] signaturesFound;
    private String[] Names;
    private boolean trackerClassesOnly;
    private int totalTimeTaken = 0;
    private StableArrayAdapter adapter;
    private String packageInfo = "";
    private CharSequence appName;
    private ActionBar actionBar;

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
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
            PackageManager pm = getPackageManager();
            try {
                assert packageName != null;
                appName = pm.getApplicationInfo(packageName, 0).loadLabel(pm);
                actionBar.setTitle(appName);
                actionBar.setSubtitle(getString(R.string.tracker_classes));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
                finish();
            }
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);

            SearchView searchView = new SearchView(actionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionBar.setCustomView(searchView, layoutParams);
        }

        inIntent = getIntent();

        trackerClassesOnly = false;

        mListView = findViewById(R.id.listView);
        mListView.setTextFilterEnabled(true);

        final Uri uriFromIntent = inIntent.getData();
        classesList = new ClassesNamesList();
        classesListAll  = new ClassesNamesList();

        if (inIntent.getData() != null)
            packageInfo = "<b>" + getString(R.string.apk_path) + ": </b>"
                    + inIntent.getData().toString() + "\n";
        else packageInfo = "";

        mProgressDialog = new ProgressDialog(ClassListingActivity.this);
        mProgressDialog.setIcon(R.drawable.ic_frost_classysharkexodus_black_24dp);
        mProgressDialog.setTitle("¸.·´¯`·.´¯`·.¸¸.·´¯`·.¸><(((º>");
        mProgressDialog.setMessage(Html.fromHtml(packageInfo));
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.show();

        Names = getResources().getStringArray(R.array.tracker_names);
        InputStream uriStream;
        try {
            uriStream = UriUtils.getStreamFromUri(ClassListingActivity.this, uriFromIntent);
            long s, e;
            s = System.currentTimeMillis();
            final byte[] bytes = readFully(uriStream, -1, true);
            e = System.currentTimeMillis();
            Log.d("Speed test", "Time: " + (e-s));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        packageInfo += "\n<b>MD5sum:</b> " + convertS(MessageDigest.getInstance("md5").digest(bytes))
                                + "\n<b>SHA1sum:</b> " + convertS(MessageDigest.getInstance("sha1").digest(bytes))
                                + "\n<b>SHA256sum:</b> " + convertS(MessageDigest.getInstance("sha256").digest(bytes));
                    } catch (NoSuchAlgorithmException ignored) {}

                    runOnUiThread(changeText);

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
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, signingCertFlag);
                            }
                        }
                    }else {
                        if (uriFromIntent != null) {
                            String archiveFilePath = uriFromIntent.getPath();
                            if (archiveFilePath != null) {
                                mPackageInfo = pm.getPackageArchiveInfo(archiveFilePath, signingCertFlag);
                            }
                        }
                    }

                    runOnUiThread(changeText);

                    if (mPackageInfo != null) packageInfo += apkCert(mPackageInfo);
                    else packageInfo += "\n<i><b>FAILED to retrieve PackageInfo!</b></i>";
                }
            }).start();

            mProgressDialog.setMessage(Html.fromHtml(packageInfo));
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.show();

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
        adapter.getFilter().filter(newText);
        return true;
    }

    private Runnable changeText = new Runnable() {
        @Override
        public void run() {
            mProgressDialog.setMessage(packageInfo);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_class_listing_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
                new AlertDialog.Builder(this)
                        .setTitle(String.format(getString(R.string.trackers_and_classes),
                                j, Names.length))
                        .setNegativeButton(android.R.string.ok, null)
                        .setMessage(statsMsg.toString()).show();
                return true;
            case R.id.action_toggle_class_listing:
                adapter = new StableArrayAdapter(ClassListingActivity.this,
                        android.R.layout.simple_list_item_1, (trackerClassesOnly ? classesList : classesListAll).getClassNames());
                actionBar.setSubtitle(getString((trackerClassesOnly ? R.string.tracker_classes : R.string.all_classes)));
                trackerClassesOnly = !trackerClassesOnly;
                mListView.setAdapter(adapter);
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
        new AlertDialog.Builder(this)
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
                File incomeFile = File.createTempFile("classes" + Thread.currentThread().getId(), ".dex", getCacheDir());
                IOUtils.bytesToFile(bytes, incomeFile);
                File optimizedFile = File.createTempFile("opt" + Thread.currentThread().getId(), ".dex", getCacheDir());
                DexFile dx = DexFile.loadDex(incomeFile.getPath(),
                        optimizedFile.getPath(), 0);

                StringBuilder found = new StringBuilder();
                signatures = getResources().getStringArray(R.array.tracker_signatures);
                signatureCount = new int[signatures.length];
                signaturesFound = new boolean[signatures.length];
                for (Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements(); ) {
                    String className = classNames.nextElement();
                    classesListAll.add(className);
                    totalClassesScanned++;
                    if (className.length()>8) {
                        if (className.contains(".")){
                            for (int i = 0; i < signatures.length; i++) {
                                totalTimeTaken++;// TESTING only
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
                incomeFile.delete();
                optimizedFile.delete();
            } catch (Exception e) {
                // ODEX, need to see how to handle
                e.printStackTrace();
            }

            ClassListingActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!trackerClassesOnly) {
                        adapter = new StableArrayAdapter(ClassListingActivity.this,
                                android.R.layout.simple_list_item_1, classesList.getClassNames());
                        actionBar.setSubtitle(getString(R.string.tracker_classes));
                    } else {
                        adapter = new StableArrayAdapter(ClassListingActivity.this,
                                android.R.layout.simple_list_item_1, classesListAll.getClassNames());
                        actionBar.setSubtitle(getString(R.string.all_classes));
                    }
                    mListView.setAdapter(adapter);
                    mProgressDialog.dismiss();
                    if(classesList.getClassNames().isEmpty() && totalClassesScanned ==0) {
                        Toast.makeText(ClassListingActivity.this,
                                "Sorry don't support /system ODEX", Toast.LENGTH_LONG).show();
                    }else {
                        if(trackerClassesOnly) {
                            Toast.makeText(ClassListingActivity.this, "LONG_click --> All Classes", Toast.LENGTH_LONG).show();
                        } viewScanSummary();
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
                                    loadClass = loader.loadClass((!trackerClassesOnly ? classesList : classesListAll).getClassName(
                                            (int) (parent.getAdapter()).getItemId(position)));

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
                                    intent.putExtra(ClassViewerActivity.EXTRA_APP_NAME, appName);
                                    startActivity(intent);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(ClassListingActivity.this, e.toString(), Toast.LENGTH_LONG).show();
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
}
