// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.os.ParcelFileDescriptor;

interface IPdfRenderService {
    void openDocument(in ParcelFileDescriptor fileDescriptor);
    int getPageCount();
    ParcelFileDescriptor renderPage(int pageIndex, int targetWidth);
    void closeDocument();
}
