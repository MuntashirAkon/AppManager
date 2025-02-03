// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.collection.ArrayMap;
import androidx.core.os.BundleCompat;
import androidx.core.os.ParcelCompat;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFileUtils;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;

public class FmActivity extends BaseActivity {
    public static class Options implements Parcelable, IJsonSerializer {
        @NonNull
        public final Uri uri;
        public final boolean isVfs;
        public final boolean readOnly;
        public final boolean mountDexFiles;

        @Nullable
        private Uri mInitUriForVfs;

        public Options(@NonNull Uri uri, boolean isVfs, boolean readOnly, boolean mountDexFiles) {
            this.uri = uri;
            this.isVfs = isVfs;
            this.readOnly = readOnly;
            this.mountDexFiles = mountDexFiles;
        }

        public void setInitUriForVfs(@Nullable Uri initUriForVfs) {
            if (!isVfs && initUriForVfs != null) {
                throw new IllegalArgumentException("initUri can only be set when the file system is virtual.");
            }
            this.mInitUriForVfs = initUriForVfs;
        }

        @Nullable
        public Uri getInitUriForVfs() {
            return mInitUriForVfs;
        }

        protected Options(@NonNull JSONObject jsonObject) throws JSONException {
            uri = Uri.parse(jsonObject.getString("uri"));
            isVfs = jsonObject.getBoolean("is_vfs");
            readOnly = jsonObject.getBoolean("read_only");
            mountDexFiles = jsonObject.getBoolean("mount_dex");
            String initUri = JSONUtils.optString(jsonObject, "init_uri", null);
            if (initUri != null) {
                mInitUriForVfs = Uri.parse(initUri);
            }
        }

        protected Options(Parcel in) {
            uri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
            isVfs = ParcelCompat.readBoolean(in);
            readOnly = ParcelCompat.readBoolean(in);
            mountDexFiles = ParcelCompat.readBoolean(in);
            mInitUriForVfs = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        }

        public static final JsonDeserializer.Creator<Options> DESERIALIZER = new JsonDeserializer.Creator<Options>() {
            @NonNull
            @Override
            public Options deserialize(@NonNull JSONObject jsonObject) throws JSONException {
                return new Options(jsonObject);
            }
        };

        public static final Creator<Options> CREATOR = new Creator<Options>() {
            @NonNull
            @Override
            public Options createFromParcel(@NonNull Parcel in) {
                return new Options(in);
            }

            @NonNull
            @Override
            public Options[] newArray(int size) {
                return new Options[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() throws JSONException {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("uri", uri.toString());
            jsonObject.put("is_vfs", isVfs);
            jsonObject.put("read_only", readOnly);
            jsonObject.put("mount_dex", mountDexFiles);
            jsonObject.put("init_uri", mInitUriForVfs != null ? mInitUriForVfs.toString() : null);
            return jsonObject;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(uri, flags);
            ParcelCompat.writeBoolean(dest, isVfs);
            ParcelCompat.writeBoolean(dest, readOnly);
            ParcelCompat.writeBoolean(dest, mountDexFiles);
            dest.writeParcelable(mInitUriForVfs, flags);
        }
    }

    public static final String LAUNCHER_ALIAS = "io.github.muntashirakon.AppManager.fm.FilesActivity";

    public static final String EXTRA_OPTIONS = "opt";

    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerRecyclerView;
    private DrawerRecyclerViewAdapter mDrawerAdapter;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        FmDrawerViewModel viewModel = new ViewModelProvider(this).get(FmDrawerViewModel.class);
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerRecyclerView = findViewById(R.id.recycler_view);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerAdapter = new DrawerRecyclerViewAdapter(this);
        mDrawerRecyclerView.setAdapter(mDrawerAdapter);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }
        viewModel.getDrawerItemsLiveData().observe(this, drawerItems ->
                mDrawerAdapter.setAdapterItems(drawerItems));
        viewModel.loadDrawerItems();
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
            if (options.isVfs) {
                options.setInitUriForVfs(uri);
            }
            loadFragment(options, position);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
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
            mDrawerLayout.open();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFragment(@NonNull Options options, @Nullable Integer position) {
        Fragment fragment = FmFragment.getNewInstance(options, position);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, fragment, FmFragment.TAG)
                .commit();
    }

