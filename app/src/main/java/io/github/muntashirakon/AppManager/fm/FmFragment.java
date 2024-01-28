// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static io.github.muntashirakon.AppManager.fm.FmTasks.FmTask.TYPE_CUT;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.core.provider.DocumentsContractCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.dialogs.FilePropertiesDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewFileDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewFolderDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewSymbolicLinkDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.RenameDialogFragment;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.FloatingActionButtonGroup;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class FmFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener,
        SpeedDialView.OnActionSelectedListener, MultiSelectionActionsView.OnItemSelectedListener {
    public static final String TAG = FmFragment.class.getSimpleName();

    public static final String ARG_URI = "uri";
    public static final String ARG_OPTIONS = "opt";
    public static final String ARG_POSITION = "pos";

    @NonNull
    public static FmFragment getNewInstance(@NonNull FmActivity.Options options, @Nullable Uri initUri,
                                            @Nullable Integer position) {
        if (!options.isVfs && initUri != null) {
            throw new IllegalArgumentException("initUri can only be set when the file system is virtual.");
        }
        FmFragment fragment = new FmFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_OPTIONS, options);
        args.putParcelable(ARG_URI, initUri);
        if (position != null) {
            args.putInt(ARG_POSITION, position);
        }
        fragment.setArguments(args);
        return fragment;
    }

    private FmViewModel mModel;
    @Nullable
    private RecyclerView mRecyclerView;
    private LinearLayoutCompat mEmptyView;
    private ImageView mEmptyViewIcon;
    private TextView mEmptyViewTitle;
    private TextView mEmptyViewDetails;
    @Nullable
    private FmAdapter mAdapter;
    @Nullable
    private SwipeRefreshLayout mSwipeRefresh;
    @Nullable
    private MultiSelectionView mMultiSelectionView;
    private FmPathListAdapter mPathListAdapter;
    private FmActivity mActivity;

    @Nullable
    private FolderShortInfo mFolderShortInfo;

    private final OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mAdapter != null && mMultiSelectionView != null && mAdapter.isInSelectionMode()) {
                mMultiSelectionView.cancel();
                return;
            }
            if (mPathListAdapter != null && mPathListAdapter.getCurrentPosition() > 0) {
                mModel.loadFiles(mPathListAdapter.calculateUri(mPathListAdapter.getCurrentPosition() - 1));
                return;
            }
            setEnabled(false);
            requireActivity().onBackPressed();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mModel = new ViewModelProvider(this).get(FmViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FmActivity.Options options = null;
        Uri uri = null;
        AtomicInteger scrollPosition = new AtomicInteger(RecyclerView.NO_POSITION);
        if (savedInstanceState != null) {
            uri = BundleCompat.getParcelable(savedInstanceState, ARG_URI, Uri.class);
            options = BundleCompat.getParcelable(savedInstanceState, ARG_OPTIONS, FmActivity.Options.class);
            scrollPosition.set(savedInstanceState.getInt(ARG_POSITION, RecyclerView.NO_POSITION));
        }
        if (uri == null) {
            uri = BundleCompat.getParcelable(requireArguments(), ARG_URI, Uri.class);
        }
        if (options == null) {
            options = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_OPTIONS, FmActivity.Options.class));
            if (requireArguments().containsKey(ARG_POSITION)) {
                scrollPosition.set(requireArguments().getInt(ARG_POSITION, RecyclerView.NO_POSITION));
            }
        }
        mActivity = (FmActivity) requireActivity();
        // Set title and subtitle
        ActionBar actionBar = mActivity.getSupportActionBar();
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        UiUtils.applyWindowInsetsAsPadding(view.findViewById(R.id.path_container), false, true);
        RecyclerView pathListView = view.findViewById(R.id.path_list);
        pathListView.setLayoutManager(new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        mPathListAdapter = new FmPathListAdapter(mModel);
        mPathListAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                pathListView.setSelection(mPathListAdapter.getCurrentPosition());
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }
        });
        pathListView.setAdapter(mPathListAdapter);
        MaterialButton pathEditButton = view.findViewById(R.id.uri_edit);
        pathEditButton.setOnClickListener(v -> {
            Uri currentUri = mModel.getCurrentUri();
            String path = currentUri != null ? FmUtils.getDisplayablePath(currentUri) : null;
            new TextInputDialogBuilder(mActivity, null)
                    .setTitle(R.string.go_to_path)
                    .setInputText(path)
                    .setPositiveButton(R.string.go, (dialog, which, inputText, isChecked) -> {
                        if (TextUtils.isEmpty(inputText)) {
                            return;
                        }
                        goToRawPath(inputText.toString().trim());
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        });
        FloatingActionButtonGroup fabGroup = view.findViewById(R.id.fab);
        fabGroup.inflate(R.menu.fragment_fm_speed_dial);
        fabGroup.setOnActionSelectedListener(this);
        UiUtils.applyWindowInsetsAsMargin(view.findViewById(R.id.fab_holder));
        mEmptyView = view.findViewById(android.R.id.empty);
        mEmptyViewIcon = view.findViewById(R.id.icon);
        mEmptyViewTitle = view.findViewById(R.id.title);
        mEmptyViewDetails = view.findViewById(R.id.message);
        mRecyclerView = view.findViewById(R.id.list_item);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mAdapter = new FmAdapter(mModel, mActivity);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (scrollPosition.get() != RecyclerView.NO_POSITION) {
                    // Update scroll position
                    mRecyclerView.setSelection(scrollPosition.get());
                    scrollPosition.set(RecyclerView.NO_POSITION);
                } else {
                    mRecyclerView.setSelection(mModel.getCurrentScrollPosition());
                }
            }
        });
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                if (mFolderShortInfo == null) {
                    return;
                }
                if (dy < 0 && mFolderShortInfo.canWrite && !fabGroup.isShown()) {
                    fabGroup.show();
                } else if (dy > 0 && fabGroup.isShown()) {
                    fabGroup.hide();
                }
            }
        });
        mMultiSelectionView = view.findViewById(R.id.selection_view);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.updateCounter(true);
        BatchOpsHandler batchOpsHandler = new BatchOpsHandler(mMultiSelectionView);
        mMultiSelectionView.setOnSelectionChangeListener(batchOpsHandler);
        // Set observer
        mModel.getLastUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            // force disable empty view
            if (mEmptyView.isShown()) {
                mEmptyView.setVisibility(View.GONE);
            }
            // Reset subtitle
            if (actionBar != null) {
                actionBar.setSubtitle(R.string.loading);
            }
            if (uri1 == null) {
                return;
            }
            if (mRecyclerView != null) {
                View v = mRecyclerView.getChildAt(0);
                if (v != null) {
                    mModel.setScrollPosition(uri1, mRecyclerView.getChildAdapterPosition(v));
                }
                mAdapter.setFmList(Collections.emptyList());
            }
            if (mMultiSelectionView.isShown()) {
                mMultiSelectionView.cancel();
            }
        });
        mModel.getFmItemsLiveData().observe(getViewLifecycleOwner(), fmItems -> {
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(false);
            }
            mAdapter.setFmList(fmItems);
            if (fmItems.isEmpty()) {
                handleEmptyView(R.drawable.ic_file, getString(R.string.empty_folder), null);
            }
        });
        mModel.getFmErrorLiveData().observe(getViewLifecycleOwner(), throwable -> {
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(false);
            }
            handleEmptyView(io.github.muntashirakon.ui.R.drawable.ic_caution, throwable.getMessage(), throwable);
        });
        mModel.getUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            FmActivity.Options options1 = mModel.getOptions();
            String alternativeRootName = options1.isVfs ? options1.uri.getLastPathSegment() : null;
            if (actionBar != null) {
                String title = uri1.getLastPathSegment();
                if (TextUtils.isEmpty(title)) {
                    title = alternativeRootName != null ? alternativeRootName : "Root";
                }
                actionBar.setTitle(title);
            }
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(true);
            }
            mPathListAdapter.setCurrentUri(uri1);
            mPathListAdapter.setAlternativeRootName(alternativeRootName);
        });
        mModel.getFolderShortInfoLiveData().observe(getViewLifecycleOwner(), folderShortInfo -> {
            mFolderShortInfo = folderShortInfo;
            if (actionBar == null) {
                return;
            }
            StringBuilder subtitle = new StringBuilder();
            // 1. Size
            if (folderShortInfo.size > 0) {
                subtitle.append(Formatter.formatShortFileSize(requireContext(), folderShortInfo.size)).append(" • ");
            }
            // 2. Folders and files
            if (folderShortInfo.folderCount > 0 && folderShortInfo.fileCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.folder_count, folderShortInfo.folderCount,
                                folderShortInfo.folderCount))
                        .append(", ")
                        .append(getResources().getQuantityString(R.plurals.file_count, folderShortInfo.fileCount,
                                folderShortInfo.fileCount));
            } else if (folderShortInfo.folderCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.folder_count, folderShortInfo.folderCount,
                        folderShortInfo.folderCount));
            } else if (folderShortInfo.fileCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.file_count, folderShortInfo.fileCount,
                        folderShortInfo.fileCount));
            } else {
                subtitle.append(getString(R.string.empty_folder));
            }
            // 3. Mode
            if (folderShortInfo.canRead || folderShortInfo.canWrite) {
                subtitle.append(" • ");
                if (folderShortInfo.canRead) {
                    subtitle.append("R");
                }
                if (folderShortInfo.canWrite) {
                    subtitle.append("W");
                }
            }
            if (!folderShortInfo.canWrite) {
                if (fabGroup.isShown()) {
                    fabGroup.hide();
                }
            } else {
                if (!fabGroup.isShown()) {
                    fabGroup.show();
                }
            }
            actionBar.setSubtitle(subtitle);
        });
        mModel.getDisplayPropertiesLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            FilePropertiesDialogFragment dialogFragment = FilePropertiesDialogFragment.getInstance(uri1);
            dialogFragment.show(mActivity.getSupportFragmentManager(), FilePropertiesDialogFragment.TAG);
        });
        mModel.getShortcutCreatorLiveData().observe(getViewLifecycleOwner(), pathBitmapPair -> {
            Path path = pathBitmapPair.first;
            Bitmap icon = pathBitmapPair.second;
            FmShortcutInfo shortcutInfo = new FmShortcutInfo(path, null);
            if (icon != null) {
                shortcutInfo.setIcon(icon);
            } else {
                Drawable drawable = Objects.requireNonNull(ContextCompat.getDrawable(requireContext(),
                        path.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file));
                shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(drawable));
            }
            CreateShortcutDialogFragment dialog = CreateShortcutDialogFragment.getInstance(shortcutInfo);
            dialog.show(getChildFragmentManager(), CreateShortcutDialogFragment.TAG);
        });
        mModel.getSharableItemsLiveData().observe(getViewLifecycleOwner(), sharableItems ->
                mActivity.startActivity(sharableItems.toSharableIntent()));
        mModel.setOptions(options, uri);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mModel != null && mRecyclerView != null) {
            View v = mRecyclerView.getChildAt(0);
            if (v != null) {
                Prefs.FileManager.setLastOpenedPath(mModel.getOptions(), mModel.getCurrentUri(), mRecyclerView.getChildAdapterPosition(v));
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mModel != null) {
            outState.putParcelable(ARG_URI, mModel.getCurrentUri());
            outState.putParcelable(ARG_OPTIONS, mModel.getOptions());
        }
        if (mRecyclerView != null) {
            View v = mRecyclerView.getChildAt(0);
            if (v != null) {
                outState.putInt(ARG_POSITION, mRecyclerView.getChildAdapterPosition(v));
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.activity_fm_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem pasteMenu = menu.findItem(R.id.action_paste);
        if (pasteMenu != null) {
            FmTasks.FmTask fmTask = FmTasks.getInstance().peek();
            pasteMenu.setEnabled(mFolderShortInfo != null && fmTask != null && mFolderShortInfo.canWrite && fmTask.canPaste());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            mModel.reload();
            return true;
        } else if (id == R.id.action_shortcut) {
            Uri uri = mPathListAdapter.getCurrentUri();
            if (uri != null) {
                mModel.createShortcut(uri);
            }
            return true;
        } else if (id == R.id.action_storage) {
            ThreadUtils.postOnBackgroundThread(() -> {
                ArrayMap<String, Uri> storageLocations = StorageUtils.getAllStorageLocations(mActivity);
                if (storageLocations.size() == 0) {
                    mActivity.runOnUiThread(() -> {
                        if (isDetached()) return;
                        new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.storage)
                                .setMessage(R.string.no_volumes_found)
                                .setNegativeButton(R.string.ok, null)
                                .show();
                    });
                    return;
                }
                Uri[] backupVolumes = new Uri[storageLocations.size()];
                CharSequence[] backupVolumesStr = new CharSequence[storageLocations.size()];
                for (int i = 0; i < storageLocations.size(); ++i) {
                    backupVolumes[i] = storageLocations.valueAt(i);
                    backupVolumesStr[i] = new SpannableStringBuilder(storageLocations.keyAt(i)).append("\n")
                            .append(getSecondaryText(mActivity, getSmallerText(backupVolumes[i].getPath())));
                }
                mActivity.runOnUiThread(() -> {
                    if (isDetached()) return;
                    new SearchableItemsDialogBuilder<>(mActivity, backupVolumesStr)
                            .setTitle(R.string.storage)
                            .setOnItemClickListener((dialog, which, item1) -> {
                                mModel.loadFiles(backupVolumes[which]);
                                dialog.dismiss();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            });
            return true;
        } else if (id == R.id.action_list_options) {
            FmListOptions listOptions = new FmListOptions();
            listOptions.setListOptionActions(mModel);
            listOptions.show(getChildFragmentManager(), FmListOptions.TAG);
            return true;
        } else if (id == R.id.action_paste) {
            FmTasks.FmTask task = FmTasks.getInstance().dequeue();
            if (task != null) {
                startBatchPaste(task);
            }
            return true;
        } else if (id == R.id.action_new_window) {
            Intent intent = new Intent(mActivity, FmActivity.class);
            if (!mModel.getOptions().isVfs) {
                intent.setDataAndType(mModel.getCurrentUri(), DocumentsContract.Document.MIME_TYPE_DIR);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = SettingsActivity.getIntent(requireContext(), "files_prefs");
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onActionSelected(@NonNull SpeedDialActionItem actionItem) {
        int id = actionItem.getId();
        if (id == R.id.action_file) {
            NewFileDialogFragment dialog = NewFileDialogFragment.getInstance(this::createNewFile);
            dialog.show(getChildFragmentManager(), NewFileDialogFragment.TAG);
        } else if (id == R.id.action_folder) {
            NewFolderDialogFragment dialog = NewFolderDialogFragment.getInstance(this::createNewFolder);
            dialog.show(getChildFragmentManager(), NewFolderDialogFragment.TAG);
        } else if (id == R.id.action_symbolic_link) {
            Uri uri = mPathListAdapter.getCurrentUri();
            if (uri == null) {
                return false;
            }
            Path path = Paths.get(uri);
            if (path.getFile() == null) {
                UIUtils.displayLongToast(R.string.symbolic_link_not_supported);
                return false;
            }
            NewSymbolicLinkDialogFragment dialog = NewSymbolicLinkDialogFragment.getInstance(this::createNewSymbolicLink);
            dialog.show(getChildFragmentManager(), NewSymbolicLinkDialogFragment.TAG);
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        List<Path> selectedFiles = mModel.getSelectedItems();
        if (selectedFiles.size() == 0) {
            // Do nothing on empty list
            return false;
        }
        if (id == R.id.action_share) {
            mModel.shareFiles(selectedFiles);
        } else if (id == R.id.action_rename) {
            RenameDialogFragment dialog = RenameDialogFragment.getInstance(null, (prefix, extension) ->
                    startBatchRenaming(selectedFiles, prefix, extension));
            dialog.show(getChildFragmentManager(), RenameDialogFragment.TAG);
        } else if (id == R.id.action_delete) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.title_confirm_deletion)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.cancel, null)
                    .setNegativeButton(R.string.confirm_file_deletion, (dialog, which) -> startBatchDeletion(selectedFiles))
                    .show();
        } else if (id == R.id.action_cut) {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(TYPE_CUT, selectedFiles);
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
        } else if (id == R.id.action_copy) {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(FmTasks.FmTask.TYPE_COPY, selectedFiles);
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
        } else if (id == R.id.action_copy_path) {
            List<String> paths = new ArrayList<>(selectedFiles.size());
            for (Path path : selectedFiles) {
                paths.add(FmUtils.getDisplayablePath(path));
            }
            Utils.copyToClipboard(mActivity, "Paths", TextUtils.join("\n", paths));
        }
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // TODO: 11/7/21
        return false;
    }

    @Override
    public void onRefresh() {
        if (mModel != null) mModel.reload();
    }

    private void goToRawPath(@NonNull String p) {
        Uri uncheckedUri = Uri.parse(p);
        if (uncheckedUri.getScheme() != null) {
            Uri checkedUri = FmUtils.sanitizeContentInput(uncheckedUri);
            if (checkedUri != null) {
                // Valid path
                mModel.loadFiles(checkedUri);
            } // else bad URI
            return;
        }
        // Bad Uri, consider it to be a file://
        if (p.startsWith(File.separator)) {
            // absolute file
            Uri checkedUri = FmUtils.sanitizeContentInput(uncheckedUri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build());
            if (checkedUri != null) {
                mModel.loadFiles(checkedUri);
            } // else bad file
            return;
        }
        // Relative path
        String goodPath = Paths.sanitize(p, false);
        if (goodPath == null || goodPath.equals(File.separator)) {
            // No relative path means current path which is already loaded
            return;
        }
        Uri currentUri = mModel.getCurrentUri();
        if (DocumentsContractCompat.isDocumentUri(requireContext(), currentUri)) {
            List<String> pathSegments = currentUri.getPathSegments();
            if (pathSegments.size() == 4) {
                // For a tree URI, the 3rd index is the path
                String lastPathSegment = pathSegments.get(3) + File.separator + goodPath;
                Uri.Builder b = new Uri.Builder()
                        .scheme(currentUri.getScheme())
                        .authority(currentUri.getAuthority())
                        .appendPath(pathSegments.get(0))
                        .appendPath(pathSegments.get(1))
                        .appendPath(pathSegments.get(2))
                        .appendPath(lastPathSegment);
                mModel.loadFiles(b.build());
            }
            // Other document Uris don't support navigation nor do they support folders/trees
            return;
        }
        // For others, simply append path segments at the end
        @SuppressWarnings("SuspiciousRegexArgument") // We aren't on Windows
        String[] segments = goodPath.split(File.separator);
        Uri.Builder b = currentUri.buildUpon();
        for (String segment : segments) {
            b.appendPath(segment);
        }
        mModel.loadFiles(b.build());
    }

    private void handleEmptyView(@DrawableRes int icon, @Nullable CharSequence title, @Nullable Throwable th) {
        if (!mEmptyView.isShown()) {
            mEmptyView.setVisibility(View.VISIBLE);
        }
        mEmptyViewIcon.setImageResource(icon);
        mEmptyViewTitle.setText(title);
        if (th == null) {
            mEmptyViewDetails.setVisibility(View.GONE);
            return;
        }
        // Only log the first three lines
        StackTraceElement[] arr = th.getStackTrace();
        StringBuilder report = new StringBuilder(th + "\n");
        int i = 0;
        for (StackTraceElement traceElement : arr) {
            if (i == 3) break;
            report.append("    at ").append(traceElement.toString()).append("\n");
            ++i;
        }
        Throwable cause = th;
        while ((cause = cause.getCause()) != null) {
            report.append(" Caused by: ").append(cause).append("\n");
            arr = cause.getStackTrace();
            i = 0;
            for (StackTraceElement stackTraceElement : arr) {
                if (i == 3) break;
                report.append("   at ").append(stackTraceElement.toString()).append("\n");
                ++i;
            }
        }
        mEmptyViewDetails.setVisibility(View.VISIBLE);
        mEmptyViewDetails.setText(report);
    }

    private void createNewFolder(String name) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path path = Paths.get(uri);
        String displayName = findNextBestDisplayName(path, name, null);
        try {
            Path newDir = path.createNewDirectory(displayName);
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(newDir.getName());
        } catch (IOException e) {
            e.printStackTrace();
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void createNewFile(String prefix, @Nullable String extension, String template) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path path = Paths.get(uri);
        String displayName = findNextBestDisplayName(path, prefix, extension);
        try {
            Path newFile = path.createNewFile(displayName, null);
            FileUtils.copyFromAsset(requireContext(), "blanks/" + template, newFile);
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(newFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void createNewSymbolicLink(String prefix, @Nullable String extension, String targetPath) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path basePath = Paths.get(uri);
        String displayName = findNextBestDisplayName(basePath, prefix, extension);
        Path sourcePath = Paths.build(basePath, displayName);
        if (sourcePath != null && sourcePath.createNewSymbolicLink(targetPath)) {
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(sourcePath.getName());
        } else {
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void startBatchDeletion(@NonNull List<Path> paths) {
        // TODO: 27/6/23 Ideally, these should be done in a bound service
        AtomicReference<Future<?>> deletionThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, paths.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (deletionThread.get() != null) {
                        deletionThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        deletionThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(paths.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                int i = 1;
                for (Path path : paths) {
                    // Update label
                    TextView l = labelRef.get();
                    if (l != null) {
                        ThreadUtils.postOnMainThread(() -> l.setText(path.getName()));
                    }
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    // Sleep, delete, progress
                    SystemClock.sleep(2_000);
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    path.delete();
                    TextView c = counterRef.get();
                    int finalI = i;
                    ThreadUtils.postOnMainThread(() -> {
                        if (c != null) {
                            c.setText(String.format(Locale.getDefault(), "%d/%d", finalI, paths.size()));
                        }
                        if (p != null) {
                            p.setProgress(finalI);
                        }
                    });
                    ++i;
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                }
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        UIUtils.displayShortToast(R.string.deleted_successfully);
                        mModel.reload();
                    });
                }
            }
        }));
    }

    private void startBatchRenaming(List<Path> paths, String prefix, @Nullable String extension) {
        AtomicReference<Future<?>> renameThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, paths.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rename)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (renameThread.get() != null) {
                        renameThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        renameThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(paths.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                int i = 1;
                for (Path path : paths) {
                    // Update label
                    TextView l = labelRef.get();
                    if (l != null) {
                        ThreadUtils.postOnMainThread(() -> l.setText(path.getName()));
                    }
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    // Sleep, rename, progress
                    SystemClock.sleep(2_000);
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    Path basePath = path.getParent();
                    if (basePath != null) {
                        String displayName = findNextBestDisplayName(basePath, prefix, extension, i);
                        path.renameTo(displayName);
                    }
                    TextView c = counterRef.get();
                    int finalI = i;
                    ThreadUtils.postOnMainThread(() -> {
                        if (c != null) {
                            c.setText(String.format(Locale.getDefault(), "%d/%d", finalI, paths.size()));
                        }
                        if (p != null) {
                            p.setProgress(finalI);
                        }
                    });
                    ++i;
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                }
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        UIUtils.displayShortToast(R.string.renamed_successfully);
                        mModel.reload();
                    });
                }
            }
        }));
    }

    private void startBatchPaste(@NonNull FmTasks.FmTask task) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        AtomicReference<Future<?>> pasteThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, task.files.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.paste)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (pasteThread.get() != null) {
                        pasteThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        pasteThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            Path targetPath = Paths.get(uri);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(task.files.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                int i = 1;
                for (Path sourcePath : task.files) {
                    // Update label
                    TextView l = labelRef.get();
                    if (l != null) {
                        ThreadUtils.postOnMainThread(() -> l.setText(sourcePath.getName()));
                    }
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    // Sleep, copy, progress
                    SystemClock.sleep(2_000);
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    if (!copy(sourcePath, targetPath)) {
                        // Failed to copy, abort
                        ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.failed_to_copy_specified_file, sourcePath.getName()))
                                .setPositiveButton(R.string.close, null)
                                .show());
                        return;
                    }
                    if (task.type == TYPE_CUT) {
                        if (!sourcePath.delete()) {
                            // Failed to move, abort
                            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.failed_to_delete_specified_file_after_copying, sourcePath.getName()))
                                    .setPositiveButton(R.string.close, null)
                                    .show());
                            return;
                        }
                    }
                    TextView c = counterRef.get();
                    int finalI = i;
                    ThreadUtils.postOnMainThread(() -> {
                        if (c != null) {
                            c.setText(String.format(Locale.getDefault(), "%d/%d", finalI, task.files.size()));
                        }
                        if (p != null) {
                            p.setProgress(finalI);
                        }
                    });
                    ++i;
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                }
                UIUtils.displayShortToast(task.type == TYPE_CUT ? R.string.moved_successfully : R.string.copied_successfully);
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        mModel.reload();
                    });
                }
            }
        }));
    }

    @WorkerThread
    private boolean copy(Path source, Path dest) {
        String name = source.getName();
        if (dest.hasFile(name)) {
            // Duplicate found. Ask user for what to do.
            CountDownLatch waitForUser = new CountDownLatch(1);
            AtomicReference<Boolean> keepBoth = new AtomicReference<>(null);
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.conflict_detected_while_copying)
                    .setMessage(getString(R.string.conflict_detected_while_copying_message, name))
                    .setCancelable(false)
                    .setOnDismissListener(dialog -> waitForUser.countDown())
                    .setPositiveButton(R.string.replace, (dialog, which) -> keepBoth.set(false))
                    .setNegativeButton(R.string.action_stop_service, (dialog, which) -> keepBoth.set(null))
                    .setNeutralButton(R.string.copy_keep_both_file, (dialog, which) -> keepBoth.set(true))
                    .show());
            try {
                waitForUser.await();
            } catch (InterruptedException ignore) {
            }
            if (keepBoth.get() == null) {
                // Abort copying
                return false;
            }
            if (keepBoth.get()) {
                // Keep both
                String prefix;
                String extension;
                if (!source.isDirectory()) {
                    prefix = Paths.trimPathExtension(name);
                    extension = Paths.getPathExtension(name);
                } else {
                    prefix = name;
                    extension = null;
                }
                String newName = findNextBestDisplayName(dest, prefix, extension);
                try {
                    Path newPath = source.isDirectory() ? dest.createNewDirectory(newName) : dest.createNewFile(newName, null);
                    // Need to create that path again
                    newPath.delete();
                    return source.copyTo(newPath) != null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                // Overwrite
                return source.copyTo(dest, true) != null;
            }
        }
        // Simply copy
        return source.copyTo(dest, false) != null;
    }

    private String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix, @Nullable String extension) {
        return findNextBestDisplayName(basePath, prefix, extension, 1);
    }

    private String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix, @Nullable String extension, int startIndex) {
        if (TextUtils.isEmpty(extension)) {
            extension = "";
        } else extension = "." + extension;
        String displayName = prefix + extension;
        int i = startIndex;
        // We need to find the next best file name if current exists
        while (basePath.hasFile(displayName)) {
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, extension);
            ++i;
        }
        return displayName;
    }

    private class BatchOpsHandler implements MultiSelectionView.OnSelectionChangeListener {
        private final MenuItem mShareMenu;
        private final MenuItem mRenameMenu;
        private final MenuItem mDeleteMenu;
        private final MenuItem mCutMenu;
        private final MenuItem mCopyMenu;
        private final MenuItem mCopyPathsMenu;

        public BatchOpsHandler(@NonNull MultiSelectionView multiSelectionView) {
            Menu menu = multiSelectionView.getMenu();
            mShareMenu = menu.findItem(R.id.action_share);
            mRenameMenu = menu.findItem(R.id.action_rename);
            mDeleteMenu = menu.findItem(R.id.action_delete);
            mCutMenu = menu.findItem(R.id.action_cut);
            mCopyMenu = menu.findItem(R.id.action_copy);
            mCopyPathsMenu = menu.findItem(R.id.action_copy_path);
        }

        @Override
        public boolean onSelectionChange(int selectionCount) {
            boolean nonZeroSelection = selectionCount > 0;
            boolean canRead = mFolderShortInfo != null && mFolderShortInfo.canRead;
            boolean canWrite = mFolderShortInfo != null && mFolderShortInfo.canWrite;
            mShareMenu.setEnabled(nonZeroSelection && canRead);
            mRenameMenu.setEnabled(nonZeroSelection && canWrite);
            mDeleteMenu.setEnabled(nonZeroSelection && canWrite);
            mCutMenu.setEnabled(nonZeroSelection && canWrite);
            mCopyMenu.setEnabled(nonZeroSelection && canRead);
            mCopyPathsMenu.setEnabled(nonZeroSelection);
            return false;
        }
    }
}
