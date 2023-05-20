// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class FmFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static final String TAG = FmFragment.class.getSimpleName();

    public static final String ARG_URI = "uri";

    @NonNull
    public static FmFragment getNewInstance(Uri uri) {
        FmFragment fragment = new FmFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
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
    private boolean updateScrollPosition;

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
        Uri uri = model.getCurrentPath() != null
                ? model.getCurrentPath().getUri()
                : requireArguments().getParcelable(ARG_URI);
        activity = (FmActivity) requireActivity();
        // Set title and subtitle
        ActionBar actionBar = activity.getSupportActionBar();
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        swipeRefresh.post(() -> swipeRefresh.setRefreshing(true));
        RecyclerView pathListView = view.findViewById(R.id.path_list);
        pathListView.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        pathListAdapter = new FmPathListAdapter(model);
        pathListView.setAdapter(pathListAdapter);
        recyclerView = view.findViewById(R.id.list_item);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new FmAdapter(model, activity);
        recyclerView.setAdapter(adapter);
        multiSelectionView = view.findViewById(R.id.selection_view);
        multiSelectionView.hide();
        // Set observer
        model.observeFiles().observe(getViewLifecycleOwner(), fmItems -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            adapter.setFmList(fmItems);
            if (updateScrollPosition) {
                // Update scroll position for the first time
                updateScrollPosition = false;
                recyclerView.post(() -> recyclerView.scrollTo(0, model.getCurrentScrollPosition()));
            } else {
                // FIXME: 20/5/23 Remember scroll positions for last calls by Uris
                recyclerView.post(() -> recyclerView.scrollToPosition(0));
            }
        });
        model.getUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            if (actionBar != null) {
                actionBar.setTitle(uri1.getLastPathSegment());
            }
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
            }
            pathListAdapter.setCurrentPath(uri1);
        });
        model.loadFiles(uri);
        updateScrollPosition = true;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Handle back press
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    @Override
    public void onPause() {
        if (model != null && recyclerView != null) {
            model.setCurrentScrollPosition(recyclerView.getScrollY());
        }
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.activity_fm_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_storage) {
            ThreadUtils.postOnBackgroundThread(() -> {
                ArrayMap<String, Uri> storageLocations = StorageUtils.getAllStorageLocations(activity, true);
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
        } else if (id == R.id.action_new_window) {
            Intent intent = new Intent(activity, FmActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
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
