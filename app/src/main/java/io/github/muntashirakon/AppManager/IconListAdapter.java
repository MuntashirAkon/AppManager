// This is a modified version of IconListAdapter.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;
import java.util.TreeSet;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

public class IconListAdapter extends BaseAdapter {
    private String[] icons;
    private PackageManager pm;
    private Context context;

    IconListAdapter(@NonNull Context context) {
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

    void resolve(IconListAsyncProvider.Updater updater) {
        TreeSet<String> icons = new TreeSet<>();
        List<PackageInfo> all_packages = pm.getInstalledPackages(0);
        updater.updateMax(all_packages.size());
        updater.update(0);

        for (int i = 0; i < all_packages.size(); ++i) {
            updater.update(i + 1);

            PackageInfo pack = all_packages.get(i);
            try {
                String icon_resource_name = pm.getResourcesForApplication(pack.packageName).getResourceName(pack.applicationInfo.icon);
                if (icon_resource_name != null) {
                    icons.add(icon_resource_name);
                }
                // FIXME: Get icons for all activities
//                for (int j = 0; j < pack.activities.length; ++j) {
//                    String icon_resource_name = pm.getResourcesForApplication(pack.packageName).getResourceName(pack.activities[i].getIconResource());
//                    if (icon_resource_name != null) {
//                        icons.add(icon_resource_name);
//                    }
//                }
            } catch (NameNotFoundException | RuntimeException ignored) {}
        }

        this.icons = new String[icons.size()];
        this.icons = icons.toArray(this.icons);
    }

    @Override
    public int getCount() {
        return icons.length;
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
