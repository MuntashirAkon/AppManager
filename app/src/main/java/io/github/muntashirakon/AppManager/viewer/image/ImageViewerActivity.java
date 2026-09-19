// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.app.WallpaperManager;
import android.print.pdf.PrintedPdfDocument;
import android.text.SpannableStringBuilder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.io.IOException;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ImageViewerActivity extends PerProcessActivity implements ImageDecodeController.Listener {
    private View mProgress;
    private View mError;
    private ZoomableImageView mImageView;
    private ImageDecodeController mDecodeController;
    @Nullable
    private Bitmap mBitmap;
    private Path mImagePath;
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
    public boolean onCreateOptionsMenu(@NonNull android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_image_viewer_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_image_reset) {
            mImageView.resetTransform();
            return true;
        } else if (itemId == R.id.action_image_rotate_left) {
            mImageView.rotateLeft();
            return true;
        } else if (itemId == R.id.action_image_rotate_right) {
            mImageView.rotateRight();
            return true;
        } else if (itemId == R.id.action_share) {
            shareImage();
            return true;
        } else if (itemId == R.id.action_print) {
            printImage();
            return true;
        } else if (itemId == R.id.action_edit) {
            editImage();
            return true;
        } else if (itemId == R.id.action_image_wallpaper) {
            setWallpaper();
            return true;
        } else if (itemId == R.id.action_image_metadata) {
            showMetadata();
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
        mImageView.resetTransform();
        mProgress.setVisibility(View.GONE);
        mError.setVisibility(View.GONE);
        mImageView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(@NonNull Throwable throwable) {
        if (mDestroyed) return;
        releaseBitmap();
        mImageView.setImageDrawable(null);
        mImageView.resetTransform();
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
        Path imagePath = Paths.get(uris.get(0));
        mImagePath = imagePath;
        setImageTitle();
        releaseBitmap();
        mImageView.setImageDrawable(null);
        // Keep the view laid out while decoding so the controller receives the actual
        // viewport dimensions.
        mImageView.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mImageView.post(() -> {
            if (!mDestroyed && mImagePath == imagePath) {
                mDecodeController.decode(mImagePath, mImageView.getWidth(), mImageView.getHeight());
            }
        });
    }

    private void showError() {
        releaseBitmap();
        mProgress.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void setImageTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getImageTitle());
        }
    }

    @NonNull
    private String getImageTitle() {
        return mImagePath != null ? mImagePath.getName() : getString(R.string.title_image_viewer);
    }

    private void shareImage() {
        if (mImagePath == null) {
            return;
        }
        Uri shareUri = FmProvider.getContentUri(mImagePath);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(getImageMimeType())
                .putExtra(Intent.EXTRA_STREAM, shareUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    private void editImage() {
        if (mImagePath == null) {
            return;
        }
        Uri editUri = mImagePath.getUri();
        String mimeType = getImageMimeType();
        Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(editUri, mimeType)
                // We should not need EXTRA_STREAM, but Graphene OS' Camera app does this:
                // https://github.com/GrapheneOS/Camera/blame/8578b470c3e276c0055958612816b2b85439b9a4/app/src/main/java/app/grapheneos/camera/CapturedItems.kt#L139
                .putExtra(Intent.EXTRA_STREAM, editUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.item_edit)));
        } catch (ActivityNotFoundException e) {
            UIUtils.displayShortToast(R.string.image_edit_unavailable);
        } catch (Throwable e) {
            UIUtils.displayShortToast("Error:" + e.getMessage());
        }
    }

    private void printImage() {
        Bitmap bitmap = mBitmap;
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager == null) {
            return;
        }
        printManager.print(getImageTitle(), new PrintDocumentAdapter() {
            private PrintAttributes mPrintAttributes;

            @Override
            public void onLayout(@NonNull PrintAttributes oldAttributes, @NonNull PrintAttributes newAttributes, @Nullable CancellationSignal cancellationSignal, @NonNull LayoutResultCallback callback, @Nullable Bundle extras) {
                if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                    callback.onLayoutCancelled();
                    return;
                }
                mPrintAttributes = newAttributes;
                callback.onLayoutFinished(new PrintDocumentInfo.Builder(getImageTitle()).setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(), !newAttributes.equals(oldAttributes));
            }

            @Override
            public void onWrite(@NonNull PageRange[] pages, @NonNull ParcelFileDescriptor destination, @NonNull CancellationSignal cancellationSignal, @NonNull WriteResultCallback callback) {
                ThreadUtils.postOnBackgroundThread(() -> {
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        return;
                    }
                    PrintedPdfDocument document = null;
                    try {
                        document = new PrintedPdfDocument(ImageViewerActivity.this, mPrintAttributes != null ? mPrintAttributes : new PrintAttributes.Builder().build());
                        PrintedPdfDocument.Page page = document.startPage(1);
                        Rect content = page.getInfo().getContentRect();
                        float scale = Math.min(content.width() / (float) bitmap.getWidth(), content.height() / (float) bitmap.getHeight());
                        int width = Math.round(bitmap.getWidth() * scale);
                        int height = Math.round(bitmap.getHeight() * scale);
                        Rect destinationRect = new Rect(content.centerX() - width / 2, content.centerY() - height / 2, content.centerX() + width / 2, content.centerY() + height / 2);
                        page.getCanvas().drawBitmap(bitmap, null, destinationRect, new Paint(Paint.ANTI_ALIAS_FLAG));
                        document.finishPage(page);
                        if (cancellationSignal.isCanceled()) {
                            callback.onWriteCancelled();
                        } else {
                            document.writeTo(new java.io.FileOutputStream(destination.getFileDescriptor()));
                            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                        }
                    } catch (Throwable e) {
                        callback.onWriteFailed(e.getMessage());
                    } finally {
                        if (document != null) document.close();
                        try {
                            destination.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
            }
        }, null);
    }

    private void setWallpaper() {
        if (mImagePath == null) {
            return;
        }
        try {
            Intent intent = WallpaperManager.getInstance(this)
                    .getCropAndSetWallpaperIntent(mImagePath.getUri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(this, R.string.image_wallpaper_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMetadata() {
        if (mImagePath == null) {
            return;
        }
        Uri uri = mImagePath.getUri();
        ThreadUtils.postOnBackgroundThread(() -> {
            CharSequence metadata = getMetadata(uri);
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.image_metadata)
                    .setMessage(metadata)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        });
    }

    @NonNull
    private CharSequence getMetadata(@NonNull Uri uri) {
        SpannableStringBuilder metadata = new SpannableStringBuilder();
        appendMetadata(metadata, getString(R.string.image_metadata_name), getImageTitle());
        appendMetadata(metadata, getString(R.string.mime_type), getImageMimeType());
        try {
            CharSequence exifMetadata = ImageMetadataReader.read(this, Paths.get(uri));
            if (exifMetadata.length() > 0) {
                metadata.append("\n");
                metadata.append(exifMetadata);
            }
        } catch (Throwable ignored) {
        }
        return metadata;
    }

    private void appendMetadata(@NonNull SpannableStringBuilder builder, @NonNull String label, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(UIUtils.getStyledKeyValue(this, label, value));
        }
    }

    @NonNull
    private String getImageMimeType() {
        String mimeType = mImagePath != null ? mImagePath.getType() : null;
        return mimeType != null && mimeType.startsWith("image/") ? mimeType : "image/*";
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
