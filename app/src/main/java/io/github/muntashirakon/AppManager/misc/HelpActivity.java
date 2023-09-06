// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.webkit.WebViewClientCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.util.UiUtils;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class HelpActivity extends BaseActivity implements SearchView.OnQueryTextListener {
    private LinearLayoutCompat mContainer;
    private WebView mWebView;
    private LinearLayoutCompat mSearchContainer;
    private SearchView mSearchView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        try {
            setContentView(R.layout.activity_help);
        } catch (Throwable th) {
            openDocsSite();
            return;
        }
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(R.string.user_manual);
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        // Check if docs are available
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
                || ResourceUtil.getRawDataId(this, "index") == 0) {
            // Docs split not installed
            openDocsSite();
            return;
        }
        mContainer = findViewById(R.id.container);
        mWebView = findViewById(R.id.webview);
        UiUtils.applyWindowInsetsAsPaddingNoTop(mContainer);

        // Fix locale issue due to WebView (https://issuetracker.google.com/issues/37113860)
        AppearanceUtils.applyOnlyLocale(this);

        mWebView.setWebViewClient(new WebViewClientImpl());
        mWebView.setNetworkAvailable(false);
        mWebView.getSettings().setAllowContentAccess(false);
        mWebView.loadUrl("file:///android_res/raw/index.html");

        mSearchContainer = findViewById(R.id.search_container);
        Button nextButton = findViewById(R.id.next_button);
        Button previousButton = findViewById(R.id.previous_button);
        mSearchView = findViewById(R.id.search_bar);
        mSearchView.findViewById(com.google.android.material.R.id.search_close_btn).setOnClickListener(v -> {
            mWebView.clearMatches();
            mSearchView.setQuery(null, false);
            Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
            TransitionManager.beginDelayedTransition(mContainer, sharedAxis);
            mSearchContainer.setVisibility(View.GONE);
        });
        mSearchView.setOnQueryTextListener(this);
        nextButton.setOnClickListener(v -> mWebView.findNext(true));
        previousButton.setOnClickListener(v -> mWebView.findNext(false));
        new FastScrollerBuilder(mWebView).useMd2Style().build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_help_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_search) {
            if (mSearchContainer.getVisibility() == View.VISIBLE) {
                mSearchView.setQuery(null, false);
                Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
                TransitionManager.beginDelayedTransition(mContainer, sharedAxis);
                mSearchContainer.setVisibility(View.GONE);
            } else {
                Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, false);
                TransitionManager.beginDelayedTransition(mContainer, sharedAxis);
                mSearchContainer.setVisibility(View.VISIBLE);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mWebView.findAllAsync(newText);
        return true;
    }

    private void openDocsSite() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_message)));
        startActivity(intent);
        finish();
    }

    class WebViewClientImpl extends WebViewClientCompat {
        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri.toString().startsWith("file:///android_res")) {
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        }
    }
}
