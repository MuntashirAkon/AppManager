/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.TreeSet;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;

public class IconPickerDialogFragment extends DialogFragment {
    public static final String TAG = "IconPickerDialogFragment";

    private IconPickerListener listener = null;
    private IconListingAdapter adapter;

    @Override
    public void onAttach(@NonNull Context activity) {
        super.onAttach(activity);
        adapter = new IconListingAdapter(activity);
        new Thread(() -> {
            adapter.resolve();
            requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    void attachIconPickerListener(IconPickerListener listener) {
        this.listener = listener;
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireActivity());
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        GridView grid = (GridView) inflater.inflate(R.layout.dialog_icon_picker, null);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((view, item, index, id) -> {
            if (listener != null) {
                listener.iconPicked(view.getAdapter().getItem(index).toString());
                if (getDialog() != null) getDialog().dismiss();
            }
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.icon_picker)
                .setView(grid)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                }).create();
    }

    public interface IconPickerListener {
        void iconPicked(String icon);
    }

    static class IconListingAdapter extends BaseAdapter {
        private String[] icons;
        private final PackageManager pm;
        private final Context context;

        public IconListingAdapter(@NonNull Context context) {
            this.context = context;
            this.pm = context.getPackageManager();
        }

        private Drawable getIcon(String icon_resource_string, PackageManager pm) {
            try {
                String pack = icon_resource_string.substring(0, icon_resource_string.indexOf(':'));
                String type = icon_resource_string.substring(icon_resource_string.indexOf(':') + 1, icon_resource_string.indexOf('/'));
                String name = icon_resource_string.substring(icon_resource_string.indexOf('/') + 1);
                Resources res = pm.getResourcesForApplication(pack);
                return ResourcesCompat.getDrawable(res, res.getIdentifier(name, type, pack), context.getTheme());
            } catch (Exception e) {
                return pm.getDefaultActivityIcon();
            }

        }

        public void resolve() {
            TreeSet<String> icons = new TreeSet<>();
            List<PackageInfo> all_packages = pm.getInstalledPackages(0);

            for (int i = 0; i < all_packages.size(); ++i) {
                PackageInfo pack = all_packages.get(i);
                try {
                    String icon_resource_name = pm.getResourcesForApplication(pack.packageName).getResourceName(pack.applicationInfo.icon);
                    if (icon_resource_name != null) icons.add(icon_resource_name);
                } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {}
            }

            this.icons = new String[icons.size()];
            this.icons = icons.toArray(this.icons);
        }

        @Override
        public int getCount() {
            return icons == null ? 0 : icons.length;
        }

        @Override
        public Object getItem(int position) {
            return icons[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView view = new ImageView(this.context);
            int size = context.getResources().getDimensionPixelSize(R.dimen.icon_size);
            view.setLayoutParams(new AbsListView.LayoutParams(size, size));
            String icon_resource_string = this.icons[position];
            view.setImageDrawable(getIcon(icon_resource_string, this.pm));
            return view;
        }
    }
}
