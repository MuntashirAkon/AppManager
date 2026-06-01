// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.AppManager.utils.Utils;

class FmPathListAdapter extends ListAdapter<FmPathListAdapter.PathPartItem, FmPathListAdapter.PathHolder> {
    private final FmViewModel mViewModel;
    private final List<String> mPathPartsCache = new ArrayList<>();
    @Nullable
    private String mAlternativeRootName = null;
    private int mCurrentPosition = -1;
    @Nullable
    private Uri mCurrentUri;

    static class PathPartItem {
        final int id;
        @NonNull final String partName;
        boolean isCurrent;

        PathPartItem(int id, @NonNull String partName, boolean isCurrent) {
            this.id = id;
            this.partName = partName;
            this.isCurrent = isCurrent;
        }
    }

    private static final DiffUtil.ItemCallback<PathPartItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<PathPartItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull PathPartItem oldItem, @NonNull PathPartItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull PathPartItem oldItem, @NonNull PathPartItem newItem) {
            return oldItem.isCurrent == newItem.isCurrent
                    && Objects.equals(oldItem.partName, newItem.partName);
        }
    };

    FmPathListAdapter(FmViewModel viewModel) {
        super(DIFF_CALLBACK);
        mViewModel = viewModel;
    }

    public void setCurrentUri(@NonNull Uri currentUri) {
        Uri lastPath = mCurrentUri;
        String lastPathStr = lastPath != null ? lastPath.toString() : null;
        mCurrentUri = currentUri;
        List<String> paths = FmUtils.uriToPathParts(currentUri);
        String currentPathStr = currentUri.toString();
        if (!currentPathStr.endsWith(File.separator)) {
            currentPathStr += File.separator;
        }
        // Two cases:
        // 1. currentPath is a subset of lastPath, update currentPosition
        // 2. Otherwise, alter pathParts and set (length - 1) as the currentPosition
        if (lastPathStr != null && lastPathStr.startsWith(currentPathStr)) {
            // Case 1
            mCurrentPosition = paths.size() - 1;
            dispatchUpdatedSnapshot(mPathPartsCache);
        } else {
            // Case 2
            mCurrentPosition = paths.size() - 1;
            mPathPartsCache.clear();
            mPathPartsCache.addAll(paths);
            dispatchUpdatedSnapshot(mPathPartsCache);
        }
    }

    private void dispatchUpdatedSnapshot(@NonNull List<String> pathStrings) {
        // We take snapshots of path to avoid loss of path parts
        List<PathPartItem> snapshots = new ArrayList<>();
        for (int i = 0; i < pathStrings.size(); i++) {
            snapshots.add(new PathPartItem(i, pathStrings.get(i), i == mCurrentPosition));
        }
        submitList(snapshots);
    }

    public void setAlternativeRootName(@Nullable String alternativeRootName) {
        mAlternativeRootName = alternativeRootName;
    }

    @Nullable
    public Uri getCurrentUri() {
        return mCurrentUri;
    }

    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    public Uri calculateUri(int position) {
        return FmUtils.uriFromPathParts(Objects.requireNonNull(mCurrentUri), mPathPartsCache, position);
    }

    @NonNull
    @Override
    public PathHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_path, parent, false);
        return new PathHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PathHolder holder, int position) {
        PathPartItem pathPartItem = getItem(position);
        String actualPathPart = pathPartItem.partName;
        String pathPart;
        if (position == 0) {
            pathPart = mAlternativeRootName != null ? mAlternativeRootName : actualPathPart;
        } else {
            pathPart = "» " + actualPathPart;
        }
        holder.textView.setText(pathPart);
        if (position == 0 && pathPart.equals("/")) {
            holder.itemView.setContentDescription(holder.itemView.getContext().getString(R.string.root));
        } else {
            holder.itemView.setContentDescription(actualPathPart);
        }
        holder.itemView.setOnClickListener(v -> {
            if (mCurrentPosition != position) {
                mViewModel.loadFiles(calculateUri(position));
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            Context context = v.getContext();
            PopupMenu popupMenu = new PopupMenu(context, v);
            Menu menu = popupMenu.getMenu();
            // Copy path
            menu.add(R.string.copy_this_path)
                    .setOnMenuItemClickListener(menuItem -> {
                        String path = FmUtils.getDisplayablePath(calculateUri(position));
                        Utils.copyToClipboard(context, "Path", path);
                        return true;
                    });
            // Open in new window
            menu.add(R.string.open_in_new_window)
                    .setOnMenuItemClickListener(menuItem -> {
                        Intent intent = new Intent(context, FmActivity.class);
                        intent.setDataAndType(calculateUri(position), DocumentsContract.Document.MIME_TYPE_DIR);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        context.startActivity(intent);
                        return true;
                    });
            // Add to favorites
            menu.add(R.string.add_to_favorites)
                    .setOnMenuItemClickListener(item -> {
                        mViewModel.addToFavorite(Paths.get(calculateUri(position)), mViewModel.getOptions());
                        return true;
                    });
            // Properties
            menu.add(R.string.file_properties)
                    .setOnMenuItemClickListener(menuItem -> {
                        mViewModel.getDisplayPropertiesLiveData().setValue(calculateUri(position));
                        return true;
                    });
            popupMenu.show();
            return true;
        });
        holder.textView.setTextColor(pathPartItem.isCurrent
                ? MaterialColors.getColor(holder.textView, androidx.appcompat.R.attr.colorPrimary)
                : MaterialColors.getColor(holder.textView, android.R.attr.textColorSecondary));
    }

    public static class PathHolder extends RecyclerView.ViewHolder {
        public final TextView textView;

        public PathHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(android.R.id.text1);
        }
    }
}