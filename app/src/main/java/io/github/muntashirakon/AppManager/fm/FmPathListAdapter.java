// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;

class FmPathListAdapter extends RecyclerView.Adapter<FmPathListAdapter.PathHolder> {
    private final FmViewModel viewModel;
    private final List<String> pathParts = Collections.synchronizedList(new ArrayList<>());
    private int currentPosition = -1;
    @Nullable
    private Uri currentUri;

    FmPathListAdapter(FmViewModel viewModel) {
        this.viewModel = viewModel;
    }

    public void setCurrentUri(@NonNull Uri currentUri) {
        Uri lastPath = this.currentUri;
        String lastPathStr = lastPath != null ? lastPath.toString() : null;
        this.currentUri = currentUri;
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
            pathParts.clear();
            pathParts.addAll(paths);
            currentPosition = pathParts.size() - 1;
            notifyDataSetChanged();
        }
    }

    @Nullable
    public Uri getCurrentUri() {
        return currentUri;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public Uri calculateUri(int position) {
        return FmUtils.uriFromPathParts(Objects.requireNonNull(currentUri), pathParts, position);
    }

    private void setCurrentPosition(int currentPosition) {
        int lastPosition = this.currentPosition;
        this.currentPosition = currentPosition;
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
        String pathPart = (position == 0 ? "" : "» ") + pathParts.get(position);
        holder.textView.setText(pathPart);
        holder.itemView.setOnClickListener(v -> {
            if (currentPosition != position) {
                viewModel.loadFiles(calculateUri(position));
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
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("File path", path));
                        UIUtils.displayShortToast(R.string.copied_to_clipboard);
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
                        viewModel.getDisplayPropertiesLiveData().setValue(calculateUri(position));
                        return true;
                    });
            popupMenu.show();
            return true;
        });
        holder.textView.setTextColor(currentPosition == position
                ? MaterialColors.getColor(holder.textView, com.google.android.material.R.attr.colorPrimary)
                : MaterialColors.getColor(holder.textView, android.R.attr.textColorSecondary));
    }

    @Override
    public int getItemCount() {
        return pathParts.size();
    }

    public static class PathHolder extends RecyclerView.ViewHolder {
        public final TextView textView;

        public PathHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
