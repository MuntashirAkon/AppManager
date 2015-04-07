package com.majeur.applicationsinfo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;


public class DetailOverflowMenu implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    private Context mContext;
    private String mPackageName;

    public DetailOverflowMenu(Context context, String packageName) {
        mContext = context;
        mPackageName = packageName;
    }

    public void setView(View view) {
        view.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        PopupMenu popupMenu = new PopupMenu(mContext, view);
        popupMenu.inflate(R.menu.fragment_detail);

        //Disable uninstall option for system apps.
        popupMenu.getMenu().findItem(R.id.action_uninstall).setEnabled(!isSystemApp());

        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();

    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_uninstall:
                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                mContext.startActivity(uninstallIntent);
                return true;
            case R.id.action_view_in_settings:
                Intent infoIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
                infoIntent.setData(Uri.parse("package:" + mPackageName));
                mContext.startActivity(infoIntent);
                return true;
            case R.id.action_view_manifest:
                Intent viewManifestIntent = new Intent(mContext, ViewManifestActivity.class);
                viewManifestIntent.putExtra(ViewManifestActivity.EXTRA_PACKAGE_NAME, mPackageName);
                mContext.startActivity(viewManifestIntent);
                return true;
        }
        return false;
    }

    public boolean isSystemApp() {
        try {
            return (mContext.getPackageManager().getApplicationInfo(mPackageName, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }
}
