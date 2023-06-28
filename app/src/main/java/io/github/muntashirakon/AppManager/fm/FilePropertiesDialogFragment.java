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
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.TextInputTextView;

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

    @Nullable
    private FileProperties mFileProperties;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_file_properties, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        FilePropertiesViewModel viewModel = new ViewModelProvider(this).get(FilePropertiesViewModel.class);
        Path path = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri.class)));
        int endIconSizeSmall = UiUtils.dpToPx(requireContext(), 34 - 16);
        ImageView iconView = bodyView.findViewById(android.R.id.icon);
        ImageView symbolicLinkIconView = bodyView.findViewById(R.id.symolic_link_icon);
        TextView nameView = bodyView.findViewById(R.id.name);
        TextView summaryView = bodyView.findViewById(R.id.summary);
        MaterialButton moreButton = bodyView.findViewById(R.id.more);
        moreButton.setVisibility(View.GONE);
        TextInputTextView pathView = bodyView.findViewById(R.id.path);
        TextInputTextView typeView = bodyView.findViewById(R.id.type);
        TextInputTextView targetPathView = bodyView.findViewById(R.id.target_file);
        TextInputLayout targetPathLayout = TextInputLayoutCompat.fromTextInputEditText(targetPathView);
        TextInputLayoutCompat.setEndIconSize(targetPathLayout, endIconSizeSmall);
        TextInputTextView openWithView = bodyView.findViewById(R.id.open_with);
        TextInputLayout openWithLayout = TextInputLayoutCompat.fromTextInputEditText(openWithView);
        TextInputLayoutCompat.setEndIconSize(openWithLayout, endIconSizeSmall);
        TextInputTextView sizeView = bodyView.findViewById(R.id.size);
        TextInputTextView dateCreatedView = bodyView.findViewById(R.id.date_created);
        TextInputTextView dateModifiedView = bodyView.findViewById(R.id.date_modified);
        TextInputLayout dateModifiedLayout = TextInputLayoutCompat.fromTextInputEditText(dateModifiedView);
        TextInputLayoutCompat.setEndIconSize(dateModifiedLayout, endIconSizeSmall);
        dateModifiedLayout.setEndIconOnClickListener(v -> {
            if (mFileProperties != null) {
                viewModel.setModificationTime(mFileProperties, System.currentTimeMillis());
            }
        });
        TextInputTextView dateAccessedView = bodyView.findViewById(R.id.date_accessed);
        TextInputLayout dateAccessedLayout = TextInputLayoutCompat.fromTextInputEditText(dateAccessedView);
        dateAccessedLayout.setEndIconOnClickListener(v -> {
            if (mFileProperties != null) {
                // TODO: 28/6/23 Set last access
                // viewModel.setLastAccessTime(mFileProperties);
            }
        });
        TextInputLayoutCompat.setEndIconSize(dateAccessedLayout, endIconSizeSmall);
        TextInputTextView moreInfoView = bodyView.findViewById(R.id.more_info);
        TextInputLayoutCompat.fromTextInputEditText(moreInfoView).setVisibility(View.GONE);
        TextInputTextView modeView = bodyView.findViewById(R.id.file_mode);
        TextInputTextView ownerView = bodyView.findViewById(R.id.owner_id);
        TextInputTextView groupView = bodyView.findViewById(R.id.group_id);
        TextInputTextView selinuxContextView = bodyView.findViewById(R.id.selinux_context);

        // TODO: 16/11/22 Handle open with
        openWithLayout.setVisibility(View.GONE);

        // Live data
        viewModel.getFilePropertiesLiveData().observe(getViewLifecycleOwner(), fileProperties -> {
            boolean noInit = mFileProperties == null;
            if (noInit || mFileProperties.isDirectory != fileProperties.isDirectory) {
                if (fileProperties.isDirectory) {
                    iconView.setImageResource(R.drawable.ic_folder);
                }
            }
            if (noInit || mFileProperties.isSymlink != fileProperties.isSymlink) {
                symbolicLinkIconView.setVisibility(fileProperties.isSymlink ? View.VISIBLE : View.GONE);
            }
            if (noInit || !Objects.equals(mFileProperties.name, fileProperties.name)) {
                nameView.setText(fileProperties.name);
            }
            if (noInit || !Objects.equals(mFileProperties.readablePath, fileProperties.readablePath)) {
                pathView.setText(fileProperties.readablePath);
            }
            if (noInit || !Objects.equals(mFileProperties.targetPath, fileProperties.targetPath)) {
                if (fileProperties.targetPath != null) {
                    targetPathView.setText(fileProperties.targetPath);
                } else {
                    TextInputLayoutCompat.fromTextInputEditText(targetPathView).setVisibility(View.GONE);
                }
            }
            if (noInit || mFileProperties.size != fileProperties.size
                    || mFileProperties.lastModified != fileProperties.lastModified) {
                if (fileProperties.size != -1) {
                    summaryView.setText(String.format(Locale.getDefault(), "%s • %s",
                            DateUtils.formatDateTime(requireContext(), fileProperties.lastModified),
                            Formatter.formatShortFileSize(requireContext(), fileProperties.size)));
                    sizeView.setText(String.format(Locale.getDefault(), "%s (%s bytes)",
                            Formatter.formatShortFileSize(requireContext(), fileProperties.size), fileProperties.size));
                }
            }
            if (noInit || mFileProperties.lastModified != fileProperties.lastModified) {
                dateModifiedView.setText(DateUtils.formatDateTime(requireContext(), fileProperties.lastModified));
            }
            if (noInit || mFileProperties.creationTime != fileProperties.creationTime) {
                dateCreatedView.setText(fileProperties.creationTime > 0 ? DateUtils.formatDateTime(requireContext(),
                        fileProperties.creationTime) : "--");
            }
            if (noInit || mFileProperties.lastAccess != fileProperties.lastAccess) {
                dateAccessedView.setText(fileProperties.lastAccess > 0 ? DateUtils.formatDateTime(requireContext(),
                        fileProperties.lastAccess) : "--");
            }
            if (noInit || mFileProperties.canRead != fileProperties.canRead) {
                // TODO: 28/6/23 Set last access
                dateAccessedLayout.setEndIconVisible(false);
            }
            if (noInit || mFileProperties.canWrite != fileProperties.canWrite) {
                dateModifiedLayout.setEndIconVisible(fileProperties.canWrite);
            }
            if (noInit || mFileProperties.mode != fileProperties.mode) {
                modeView.setText(fileProperties.mode != 0 ? getFormattedMode(fileProperties.mode) : "--");
            }
            if (noInit || mFileProperties.uidGidPair != fileProperties.uidGidPair) {
                if (fileProperties.uidGidPair == null) {
                    ownerView.setText("--");
                    groupView.setText("--");
                }
            }
            if (noInit || !Objects.equals(mFileProperties.context, fileProperties.context)) {
                selinuxContextView.setText(fileProperties.context != null ? fileProperties.context : "--");
            }
            mFileProperties = new FileProperties(fileProperties);
            // Load others
            if (fileProperties.size == -1) {
                viewModel.loadFileSize(fileProperties);
            }
            if (fileProperties.uidGidPair != null) {
                viewModel.loadOwnerInfo(fileProperties.uidGidPair.uid);
                viewModel.loadGroupInfo(fileProperties.uidGidPair.gid);
            }
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
                    TextInputLayoutCompat.fromTextInputEditText(moreInfoView).setVisibility(View.VISIBLE);
                    moreInfoView.setText(message);
                }
            }
        });
        viewModel.getOwnerLiveData().observe(getViewLifecycleOwner(), ownerName -> {
            assert mFileProperties != null;
            assert mFileProperties.uidGidPair != null;
            ownerView.setText(String.format(Locale.ROOT, "%s (%d)", ownerName, mFileProperties.uidGidPair.uid));
        });
        viewModel.getGroupLiveData().observe(getViewLifecycleOwner(), groupName -> {
            assert mFileProperties != null;
            assert mFileProperties.uidGidPair != null;
            groupView.setText(String.format(Locale.ROOT, "%s (%d)", groupName, mFileProperties.uidGidPair.gid));
        });

        // Load live data
        viewModel.loadFileProperties(path);
        viewModel.loadFmItem(path);
    }

    @SuppressWarnings("OctalInteger")
    @NonNull
    private String getFormattedMode(int mode) {
        // Ref: https://man7.org/linux/man-pages/man7/inode.7.html
        String s = getSingleMode(mode >> 6, (mode & 04000) != 0, "s") +
                getSingleMode(mode >> 3, (mode & 02000) != 0, "s") +
                getSingleMode(mode, (mode & 01000) != 0, "t");
        return String.format(Locale.ROOT, "%s (%o)", s, mode & 07777);
    }

    @SuppressWarnings("OctalInteger")
    @NonNull
    private String getSingleMode(int mode, boolean special, String specialChar) {
        boolean canExecute = (mode & 01) != 0;
        String execMode;
        if (canExecute) {
            execMode = special ? specialChar.toLowerCase(Locale.ROOT) : "x";
        } else if (special) {
            execMode = specialChar.toUpperCase(Locale.ROOT);
        } else execMode = "-";
        return ((mode & 04) != 0 ? "r" : "-") +
                ((mode & 02) != 0 ? "w" : "-") +
                execMode;
    }

    public static class FilePropertiesViewModel extends AndroidViewModel {
        private final MutableLiveData<FileProperties> mFilePropertiesLiveData = new MutableLiveData<>();
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

        public void setModificationTime(@NonNull FileProperties properties, long time) {
            ThreadUtils.postOnBackgroundThread(() -> {
                if (properties.path.setLastModified(time)) {
                    FileProperties newProperties = new FileProperties(properties);
                    newProperties.lastModified = newProperties.path.lastModified();
                    mFilePropertiesLiveData.postValue(newProperties);
                }
            });
        }

        public void loadFileProperties(@NonNull Path path) {
            ThreadUtils.postOnBackgroundThread(() -> {
                FileProperties properties = new FileProperties();
                Path[] children = path.listFiles();
                int count = children.length;
                int folderCount = 0;
                for (Path child : children) {
                    if (child.isDirectory()) {
                        ++folderCount;
                    }
                }
                properties.path = path;
                properties.name = path.getName();
                properties.readablePath = FmUtils.getDisplayablePath(path);
                properties.folderCount = folderCount;
                properties.fileCount = count - folderCount;
                properties.isDirectory = path.isDirectory();
                properties.isSymlink = path.isSymbolicLink();
                properties.canRead = path.canRead();
                properties.canWrite = path.canWrite();
                properties.lastAccess = path.lastAccess();
                properties.lastModified = path.lastModified();
                properties.creationTime = path.creationTime();
                properties.mode = path.getMode();
                properties.uidGidPair = path.getUidGid();
                properties.context = path.getSelinuxContext();
                if (properties.isSymlink) {
                    try {
                        properties.targetPath = path.getRealFilePath();
                    } catch (IOException ignore) {
                    }
                }
                mFilePropertiesLiveData.postValue(properties);
            });
        }

        public void loadFileSize(@NonNull FileProperties properties) {
            sizeResult = ThreadUtils.postOnBackgroundThread(() -> {
                FileProperties newProperties = new FileProperties(properties);
                newProperties.size = Paths.size(newProperties.path);
                mFilePropertiesLiveData.postValue(newProperties);
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

        public LiveData<FileProperties> getFilePropertiesLiveData() {
            return mFilePropertiesLiveData;
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

    private static class FileProperties {
        public Path path;
        public String name;
        public String readablePath;
        public int folderCount;
        public int fileCount;
        public boolean isDirectory;
        public boolean isSymlink;
        public boolean canRead;
        public boolean canWrite;
        public long size = -1;
        public long lastAccess;
        public long lastModified;
        public long creationTime;
        public int mode;
        @Nullable
        public UidGidPair uidGidPair;
        @Nullable
        public String context;
        @Nullable
        public String targetPath;

        public FileProperties() {
        }

        public FileProperties(@NonNull FileProperties fileProperties) {
            path = fileProperties.path;
            name = fileProperties.name;
            readablePath = fileProperties.readablePath;
            folderCount = fileProperties.folderCount;
            fileCount = fileProperties.fileCount;
            isDirectory = fileProperties.isDirectory;
            isSymlink = fileProperties.isSymlink;
            canRead = fileProperties.canRead;
            canWrite = fileProperties.canWrite;
            size = fileProperties.size;
            lastAccess = fileProperties.lastAccess;
            lastModified = fileProperties.lastModified;
            creationTime = fileProperties.creationTime;
            mode = fileProperties.mode;
            uidGidPair = fileProperties.uidGidPair;
            context = fileProperties.context;
            targetPath = fileProperties.targetPath;
        }
    }
}
