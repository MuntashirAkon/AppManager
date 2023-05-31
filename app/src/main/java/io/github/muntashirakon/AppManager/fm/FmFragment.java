// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.BundleCompat;
import io.github.muntashirakon.AppManager.shortcut.LauncherShortcuts;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class FmFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static final String TAG = FmFragment.class.getSimpleName();

    public static final String ARG_URI = "uri";
    public static final String ARG_OPTIONS = "opt";
    public static final String ARG_POSITION = "pos";

    @NonNull
    public static FmFragment getNewInstance(@NonNull FmActivity.Options options, @Nullable Uri initUri) {
        if (!options.isVfs && initUri != null) {
            throw new IllegalArgumentException("initUri can only be set when the file system is virtual.");
        }
        FmFragment fragment = new FmFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_OPTIONS, options);
        args.putParcelable(ARG_URI, initUri);
        fragment.setArguments(args);
        return fragment;
    }

    private FmViewModel model;
    @Nullable
    private RecyclerView recyclerView;
    @Nullable
    private FmAdapter adapter;
    @Nullable
    private SwipeRefreshLayout swipeRefresh;
    @Nullable
    private MultiSelectionView multiSelectionView;
    private FmPathListAdapter pathListAdapter;
    private FmActivity activity;

    private final OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (pathListAdapter != null && pathListAdapter.getCurrentPosition() > 0) {
                model.loadFiles(pathListAdapter.calculateUri(pathListAdapter.getCurrentPosition() - 1));
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
        model = new ViewModelProvider(this).get(FmViewModel.class);
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
        }
        activity = (FmActivity) requireActivity();
        // Set title and subtitle
        ActionBar actionBar = activity.getSupportActionBar();
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        RecyclerView pathListView = view.findViewById(R.id.path_list);
        pathListView.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        pathListAdapter = new FmPathListAdapter(model);
        pathListAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                LinearLayoutManager layoutManager = (LinearLayoutManager) pathListView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                layoutManager.scrollToPositionWithOffset(pathListAdapter.getCurrentPosition(), 0);
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }
        });
        pathListView.setAdapter(pathListAdapter);
        MaterialButton pathEditButton = view.findViewById(R.id.uri_edit);
        pathEditButton.setOnClickListener(v -> {
            String path = FmUtils.getDisplayablePath(model.getCurrentUri());
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.go_to_path)
                    .setInputText(path)
                    .setPositiveButton(R.string.go, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String p = inputText.toString();
                            model.loadFiles(p.startsWith(File.separator) ? Uri.fromFile(new File(p)) : Uri.parse(p));
                        }
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        });
        recyclerView = view.findViewById(R.id.list_item);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new FmAdapter(model, activity);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                if (scrollPosition.get() != RecyclerView.NO_POSITION) {
                    // Update scroll position
                    layoutManager.scrollToPositionWithOffset(scrollPosition.get(), 0);
                    scrollPosition.set(RecyclerView.NO_POSITION);
                } else {
                    layoutManager.scrollToPositionWithOffset(model.getCurrentScrollPosition(), 0);
                }
            }
        });
        recyclerView.setAdapter(adapter);
        multiSelectionView = view.findViewById(R.id.selection_view);
        multiSelectionView.hide();
        // Set observer
        model.getLastUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            if (uri1 == null) {
                return;
            }
            if (recyclerView != null) {
                View v = recyclerView.getChildAt(0);
                if (v != null) {
                    model.setScrollPosition(uri1, recyclerView.getChildAdapterPosition(v));
                }
                adapter.setFmList(Collections.emptyList());
            }
        });
        model.getFmItemsLiveData().observe(getViewLifecycleOwner(), fmItems -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            adapter.setFmList(fmItems);
        });
        model.getUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            if (actionBar != null) {
                String title = uri1.getLastPathSegment();
                if (TextUtils.isEmpty(title)) {
                    title = "Root"; // FIXME: 21/5/23 Use localisation?
                }
                actionBar.setTitle(title);
            }
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
            }
            pathListAdapter.setCurrentUri(uri1);
        });
        model.getFolderShortInfoLiveData().observe(getViewLifecycleOwner(), folderShortInfo -> {
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
            actionBar.setSubtitle(subtitle);
        });
        model.getDisplayPropertiesLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            FilePropertiesDialogFragment dialogFragment = FilePropertiesDialogFragment.getInstance(uri1);
            dialogFragment.show(activity.getSupportFragmentManager(), FilePropertiesDialogFragment.TAG);
        });
        model.getShortcutCreatorLiveData().observe(getViewLifecycleOwner(), pathBitmapPair -> {
            Path path = pathBitmapPair.first;
            Bitmap icon = pathBitmapPair.second;
            if (path.isDirectory()) {
                LauncherShortcuts.fm_createForFolder(activity, path.getName(), path.getUri());
            } else {
                LauncherShortcuts.fm_createForFile(activity, path.getName(), icon, path.getUri(), null);
            }
        });
        model.setOptions(options, uri);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (model != null) {
            outState.putParcelable(ARG_URI, model.getCurrentUri());
            outState.putParcelable(ARG_OPTIONS, model.getOptions());
        }
        if (recyclerView != null) {
            View v = recyclerView.getChildAt(0);
            if (v != null) {
                outState.putInt(ARG_POSITION, recyclerView.getChildAdapterPosition(v));
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
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            model.reload();
            return true;
        } else if (id == R.id.action_shortcut) {
            Uri uri = pathListAdapter.getCurrentUri();
            if (uri != null) {
                model.createShortcut(uri);
            }
            return true;
        } else if (id == R.id.action_storage) {
            ThreadUtils.postOnBackgroundThread(() -> {
                ArrayMap<String, Uri> storageLocations = StorageUtils.getAllStorageLocations(activity);
                if (storageLocations.size() == 0) {
                    activity.runOnUiThread(() -> {
                        if (isDetached()) return;
                        new MaterialAlertDialogBuilder(activity)
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
                            .append(getSecondaryText(activity, getSmallerText(backupVolumes[i].getPath())));
                }
                activity.runOnUiThread(() -> {
                    if (isDetached()) return;
                    new SearchableItemsDialogBuilder<>(activity, backupVolumesStr)
                            .setTitle(R.string.storage)
                            .setOnItemClickListener((dialog, which, item1) -> {
                                model.loadFiles(backupVolumes[which]);
                                dialog.dismiss();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            });
            return true;
        } else if (id == R.id.action_list_options) {
            FmListOptions listOptions = new FmListOptions();
            listOptions.setListOptionActions(model);
            listOptions.show(getChildFragmentManager(), FmListOptions.TAG);
            return true;
        } else if (id == R.id.action_new_window) {
            Intent intent = new Intent(activity, FmActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        if (model != null) model.reload();
    }
}
