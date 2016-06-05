package com.majeur.applicationsinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setView(getLayoutInflater().inflate(R.layout.about_dialog_message, null))
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }
}
