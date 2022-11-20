// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.system.ErrnoException;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.io.Paths;

public class FilePropertiesDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = FilePropertiesDialogFragment.class.getSimpleName();

    private static final String ARG_PATH = "path";

    @NonNull
    public static FilePropertiesDialogFragment getInstance(@NonNull Path path) {
        FilePropertiesDialogFragment fragment = new FilePropertiesDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PATH, path.getUri());
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_file_properties, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        FilePropertiesViewModel viewModel = new ViewModelProvider(this).get(FilePropertiesViewModel.class);
        Path path = Paths.get((Uri) requireArguments().getParcelable(ARG_PATH));
        ImageView iconView = bodyView.findViewById(R.id.icon);
        TextView nameView = bodyView.findViewById(R.id.name);
        TextView summaryView = bodyView.findViewById(R.id.summary);
        MaterialButton moreButton = bodyView.findViewById(R.id.more);
        moreButton.setVisibility(View.GONE);
        TextView pathView = bodyView.findViewById(R.id.path);
        TextView typeView = bodyView.findViewById(R.id.type);
        TextView targetPathView = bodyView.findViewById(R.id.target_file);
        TextInputLayout openWithLayoutView = bodyView.findViewById(R.id.open_with_layout);
        TextView openWithView = bodyView.findViewById(R.id.open_with);
        TextView sizeView = bodyView.findViewById(R.id.size);
        TextView dateCreatedView = bodyView.findViewById(R.id.date_created);
        TextView dateModifiedView = bodyView.findViewById(R.id.date_modified);
        TextView dateAccessedView = bodyView.findViewById(R.id.date_accessed);
        TextView moreInfoView = bodyView.findViewById(R.id.more_info);
        ((View) moreInfoView.getParent()).setVisibility(View.GONE);

        // Set values
        iconView.setImageResource(path.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file_document);
        nameView.setText(path.getName());
        String modificationDate = DateUtils.formatDateTime(path.lastModified());
        pathView.setText(path.getUri().toString());
        String realFile = null;
        if (path.isSymbolicLink()) {
            try {
                realFile = path.getRealFilePath();
            } catch (IOException ignore) {
            }
        }
        if (realFile != null) {
            targetPathView.setText(realFile);
        } else {
            ((View) targetPathView.getParent()).setVisibility(View.GONE);
        }
        // TODO: 16/11/22 Handle open with
        openWithLayoutView.setVisibility(View.GONE);
        dateModifiedView.setText(modificationDate);
        ExtendedFile file = path.getFile();
        dateCreatedView.setText("--");
        dateAccessedView.setText("--");
        if (file != null) {
            try {
                dateCreatedView.setText(DateUtils.formatDateTime(file.creationTime()));
            } catch (ErrnoException ignore) {
            }
            try {
                dateAccessedView.setText(DateUtils.formatDateTime(file.lastAccess()));
            } catch (ErrnoException ignore) {
            }
        }
        // Live data
        viewModel.getFileSizeLiveData().observe(getViewLifecycleOwner(), size -> {
            summaryView.setText(String.format(Locale.getDefault(), "%s • %s", modificationDate,
                    Formatter.formatShortFileSize(requireContext(), size)));
            sizeView.setText(String.format(Locale.getDefault(), "%s (%s bytes)",
                    Formatter.formatShortFileSize(requireContext(), size), size));
        });
        viewModel.getFileContentInfoLiveData().observe(getViewLifecycleOwner(), contentInfo -> {
            String name = contentInfo.getName();
            String mime = contentInfo.getMimeType();
            String message = contentInfo.getMessage();
            if (mime != null) {
                typeView.setText(String.format(Locale.ROOT, "%s (%s)", name, mime));
            } else {
                typeView.setText(name);
            }
            if (message != null) {
                ((View) moreInfoView.getParent()).setVisibility(View.VISIBLE);
                moreInfoView.setText(message);
            }
        });
        // Load live data
        viewModel.loadFileSize(path);
        viewModel.loadFileContentInfo(path);
    }

    public static class FilePropertiesViewModel extends AndroidViewModel {
        private final MutableLiveData<Long> mFileSizeLiveData = new MutableLiveData<>();
        private final MutableLiveData<PathContentInfo> mFileContentInfoLiveData = new MutableLiveData<>();
        private final ExecutorService mExecutor = MultithreadedExecutor.getNewInstance();

        public FilePropertiesViewModel(@NonNull Application application) {
            super(application);
        }

        @Override
        protected void onCleared() {
            mExecutor.shutdownNow();
            super.onCleared();
        }

        public void loadFileSize(@NonNull Path path) {
            mExecutor.submit(() -> {
                long size = Paths.size(path);
                mFileSizeLiveData.postValue(size);
            });
        }

        public void loadFileContentInfo(@NonNull Path path) {
            mExecutor.submit(() -> mFileContentInfoLiveData.postValue(path.getPathContentInfo()));
        }

        public LiveData<Long> getFileSizeLiveData() {
            return mFileSizeLiveData;
        }

        public LiveData<PathContentInfo> getFileContentInfoLiveData() {
            return mFileContentInfoLiveData;
        }
    }
}
