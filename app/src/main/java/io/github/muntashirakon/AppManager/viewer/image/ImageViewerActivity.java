// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import java.util.List;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ImageViewerActivity extends PerProcessActivity implements ImageDecodeController.Listener {
    private View mProgress;
    private View mError;
    private android.widget.ImageView mImageView;
    private ImageDecodeController mDecodeController;
    @Nullable
    private Bitmap mBitmap;
    private Uri mImageUri;
    private boolean mDestroyed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mProgress = findViewById(R.id.progress_linear);
        mError = findViewById(R.id.error);
        mImageView = findViewById(R.id.image);
        mDecodeController = new ImageDecodeController(this);
        openImage(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openImage(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onImageDecoded(@NonNull Bitmap bitmap) {
        if (mDestroyed) {
            bitmap.recycle();
            return;
        }
        releaseBitmap();
        mBitmap = bitmap;
        mImageView.setImageBitmap(bitmap);
        mProgress.setVisibility(View.GONE);
        mError.setVisibility(View.GONE);
        mImageView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(@NonNull Throwable throwable) {
        if (mDestroyed) return;
        releaseBitmap();
        mImageView.setImageDrawable(null);
        mProgress.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void openImage(@NonNull android.content.Intent intent) {
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null || uris.size() != 1) {
            showError();
            return;
        }
        Uri uri = uris.get(0);
        mImageUri = uri;
        setImageTitle(uri);
        releaseBitmap();
        mImageView.setImageDrawable(null);
        // Keep the view laid out while decoding so the controller receives the actual
        // viewport dimensions.
        mImageView.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mImageView.post(() -> {
            if (!mDestroyed && mImageUri == uri) {
                mDecodeController.decode(uri, mImageView.getWidth(), mImageView.getHeight());
            }
        });
    }

    private void showError() {
        releaseBitmap();
        mProgress.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void setImageTitle(@NonNull Uri uri) {
        String filename = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                }
            } catch (Throwable ignored) {
            }
        }
        if (filename == null || filename.isEmpty()) filename = uri.getLastPathSegment();
        if (filename == null || filename.isEmpty()) filename = getString(R.string.title_image_viewer);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(filename);
    }

    private void releaseBitmap() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
        mBitmap = null;
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        if (mDecodeController != null) {
            mDecodeController.close();
        }
        releaseBitmap();
        super.onDestroy();
    }
}
