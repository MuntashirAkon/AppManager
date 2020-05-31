package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.MainCallbacks;
import io.github.muntashirakon.AppManager.fragments.MainListFragment;
import io.github.muntashirakon.AppManager.R;

public class MainActivity extends AppCompatActivity implements MainCallbacks {
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static String packageList;
    public static String permName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainListFragment.sActivity = this;
        setContentView(R.layout.activity_main);
        packageList = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        permName = getIntent().getStringExtra("perm_name");
        if (permName == null) permName = "Onboard.packages";
    }

    @Override
    public void onItemSelected(String packageName) {
        Intent intent = new Intent(this, AppInfoActivity.class);
        intent.putExtra(AppInfoActivity.EXTRA_PACKAGE_NAME, packageName);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.about)
                    .setView(getLayoutInflater().inflate(R.layout.dialog_about, null))
                    .setNegativeButton(android.R.string.ok, null)
                    .setIcon(R.drawable.ic_launcher_app_manager)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
