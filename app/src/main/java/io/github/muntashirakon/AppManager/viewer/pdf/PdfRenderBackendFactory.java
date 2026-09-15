// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.os.Build;

import io.github.muntashirakon.AppManager.BuildConfig;

final class PdfRenderBackendFactory {
    private PdfRenderBackendFactory() {
    }

    static PdfRenderBackend create() {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("PdfRenderBackend", "Using PdfRenderer backend on API " + Build.VERSION.SDK_INT);
        }
        return new PdfRendererBackend();
    }
}
