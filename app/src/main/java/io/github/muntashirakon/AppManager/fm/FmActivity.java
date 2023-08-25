// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.os.ParcelCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.Paths;

public class FmActivity extends BaseActivity {
    public static class Options implements Parcelable {
        @NonNull
        public final Uri uri;
        public final boolean isVfs;
        public final boolean readOnly;
        public final boolean mountDexFiles;

        public Options(@NonNull Uri uri, boolean isVfs, boolean readOnly, boolean mountDexFiles) {
            this.uri = uri;
            this.isVfs = isVfs;
            this.readOnly = readOnly;
            this.mountDexFiles = mountDexFiles;
        }

        protected Options(Parcel in) {
            uri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
            isVfs = ParcelCompat.readBoolean(in);
            readOnly = ParcelCompat.readBoolean(in);
            mountDexFiles = ParcelCompat.readBoolean(in);
        }

        public static final Creator<Options> CREATOR = new Creator<Options>() {
            @Override
            public Options createFromParcel(Parcel in) {
                return new Options(in);
            }

            @Override
            public Options[] newArray(int size) {
                return new Options[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(uri, flags);
            ParcelCompat.writeBoolean(dest, isVfs);
            ParcelCompat.writeBoolean(dest, readOnly);
            ParcelCompat.writeBoolean(dest, mountDexFiles);
        }
    }

    public static final String LAUNCHER_ALIAS = "io.github.muntashirakon.AppManager.fm.FilesActivity";

    public static final String EXTRA_OPTIONS = "opt";

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        Uri uri = getIntent().getData();
        if (uri != null && uri.getScheme() == null) {
            // file:// URI can have no schema. So, fix it by adding file://
            if (uri.getPath() != null && uri.getAuthority() == null) {
                uri = uri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build();
            } else {
                // Avoid loading invalid paths
                uri = null;
            }
        }
        uri = FmUtils.sanitizeContentInput(uri);
        if (savedInstanceState == null) {
            Options options = getIntent().getExtras() != null ? BundleCompat.getParcelable(getIntent().getExtras(), EXTRA_OPTIONS, Options.class) : null;
            Integer position = null;
            if (options == null) {
                if (uri != null) {
                    options = new Options(uri, false, false, false);
                } else if (Prefs.FileManager.isRememberLastOpenedPath()) {
                    Pair<FmActivity.Options, Pair<Uri, Integer>> optionsUriPostionPair = Prefs.FileManager.getLastOpenedPath();
                    if (optionsUriPostionPair != null) {
                        options = optionsUriPostionPair.first;
                        if (options.isVfs) {
                            uri = optionsUriPostionPair.second.first;
                        }
                        position = optionsUriPostionPair.second.second;
                    }
                }
                if (options == null) {
                    // Use home
                    options = new Options(Prefs.FileManager.getHome(), false, false, false);
                }
            }
            Uri uncheckedUri = options.uri;
            Uri checkedUri = ExUtils.exceptionAsNull(() -> Paths.getStrict(uncheckedUri).exists() ? uncheckedUri : null);
            if (checkedUri == null) {
                // Use default directory
                options = new Options(Uri.fromFile(Environment.getExternalStorageDirectory()), false, false, false);
            }
            Fragment fragment = FmFragment.getNewInstance(options, options.isVfs ? uri : null, position);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_layout, fragment, FmFragment.TAG)
                    .commit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        Options options = intent.getExtras() != null ? BundleCompat.getParcelable(intent.getExtras(), EXTRA_OPTIONS, Options.class) : null;
        if (options != null) {
            Intent intent2 = new Intent(this, FmActivity.class);
            if (uri != null) {
                intent2.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
            }
            intent2.putExtra(EXTRA_OPTIONS, options);
            intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent2);
            return;
        }
        if (uri != null) {
            Intent intent2 = new Intent(this, FmActivity.class);
            intent2.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
            intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent2);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
