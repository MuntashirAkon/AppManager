package com.majeur.applicationsinfo;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

public class DetailActivity extends Activity {

    private final int LAYOUT_ID = 0x8898;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setId(LAYOUT_ID);
        setContentView(frameLayout);
        String packageName = getIntent().getStringExtra(DetailFragment.EXTRA_PACKAGE_NAME);

        getFragmentManager()
                .beginTransaction()
                .replace(LAYOUT_ID, DetailFragment.getInstance(packageName))
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
