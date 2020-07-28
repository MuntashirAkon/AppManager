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
            private ImageView imageView;
            private @Nullable PackageItemInfo info;
            private Handler handler;
            private PackageManager packageManager;

            public IconLoaderThread(ImageView imageView, @Nullable PackageItemInfo info) {
                this.imageView = imageView;
                this.info = info;
                handler = new Handler(AppManager.getInstance().getMainLooper());
                packageManager = AppManager.getInstance().getPackageManager();
            }
            @Override
            public void run() {
                if (!Thread.currentThread().isInterrupted())
                    handler.post(() -> imageView.setVisibility(View.INVISIBLE));
                else return;
                Drawable icon;
                if (!Thread.currentThread().isInterrupted()) {
                    if (info != null) {
                        icon = info.loadIcon(packageManager);
                    } else icon = packageManager.getDefaultActivityIcon();
                } else return;
                if (!Thread.currentThread().isInterrupted())
                    handler.post(() -> {
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImageDrawable(icon);
                    });
            }
        }
