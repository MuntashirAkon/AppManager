// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
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
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Groups;
import io.github.muntashirakon.AppManager.users.Owners;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.ExtendedFile;
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

    private ImageView mIconView;
    private ImageView mSymlinkIconView;
    private TextView mNameView;
    private TextView mSummaryView;
    private MaterialButton mMoreButton;
    private TextInputTextView mPathView;
    private TextInputTextView mTypeView;
    private TextInputTextView mTargetPathView;
    private TextInputLayout mTargetPathLayout;
    private TextInputTextView mOpenWithView;
    private TextInputLayout mOpenWithLayout;
    private TextInputTextView mSizeView;
    private TextInputTextView mDateCreatedView;
    private TextInputTextView mDateModifiedView;
    private TextInputLayout mDateModifiedLayout;
    private TextInputTextView mDateAccessedView;
    private TextInputLayout mDateAccessedLayout;
    private TextInputTextView mMoreInfoView;
    private TextInputTextView mModeView;
    private TextInputLayout mModeLayout;
    private TextInputTextView mOwnerView;
    private TextInputLayout mOwnerLayout;
    private TextInputTextView mGroupView;
    private TextInputLayout mGroupLayout;
    private TextInputTextView mSelinuxContextView;
    private TextInputLayout mSelinuxContextLayout;

    private FilePropertiesViewModel mViewModel;
    @Nullable
    private FileProperties mFileProperties;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_file_properties, container, false);
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        Path path = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri.class)));
        mViewModel = new ViewModelProvider(this).get(FilePropertiesViewModel.class);
        int endIconSizeSmall = UiUtils.dpToPx(requireContext(), 34 - 16);
        mIconView = bodyView.findViewById(android.R.id.icon);
        mSymlinkIconView = bodyView.findViewById(R.id.symolic_link_icon);
        mNameView = bodyView.findViewById(R.id.name);
        mSummaryView = bodyView.findViewById(R.id.summary);
        mMoreButton = bodyView.findViewById(R.id.more);
        mMoreButton.setVisibility(View.GONE);
        mPathView = bodyView.findViewById(R.id.path);
        mTypeView = bodyView.findViewById(R.id.type);
        mTargetPathView = bodyView.findViewById(R.id.target_file);
        mTargetPathLayout = TextInputLayoutCompat.fromTextInputEditText(mTargetPathView);
        TextInputLayoutCompat.setEndIconSize(mTargetPathLayout, endIconSizeSmall);
        mOpenWithView = bodyView.findViewById(R.id.open_with);
        mOpenWithLayout = TextInputLayoutCompat.fromTextInputEditText(mOpenWithView);
        TextInputLayoutCompat.setEndIconSize(mOpenWithLayout, endIconSizeSmall);
        // TODO: 16/11/22 Handle open with
        mOpenWithLayout.setVisibility(View.GONE);
        mSizeView = bodyView.findViewById(R.id.size);
        mDateCreatedView = bodyView.findViewById(R.id.date_created);
        mDateModifiedView = bodyView.findViewById(R.id.date_modified);
        mDateModifiedLayout = TextInputLayoutCompat.fromTextInputEditText(mDateModifiedView);
        TextInputLayoutCompat.setEndIconSize(mDateModifiedLayout, endIconSizeSmall);
        mDateModifiedLayout.setEndIconOnClickListener(v -> {
            if (mFileProperties != null) {
                mViewModel.setModificationTime(mFileProperties, System.currentTimeMillis());
            }
        });
        mDateAccessedView = bodyView.findViewById(R.id.date_accessed);
        mDateAccessedLayout = TextInputLayoutCompat.fromTextInputEditText(mDateAccessedView);
        mDateAccessedLayout.setEndIconOnClickListener(v -> {
            if (mFileProperties != null) {
                mViewModel.setLastAccessTime(mFileProperties, System.currentTimeMillis());
            }
        });
        TextInputLayoutCompat.setEndIconSize(mDateAccessedLayout, endIconSizeSmall);
        mMoreInfoView = bodyView.findViewById(R.id.more_info);
        TextInputLayoutCompat.fromTextInputEditText(mMoreInfoView).setVisibility(View.GONE);
        mModeView = bodyView.findViewById(R.id.file_mode);
        mModeLayout = TextInputLayoutCompat.fromTextInputEditText(mModeView);
        TextInputLayoutCompat.setEndIconSize(mModeLayout, endIconSizeSmall);
        // TODO: 28/6/23 Add option to edit mode
        mModeLayout.setEndIconVisible(false);
        mOwnerView = bodyView.findViewById(R.id.owner_id);
        mOwnerLayout = TextInputLayoutCompat.fromTextInputEditText(mOwnerView);
        TextInputLayoutCompat.setEndIconSize(mOwnerLayout, endIconSizeSmall);
        // TODO: 28/6/23 Add option to edit UID
        mOwnerLayout.setEndIconVisible(false);
        mGroupView = bodyView.findViewById(R.id.group_id);
        mGroupLayout = TextInputLayoutCompat.fromTextInputEditText(mGroupView);
        TextInputLayoutCompat.setEndIconSize(mGroupLayout, endIconSizeSmall);
        // TODO: 28/6/23 Add option to edit GID
        mGroupLayout.setEndIconVisible(false);
        mSelinuxContextView = bodyView.findViewById(R.id.selinux_context);
        mSelinuxContextLayout = TextInputLayoutCompat.fromTextInputEditText(mSelinuxContextView);
        TextInputLayoutCompat.setEndIconSize(mSelinuxContextLayout, endIconSizeSmall);
        mSelinuxContextLayout.setEndIconOnClickListener(v -> displaySeContextUpdater());

        // Live data
        mViewModel.getFilePropertiesLiveData().observe(getViewLifecycleOwner(), this::updateProperties);
        mViewModel.getFmItemLiveData().observe(getViewLifecycleOwner(), fmItem -> {
            ImageLoader.getInstance().displayImage(fmItem.tag, mIconView, new FmIconFetcher(fmItem));
            PathContentInfo contentInfo = fmItem.getContentInfo();
            if (contentInfo != null) {
                String name = contentInfo.getName();
                String mime = contentInfo.getMimeType();
                String message = contentInfo.getMessage();
                if (mime != null) {
                    mTypeView.setText(String.format(Locale.ROOT, "%s (%s)", name, mime));
                } else {
                    mTypeView.setText(name);
                }
                if (message != null) {
                    TextInputLayoutCompat.fromTextInputEditText(mMoreInfoView).setVisibility(View.VISIBLE);
                    mMoreInfoView.setText(message);
                }
            }
        });
        mViewModel.getOwnerLiveData().observe(getViewLifecycleOwner(), ownerName -> {
            assert mFileProperties != null;
            assert mFileProperties.uidGidPair != null;
            mOwnerView.setText(String.format(Locale.ROOT, "%s (%d)", ownerName, mFileProperties.uidGidPair.uid));
        });
        mViewModel.getGroupLiveData().observe(getViewLifecycleOwner(), groupName -> {
            assert mFileProperties != null;
            assert mFileProperties.uidGidPair != null;
            mGroupView.setText(String.format(Locale.ROOT, "%s (%d)", groupName, mFileProperties.uidGidPair.gid));
        });

        // Load live data
        mViewModel.loadFileProperties(path);
        mViewModel.loadFmItem(path);
    }

    private void updateProperties(@NonNull FileProperties fileProperties) {
        boolean noInit = mFileProperties == null;
        boolean uidGidChanged = noInit || mFileProperties.uidGidPair != fileProperties.uidGidPair;
        if (noInit || mFileProperties.isDirectory != fileProperties.isDirectory) {
            if (fileProperties.isDirectory) {
                mIconView.setImageResource(R.drawable.ic_folder);
            }
        }
        if (noInit || mFileProperties.isSymlink != fileProperties.isSymlink) {
            mSymlinkIconView.setVisibility(fileProperties.isSymlink ? View.VISIBLE : View.GONE);
        }
        if (noInit || !Objects.equals(mFileProperties.name, fileProperties.name)) {
            mNameView.setText(fileProperties.name);
        }
        if (noInit || !Objects.equals(mFileProperties.readablePath, fileProperties.readablePath)) {
            mPathView.setText(fileProperties.readablePath);
        }
        if (noInit || !Objects.equals(mFileProperties.targetPath, fileProperties.targetPath)) {
            if (fileProperties.targetPath != null) {
                mTargetPathView.setText(fileProperties.targetPath);
            } else {
                TextInputLayoutCompat.fromTextInputEditText(mTargetPathView).setVisibility(View.GONE);
            }
        }
        if (noInit || mFileProperties.size != fileProperties.size
                || mFileProperties.lastModified != fileProperties.lastModified) {
            if (fileProperties.size != -1) {
                mSummaryView.setText(String.format(Locale.getDefault(), "%s • %s",
                        DateUtils.formatDateTime(requireContext(), fileProperties.lastModified),
                        Formatter.formatShortFileSize(requireContext(), fileProperties.size)));
                mSizeView.setText(String.format(Locale.getDefault(), "%s (%s bytes)",
                        Formatter.formatShortFileSize(requireContext(), fileProperties.size), fileProperties.size));
            }
        }
        if (noInit || mFileProperties.lastModified != fileProperties.lastModified) {
            mDateModifiedView.setText(DateUtils.formatDateTime(requireContext(), fileProperties.lastModified));
        }
        if (noInit || mFileProperties.creationTime != fileProperties.creationTime) {
            mDateCreatedView.setText(fileProperties.creationTime > 0 ? DateUtils.formatDateTime(requireContext(),
                    fileProperties.creationTime) : "--");
        }
        if (noInit || mFileProperties.lastAccess != fileProperties.lastAccess) {
            mDateAccessedView.setText(fileProperties.lastAccess > 0 ? DateUtils.formatDateTime(requireContext(),
                    fileProperties.lastAccess) : "--");
        }
        if (noInit || mFileProperties.canRead != fileProperties.canRead) {
            mDateAccessedLayout.setEndIconVisible(fileProperties.canRead && fileProperties.isPhysicalFs);
        }
        if (noInit || mFileProperties.canWrite != fileProperties.canWrite) {
            mDateModifiedLayout.setEndIconVisible(fileProperties.canWrite && fileProperties.isPhysicalFs);
        }
        if (noInit || mFileProperties.mode != fileProperties.mode) {
            mModeView.setText(fileProperties.mode != 0 ? getFormattedMode(fileProperties.mode) : "--");
        }
        if (uidGidChanged) {
            if (fileProperties.uidGidPair == null) {
                mOwnerView.setText("--");
                mGroupView.setText("--");
            }
        }
        if (noInit || !Objects.equals(mFileProperties.context, fileProperties.context)) {
            mSelinuxContextView.setText(fileProperties.context != null ? fileProperties.context : "--");
        }
        mSelinuxContextLayout.setEndIconVisible(Ops.isRoot() && fileProperties.isPhysicalFs);
        mFileProperties = fileProperties;
        // Load others
        if (fileProperties.size == -1) {
            mViewModel.loadFileSize(fileProperties);
        }
        if (fileProperties.uidGidPair != null && uidGidChanged) {
            mViewModel.loadOwnerInfo(fileProperties.uidGidPair.uid);
            mViewModel.loadGroupInfo(fileProperties.uidGidPair.gid);
        }
    }

    private void displaySeContextUpdater() {
        assert mFileProperties != null;
        new TextInputDialogBuilder(requireContext(), null)
                .setTitle(R.string.title_change_selinux_context)
                .setInputText(mFileProperties.context)
                .setCheckboxLabel(R.string.apply_recursively)
                .setPositiveButton(R.string.ok, (dialog, which, context, recursive) -> {
                    if (!TextUtils.isEmpty(context)) {
                        mViewModel.setSeContext(mFileProperties, context.toString().trim(), recursive);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.restore, (dialog, which, context, recursive) ->
                        mViewModel.restorecon(mFileProperties, recursive))
                .show();
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

        public void restorecon(@NonNull FileProperties properties, boolean recursive) {
            ThreadUtils.postOnBackgroundThread(() -> {
                ExtendedFile file = properties.path.getFile();
                if (file == null) {
                    return;
                }
                if (recursive) {
                    restoreconRecursive(file);
                }
                if (file.restoreSelinuxContext()) {
                    FileProperties newProperties = new FileProperties(properties);
                    newProperties.context = newProperties.path.getSelinuxContext();
                    mFilePropertiesLiveData.postValue(newProperties);
                }
            });
        }

        public void setSeContext(@NonNull FileProperties properties, @NonNull String newContext, boolean recursive) {
            ThreadUtils.postOnBackgroundThread(() -> {
                ExtendedFile file = properties.path.getFile();
                if (file == null) {
                    return;
                }
                if (recursive) {
                    setSeContextRecursive(file, newContext);
                }
                if (file.setSelinuxContext(newContext)) {
                    FileProperties newProperties = new FileProperties(properties);
                    newProperties.context = newProperties.path.getSelinuxContext();
                    mFilePropertiesLiveData.postValue(newProperties);
                }
            });
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

        public void setLastAccessTime(@NonNull FileProperties properties, long time) {
            ThreadUtils.postOnBackgroundThread(() -> {
                if (properties.path.setLastAccess(time)) {
                    FileProperties newProperties = new FileProperties(properties);
                    newProperties.lastAccess = newProperties.path.lastAccess();
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
                properties.isPhysicalFs = path.getFile() != null;
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

        private boolean restoreconRecursive(@NonNull ExtendedFile dir) {
            if (dir.isSymlink()) {
                // Avoid following symbolic links
                return true;
            }
            ExtendedFile[] files = dir.listFiles();
            boolean success = true;
            if (files != null) {
                for (ExtendedFile file : files) {
                    if (file.isDirectory()) {
                        success &= restoreconRecursive(file);
                    }
                    if (!file.restoreSelinuxContext()) {
                        Log.w(TAG, "Failed to restorecon on " + file);
                        success = false;
                    }
                }
            }
            return success;
        }

        private boolean setSeContextRecursive(@NonNull ExtendedFile dir, @NonNull String newContext) {
            if (dir.isSymlink()) {
                // Avoid following symbolic links
                return true;
            }
            ExtendedFile[] files = dir.listFiles();
            boolean success = true;
            if (files != null) {
                for (ExtendedFile file : files) {
                    if (file.isDirectory()) {
                        success &= setSeContextRecursive(file, newContext);
                    }
                    if (!file.setSelinuxContext(newContext)) {
                        Log.w(TAG, "Failed to set SELinux context on " + file);
                        success = false;
                    }
                }
            }
            return success;
        }
    }

    private static class FileProperties {
        public Path path;
        public boolean isPhysicalFs;
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
            isPhysicalFs = fileProperties.isPhysicalFs;
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
