// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
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

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;

class FmPathListAdapter extends RecyclerView.Adapter<FmPathListAdapter.PathHolder> {
    private final FmViewModel viewModel;
    private final List<String> pathParts = Collections.synchronizedList(new ArrayList<>());
    private int currentPosition = -1;
    @Nullable
    private Uri currentPath;

    FmPathListAdapter(FmViewModel viewModel) {
        this.viewModel = viewModel;
    }

    public void setCurrentPath(@NonNull Uri currentPath) {
        Uri lastPath = this.currentPath;
        String lastPathStr = lastPath != null ? lastPath.toString() : null;
        this.currentPath = currentPath;
        String currentPathStr = this.currentPath.toString();
        if (!currentPathStr.endsWith(File.separator)) {
            currentPathStr += File.separator;
        }
        // Two cases:
        // 1. currentPath is a subset of lastPath, update currentPosition
        // 2. Otherwise, alter pathParts and set length - 1 as the currentPosition
        if (lastPathStr != null && lastPathStr.startsWith(currentPathStr)) {
            // Case 1
            setCurrentPosition(calculateCurrentPosition(currentPath));
        } else {
            // Case 2
            pathParts.clear();
            if (currentPath.getScheme().equals("file") && currentPath.getPath().startsWith(File.separator)) {
                // Add file separator as the first/root item
                pathParts.add(File.separator);
            }
            pathParts.addAll(currentPath.getPathSegments());
            currentPosition = calculateCurrentPosition(currentPath);
            notifyDataSetChanged();
        }
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public String getCurrentDisplayName() {
        return pathParts.get(currentPosition);
    }

    private int calculateCurrentPosition(@NonNull Uri uri) {
        if (uri.getScheme().equals("file") && uri.getPath().startsWith(File.separator)) {
            // Needs a file separator which adds one more items at the beginning
            return uri.getPathSegments().size();
        }
        return uri.getPathSegments().size() - 1;
    }

    public Uri calculateUri(int position) {
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < position; ++i) {
            String pathPart = pathParts.get(i);
            if (!pathPart.equals(File.separator)) {
                pathBuilder.append(pathPart).append(File.separator);
            } else {
                pathBuilder.append(File.separator);
            }
        }
        pathBuilder.append(pathParts.get(position));
        return currentPath.buildUpon()
                .path(pathBuilder.toString())
                .build();
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
                        Uri uri = calculateUri(position);
                        String path;
                        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                            path = uri.getPath();
                        } else path = uri.toString();
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
