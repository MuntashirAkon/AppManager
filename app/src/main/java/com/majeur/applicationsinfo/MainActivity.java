package com.majeur.applicationsinfo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class MainActivity extends Activity implements MainCallbacks {

    private boolean mIsDualPane;
    private boolean mIsArtShowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIsDualPane = findViewById(R.id.item_detail_container) != null;

        //Show an art when no fragment is showed, we make sure no detail fragment is present.
        if (mIsDualPane && getFragmentManager().findFragmentByTag(DetailFragment.FRAGMENT_TAG) == null) {
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.icon_art);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ((FrameLayout) findViewById(R.id.item_detail_container)).addView(imageView);
            mIsArtShowed = true;
        }
    }

    @Override
    public void onItemSelected(String packageName) {
        if (mIsDualPane) {
            //Hide art when a fragment is showed.
            if (mIsArtShowed) {
                ((FrameLayout) findViewById(R.id.item_detail_container)).removeAllViews();
                mIsArtShowed = false;
            }
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.item_detail_container, DetailFragment.getInstance(packageName), DetailFragment.FRAGMENT_TAG)
                    .commit();
        } else {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra(DetailFragment.EXTRA_PACKAGE_NAME, packageName);
            startActivity(intent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, getIntentFilter());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    /**
     * Used to update the list if a package is added or removed.
     */
    private IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        return filter;
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MainListFragment mainListFragment = (MainListFragment) getFragmentManager().findFragmentById(R.id.item_list);
            if (mainListFragment != null)
                mainListFragment.loadList();
        }
    };
}
