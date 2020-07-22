package io.github.muntashirakon.AppManager.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.progressindicator.ProgressIndicator;

public class OneClickOpsActivity extends AppCompatActivity {
    private ListItemCreator mItemCreator;
    private ProgressIndicator mProgressIndicator;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mItemCreator = new ListItemCreator(this, R.id.container);
        mProgressIndicator = findViewById(R.id.progress_linear);
        setItems();
    }

    private void setItems() {
        mItemCreator.addItemWithTitle("Not yet implemented");
        mProgressIndicator.hide();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}