package io.github.muntashirakon.AppManager.main;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.utils.Tuple;

/**
 * Stores an application info
 */
public class ApplicationItem extends PackageItemInfo {
    /**
     * Backup info
     */
    public @Nullable MetadataManager.MetadataV1 metadataV1;
    /**
     * Application flags. See {@link ApplicationInfo#flags}
     */
    public int flags = 0;
    /**
     * Kernel user id. See {@link ApplicationInfo#uid}
     */
    public int uid = 0;
    /**
     * Application label (or name)
     */
    public String label;
    /**
     * True if debuggable, false otherwise
     */
    public boolean debuggable = false;
    /**
     * Last update date
     */
    public Long lastUpdateTime;
    /**
     * Target SDK version * -1
     */
    public Long size = -1L;
    /**
     * Issuer and signature
     */
    public @Nullable Tuple<String, String> sha;
    /**
     * Blocked components count
     */
    public Integer blockedCount = 0;
    /**
     * Whether the item is a user app (or system app)
     */
    public boolean isUser;
    /**
     * Whether the app is disabled
     */
    public boolean isDisabled;
    /**
     * Whether the app is installed
     */
    public boolean isInstalled = true;
    /**
     * Whether the item is selected
     */
    public boolean isSelected = false;

    public ApplicationItem() {
        super();
    }

    public ApplicationItem(PackageItemInfo orig) {
        super(orig);
    }

    @Override
    public Drawable loadIcon(PackageManager pm) {
        if (isInstalled) {
            try {
                return pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException ignore) {}
        }
        return pm.getDefaultActivityIcon();
    }
}
