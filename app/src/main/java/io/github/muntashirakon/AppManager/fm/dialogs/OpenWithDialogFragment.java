// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;
import io.github.muntashirakon.widget.SearchView;

public class OpenWithDialogFragment extends DialogFragment {
    public static final String TAG = OpenWithDialogFragment.class.getSimpleName();

    private static final String ARG_PATH = "path";
    private static final String ARG_TYPE = "type";
    private static final String ARG_CLOSE_ACTIVITY = "close";

    @NonNull
    public static OpenWithDialogFragment getInstance(@NonNull Path path) {
        return getInstance(path, null);
    }

    @NonNull
    public static OpenWithDialogFragment getInstance(@NonNull Path path, @Nullable String type) {
        return getInstance(path.getUri(), type, false);
    }

    @NonNull
    public static OpenWithDialogFragment getInstance(@NonNull Uri uri, @Nullable String type, boolean closeActivity) {
        OpenWithDialogFragment fragment = new OpenWithDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PATH, uri);
        args.putString(ARG_TYPE, type);
        args.putBoolean(ARG_CLOSE_ACTIVITY, closeActivity);
        fragment.setArguments(args);
        return fragment;
    }

    private static class ResolvedActivityInfo {
        @NonNull
        public final ResolveInfo resolveInfo;
        @NonNull
        public final String packageName;
        @NonNull
        public final String name;
        @NonNull
        public final String shortName;
        @NonNull
        public final CharSequence label;
        @NonNull
        public final CharSequence appLabel;

        private ResolvedActivityInfo(@NonNull ResolveInfo resolveInfo, @NonNull CharSequence label, @NonNull CharSequence appLabel) {
            this.resolveInfo = resolveInfo;
            this.packageName = resolveInfo.activityInfo.packageName;
            this.name = resolveInfo.activityInfo.name;
            this.shortName = getShortActivityName(this.name);
            this.label = label;
            this.appLabel = appLabel;
        }

        public boolean matches(@Nullable String constraint) {
            if (constraint == null) {
                return true;
            }
            // Match the following:
            // 1. Label
            // 2. Short name
            // 3. App label
            return label.toString().toLowerCase(Locale.getDefault()).contains(constraint)
                    || shortName.contains(constraint)
                    || appLabel.toString().toLowerCase(Locale.getDefault()).contains(constraint);
        }

        @NonNull
        private String getShortActivityName(@NonNull String longName) {
            int idxOfDot = longName.lastIndexOf('.');
            if (idxOfDot == -1) {
                return longName;
            }
            return longName.substring(idxOfDot + 1);
        }
    }

    private Path mPath;
    private String mCustomType;
    private boolean mCloseActivity;
    private View mDialogView;
    private SearchView mSearchView;
    private OpenWithViewModel mViewModel;
    private MatchingActivitiesRecyclerViewAdapter mAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(OpenWithViewModel.class);
        mPath = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri.class)));
        mCustomType = requireArguments().getString(ARG_TYPE, null);
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY, false);
        mAdapter = new MatchingActivitiesRecyclerViewAdapter(mViewModel, requireActivity());
        mAdapter.setIntent(getIntent(mPath, mCustomType));
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_open_with, null);
        mSearchView = mDialogView.findViewById(io.github.muntashirakon.ui.R.id.action_search);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.setFilteredItems(newText);
                return true;
            }
        });
        RecyclerView matchingActivitiesView = mDialogView.findViewById(R.id.intent_matching_activities);
        matchingActivitiesView.setLayoutManager(new LinearLayoutManager(requireContext()));
        matchingActivitiesView.setAdapter(mAdapter);
        // TODO: 19/11/22 Add support for always open and only for this file
        CheckBox alwaysOpen = mDialogView.findViewById(R.id.always_open);
        CheckBox openForThisFileOnly = mDialogView.findViewById(R.id.only_for_this_file);
        alwaysOpen.setVisibility(View.GONE);
        openForThisFileOnly.setVisibility(View.GONE);
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(requireActivity())
                .setTitle(R.string.file_open_with)
                .setSubtitle(mPath.getName())
                .setEndIcon(R.drawable.ic_open_in_new, v1 -> {
                    if (mAdapter != null && mAdapter.getIntent().resolveActivityInfo(requireActivity()
                            .getPackageManager(), 0) != null) {
                        startActivity(mAdapter.getIntent());
                    }
                    dismiss();
                })
                .setEndIconContentDescription(R.string.file_open_with_os_default_dialog);
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(requireActivity())
                .setCustomTitle(titleBuilder.build())
                .setView(mDialogView)
                .setPositiveButton(R.string.file_open_as, null)
                .setNeutralButton(R.string.file_open_with_custom_activity, null)
                .create();
        alertDialog.setOnShowListener(dialog -> {
            Button fileOpenAsButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button customButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            fileOpenAsButton.setOnClickListener(v -> {
                String[] customTypes = requireContext().getResources().getStringArray(R.array.file_open_as_option_types);
                new SearchableItemsDialogBuilder<>(requireActivity(), R.array.file_open_as_options)
                        .setTitle(R.string.file_open_as)
                        .hideSearchBar(true)
                        .setOnItemClickListener((dialog1, which, item) -> {
                            mCustomType = customTypes[which];
                            if (mAdapter != null) {
                                mAdapter.setIntent(getIntent(mPath, mCustomType));
                                if (mViewModel != null) {
                                    // Reload activities
                                    mViewModel.loadMatchingActivities(mAdapter.getIntent());
                                }
                            }
                            dialog1.dismiss();
                        })
                        .setNegativeButton(R.string.close, null)
                        .show();
            });
            // TODO: 20/11/22 Add option to set custom activity
            customButton.setVisibility(View.GONE);
        });
        return alertDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (mViewModel != null) {
            mViewModel.getMatchingActivitiesLiveData().observe(getViewLifecycleOwner(), matchingActivities -> {
                mAdapter.setDefaultList(matchingActivities);
                // Don't display search bar if items are less than 6
                mSearchView.setVisibility(matchingActivities.size() < 6 ? View.GONE : View.VISIBLE);
            });
            mViewModel.getPathContentInfoLiveData().observe(getViewLifecycleOwner(), pathContentInfo -> {
                if (mAdapter != null) {
                    mAdapter.setIntent(getIntent(mPath, pathContentInfo.getMimeType()));
                    if (mViewModel != null) {
                        // Reload activities
                        mViewModel.loadMatchingActivities(mAdapter.getIntent());
                    }
                }
            });
            mViewModel.getIntentLiveData().observe(getViewLifecycleOwner(), intent -> {
                startActivity(intent);
                dismiss();
            });
            if (mCustomType == null) {
                mViewModel.loadFileContentInfo(mPath);
            }
            if (mAdapter != null) {
                mViewModel.loadMatchingActivities(mAdapter.getIntent());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCloseActivity) {
            requireActivity().finish();
        }
    }

    @NonNull
    private Intent getIntent(@NonNull Path path, @Nullable String customType) {
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
        if (path.canRead()) {
            flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        if (path.canWrite()) {
            flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FmProvider.getContentUri(path), customType != null ? customType : path.getType());
        intent.setFlags(flags);
        return intent;
    }

    private static class MatchingActivitiesRecyclerViewAdapter extends RecyclerView.Adapter<MatchingActivitiesRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<ResolvedActivityInfo> mMatchingActivities = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> mFilteredItems = new ArrayList<>();
        private final Activity mActivity;
        private final OpenWithViewModel mViewModel;
        private final ImageLoader mImageLoader = ImageLoader.getInstance();

        private Intent mIntent;
        @Nullable
        private String mConstraint;

        public MatchingActivitiesRecyclerViewAdapter(OpenWithViewModel viewModel, Activity activity) {
            mViewModel = viewModel;
            mActivity = activity;
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void setIntent(Intent intent) {
            mIntent = intent;
        }

        public void setDefaultList(@Nullable List<ResolvedActivityInfo> matchingActivities) {
            mMatchingActivities.clear();
            if (matchingActivities != null) {
                mMatchingActivities.addAll(matchingActivities);
            }
            filterItems();
        }

        void setFilteredItems(@Nullable String constraint) {
            mConstraint = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.getDefault());
            filterItems();
        }

        private void filterItems() {
            synchronized (mFilteredItems) {
                int lastCount = mFilteredItems.size();
                mFilteredItems.clear();
                for (int i = 0; i < mMatchingActivities.size(); ++i) {
                    if (mConstraint == null || mMatchingActivities.get(i).matches(mConstraint)) {
                        mFilteredItems.add(i);
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, lastCount, mFilteredItems.size());
            }
        }

        @NonNull
        @Override
        public MatchingActivitiesRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new MatchingActivitiesRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MatchingActivitiesRecyclerViewAdapter.ViewHolder holder, int position) {
            int index;
            synchronized (mFilteredItems) {
                index = mFilteredItems.get(position);
            }
            ResolvedActivityInfo resolvedInfo = mMatchingActivities.get(index);
            holder.title.setText(resolvedInfo.label);
            String activityName = resolvedInfo.name;
            String summary = resolvedInfo.appLabel + "\n" + resolvedInfo.shortName;
            holder.summary.setText(summary);
            String tag = resolvedInfo.packageName + "_" + resolvedInfo.label;
            holder.icon.setTag(tag);
            mImageLoader.displayImage(tag, holder.icon, new ResolveInfoImageFetcher(resolvedInfo.resolveInfo));
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(mIntent);
                intent.setClassName(resolvedInfo.packageName, activityName);
                mViewModel.openIntent(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (!FeatureController.isInterceptorEnabled()) {
                    return false;
                }
                Intent intent = new Intent(mIntent);
                intent.putExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME, resolvedInfo.packageName);
                intent.putExtra(ActivityInterceptor.EXTRA_CLASS_NAME, activityName);
                intent.setClassName(mActivity, ActivityInterceptor.class.getName());
                mViewModel.openIntent(intent);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            synchronized (mFilteredItems) {
                return mFilteredItems.size();
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView summary;
            ImageView icon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.title);
                summary = itemView.findViewById(android.R.id.summary);
                icon = itemView.findViewById(android.R.id.icon);
            }
        }
    }

    public static class OpenWithViewModel extends AndroidViewModel {
        private final MutableLiveData<List<ResolvedActivityInfo>> mMatchingActivitiesLiveData = new MutableLiveData<>();
        private final MutableLiveData<PathContentInfo> mPathContentInfoLiveData = new MutableLiveData<>();
        private final SingleLiveEvent<Intent> mIntentLiveData = new SingleLiveEvent<>();
        private final PackageManager mPm;

        public OpenWithViewModel(@NonNull Application application) {
            super(application);
            mPm = application.getPackageManager();
        }

        public void loadMatchingActivities(@NonNull Intent intent) {
            ThreadUtils.postOnBackgroundThread(() -> {
                List<ResolveInfo> resolveInfoList = mPm.queryIntentActivities(intent, 0);
                List<ResolvedActivityInfo> resolvedActivityInfoList = new ArrayList<>(resolveInfoList.size());
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    CharSequence label = resolveInfo.loadLabel(mPm);
                    CharSequence appLabel = resolveInfo.activityInfo.applicationInfo.loadLabel(mPm);
                    resolvedActivityInfoList.add(new ResolvedActivityInfo(resolveInfo, label, appLabel));
                }
                mMatchingActivitiesLiveData.postValue(resolvedActivityInfoList);
            });
        }

        public void loadFileContentInfo(@NonNull Path path) {
            ThreadUtils.postOnBackgroundThread(() -> mPathContentInfoLiveData.postValue(path.getPathContentInfo()));
        }

        public void openIntent(@NonNull Intent intent) {
            mIntentLiveData.setValue(intent);
        }

        public LiveData<List<ResolvedActivityInfo>> getMatchingActivitiesLiveData() {
            return mMatchingActivitiesLiveData;
        }

        public LiveData<PathContentInfo> getPathContentInfoLiveData() {
            return mPathContentInfoLiveData;
        }

        public LiveData<Intent> getIntentLiveData() {
            return mIntentLiveData;
        }

        @Override
        protected void onCleared() {
            super.onCleared();
        }
    }

    private static class ResolveInfoImageFetcher implements ImageLoader.ImageFetcherInterface {
        @Nullable
        private final ResolveInfo mInfo;

        public ResolveInfoImageFetcher(@Nullable ResolveInfo info) {
            mInfo = info;
        }

        @Override
        @NonNull
        public ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag) {
            PackageManager pm = ContextUtils.getContext().getPackageManager();
            Drawable drawable = mInfo != null ? mInfo.loadIcon(pm) : null;
            return new ImageLoader.ImageFetcherResult(tag, drawable != null ? UIUtils.getBitmapFromDrawable(drawable) : null,
                    false, true,
                    new ImageLoader.DefaultImageDrawable("android_default_icon", pm.getDefaultActivityIcon()));
        }
    }
}
