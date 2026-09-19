// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class PdfViewerActivity extends PerProcessActivity {
    private PdfRenderController mRenderController;
    private PdfPageAdapter mPageAdapter;
    private View mProgress;
    private View mError;
    private RecyclerView mPages;
    private Path mDocumentPath;
    private int mPageCount;
    private float mZoom = 1f;
    private ArrayDeque<Integer> mExportPages;
    @Nullable
    private DocumentFile mExportDirectory;
    private String mExportExtension;
    private String mExportMimeType;
    private boolean mDestroyed;
    private final ActivityResultLauncher<Intent> mExportDirectoryPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null
                        && mExportPages != null) {
                    mExportDirectory = DocumentFile.fromTreeUri(this, data.getData());
                    exportNextPage();
                }
            });
    private final ActivityResultLauncher<String> mExportPngDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/png"), this::exportSinglePage);
    private final ActivityResultLauncher<String> mExportJpegDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/jpeg"), this::exportSinglePage);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_pdf_viewer);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        mProgress = findViewById(R.id.progress_linear);
        mError = findViewById(R.id.error);
        mPages = findViewById(R.id.pages);
        mPages.setLayoutManager(new LinearLayoutManager(this));

        mRenderController = new PdfRenderController(this, new PdfRenderController.Listener() {
            @Override
            public void onDocumentOpened(int pageCount) {
                if (mDestroyed) return;
                mPageCount = pageCount;
                mProgress.setVisibility(View.GONE);
                mError.setVisibility(View.GONE);
                mPages.setVisibility(View.VISIBLE);
                mPageAdapter.setPageCount(pageCount);
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                if (mDestroyed) return;
                mProgress.setVisibility(View.GONE);
                mError.setVisibility(View.VISIBLE);
                mPageAdapter.setPageCount(0);
            }
        });
        mPageAdapter = new PdfPageAdapter(this, mRenderController);
        mPages.setAdapter(mPageAdapter);
        openDocument(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mRenderController != null) {
            mProgress.setVisibility(View.VISIBLE);
            mError.setVisibility(View.GONE);
            mPages.setVisibility(View.GONE);
            mPageAdapter.setPageCount(0);
            openDocument(intent);
        }
    }

    private void openDocument(@NonNull Intent intent) {
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null || uris.size() != 1) {
            mProgress.setVisibility(View.GONE);
            mError.setVisibility(View.VISIBLE);
            return;
        }
        Uri uri = uris.get(0);
        mDocumentPath = Paths.get(uris.get(0));
        setDocumentTitle();
        mRenderController.open(uri);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_pdf_viewer_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_zoom_in) {
            setZoom(Math.min(2f, mZoom + .25f));
            return true;
        } else if (id == R.id.action_zoom_out) {
            setZoom(Math.max(.75f, mZoom - .25f));
            return true;
        } else if (id == R.id.action_go_to_page) {
            showGoToPageDialog();
            return true;
        } else if (id == R.id.action_print_pdf) {
            printDocument();
            return true;
        } else if (id == R.id.action_export_pdf) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_share_pdf) {
            shareDocument();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setZoom(float zoom) {
        mZoom = zoom;
        mPages.setPivotX(0);
        mPages.setPivotY(0);
        mPages.setScaleX(zoom);
        mPages.setScaleY(zoom);
    }

    private void showGoToPageDialog() {
        new TextInputDialogBuilder(this, R.string.pdf_export_range)
                .setTitle(R.string.pdf_go_to_page)
                .setHelperText("1–" + mPageCount)
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which, input, isChecked) -> {
                    try {
                        int page = Integer.parseInt(input.toString());
                        if (page < 1 || page > mPageCount) {
                            throw new NumberFormatException();
                        }
                        mPages.scrollToPosition(page - 1);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.pdf_export_invalid_range, mPageCount), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void shareDocument() {
        if (mDocumentPath == null) {
            return;
        }
        Uri shareUri = FmProvider.getContentUri(mDocumentPath);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, shareUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, null));
    }

    private void showExportDialog() {
        List<Integer> formats = java.util.Arrays.asList(0, 1);
        CharSequence[] formatNames = {getText(R.string.pdf_export_png), getText(R.string.pdf_export_jpeg)};
        new SearchableSingleChoiceDialogBuilder<>(this, formats, formatNames)
                .setTitle(R.string.pdf_export_format)
                .setOnSingleChoiceClickListener((dialog, which, item, isChecked) -> {
                    if (!isChecked) return;
                    mExportExtension = item == 0 ? "png" : "jpg";
                    mExportMimeType = item == 0 ? "image/png" : "image/jpeg";
                    dialog.dismiss();
                    showExportRangeDialog();
                }).show();
    }

    private void showExportRangeDialog() {
        new TextInputDialogBuilder(this, R.string.pdf_export_range)
                .setTitle(R.string.pdf_export_range)
                .setHelperText(R.string.pdf_export_range_hint)
                .setInputInputType(InputType.TYPE_CLASS_TEXT)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which, input, isChecked) -> {
                    try {
                        mExportPages = new ArrayDeque<>(parsePageRange(input.toString(), mPageCount));
                        if (mExportPages.size() == 1) {
                            String filename = getExportBaseName() + "-page-"
                                    + String.format(Locale.US, "%03d", mExportPages.peekFirst() + 1)
                                    + "." + mExportExtension;
                            if ("png".equals(mExportExtension)) {
                                mExportPngDocument.launch(filename);
                            } else mExportJpegDocument.launch(filename);
                        } else {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            mExportDirectoryPicker.launch(intent);
                        }
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    @NonNull
    private List<Integer> parsePageRange(@NonNull String value, int pageCount) {
        Set<Integer> pages = new TreeSet<>();
        try {
            for (String part : value.split(",")) {
                String range = part.trim();
                if (range.isEmpty()) {
                    throw new IllegalArgumentException();
                }
                String[] bounds = range.split("-", -1);
                int first = Integer.parseInt(bounds[0].trim());
                int last = bounds.length == 1 ? first : Integer.parseInt(bounds[1].trim());
                if (bounds.length > 2 || first < 1 || last < first || last > pageCount) {
                    throw new IllegalArgumentException();
                }
                for (int page = first; page <= last; page++) {
                    pages.add(page - 1);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(getString(R.string.pdf_export_invalid_range, pageCount));
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.pdf_export_invalid_range, pageCount));
        }
        return new ArrayList<>(pages);
    }

    private void exportNextPage() {
        if (mExportDirectory == null || mExportPages == null || mExportPages.isEmpty()) {
            mProgress.setVisibility(View.GONE);
            if (mExportDirectory != null) {
                Toast.makeText(this, R.string.pdf_export_complete, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        mProgress.setVisibility(View.VISIBLE);
        int pageIndex = mExportPages.removeFirst();
        mRenderController.renderPage(pageIndex, 2048, new PdfRenderController.PageListener() {
            @Override
            public void onPageRendered(@NonNull Bitmap bitmap) {
                ThreadUtils.postOnBackgroundThread(() -> {
                    try {
                        String name = getExportBaseName() + "-page-" + String.format(Locale.US, "%03d", pageIndex + 1)
                                + "." + mExportExtension;
                        DocumentFile file = mExportDirectory.createFile(mExportMimeType, name);
                        if (file == null) throw new IOException("Unable to create export file");
                        try (OutputStream output = getContentResolver().openOutputStream(file.getUri())) {
                            if (output == null || !bitmap.compress("png".equals(mExportExtension)
                                            ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                                    95, output))
                                throw new IOException("Unable to write export file");
                        }
                        runOnUiThread(() -> exportNextPage());
                    } catch (Throwable e) {
                        runOnUiThread(() -> {
                            mProgress.setVisibility(View.GONE);
                            Toast.makeText(PdfViewerActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                mProgress.setVisibility(View.GONE);
                Toast.makeText(PdfViewerActivity.this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exportSinglePage(@Nullable Uri destination) {
        if (destination == null || mExportPages == null || mExportPages.isEmpty()) return;
        mProgress.setVisibility(View.VISIBLE);
        int pageIndex = mExportPages.removeFirst();
        mRenderController.renderPage(pageIndex, 2048, new PdfRenderController.PageListener() {
            @Override
            public void onPageRendered(@NonNull Bitmap bitmap) {
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (OutputStream output = getContentResolver().openOutputStream(destination)) {
                        if (output == null || !bitmap.compress("png".equals(mExportExtension)
                                ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, output)) {
                            throw new IOException("Unable to write export file");
                        }
                        runOnUiThread(() -> {
                            mProgress.setVisibility(View.GONE);
                            Toast.makeText(PdfViewerActivity.this, R.string.pdf_export_complete, Toast.LENGTH_SHORT).show();
                        });
                    } catch (Throwable e) {
                        runOnUiThread(() -> {
                            mProgress.setVisibility(View.GONE);
                            Toast.makeText(PdfViewerActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                mProgress.setVisibility(View.GONE);
                Toast.makeText(PdfViewerActivity.this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private String getExportBaseName() {
        String name = Paths.sanitizeFilename(getDocumentTitle(), "_",
                Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED);
        if (name == null) {
            return null;
        }
        return name.endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }

    private void printDocument() {
        if (mDocumentPath == null || mPageCount == 0) return;
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager == null) return;
        printManager.print(getDocumentTitle(), new PrintDocumentAdapter() {
            @Override
            public void onLayout(@NonNull PrintAttributes oldAttributes, @NonNull PrintAttributes newAttributes,
                                 @Nullable CancellationSignal cancellationSignal, @NonNull LayoutResultCallback callback,
                                 @Nullable Bundle extras) {
                if (cancellationSignal != null && cancellationSignal.isCanceled()) return;
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(getDocumentTitle())
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(mPageCount)
                        .build();
                callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
            }

            @Override
            public void onWrite(@NonNull PageRange[] pages, @NonNull ParcelFileDescriptor destination,
                                @NonNull CancellationSignal cancellationSignal, @NonNull WriteResultCallback callback) {
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (InputStream input = mDocumentPath.openInputStream();
                         FileOutputStream output = new FileOutputStream(destination.getFileDescriptor())) {
                        byte[] buffer = new byte[16 * 1024];
                        int count;
                        while (!cancellationSignal.isCanceled() && (count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                        if (cancellationSignal.isCanceled()) {
                            callback.onWriteCancelled();
                        } else {
                            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                        }
                    } catch (Throwable e) {
                        callback.onWriteFailed(e.getMessage());
                    } finally {
                        try {
                            destination.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
            }
        }, null);
    }

    private void setDocumentTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mDocumentPath.getName());
        }
    }

    @NonNull
    private String getDocumentTitle() {
        return mDocumentPath != null ? mDocumentPath.getName() : getString(R.string.title_pdf_viewer);
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        if (mPageAdapter != null) {
            mPageAdapter.clear();
        }
        if (mRenderController != null) {
            mRenderController.close();
        }
        super.onDestroy();
    }
}
