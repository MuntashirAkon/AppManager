// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.font;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.textfield.TextInputEditText;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.List;
import java.io.FileOutputStream;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FontViewerActivity extends PerProcessActivity implements FontLoadController.Listener {
    private View mProgress;
    private View mError;
    private TextInputEditText mPreviewInput;
    private LinearLayout mPreviews;
    private FontLoadController mLoadController;
    @Nullable
    private Path mFontPath;
    private List<FontFace> mFaces = new ArrayList<>();
    private final List<TextView> mPreviewViews = new ArrayList<>();
    private float mTextSizeSp = 32;
    private boolean mDestroyed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mProgress = findViewById(R.id.progress_linear);
        mError = findViewById(R.id.error);
        mPreviewInput = findViewById(R.id.preview_input);
        mPreviews = findViewById(R.id.previews);
        mPreviewInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreviewText(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mLoadController = new FontLoadController(this);
        mPreviewInput.setEnabled(false);
        openFont(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mLoadController != null) {
            openFont(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_share) {
            shareFont();
            return true;
        } else if (itemId == R.id.action_print) {
            printFont();
            return true;
        } else if (itemId == R.id.action_font_text_size_decrease) {
            setTextSize(Math.max(16, mTextSizeSp - 4));
            return true;
        } else if (itemId == R.id.action_font_text_size_increase) {
            setTextSize(Math.min(96, mTextSizeSp + 4));
            return true;
        } else if (itemId == R.id.action_font_text_size_reset) {
            setTextSize(32);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_font_viewer_actions, menu);
        return true;
    }

    @Override
    public void onFontsLoaded(@NonNull List<FontFace> faces) {
        if (mDestroyed) return;
        mFaces = new ArrayList<>(faces);
        mPreviews.removeAllViews();
        mPreviewViews.clear();
        for (int i = 0; i < faces.size(); ++i) {
            FontFace face = faces.get(i);
            TextView label = new TextView(this);
            label.setText(buildFaceLabel(i + 1, face));
            label.setTextSize(14);
            label.setPadding(0, i == 0 ? 0 : 24, 0, 4);
            mPreviews.addView(label);

            TextView preview = new TextView(this);
            preview.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            preview.setText(mPreviewInput.getText());
            preview.setTextSize(mTextSizeSp);
            preview.setLineSpacing(8, 1f);
            preview.setTextIsSelectable(true);
            preview.setTypeface(face.typeface);
            mPreviews.addView(preview);
            mPreviewViews.add(preview);
        }
        mPreviewInput.setEnabled(true);
        mProgress.setVisibility(View.GONE);
        mError.setVisibility(View.GONE);
        mPreviewInput.setVisibility(View.VISIBLE);
        mPreviews.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(@NonNull Throwable throwable) {
        if (mDestroyed) return;
        mFaces.clear();
        mProgress.setVisibility(View.GONE);
        mPreviewInput.setVisibility(View.GONE);
        mPreviews.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void openFont(@NonNull android.content.Intent intent) {
        mFontPath = null;
        mFaces.clear();
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null || uris.size() != 1) {
            showError();
            return;
        }
        Path fontPath = Paths.get(uris.get(0));
        mFontPath = fontPath;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(fontPath.getName());
        }
        mProgress.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
        mPreviewInput.setEnabled(false);
        mPreviewInput.setVisibility(View.GONE);
        mPreviews.removeAllViews();
        mPreviewViews.clear();
        mPreviews.setVisibility(View.GONE);
        mLoadController.load(fontPath);
    }

    private void shareFont() {
        if (mFontPath == null) {
            return;
        }
        Uri shareUri = FmProvider.getContentUri(mFontPath);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(mFontPath.getType())
                .putExtra(Intent.EXTRA_STREAM, shareUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    private void printFont() {
        if (mFaces.isEmpty()) {
            return;
        }
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager != null) {
            printManager.print(getPrintTitle(), new FontPrintDocumentAdapter(), null);
        }
    }

    @NonNull
    private String getPrintTitle() {
        return mFontPath != null ? mFontPath.getName() : getString(R.string.title_font_viewer);
    }

    private final class FontPrintDocumentAdapter extends PrintDocumentAdapter {
        private PrintAttributes mPrintAttributes;
        private List<Integer> mFacePageCounts = new ArrayList<>();
        private int mPageCount;

        @Override
        public void onLayout(@NonNull PrintAttributes oldAttributes,
                             @NonNull PrintAttributes newAttributes,
                             @Nullable CancellationSignal cancellationSignal,
                             @NonNull LayoutResultCallback callback,
                             @Nullable Bundle extras) {
            if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            mPrintAttributes = newAttributes;
            mFacePageCounts = getFacePageCounts(newAttributes);
            mPageCount = 0;
            for (int pageCount : mFacePageCounts) {
                mPageCount += pageCount;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(getPrintTitle())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(mPageCount)
                    .build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }

        @Override
        public void onWrite(@NonNull PageRange[] pages,
                            @NonNull ParcelFileDescriptor destination,
                            @NonNull CancellationSignal cancellationSignal,
                            @NonNull WriteResultCallback callback) {
            ThreadUtils.postOnBackgroundThread(() -> {
                PrintedPdfDocument document = null;
                try {
                    if (mPrintAttributes == null || mPageCount == 0) {
                        callback.onWriteFailed("No font preview available");
                        return;
                    }
                    document = new PrintedPdfDocument(FontViewerActivity.this, mPrintAttributes);
                    int documentPage = 0;
                    for (int faceIndex = 0; faceIndex < mFaces.size(); ++faceIndex) {
                        int facePageCount = mFacePageCounts.get(faceIndex);
                        for (int facePage = 0; facePage < facePageCount; ++facePage) {
                            if (cancellationSignal.isCanceled()) {
                                callback.onWriteCancelled();
                                return;
                            }
                            if (containsPage(pages, documentPage)) {
                                PrintedPdfDocument.Page page = document.startPage(documentPage + 1);
                                drawFacePage(page, mFaces.get(faceIndex), faceIndex, facePage,
                                        facePageCount);
                                document.finishPage(page);
                            }
                            ++documentPage;
                        }
                    }
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                    } else {
                        try (FileOutputStream output = new FileOutputStream(
                                destination.getFileDescriptor())) {
                            document.writeTo(output);
                        }
                        callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                    }
                } catch (Throwable e) {
                    callback.onWriteFailed(e.getMessage());
                } finally {
                    if (document != null) {
                        document.close();
                    }
                    try {
                        destination.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    @NonNull
    private List<Integer> getFacePageCounts(@NonNull PrintAttributes printAttributes) {
        List<Integer> pageCounts = new ArrayList<>();
        PrintedPdfDocument document = null;
        try {
            document = new PrintedPdfDocument(this, printAttributes);
            PrintedPdfDocument.Page page = document.startPage(1);
            Rect content = page.getInfo().getContentRect();
            document.finishPage(page);
            for (int i = 0; i < mFaces.size(); ++i) {
                pageCounts.add(getFacePageCount(mFaces.get(i), i, content));
            }
        } finally {
            if (document != null) {
                document.close();
            }
        }
        return pageCounts;
    }

    private int getFacePageCount(@NonNull FontFace face, int faceIndex, @NonNull Rect content) {
        StaticLayout title = createPrintLayout(buildFaceLabel(faceIndex + 1, face),
                createPrintTitlePaint(), content.width());
        StaticLayout preview = createPrintLayout(mPreviewInput.getText(),
                createPrintPreviewPaint(face), content.width());
        int previewHeight = Math.max(1, content.height() - title.getHeight() - 24);
        return Math.max(1, (preview.getHeight() + previewHeight - 1) / previewHeight);
    }

    private void drawFacePage(@NonNull PrintedPdfDocument.Page page, @NonNull FontFace face,
                              int faceIndex, int facePage, int facePageCount) {
        Canvas canvas = page.getCanvas();
        Rect content = page.getInfo().getContentRect();
        canvas.drawColor(Color.WHITE);

        StaticLayout title = createPrintLayout(buildFaceLabel(faceIndex + 1, face),
                createPrintTitlePaint(), content.width());
        StaticLayout preview = createPrintLayout(mPreviewInput.getText(),
                createPrintPreviewPaint(face), content.width());
        int previewHeight = Math.max(1, content.height() - title.getHeight() - 24);

        canvas.save();
        canvas.translate(content.left, content.top);
        title.draw(canvas);
        canvas.translate(0, title.getHeight() + 24);
        canvas.clipRect(0, 0, content.width(), previewHeight);
        canvas.translate(0, -facePage * previewHeight);
        preview.draw(canvas);
        canvas.restore();
    }

    @NonNull
    private TextPaint createPrintTitlePaint() {
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(18);
        return paint;
    }

    @NonNull
    private TextPaint createPrintPreviewPaint(@NonNull FontFace face) {
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTypeface(face.typeface);
        paint.setTextSize(mTextSizeSp);
        return paint;
    }

    @NonNull
    private StaticLayout createPrintLayout(@NonNull CharSequence text, @NonNull TextPaint paint,
                                           int width) {
        return new StaticLayout(text, paint, Math.max(1, width), Layout.Alignment.ALIGN_NORMAL,
                1f, 8, false);
    }

    private boolean containsPage(@NonNull PageRange[] pages, int page) {
        for (PageRange range : pages) {
            if (range.getStart() <= page && page <= range.getEnd()) {
                return true;
            }
        }
        return false;
    }

    private void showError() {
        mProgress.setVisibility(View.GONE);
        mPreviewInput.setVisibility(View.GONE);
        mPreviews.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void updatePreviewText(@NonNull CharSequence text) {
        for (TextView preview : mPreviewViews) {
            preview.setText(text);
        }
    }

    private void setTextSize(float textSizeSp) {
        mTextSizeSp = textSizeSp;
        for (TextView preview : mPreviewViews) {
            preview.setTextSize(textSizeSp);
        }
    }

    @NonNull
    private String buildFaceLabel(int faceNumber, @NonNull FontFace face) {
        StringBuilder label = new StringBuilder(getString(R.string.font_viewer_face, faceNumber));
        String style;
        switch (face.style) {
            case android.graphics.Typeface.BOLD:
                style = getString(R.string.font_viewer_style_bold);
                break;
            case android.graphics.Typeface.ITALIC:
                style = getString(R.string.font_viewer_style_italic);
                break;
            case android.graphics.Typeface.BOLD_ITALIC:
                style = getString(R.string.font_viewer_style_bold_italic);
                break;
            default:
                style = getString(R.string.font_viewer_style_regular);
                break;
        }
        label.append(" · ").append(style);
        if (face.weight >= 0) {
            label.append(" · ").append(getString(R.string.font_viewer_weight, face.weight));
        }
        if (face.ttcIndex >= 0) {
            label.append(" · ").append(getString(R.string.font_viewer_ttc_index, face.ttcIndex));
        }
        if (face.variationSettings != null) {
            label.append(" · ").append(getString(R.string.font_viewer_variations,
                    face.variationSettings));
        }
        return label.toString();
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        if (mLoadController != null) {
            mLoadController.close();
        }
        super.onDestroy();
    }
}
