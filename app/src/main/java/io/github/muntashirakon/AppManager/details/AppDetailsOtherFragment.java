// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.AppManager.details.AppDetailsViewModel.OPEN_GL_ES;
import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.divider.MaterialDivider;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.scanner.NativeLibraries;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsOtherFragment extends AppDetailsFragment {
    @IntDef(value = {
            FEATURES,
            CONFIGURATIONS,
            SIGNATURES,
            SHARED_LIBRARIES
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OtherProperty {
    }

    private AppDetailsRecyclerAdapter mAdapter;
    private boolean mIsExternalApk;
    @OtherProperty
    private int mNeededProperty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNeededProperty = requireArguments().getInt(ARG_TYPE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyView.setText(getNotFoundString(mNeededProperty));
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        alertView.setVisibility(View.GONE);
        if (viewModel == null) return;
        viewModel.get(mNeededProperty).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter != null && viewModel.isPackageExist()) {
                mIsExternalApk = viewModel.isExternalApk();
                mAdapter.setDefaultList(appDetailsItems);
            } else ProgressIndicatorCompat.setVisibility(progressIndicator, false);
        });
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_details) {
            refreshDetails();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity.searchView != null) {
            activity.searchView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, int type) {
        if (viewModel != null) {
            viewModel.setSearchQuery(searchQuery, type, mNeededProperty);
        }
        return true;
    }

    private void refreshDetails() {
        if (viewModel == null || mIsExternalApk) return;
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        viewModel.triggerPackageChange();
    }

    /**
     * Return corresponding section's array
     */
    private int getNotFoundString(@OtherProperty int index) {
        switch (index) {
            case FEATURES:
                return R.string.no_feature;
            case CONFIGURATIONS:
                return R.string.no_configurations;
            case SIGNATURES:
                return R.string.app_signing_no_signatures;
            case SHARED_LIBRARIES:
                return R.string.no_shared_libs;
            default:
                return 0;
        }
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @OtherProperty
        private int mRequestedProperty;
        @Nullable
        private String mConstraint;
        private final int mCardColor0;
        private final int mCardColor1;
        private final int mDefaultIndicatorColor;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
            mCardColor0 = ColorCodes.getListItemColor0(activity);
            mCardColor1 = ColorCodes.getListItemColor1(activity);
            mDefaultIndicatorColor = ColorCodes.getListItemDefaultIndicatorColor(activity);
        }

        @UiThread
        void setDefaultList(@NonNull List<AppDetailsItem<?>> list) {
            mRequestedProperty = mNeededProperty;
            mConstraint = viewModel == null ? null : viewModel.getSearchQuery();
            ProgressIndicatorCompat.setVisibility(progressIndicator, false);
            synchronized (mAdapterList) {
                mAdapterList.clear();
                mAdapterList.addAll(list);
                notifyDataSetChanged();
            }
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            MaterialButton launchBtn;
            MaterialDivider divider;
            Chip chipType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                switch (mRequestedProperty) {
                    case FEATURES:
                        textView1 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.gles_ver);
                        break;
                    case CONFIGURATIONS:
                        textView1 = itemView.findViewById(R.id.reqgles);
                        textView2 = itemView.findViewById(R.id.reqfea);
                        textView3 = itemView.findViewById(R.id.reqkey);
                        textView4 = itemView.findViewById(R.id.reqnav);
                        textView5 = itemView.findViewById(R.id.reqtouch);
                        break;
                    case SHARED_LIBRARIES:
                        textView1 = itemView.findViewById(R.id.item_title);
                        textView2 = itemView.findViewById(R.id.item_subtitle);
                        launchBtn = itemView.findViewById(R.id.item_open);
                        divider = itemView.findViewById(R.id.divider);
                        chipType = itemView.findViewById(R.id.lib_type);
                        textView1.setTextIsSelectable(true);
                        textView2.setTextIsSelectable(true);
                        break;
                    case SIGNATURES:
                        textView1 = itemView.findViewById(R.id.checksum_description);
                    default:
                        break;
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams") final View view;
            switch (mRequestedProperty) {
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
                    break;
                case FEATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_secondary, parent, false);
                    break;
                case CONFIGURATIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_tertiary, parent, false);
                    break;
                case SIGNATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_signature, parent, false);
                    break;
                case SHARED_LIBRARIES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_lib, parent, false);
                    break;
            }
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            switch (mRequestedProperty) {
                case FEATURES:
                    getFeaturesView(context, holder, position);
                    break;
                case CONFIGURATIONS:
                    getConfigurationView(context, holder, position);
                    break;
                case SIGNATURES:
                    getSignatureView(context, holder, position);
                    break;
                case SHARED_LIBRARIES:
                    getSharedLibsView(context, holder, position);
                    break;
                default:
                    break;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        private void getSharedLibsView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            AppDetailsItem<?> item;
            synchronized (mAdapterList) {
                item = mAdapterList.get(index);
            }
            holder.textView1.setText(item.name);
            if (item.vanillaItem instanceof File) {
                File libFile = (File) item.vanillaItem;
                StringBuilder sb = new StringBuilder(Formatter.formatFileSize(context, libFile.length()))
                        .append("\n").append(libFile.getAbsolutePath());
                holder.textView2.setText(sb);
                holder.chipType.setText(libFile.getName().endsWith(".so") ? "SO" : "JAR");
                holder.launchBtn.setVisibility(View.VISIBLE);
                holder.launchBtn.setIconResource(R.drawable.ic_open_in_new);
                holder.launchBtn.setOnClickListener(openAsFolderInFM(context, libFile.getParent()));
            } else if (item.vanillaItem instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) item.vanillaItem;
                String apkFileStr = packageInfo.applicationInfo.publicSourceDir;
                Path apkFile = apkFileStr != null ? Paths.get(apkFileStr) : null;
                StringBuilder sb = new StringBuilder()
                        .append(packageInfo.packageName)
                        .append("\n");
                if (apkFile != null) {
                    sb.append(Formatter.formatFileSize(context, apkFile.length())).append(", ");
                }
                sb.append(getString(R.string.version_name_with_code, packageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(packageInfo)));
                if (apkFile != null) {
                    sb.append("\n").append(apkFile.getFilePath());
                    holder.launchBtn.setVisibility(View.VISIBLE);
                    holder.launchBtn.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_information);
                    holder.launchBtn.setOnClickListener(v -> {
                        Intent intent = AppDetailsActivity.getIntent(context, apkFile, false);
                        startActivity(intent);
                    });
                } else holder.launchBtn.setVisibility(View.GONE);
                holder.textView2.setText(sb);
                holder.chipType.setText("APK");
            } else if (item.vanillaItem instanceof NativeLibraries.ElfLib) {
                holder.textView2.setText(((LocalizedString) item.vanillaItem).toLocalizedString(context));
                String type;
                switch (((NativeLibraries.ElfLib) item.vanillaItem).getType()) {
                    case NativeLibraries.ElfLib.TYPE_DYN:
                        type = "SHARED";
                        break;
                    case NativeLibraries.ElfLib.TYPE_EXEC:
                        type = "EXEC";
                        break;
                    default:
                        type = "SO";
                }
                holder.chipType.setText(type);
                holder.launchBtn.setVisibility(View.GONE);
            } else if (item.vanillaItem instanceof NativeLibraries.InvalidLib) {
                holder.textView2.setText(((LocalizedString) item.vanillaItem).toLocalizedString(context));
                holder.chipType.setText("⚠️");
                holder.launchBtn.setVisibility(View.GONE);
            }
            holder.divider.setDividerColor(mDefaultIndicatorColor);
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(mCardColor1);
        }

        @SuppressLint("SetTextI18n")
        private void getFeaturesView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            MaterialCardView view = (MaterialCardView) holder.itemView;
            final FeatureInfo featureInfo;
            synchronized (mAdapterList) {
                featureInfo = (FeatureInfo) mAdapterList.get(index).vanillaItem;
            }
            // Currently, feature only has a single flag, which specifies whether the feature is required.
            boolean isRequired = (featureInfo.flags & FeatureInfo.FLAG_REQUIRED) != 0;
            boolean isAvailable;
            if (featureInfo.name.equals(OPEN_GL_ES)) {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                int glEsVersion = activityManager.getDeviceConfigurationInfo().reqGlEsVersion;
                isAvailable = featureInfo.reqGlEsVersion <= glEsVersion;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isAvailable = packageManager.hasSystemFeature(featureInfo.name, featureInfo.version);
            } else {
                isAvailable = packageManager.hasSystemFeature(featureInfo.name);
            }
            // Set background
            if (isRequired && !isAvailable) {
                view.setCardBackgroundColor(ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.red));
            } else if (!isAvailable) {
                view.setCardBackgroundColor(ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.disabled_user));
            } else {
                view.setCardBackgroundColor(index % 2 == 0 ? mCardColor1 : mCardColor0);
            }
            if (featureInfo.name.equals(OPEN_GL_ES)) {
                // OpenGL ES
                if (featureInfo.reqGlEsVersion == FeatureInfo.GL_ES_VERSION_UNDEFINED) {
                    holder.textView1.setText(featureInfo.name);
                } else {
                    // GL ES version
                    holder.textView1.setText(String.format(Locale.ROOT, "%s %s",
                            getString(R.string.gles_version), Utils.getGlEsVersion(featureInfo.reqGlEsVersion)));
                }
                holder.textView3.setVisibility(View.GONE);
                return;
            }
            // Set feature name
            holder.textView1.setText(featureInfo.name);
            // Feature version: 0 means any version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && featureInfo.version != 0) {
                holder.textView3.setVisibility(View.VISIBLE);
                holder.textView3.setText(getString(R.string.minimum_version, featureInfo.version));
            } else holder.textView3.setVisibility(View.GONE);
        }

        private void getConfigurationView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            MaterialCardView view = (MaterialCardView) holder.itemView;
            final ConfigurationInfo configurationInfo;
            synchronized (mAdapterList) {
                configurationInfo = (ConfigurationInfo) mAdapterList.get(index).vanillaItem;
            }
            view.setCardBackgroundColor(index % 2 == 0 ? mCardColor1 : mCardColor0);
            // GL ES version
            holder.textView1.setText(String.format(Locale.ROOT, "%s %s",
                    getString(R.string.gles_version), Utils.getGlEsVersion(configurationInfo.reqGlEsVersion)));
            // Flag & others
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.input_features),
                    Utils.getInputFeaturesString(configurationInfo.reqInputFeatures)));
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.keyboard_type),
                    getString(Utils.getKeyboardType(configurationInfo.reqKeyboardType))));
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.navigation),
                    getString(Utils.getNavigation(configurationInfo.reqNavigation))));
            holder.textView5.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.touchscreen),
                    getString(Utils.getTouchScreen(configurationInfo.reqTouchScreen))));
        }

        private void getSignatureView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            TextView textView = holder.textView1;
            AppDetailsItem<?> item;
            synchronized (mAdapterList) {
                item = mAdapterList.get(index);
            }
            final X509Certificate signature = (X509Certificate) item.vanillaItem;
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            if (index == 0) {
                // Display verifier info
                builder.append(PackageUtils.getApkVerifierInfo(Objects.requireNonNull(viewModel).getApkVerifierResult(), context));
            }
            if (!TextUtils.isEmpty(item.name)) {
                builder.append(UIUtils.getTitleText(context, item.name)).append("\n");
            }
            try {
                builder.append(PackageUtils.getSigningCertificateInfo(context, signature));
            } catch (CertificateEncodingException ignore) {
            }
            textView.setText(builder);
            textView.setTextIsSelectable(true);
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(index % 2 == 0 ? mCardColor1 : mCardColor0);
        }
    }
}
