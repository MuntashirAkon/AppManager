// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfRenderService extends Service {
    private final Object mRendererLock = new Object();
    private final ExecutorService mRenderExecutor = Executors.newSingleThreadExecutor();
    private final List<Future<?>> mRenderTasks = new ArrayList<>();
    @Nullable
    private PdfRenderBackend mRenderer;

    private final IPdfRenderService.Stub mBinder = new IPdfRenderService.Stub() {
        @Override
        public void openDocument(ParcelFileDescriptor fileDescriptor) throws RemoteException {
            if (fileDescriptor == null) {
                throw new RemoteException("Missing PDF file descriptor");
            }
            synchronized (mRendererLock) {
                closeRendererLocked();
                try {
                    PdfRenderBackend renderer = PdfRenderBackendFactory.create();
                    renderer.open(fileDescriptor);
                    mRenderer = renderer;
                } catch (IOException | RuntimeException e) {
                    try {
                        fileDescriptor.close();
                    } catch (IOException ignored) {
                    }
                    throw remoteException(e);
                }
            }
        }

        @Override
        public int getPageCount() throws RemoteException {
            synchronized (mRendererLock) {
                if (mRenderer == null) throw new RemoteException("PDF renderer is closed");
                return mRenderer.getPageCount();
            }
        }

        @Override
        public int[] getPageDimensions(int pageIndex) throws RemoteException {
            synchronized (mRendererLock) {
                if (mRenderer == null) {
                    throw new RemoteException("PDF renderer is closed");
                }
                if (pageIndex < 0 || pageIndex >= mRenderer.getPageCount()) {
                    throw new RemoteException("Invalid PDF page");
                }
                try {
                    return mRenderer.getPageDimensions(pageIndex);
                } catch (RuntimeException e) {
                    throw remoteException(e);
                }
            }
        }

        @Override
        public ParcelFileDescriptor renderPage(int pageIndex, int targetWidth) throws RemoteException {
            final ParcelFileDescriptor[] pipe;
            synchronized (mRendererLock) {
                if (mRenderer == null) throw new RemoteException("PDF renderer is closed");
                if (pageIndex < 0 || pageIndex >= mRenderer.getPageCount()) {
                    throw new RemoteException("Invalid PDF page");
                }
                try {
                    pipe = ParcelFileDescriptor.createPipe();
                    Future<?> task = mRenderExecutor.submit(() -> renderPageToPipe(pageIndex, targetWidth, pipe[1]));
                    mRenderTasks.add(task);
                } catch (IOException | RuntimeException e) {
                    throw remoteException(e);
                }
            }
            return pipe[0];
        }

        @Override
        public void closeDocument() {
            synchronized (mRendererLock) {
                cancelRenderTasksLocked();
                closeRendererLocked();
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        synchronized (mRendererLock) {
            cancelRenderTasksLocked();
            closeRendererLocked();
        }
        mRenderExecutor.shutdownNow();
        super.onDestroy();
    }

    private void renderPageToPipe(int pageIndex, int targetWidth, ParcelFileDescriptor writeEnd) {
        Bitmap bitmap = null;
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
            synchronized (mRendererLock) {
                if (mRenderer == null) {
                    return;
                }
                bitmap = mRenderer.renderPage(pageIndex, targetWidth);
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException | RuntimeException ignored) {
            // Closing the pipe makes the client observe a failed render.
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private void closeRendererLocked() {
        if (mRenderer != null) {
            mRenderer.close();
            mRenderer = null;
        }
    }

    private void cancelRenderTasksLocked() {
        for (Future<?> task : mRenderTasks) task.cancel(true);
        mRenderTasks.clear();
    }

    private static RemoteException remoteException(Throwable throwable) {
        RemoteException exception = new RemoteException(throwable.getMessage());
        exception.initCause(throwable);
        return exception;
    }
}
