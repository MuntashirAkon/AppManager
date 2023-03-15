// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Intent;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.MultiSelectionView;

public class FmAdapter extends MultiSelectionView.Adapter<FmAdapter.ViewHolder> {
    private final List<FmItem> adapterList = new ArrayList<>();
    private final FmViewModel viewModel;
    private final FmActivity fmActivity;
    @ColorInt
    private final int highlightColor;

    public FmAdapter(FmViewModel viewModel, FmActivity activity) {
        this.viewModel = viewModel;
        this.fmActivity = activity;
        highlightColor = ColorCodes.getListItemSelectionColor(activity);
    }

    public void setFmList(List<FmItem> list) {
        adapterList.clear();
        adapterList.addAll(list);
        notifySelectionChange();
        notifyDataSetChanged();
    }

    @Override
    public int getHighlightColor() {
        return highlightColor;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
        View actionView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_right_standalone_action, parent, false);
        LinearLayoutCompat layout = view.findViewById(android.R.id.widget_frame);
        layout.addView(actionView);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FmItem item = adapterList.get(position);
        holder.title.setText(item.path.getName());
        String modificationDate = DateUtils.formatDateTime(item.path.lastModified());
        // Set icon
        if (item.type == FileType.DIRECTORY) {
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.subtitle.setText(String.format(Locale.getDefault(), "%d • %s", item.path.listFiles().length,
                    modificationDate));
            holder.itemView.setOnClickListener(v -> fmActivity.loadNewFragment(
                    FmFragment.getNewInstance(item.path.getUri())));
        } else {
            holder.icon.setImageResource(R.drawable.ic_file_document);
            holder.subtitle.setText(String.format(Locale.getDefault(), "%s • %s",
                    Formatter.formatShortFileSize(fmActivity, item.path.length()), modificationDate));
            holder.itemView.setOnClickListener(v -> {
                // TODO: 16/11/22 Retrieve default open with from DB and open the file with it
                OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(item.path);
                fragment.show(fmActivity.getSupportFragmentManager(), OpenWithDialogFragment.TAG);
            });
        }
        // Set background colors
        holder.itemView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent));
        // Set selections
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        // Set actions
        holder.action.setOnClickListener(v -> displayActions(holder.action, item));
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        return adapterList.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return adapterList.size();
    }

    @Override
    protected void select(int position) {
        // TODO: 4/7/21
    }

    @Override
    protected void deselect(int position) {
        // TODO: 4/7/21
    }

    @Override
    protected boolean isSelected(int position) {
        // TODO: 4/7/21
        return false;
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        // TODO: 4/7/21
    }

    @Override
    protected int getSelectedItemCount() {
        // TODO: 4/7/21
        return 0;
    }

    @Override
    protected int getTotalItemCount() {
        return adapterList.size();
    }

    private void displayActions(View anchor, FmItem item) {
        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
        popupMenu.inflate(R.menu.fragment_fm_item_actions);
        Menu menu = popupMenu.getMenu();
        MenuItem openWithAction = menu.findItem(R.id.action_open_with);
        MenuItem cutAction = menu.findItem(R.id.action_cut);
        MenuItem copyAction = menu.findItem(R.id.action_copy);
        MenuItem renameAction = menu.findItem(R.id.action_rename);
        MenuItem deleteAction = menu.findItem(R.id.action_delete);
        MenuItem shareAction = menu.findItem(R.id.action_share);
        // Disable actions based on criteria
        boolean canRead = item.path.canRead();
        boolean canWrite = item.path.canWrite();
        openWithAction.setEnabled(canRead);
        cutAction.setEnabled(canRead && canWrite);
        copyAction.setEnabled(canRead);
        renameAction.setEnabled(canRead && canWrite);
        deleteAction.setEnabled(canRead && canWrite);
        shareAction.setEnabled(canRead);
        // Set actions
        openWithAction.setOnMenuItemClickListener(menuItem -> {
            OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(item.path);
            fragment.show(fmActivity.getSupportFragmentManager(), OpenWithDialogFragment.TAG);
            return true;
        });
        menu.findItem(R.id.action_cut).setOnMenuItemClickListener(menuItem -> {
            // TODO: 21/11/22
            UIUtils.displayLongToast("Not implemented.");
            return false;
        });
        menu.findItem(R.id.action_copy).setOnMenuItemClickListener(menuItem -> {
            // TODO: 21/11/22
            UIUtils.displayLongToast("Not implemented.");
            return false;
        });
        menu.findItem(R.id.action_rename).setOnMenuItemClickListener(menuItem -> {
            RenameDialogFragment dialog = RenameDialogFragment.getInstance(item.path.getName(), (prefix, extension) -> {
                String displayName;
                if (!TextUtilsCompat.isEmpty(extension)) {
                    displayName = prefix + "." + extension;
                } else {
                    displayName = prefix;
                }
                if (item.path.renameTo(displayName)) {
                    UIUtils.displayShortToast(R.string.renamed_successfully);
                    viewModel.reload();
                } else {
                    UIUtils.displayShortToast(R.string.failed);
                }
            });
            dialog.show(fmActivity.getSupportFragmentManager(), RenameDialogFragment.TAG);
            return false;
        });
        menu.findItem(R.id.action_delete).setOnMenuItemClickListener(menuItem -> {
            new MaterialAlertDialogBuilder(fmActivity)
                    .setTitle(fmActivity.getString(R.string.delete_filename, item.path.getName()))
                    .setMessage(R.string.are_you_sure)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm_file_deletion, (dialog, which) -> {
                        if (item.path.delete()) {
                            UIUtils.displayShortToast(R.string.deleted_successfully);
                            viewModel.reload();
                        } else {
                            UIUtils.displayShortToast(R.string.failed);
                        }
                    })
                    .show();
            return true;
        });
        menu.findItem(R.id.action_share).setOnMenuItemClickListener(menuItem -> {
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType(item.path.getType())
                    .putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(item.path))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fmActivity.startActivity(Intent.createChooser(intent, item.path.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        menu.findItem(R.id.action_properties).setOnMenuItemClickListener(menuItem -> {
            FilePropertiesDialogFragment dialogFragment = FilePropertiesDialogFragment.getInstance(item.path);
            dialogFragment.show(fmActivity.getSupportFragmentManager(), FilePropertiesDialogFragment.TAG);
            return true;
        });
        popupMenu.show();
    }

    protected static class ViewHolder extends MultiSelectionView.ViewHolder {
        final MaterialCardView itemView;
        final AppCompatImageView icon;
        final MaterialButton action;
        final AppCompatTextView title;
        final AppCompatTextView subtitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(android.R.id.icon);
            action = itemView.findViewById(android.R.id.button1);
            title = itemView.findViewById(android.R.id.title);
            subtitle = itemView.findViewById(android.R.id.summary);
            action.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_more_vert);
            itemView.findViewById(R.id.divider).setVisibility(View.GONE);
        }
    }
}
