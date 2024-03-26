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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

class FmPathListAdapter extends RecyclerView.Adapter<FmPathListAdapter.PathHolder> {
    private final FmViewModel mViewModel;
    private final List<String> mPathParts = Collections.synchronizedList(new ArrayList<>());
    @Nullable
    private String mAlternativeRootName = null;
    private int mCurrentPosition = -1;
    @Nullable
    private Uri mCurrentUri;

    FmPathListAdapter(FmViewModel viewModel) {
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
            setCurrentPosition(paths.size() - 1);
        } else {
            // Case 2
            mCurrentPosition = mPathParts.size() - 1;
            AdapterUtils.notifyDataSetChanged(this, mPathParts, paths);
        }
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
        return FmUtils.uriFromPathParts(Objects.requireNonNull(mCurrentUri), mPathParts, position);
    }

    private void setCurrentPosition(int currentPosition) {
        int lastPosition = mCurrentPosition;
        mCurrentPosition = currentPosition;
        if (lastPosition >= 0) {
            notifyItemChanged(lastPosition);
        }
        notifyItemChanged(currentPosition);
    }

    @NonNull
    @Override
    public PathHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_path, parent, false);
        return new PathHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PathHolder holder, int position) {
        String pathPart;
        if (position == 0) {
            pathPart = mAlternativeRootName != null ? mAlternativeRootName : mPathParts.get(position);
        } else pathPart = "» " + mPathParts.get(position);
        holder.textView.setText(pathPart);
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
            // Properties
            menu.add(R.string.file_properties)
                    .setOnMenuItemClickListener(menuItem -> {
                        mViewModel.getDisplayPropertiesLiveData().setValue(calculateUri(position));
                        return true;
                    });
            popupMenu.show();
            return true;
        });
        holder.textView.setTextColor(mCurrentPosition == position
                ? MaterialColors.getColor(holder.textView, com.google.android.material.R.attr.colorPrimary)
                : MaterialColors.getColor(holder.textView, android.R.attr.textColorSecondary));
    }

    @Override
    public int getItemCount() {
        return mPathParts.size();
    }

    public static class PathHolder extends RecyclerView.ViewHolder {
        public final TextView textView;

        public PathHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
