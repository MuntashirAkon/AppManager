// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.os.Build;
import android.os.ext.SdkExtensions;

import io.github.muntashirakon.AppManager.BuildConfig;

final class PdfRenderBackendFactory {
    private PdfRenderBackendFactory() {
    }

    static PdfRenderBackend create() {
        final PdfRenderBackend backend;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            backend = new PdfRendererBackend();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            backend = new Pre35PdfRendererBackend();
        } else {
            backend = new Pre30PdfRendererBackend();
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("PdfRenderBackend", "Using " + backend.getClass().getSimpleName()
                    + " on API " + Build.VERSION.SDK_INT);
        }
        return backend;
    }
}
