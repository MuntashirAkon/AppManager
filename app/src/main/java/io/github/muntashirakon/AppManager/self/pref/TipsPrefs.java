// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.pref;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.utils.AppPref;

public class TipsPrefs {
    private static final int TIPS_TAB_APP_OPS = 1 << 0;
    private static final int TIPS_TAB_USES_PERMISSIONS = 1 << 1;
    private static final int TIPS_TAB_PERMISSIONS = 1 << 2;

    private static TipsPrefs instance;

    @NonNull
    public static TipsPrefs getInstance() {
        if (instance != null) {
            return instance;
        }
        return instance = new TipsPrefs();
    }

    private int flags;

    private TipsPrefs() {
        this.flags = AppPref.getInt(AppPref.PrefKey.PREF_TIPS_PREFS_INT);
    }

    public boolean displayInAppOpsTab() {
        return (flags & TIPS_TAB_APP_OPS) != 0;
    }

    public void setDisplayInAppOpsTab(boolean display) {
        save(TIPS_TAB_APP_OPS, display);
    }

    public boolean displayInUsesPermissionsTab() {
        return (flags & TIPS_TAB_USES_PERMISSIONS) != 0;
    }

    public void setDisplayInUsesPermissionsTab(boolean display) {
        save(TIPS_TAB_USES_PERMISSIONS, display);
    }

    public boolean displayInPermissionsTab() {
        return (flags & TIPS_TAB_PERMISSIONS) != 0;
    }

    public void setDisplayInPermissionsTab(boolean display) {
        save(TIPS_TAB_PERMISSIONS, display);
    }

    private void save(int flag, boolean display) {
        if (display) flags |= flag;
        else flags &= ~flag;
        AppPref.set(AppPref.PrefKey.PREF_TIPS_PREFS_INT, flags);
    }
}
