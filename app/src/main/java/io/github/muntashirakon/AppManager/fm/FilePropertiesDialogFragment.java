// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.Objects;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.BundleCompat;
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.users.Groups;
import io.github.muntashirakon.AppManager.users.Owners;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

public class FilePropertiesDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = FilePropertiesDialogFragment.class.getSimpleName();

    private static final String ARG_PATH = "path";

    @NonNull
    public static FilePropertiesDialogFragment getInstance(@NonNull Uri uri) {
        FilePropertiesDialogFragment fragment = new FilePropertiesDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PATH, uri);
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
        Path path = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri.class)));
        ImageView iconView = bodyView.findViewById(android.R.id.icon);
        ImageView symbolicLinkiconView = bodyView.findViewById(R.id.symolic_link_icon);
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
        TextView modeView = bodyView.findViewById(R.id.file_mode);
        TextView ownerView = bodyView.findViewById(R.id.owner_id);
        TextView groupView = bodyView.findViewById(R.id.group_id);
        TextView selinuxContextView = bodyView.findViewById(R.id.selinux_context);

        // Set values
        iconView.setImageResource(path.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file_document);
        symbolicLinkiconView.setVisibility(path.isSymbolicLink() ? View.VISIBLE : View.GONE);
        nameView.setText(path.getName());
        String modificationDate = DateUtils.formatDateTime(path.lastModified());
        pathView.setText(FmUtils.getDisplayablePath(path));
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
        dateCreatedView.setText("--");
        dateAccessedView.setText("--");
        long creationTime = path.creationTime();
        long lastAccessTime = path.lastAccess();
        dateCreatedView.setText(creationTime > 0 ? DateUtils.formatDateTime(creationTime) : "--");
        dateAccessedView.setText(lastAccessTime > 0 ? DateUtils.formatDateTime(lastAccessTime) : "--");
        int mode = path.getMode();
        modeView.setText(mode != 0 ? getFormattedMode(mode) : "--");
        UidGidPair uidGidPair = path.getUidGid();
        // TODO: 7/12/22 Display owner and group name using syscall (setpwent, getpwent, endpwent)
        if (uidGidPair == null) {
            ownerView.setText("--");
            groupView.setText("--");
        }
        String context = path.getSelinuxContext();
        selinuxContextView.setText(context != null ? context : "--");

        // Live data
        viewModel.getFileSizeLiveData().observe(getViewLifecycleOwner(), size -> {
            summaryView.setText(String.format(Locale.getDefault(), "%s • %s", modificationDate,
                    Formatter.formatShortFileSize(requireContext(), size)));
            sizeView.setText(String.format(Locale.getDefault(), "%s (%s bytes)",
                    Formatter.formatShortFileSize(requireContext(), size), size));
        });
        viewModel.getFmItemLiveData().observe(getViewLifecycleOwner(), fmItem -> {
            ImageLoader.getInstance().displayImage(fmItem.tag, iconView, new FmIconFetcher(fmItem));
            PathContentInfo contentInfo = fmItem.getContentInfo();
            if (contentInfo != null) {
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
            }
        });
        viewModel.getOwnerLiveData().observe(getViewLifecycleOwner(), ownerName -> {
            assert uidGidPair != null;
            ownerView.setText(String.format(Locale.ROOT, "%s (%d)", ownerName, uidGidPair.uid));
        });
        viewModel.getGroupLiveData().observe(getViewLifecycleOwner(), groupName -> {
            assert uidGidPair != null;
            groupView.setText(String.format(Locale.ROOT, "%s (%d)", groupName, uidGidPair.gid));
        });

        // Load live data
        viewModel.loadFileSize(path);
        viewModel.loadFmItem(path);
        if (uidGidPair != null) {
            viewModel.loadOwnerInfo(uidGidPair.uid);
            viewModel.loadGroupInfo(uidGidPair.gid);
        }
    }

    @SuppressWarnings("OctalInteger")
    @NonNull
    private String getFormattedMode(int mode) {
        String s = ((mode & 0400) != 0 ? "r" : "-") +
                ((mode & 0200) != 0 ? "w" : "-") +
                ((mode & 0100) != 0 ? "x" : "-") +
                ((mode & 040) != 0 ? "r" : "-") +
                ((mode & 020) != 0 ? "w" : "-") +
                ((mode & 010) != 0 ? "x" : "-") +
                ((mode & 04) != 0 ? "r" : "-") +
                ((mode & 02) != 0 ? "w" : "-") +
                ((mode & 01) != 0 ? "x" : "-");
        return String.format(Locale.ROOT, "%s (%o)", s, mode & 0777);
    }

    public static class FilePropertiesViewModel extends AndroidViewModel {
        private final MutableLiveData<Long> mFileSizeLiveData = new MutableLiveData<>();
        private final MutableLiveData<FmItem> mFmItemLiveData = new MutableLiveData<>();
        private final MutableLiveData<String> mOwnerLiveData = new MutableLiveData<>();
        private final MutableLiveData<String> mGroupLiveData = new MutableLiveData<>();

        @Nullable
        private Future<?> sizeResult;

        public FilePropertiesViewModel(@NonNull Application application) {
            super(application);
        }

        @Override
        protected void onCleared() {
            // Size checks can take forever, so it's a good idea to terminate the process when the dialog is exited
            if (sizeResult != null) {
                sizeResult.cancel(true);
            }
            super.onCleared();
        }

        public void loadFileSize(@NonNull Path path) {
            sizeResult = ThreadUtils.postOnBackgroundThread(() -> {
                long size = Paths.size(path);
                mFileSizeLiveData.postValue(size);
            });
        }

        public void loadFmItem(@NonNull Path path) {
            ThreadUtils.postOnBackgroundThread(() -> {
                FmItem fmItem = new FmItem(path);
                fmItem.setContentInfo(path.getPathContentInfo());
                mFmItemLiveData.postValue(fmItem);
            });
        }

        public void loadOwnerInfo(int uid) {
            ThreadUtils.postOnBackgroundThread(() -> {
                String ownerName = Owners.getOwnerName(uid);
                mOwnerLiveData.postValue(ownerName);
            });
        }

        public void loadGroupInfo(int gid) {
            ThreadUtils.postOnBackgroundThread(() -> {
                String groupName = Groups.getGroupName(gid);
                mGroupLiveData.postValue(groupName);
            });
        }

        public LiveData<Long> getFileSizeLiveData() {
            return mFileSizeLiveData;
        }

        public LiveData<FmItem> getFmItemLiveData() {
            return mFmItemLiveData;
        }

        public LiveData<String> getOwnerLiveData() {
            return mOwnerLiveData;
        }

        public LiveData<String> getGroupLiveData() {
            return mGroupLiveData;
        }
    }
}
