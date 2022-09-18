// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;

// Copyright 2017 Adam M. Szalkowski
public class IconPickerDialogFragment extends DialogFragment {
    public static final String TAG = "IconPickerDialogFragment";

    private IconPickerListener listener;
    private IconListingAdapter adapter;
    private IconPickerViewModel model;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ImageLoader imageLoader = new ImageLoader(executor);

    public void attachIconPickerListener(IconPickerListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(IconPickerViewModel.class);
        model.getIconsLiveData().observe(this, icons -> {
            if (adapter == null) return;
            adapter.icons = icons;
            adapter.notifyDataSetChanged();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        adapter = new IconListingAdapter(requireActivity());
        GridView grid = (GridView) View.inflate(requireActivity(), R.layout.dialog_icon_picker, null);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((view, item, index, id) -> {
            if (listener != null) {
                listener.iconPicked((IconItemInfo) view.getAdapter().getItem(index));
                if (getDialog() != null) getDialog().dismiss();
            }
        });
        model.resolveIcons(executor);
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.icon_picker)
                .setView(grid)
                .setNegativeButton(R.string.cancel, null).create();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        executor.shutdownNow();
        imageLoader.close();
    }

    public interface IconPickerListener {
        void iconPicked(PackageItemInfo icon);
    }

    class IconListingAdapter extends BaseAdapter {
        private IconItemInfo[] icons;
        private final FragmentActivity activity;

        public IconListingAdapter(@NonNull FragmentActivity activity) {
            this.activity = activity;
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
            ImageView view;
            if (convertView == null) {
                view = (ImageView) (convertView = new AppCompatImageView(this.activity));
                int size = activity.getResources().getDimensionPixelSize(R.dimen.icon_size);
                convertView.setLayoutParams(new AbsListView.LayoutParams(size, size));
            } else {
                view = (ImageView) convertView;
            }
            IconItemInfo info = this.icons[position];

            executor.submit(() -> imageLoader.displayImage(info.packageName, info, view));
            return convertView;
        }
    }

    public static class IconPickerViewModel extends AndroidViewModel {
        private final PackageManager pm;
        private final MutableLiveData<IconItemInfo[]> iconsLiveData = new MutableLiveData<>();

        public IconPickerViewModel(@NonNull Application application) {
            super(application);
            pm = application.getPackageManager();
        }

        public LiveData<IconItemInfo[]> getIconsLiveData() {
            return iconsLiveData;
        }

        public void resolveIcons(@NonNull ExecutorService executor) {
            executor.submit(() -> {
                TreeSet<IconItemInfo> icons = new TreeSet<>();
                List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

                for (PackageInfo pack : installedPackages) {
                    try {
                        String iconResourceName = pm.getResourcesForApplication(pack.packageName)
                                .getResourceName(pack.applicationInfo.icon);
                        if (iconResourceName != null) icons.add(new IconItemInfo(pack.packageName, iconResourceName));
                    } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                    }
                }
                iconsLiveData.postValue(icons.toArray(new IconItemInfo[0]));
            });
        }
    }

    private static class IconItemInfo extends PackageItemInfo implements Comparable<IconItemInfo> {
        private final String iconResourceString;
        private final Context context = AppManager.getContext();

        public IconItemInfo(String packageName, String iconResourceString) {
            this.packageName = packageName;
            this.name = this.iconResourceString = iconResourceString;
        }

        @Override
        public Drawable loadIcon(@NonNull PackageManager pm) {
            try {
                return ResourceUtil.getResourceFromName(pm, iconResourceString).getDrawable(context.getTheme());
            } catch (Exception e) {
                return pm.getDefaultActivityIcon();
            }
        }

        @Override
        public int compareTo(@NonNull IconItemInfo o) {
            return iconResourceString.compareTo(o.iconResourceString);
        }
    }
}
