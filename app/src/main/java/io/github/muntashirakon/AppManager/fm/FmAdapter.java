// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.RenameDialogFragment;
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

class FmAdapter extends MultiSelectionView.Adapter<FmAdapter.ViewHolder> {
    private static final List<String> DEX_EXTENSIONS = Arrays.asList("dex", "jar");

    private final List<FmItem> mAdapterList = Collections.synchronizedList(new ArrayList<>());
    private final FmViewModel mViewModel;
    private final FmActivity mFmActivity;

    public FmAdapter(FmViewModel viewModel, FmActivity activity) {
        mViewModel = viewModel;
        mFmActivity = activity;
    }

    public void setFmList(List<FmItem> list) {
        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
        notifySelectionChange();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fm, parent, false);
        View actionView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_right_standalone_action, parent, false);
        LinearLayoutCompat layout = view.findViewById(android.R.id.widget_frame);
        layout.addView(actionView);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FmItem item = mAdapterList.get(position);
        holder.itemView.setTag(item.path);
        holder.title.setText(item.getName());
        // Load attributes
        cacheAndLoadAttributes(holder, item);
        if (item.isDirectory) {
            holder.itemView.setOnClickListener(v -> {
                if (isInSelectionMode()) {
                    toggleSelection(position);
                    return;
                }
                mViewModel.loadFiles(item.path.getUri());
            });
        } else {
            holder.itemView.setOnClickListener(v -> {
                if (isInSelectionMode()) {
                    toggleSelection(position);
                    return;
                }
                // TODO: 16/11/22 Retrieve default open with from DB and open the file with it
                OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(item.path);
                fragment.show(mFmActivity.getSupportFragmentManager(), OpenWithDialogFragment.TAG);
            });
        }
        // Symbolic link
        holder.symbolicLinkIcon.setVisibility(item.path.isSymbolicLink() ? View.VISIBLE : View.GONE);
        // Set background colors
        holder.itemView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent));
        // Set selections
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        // Set actions
        holder.action.setOnClickListener(v -> displayActions(holder.action, item));
        holder.itemView.setOnLongClickListener(v -> {
            // Long click listener: Select/deselect an app.
            // 1) Turn selection mode on if this is the first item in the selection list
            // 2) Select between last selection position and this position (inclusive) if selection mode is on
            Path lastSelectedItem = mViewModel.getLastSelectedItem();
            int lastSelectedItemPosition = -1;
            if (lastSelectedItem != null) {
                int i = 0;
                for (FmItem fmItem : mAdapterList) {
                    if (fmItem.path.equals(lastSelectedItem)) {
                        lastSelectedItemPosition = i;
                        break;
                    }
                    ++i;
                }
            }
            if (lastSelectedItemPosition >= 0) {
                // Select from last selection to this selection
                selectRange(lastSelectedItemPosition, position);
            } else toggleSelection(position);
            return true;
        });
        super.onBindViewHolder(holder, position);
    }

    private void cacheAndLoadAttributes(@NonNull ViewHolder holder, @NonNull FmItem item) {
        if (item.isCached()) {
            loadAttributes(holder, item);
        } else {
            // TODO: 9/9/23 Store these threads in a list and cancel them when not needed
            ThreadUtils.postOnBackgroundThread(() -> {
                WeakReference<ViewHolder> holderRef = new WeakReference<>(holder);
                WeakReference<FmItem> itemRef = new WeakReference<>(item);
                item.cache();
                ThreadUtils.postOnMainThread(() -> {
                    ViewHolder h = holderRef.get();
                    FmItem i = itemRef.get();
                    if (h != null && i != null && Objects.equals(h.itemView.getTag(), i.path)) {
                        loadAttributes(h, i);
                    }
                });
            });
        }
    }

    @MainThread
    private void loadAttributes(@NonNull ViewHolder holder, @NonNull FmItem item) {
        // Set icon
        String tag = item.getTag();
        holder.icon.setTag(tag);
        ImageLoader.getInstance().displayImage(tag, holder.icon, new FmIconFetcher(item));
        // Set sub-icon
        // TODO: 24/5/23 Set sub-icon if needed
        // Attrs
        String modificationDate = DateUtils.formatDateTime(mFmActivity, item.getLastModified());
        if (item.isDirectory) {
            holder.subtitle.setText(String.format(Locale.getDefault(), "%d • %s", item.getChildCount(),
                    modificationDate));
        } else {
            holder.subtitle.setText(String.format(Locale.getDefault(), "%s • %s",
                    Formatter.formatShortFileSize(mFmActivity, item.getSize()), modificationDate));
        }
    }

    @Override
    public long getItemId(int position) {
        return mAdapterList.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return mAdapterList.size();
    }

    @Override
    protected void select(int position) {
        mViewModel.setSelectedItem(mAdapterList.get(position).path, true);
    }

    @Override
    protected void deselect(int position) {
        mViewModel.setSelectedItem(mAdapterList.get(position).path, false);
    }

    @Override
    protected boolean isSelected(int position) {
        return mViewModel.isSelected(mAdapterList.get(position).path);
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        mViewModel.clearSelections();
    }

    @Override
    protected int getSelectedItemCount() {
        return mViewModel.getSelectedItemCount();
    }

    @Override
    protected int getTotalItemCount() {
        return mAdapterList.size();
    }

    private void displayActions(View anchor, FmItem item) {
        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
        popupMenu.setForceShowIcon(true);
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
            fragment.show(mFmActivity.getSupportFragmentManager(), OpenWithDialogFragment.TAG);
            return true;
        });
        menu.findItem(R.id.action_cut).setOnMenuItemClickListener(menuItem -> {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(FmTasks.FmTask.TYPE_CUT, Collections.singletonList(item.path));
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
            return false;
        });
        menu.findItem(R.id.action_copy).setOnMenuItemClickListener(menuItem -> {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(FmTasks.FmTask.TYPE_COPY, Collections.singletonList(item.path));
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
            return false;
        });
        menu.findItem(R.id.action_rename).setOnMenuItemClickListener(menuItem -> {
            RenameDialogFragment dialog = RenameDialogFragment.getInstance(item.path.getName(), (prefix, extension) -> {
                String displayName;
                if (!TextUtils.isEmpty(extension)) {
                    displayName = prefix + "." + extension;
                } else {
                    displayName = prefix;
                }
                if (item.path.renameTo(displayName)) {
                    UIUtils.displayShortToast(R.string.renamed_successfully);
                    mViewModel.reload();
                } else {
                    UIUtils.displayShortToast(R.string.failed);
                }
            });
            dialog.show(mFmActivity.getSupportFragmentManager(), RenameDialogFragment.TAG);
            return false;
        });
        menu.findItem(R.id.action_delete).setOnMenuItemClickListener(menuItem -> {
            new MaterialAlertDialogBuilder(mFmActivity)
                    .setTitle(mFmActivity.getString(R.string.delete_filename, item.path.getName()))
                    .setMessage(R.string.are_you_sure)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm_file_deletion, (dialog, which) -> {
                        if (item.path.delete()) {
                            UIUtils.displayShortToast(R.string.deleted_successfully);
                            mViewModel.reload();
                        } else {
                            UIUtils.displayShortToast(R.string.failed);
                        }
                    })
                    .show();
            return true;
        });
        menu.findItem(R.id.action_share).setOnMenuItemClickListener(menuItem -> {
            mViewModel.shareFiles(Collections.singletonList(item.path));
            return true;
        });
        boolean isVfs = mViewModel.getOptions().isVfs();
        menu.findItem(R.id.action_shortcut)
                // TODO: 31/5/23 Enable creating shortcuts for VFS
                .setEnabled(!isVfs)
                .setVisible(!isVfs)
                .setOnMenuItemClickListener(menuItem -> {
                    mViewModel.createShortcut(item);
                    return true;
                });
        MenuItem favItem = menu.findItem(R.id.action_add_to_favorites);
        favItem.setOnMenuItemClickListener(menuItem -> {
            mViewModel.addToFavorite(item.path, mViewModel.getOptions());
            return true;
        });
        favItem.setEnabled(item.isDirectory);
        favItem.setVisible(item.isDirectory);
        menu.findItem(R.id.action_copy_path).setOnMenuItemClickListener(menuItem -> {
            String path = FmUtils.getDisplayablePath(item.path);
            Utils.copyToClipboard(mFmActivity, "Path", path);
            return true;
        });
        menu.findItem(R.id.action_properties).setOnMenuItemClickListener(menuItem -> {
            mViewModel.getDisplayPropertiesLiveData().setValue(item.path.getUri());
            return true;
        });
        popupMenu.show();
    }

    protected static class ViewHolder extends MultiSelectionView.ViewHolder {
        final MaterialCardView itemView;
        final ShapeableImageView icon;
        final ShapeableImageView symbolicLinkIcon;
        final MaterialButton action;
        final AppCompatTextView title;
        final AppCompatTextView subtitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(android.R.id.icon);
            symbolicLinkIcon = itemView.findViewById(R.id.symolic_link_icon);
            action = itemView.findViewById(android.R.id.button1);
            title = itemView.findViewById(android.R.id.title);
            subtitle = itemView.findViewById(android.R.id.summary);
            action.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_more_vert);
            itemView.findViewById(R.id.divider).setVisibility(View.GONE);
        }
    }
}
