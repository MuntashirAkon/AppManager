// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class PdfViewerActivity extends PerProcessActivity {
    private PdfRenderController mRenderController;
    private PdfPageAdapter mPageAdapter;
    private View mProgress;
    private View mError;
    private boolean mDestroyed;

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
        RecyclerView recyclerView = findViewById(R.id.pages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mRenderController = new PdfRenderController(this, new PdfRenderController.Listener() {
            @Override
            public void onDocumentOpened(int pageCount) {
                if (mDestroyed) return;
                mProgress.setVisibility(View.GONE);
                mError.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
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
        recyclerView.setAdapter(mPageAdapter);
        openDocument(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mRenderController != null) {
            mProgress.setVisibility(View.VISIBLE);
            mError.setVisibility(View.GONE);
            findViewById(R.id.pages).setVisibility(View.GONE);
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
        setDocumentTitle(uri);
        mRenderController.open(uri);
    }

    private void setDocumentTitle(@NonNull Uri uri) {
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
        if (filename == null || filename.isEmpty()) {
            filename = uri.getLastPathSegment();
        }
        if (filename == null || filename.isEmpty()) {
            filename = getString(R.string.title_pdf_viewer);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(filename);
        }
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
