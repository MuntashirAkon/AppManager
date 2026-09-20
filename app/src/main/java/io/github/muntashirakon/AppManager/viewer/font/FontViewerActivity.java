// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.font;

import android.net.Uri;
import android.os.Bundle;
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

import io.github.muntashirakon.AppManager.PerProcessActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class FontViewerActivity extends PerProcessActivity implements FontLoadController.Listener {
    private View mProgress;
    private View mError;
    private TextInputEditText mPreviewInput;
    private LinearLayout mPreviews;
    private FontLoadController mLoadController;
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
        mProgress.setVisibility(View.GONE);
        mPreviewInput.setVisibility(View.GONE);
        mPreviews.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void openFont(@NonNull android.content.Intent intent) {
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null || uris.size() != 1) {
            showError();
            return;
        }
        Path fontPath = Paths.get(uris.get(0));
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
