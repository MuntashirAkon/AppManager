// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.transition.MaterialSharedAxis;

import java.lang.reflect.Method;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public class HelpActivity extends BaseActivity {
    private LinearLayoutCompat container;
    private WebView webView;
    private LinearLayoutCompat searchContainer;
    private SearchView searchView;
    private static boolean firstTime = true;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_help);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(R.string.instructions);
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        // Check if docs are available
        if (IOUtils.getRawDataId(this, "index") == 0) {
            // Docs split not installed
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_message)));
            startActivity(intent);
            finish();
            return;
        }
        container = findViewById(R.id.container);
        // WebView has to be loaded dynamically to prevent in-app localization issue.
        webView = new WebView(AppManager.getContext());
        if (firstTime) {
            // Recreate if loaded for the first time to prevent localization issue.
            recreate();
            firstTime = false;
            return;
        }
        LinearLayoutCompat webviewWrapper = findViewById(R.id.webview_wrapper);
        webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        webviewWrapper.addView(webView);
        webView.setWebViewClient(new WebViewClientImpl());
        webView.setNetworkAvailable(false);
        WebSettings webSettings = webView.getSettings();
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_ON);
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(webSettings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
            }
        }
        webView.loadUrl("file:///android_res/raw/index.html");

        searchContainer = findViewById(R.id.search_container);
        Button nextButton = findViewById(R.id.next_button);
        Button previousButton = findViewById(R.id.previous_button);
        searchView = findViewById(R.id.search_bar);
        searchView.findViewById(R.id.search_close_btn).setOnClickListener(v -> {
            searchView.setQuery(null, false);
            Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
            TransitionManager.beginDelayedTransition(container, sharedAxis);
            searchContainer.setVisibility(View.GONE);
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                webView.findAll(newText);
                try {
                    // Can't use getMethod() as it's a private method
                    for (Method m : WebView.class.getDeclaredMethods()) {
                        if (m.getName().equals("setFindIsUp")) {
                            m.setAccessible(true);
                            m.invoke(webView, true);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        nextButton.setOnClickListener(v -> webView.findNext(true));
        previousButton.setOnClickListener(v -> webView.findNext(false));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_help_actions, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
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
            if (searchContainer.getVisibility() == View.VISIBLE) {
                searchView.setQuery(null, false);
                Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
                TransitionManager.beginDelayedTransition(container, sharedAxis);
                searchContainer.setVisibility(View.GONE);
            } else {
                Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, false);
                TransitionManager.beginDelayedTransition(container, sharedAxis);
                searchContainer.setVisibility(View.VISIBLE);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class WebViewClientImpl extends WebViewClientCompat {
        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri.toString().startsWith("file://android_res")) {
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        }
    }
}
