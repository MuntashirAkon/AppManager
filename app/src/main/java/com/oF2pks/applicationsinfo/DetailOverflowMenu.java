package com.oF2pks.applicationsinfo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.io.File;


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
            case R.id.action_view_exodus:
                scan3("");
                return true;
            case R.id.action_view_exodus2:
                scan3("longClick");
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

    private void scan3(String sClick){
        PackageManager pm2 = mContext.getPackageManager();
        Intent intent2 = new Intent();
        intent2.setClassName("com.oF2pks.classyshark3xodus","com.google.classysharkandroid.activities.ClassesListActivity");
        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {

            File file = new File(pm2.getPackageInfo(mPackageName, 0).applicationInfo.publicSourceDir);
            if (null != intent2) {
                intent2.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                intent2.putExtra("APP_NAME", mPackageName);
                intent2.putExtra("CLICK_PRESS", sClick);

                try {
                    mContext.startActivity(intent2);
                } catch (Exception e) {
                    Toast.makeText(mContext, e.toString(), Toast.LENGTH_LONG).show();
                }
            }

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
