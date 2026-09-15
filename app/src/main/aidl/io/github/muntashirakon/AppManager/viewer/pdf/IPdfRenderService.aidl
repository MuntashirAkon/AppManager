// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.os.ParcelFileDescriptor;

interface IPdfRenderService {
    const int CAPABILITY_PASSWORD = 1;
    const int CAPABILITY_ANNOTATIONS = 1 << 1;
    const int CAPABILITY_FORMS = 1 << 2;
    const int CAPABILITY_METADATA = 1 << 3;
    const int CAPABILITY_PDF_WRITE = 1 << 4;

    const int RENDER_MODE_DISPLAY = 0;
    const int RENDER_MODE_PRINT = 1;

    void openDocument(in ParcelFileDescriptor fileDescriptor, String password);
    String getBackendName();
    int getCapabilities();
    int getPageCount();
    int getDocumentLinearizationType();
    int getPdfFormType();
    boolean shouldScaleForPrinting();
    int[] getPageDimensions(int pageIndex);
    ParcelFileDescriptor renderPage(int pageIndex, int targetWidth);
    ParcelFileDescriptor renderPageWithOptions(int pageIndex, int targetWidth, int renderMode, int renderFlags);
    void writeDocument(in ParcelFileDescriptor destination, boolean removePasswordProtection);
    void closeDocument();
}
