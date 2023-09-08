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
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

// Copyright 2017 Adam M. Szalkowski
public class IconPickerDialogFragment extends DialogFragment {
    public static final String TAG = "IconPickerDialogFragment";

    private IconPickerListener mListener;
    private IconListingAdapter mAdapter;
    private IconPickerViewModel mModel;

    public void attachIconPickerListener(IconPickerListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(IconPickerViewModel.class);
        mModel.getIconsLiveData().observe(this, icons -> {
            if (mAdapter == null) return;
            mAdapter.mIcons = icons;
            mAdapter.notifyDataSetChanged();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mAdapter = new IconListingAdapter(requireActivity());
        GridView grid = (GridView) View.inflate(requireActivity(), R.layout.dialog_icon_picker, null);
        grid.setAdapter(mAdapter);
        grid.setOnItemClickListener((view, item, index, id) -> {
            if (mListener != null) {
                mListener.iconPicked((IconItemInfo) view.getAdapter().getItem(index));
                if (getDialog() != null) getDialog().dismiss();
            }
        });
        mModel.resolveIcons();
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.icon_picker)
                .setView(grid)
                .setNegativeButton(R.string.cancel, null).create();
    }

    public interface IconPickerListener {
        void iconPicked(PackageItemInfo icon);
    }

    static class IconListingAdapter extends BaseAdapter {
        private IconItemInfo[] mIcons;
        private final FragmentActivity mActivity;

        public IconListingAdapter(@NonNull FragmentActivity activity) {
            mActivity = activity;
        }

        @Override
        public int getCount() {
            return mIcons == null ? 0 : mIcons.length;
        }

        @Override
        public Object getItem(int position) {
            return mIcons[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView view;
            if (convertView == null) {
                view = (ImageView) (convertView = new AppCompatImageView(mActivity));
                int size = mActivity.getResources().getDimensionPixelSize(R.dimen.icon_size);
                convertView.setLayoutParams(new AbsListView.LayoutParams(size, size));
            } else {
                view = (ImageView) convertView;
            }
            IconItemInfo info = mIcons[position];
            view.setTag(info.packageName);
            ImageLoader.getInstance().displayImage(info.packageName, info, view);
            return convertView;
        }
    }

    public static class IconPickerViewModel extends AndroidViewModel {
        private final PackageManager mPm;
        private final MutableLiveData<IconItemInfo[]> mIconsLiveData = new MutableLiveData<>();

        @Nullable
        private Future<?> mIconLoaderResult;

        public IconPickerViewModel(@NonNull Application application) {
            super(application);
            mPm = application.getPackageManager();
        }

        @Override
        protected void onCleared() {
            if (mIconLoaderResult != null) {
                mIconLoaderResult.cancel(true);
            }
            super.onCleared();
        }

        public LiveData<IconItemInfo[]> getIconsLiveData() {
            return mIconsLiveData;
        }

        public void resolveIcons() {
            if (mIconLoaderResult != null) {
                mIconLoaderResult.cancel(true);
            }
            mIconLoaderResult = ThreadUtils.postOnBackgroundThread(() -> {
                TreeSet<IconItemInfo> icons = new TreeSet<>();
                List<PackageInfo> installedPackages = mPm.getInstalledPackages(0);

                for (PackageInfo pack : installedPackages) {
                    try {
                        String iconResourceName = mPm.getResourcesForApplication(pack.packageName)
                                .getResourceName(pack.applicationInfo.icon);
                        if (iconResourceName != null) {
                            icons.add(new IconItemInfo(getApplication(), pack.packageName, iconResourceName));
                        }
                    } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                    }
                    if (ThreadUtils.isInterrupted()) {
                        return;
                    }
                }
                mIconsLiveData.postValue(icons.toArray(new IconItemInfo[0]));
            });
        }
    }

    private static class IconItemInfo extends PackageItemInfo implements Comparable<IconItemInfo> {
        private final String mIconResourceString;
        private final Context mContext;

        public IconItemInfo(Context context, String packageName, String iconResourceString) {
            mContext = context;
            this.packageName = packageName;
            this.name = mIconResourceString = iconResourceString;
        }

        @Override
        public Drawable loadIcon(@NonNull PackageManager pm) {
            try {
                Drawable drawable = ResourceUtil.getResourceFromName(pm, mIconResourceString).getDrawable(mContext.getTheme());
                if (drawable != null) {
                    return drawable;
                }
            } catch (Exception ignore) {
            }
            return pm.getDefaultActivityIcon();
        }

        @Override
        public int compareTo(@NonNull IconItemInfo o) {
            return mIconResourceString.compareTo(o.mIconResourceString);
        }
    }
}
