// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class OpenWithDialogFragment extends DialogFragment {
    public static final String TAG = OpenWithDialogFragment.class.getSimpleName();

    private static final String ARG_PATH = "path";
    private static final String ARG_TYPE = "type";

    @NonNull
    public static OpenWithDialogFragment getInstance(@NonNull Path path) {
        return getInstance(path, null);
    }

    @NonNull
    public static OpenWithDialogFragment getInstance(@NonNull Path path, @Nullable String type) {
        OpenWithDialogFragment fragment = new OpenWithDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PATH, path.getUri());
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    private Path mPath;
    private String mCustomType;
    private View mDialogView;
    private OpenWithViewModel mViewModel;
    private MatchingActivitiesRecyclerViewAdapter mAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(OpenWithViewModel.class);
        mPath = Paths.get((Uri) requireArguments().getParcelable(ARG_PATH));
        mCustomType = requireArguments().getString(ARG_TYPE, null);
        mAdapter = new MatchingActivitiesRecyclerViewAdapter(mViewModel, requireActivity());
        mAdapter.setIntent(getIntent(mPath, mCustomType));
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_open_with, null);
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
                .setSubtitle(mPath.getUri().toString())
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
            mViewModel.getMatchingActivitiesLiveData().observe(getViewLifecycleOwner(), mAdapter::setDefaultList);
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

    @NonNull
    private Intent getIntent(@NonNull Path path, @Nullable String customType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FmProvider.getContentUri(path), customType != null ? customType : path.getType());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    private static class MatchingActivitiesRecyclerViewAdapter extends RecyclerView.Adapter<MatchingActivitiesRecyclerViewAdapter.ViewHolder> {
        private final List<ResolveInfo> mMatchingActivities = new ArrayList<>();
        private final PackageManager mPm;
        private final Activity mActivity;
        private final OpenWithViewModel mViewModel;
        private final ImageLoader mImageLoader;

        private Intent mIntent;

        public MatchingActivitiesRecyclerViewAdapter(OpenWithViewModel viewModel, Activity activity) {
            mViewModel = viewModel;
            mImageLoader = new ImageLoader();
            mActivity = activity;
            mPm = activity.getPackageManager();
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void setIntent(Intent intent) {
            mIntent = intent;
        }

        public void setDefaultList(@Nullable List<ResolveInfo> matchingActivities) {
            mMatchingActivities.clear();
            if (matchingActivities != null) {
                mMatchingActivities.addAll(matchingActivities);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MatchingActivitiesRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new MatchingActivitiesRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MatchingActivitiesRecyclerViewAdapter.ViewHolder holder, int position) {
            ResolveInfo resolveInfo = mMatchingActivities.get(position);
            ActivityInfo info = resolveInfo.activityInfo;
            holder.title.setText(info.loadLabel(mPm));
            String activityName = info.name;
            String summary = info.packageName + "\n" + getShortActivityName(activityName);
            holder.summary.setText(summary);
            mImageLoader.displayImage(info.packageName + "_" + activityName, info, holder.icon);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(mIntent);
                intent.setClassName(info.packageName, activityName);
                mViewModel.openIntent(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (!FeatureController.isInterceptorEnabled()) {
                    return false;
                }
                Intent intent = new Intent(mIntent);
                intent.putExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME, info.packageName);
                intent.putExtra(ActivityInterceptor.EXTRA_CLASS_NAME, activityName);
                intent.setClassName(mActivity, ActivityInterceptor.class.getName());
                mViewModel.openIntent(intent);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mMatchingActivities.size();
        }

        @NonNull
        private String getShortActivityName(@NonNull String longName) {
            int idxOfDot = longName.lastIndexOf('.');
            if (idxOfDot == -1) {
                return longName;
            }
            return longName.substring(idxOfDot + 1);
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
        private final MutableLiveData<List<ResolveInfo>> mMatchingActivitiesLiveData = new MutableLiveData<>();
        private final MutableLiveData<PathContentInfo> mPathContentInfoLiveData = new MutableLiveData<>();
        private final SingleLiveEvent<Intent> mIntentLiveData = new SingleLiveEvent<>();
        private final ExecutorService mExecutor = MultithreadedExecutor.getNewInstance();
        private final PackageManager mPm;

        public OpenWithViewModel(@NonNull Application application) {
            super(application);
            mPm = application.getPackageManager();
        }

        public void loadMatchingActivities(@NonNull Intent intent) {
            mExecutor.submit(() ->
                    mMatchingActivitiesLiveData.postValue(mPm.queryIntentActivities(intent, 0)));
        }

        public void loadFileContentInfo(@NonNull Path path) {
            mExecutor.submit(() -> mPathContentInfoLiveData.postValue(path.getPathContentInfo()));
        }

        public void openIntent(@NonNull Intent intent) {
            mIntentLiveData.setValue(intent);
        }

        public LiveData<List<ResolveInfo>> getMatchingActivitiesLiveData() {
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
}
