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

package io.github.muntashirakon.AppManager.types;

import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;

public class IconLoaderThread extends Thread {
    private final ImageView imageView;
    @Nullable
    private final PackageItemInfo info;
    private final Handler handler;
    private final PackageManager packageManager;

    public IconLoaderThread(ImageView imageView, @Nullable PackageItemInfo info) {
        this.imageView = imageView;
        this.info = info;
        handler = new Handler(AppManager.getInstance().getMainLooper());
        packageManager = AppManager.getInstance().getPackageManager();
    }

    @Override
    public void run() {
        if (!Thread.currentThread().isInterrupted()) {
            handler.post(() -> imageView.setVisibility(View.INVISIBLE));
        } else return;
        Drawable icon;
        if (!Thread.currentThread().isInterrupted()) {
            if (info != null) {
                icon = info.loadIcon(packageManager);
            } else icon = packageManager.getDefaultActivityIcon();
        } else return;
        if (!Thread.currentThread().isInterrupted()) {
            handler.post(() -> {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(icon);
            });
        }
    }
}