    public static class FmDrawerViewModel extends AndroidViewModel {
        private final MutableLiveData<List<FmDrawerItem>> mDrawerItemsLiveData = new MutableLiveData<>();

        public FmDrawerViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<List<FmDrawerItem>> getDrawerItemsLiveData() {
            return mDrawerItemsLiveData;
        }

        public void loadDrawerItems() {
            ThreadUtils.postOnBackgroundThread(() -> {
                List<FmDrawerItem> drawerItems = new ArrayList<>();
                Context context = getApplication();
                ArrayMap<String, Uri> storageLocations = StorageUtils.getAllStorageLocations(getApplication());
                drawerItems.add(new FmDrawerItem(-1, context.getString(R.string.storage), null, FmDrawerItem.ITEM_TYPE_LABEL));
                for (int i = 0; i < storageLocations.size(); ++i) {
                    Uri uri = storageLocations.valueAt(i);
                    Options options = new Options(uri, false, false, false);
                    PackageManager pm = getApplication().getPackageManager();
                    ResolveInfo resolveInfo = DocumentFileUtils.getUriSource(getApplication(), uri);
                    String name = resolveInfo != null ? resolveInfo.loadLabel(pm).toString() : storageLocations.keyAt(i);
                    Drawable icon = resolveInfo != null ? resolveInfo.loadIcon(pm) : null;
                    FmDrawerItem drawerItem = new FmDrawerItem(-1, name, options, FmDrawerItem.ITEM_TYPE_LOCATION);
                    drawerItem.iconRes = R.drawable.ic_content_save;
                    drawerItem.icon = icon;
                    drawerItems.add(drawerItem);
                }
                mDrawerItemsLiveData.postValue(drawerItems);
            });
        }
    }

    public static class DrawerRecyclerViewAdapter extends RecyclerView.Adapter<DrawerRecyclerViewAdapter.ViewHolder> {
        private List<FmDrawerItem> mAdapterItems = Collections.emptyList();
        private final FmActivity mFmActivity;

        public DrawerRecyclerViewAdapter(@NonNull FmActivity activity) {
            mFmActivity = activity;
        }

        public void setAdapterItems(@NonNull List<FmDrawerItem> adapterItems) {
            int previousCount = mAdapterItems.size();
            mAdapterItems = adapterItems;
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterItems.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @FmDrawerItem.DrawerItemType int viewType) {
            int layoutId;
            if (viewType == FmDrawerItem.ITEM_TYPE_LABEL) {
                layoutId = R.layout.item_title_action;
            } else layoutId = R.layout.item_fm_drawer;
            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FmDrawerItem item = mAdapterItems.get(position);
            holder.labelView.setText(item.name);
            if (item.type == FmDrawerItem.ITEM_TYPE_LABEL) {
                getLabelView(holder, item);
            } else {
                getView(holder, item);
            }
        }

        public void getLabelView(@NonNull ViewHolder holder, FmDrawerItem item) {
            if (holder.actionView != null) {
                holder.actionView.setVisibility(View.GONE);
            }
        }

        public void getView(@NonNull ViewHolder holder, FmDrawerItem item) {
            if (holder.iconView != null) {
                if (item.icon != null) {
                    holder.iconView.setImageDrawable(item.icon);
                } else holder.iconView.setImageResource(item.iconRes);
            }
            holder.itemView.setOnClickListener(v -> {
                Options options = item.options;
                if (options != null) {
                    mFmActivity.mDrawerLayout.close();
                    mFmActivity.loadFragment(options, null);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                // TODO: 2/2/25 Display options to edit/remove items
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mAdapterItems.size();
        }

        @FmDrawerItem.DrawerItemType
        @Override
        public int getItemViewType(int position) {
            return mAdapterItems.get(position).type;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            @Nullable
            private final AppCompatImageView iconView;
            private final AppCompatTextView labelView;
            @Nullable
            private final MaterialButton actionView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.item_icon);
                labelView = itemView.findViewById(R.id.item_title);
                actionView = itemView.findViewById(R.id.item_action);

            }
        }
    }
}
