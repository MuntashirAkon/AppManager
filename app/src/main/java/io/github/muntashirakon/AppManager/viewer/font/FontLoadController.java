// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.font;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontVariationAxis;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.io.Path;

final class FontLoadController implements AutoCloseable {
    interface Listener {
        void onFontsLoaded(@NonNull List<FontFace> faces);

        void onError(@NonNull Throwable throwable);
    }

    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @Nullable
    private Future<?> mLoadTask;
    private boolean mClosed;
    private int mGeneration;

    FontLoadController(@NonNull Listener listener) {
        mListener = listener;
    }

    void load(@NonNull Path path) {
        cancel();
        final int generation = ++mGeneration;
        mLoadTask = mExecutor.submit(() -> loadFont(path, generation));
    }

    private void loadFont(@NonNull Path path, int generation) {
        File file = path.getFile();
        boolean deleteFile = false;
        try {
            if (file == null || !file.canRead()) {
                file = FileCache.getGlobalFileCache().getCachedFile(path);
                deleteFile = true;
            }
            if (!file.isFile() || !file.canRead()) {
                throw new IOException("Unable to read font");
            }

            List<FontFace> faces;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                faces = loadFaces(file);
            } else {
                Typeface typeface = Typeface.createFromFile(file);
                // Older platform implementations may return the default typeface
                // for invalid input instead of reporting a failure.
                if (typeface == null || Typeface.DEFAULT.equals(typeface)) {
                    throw new IOException("Unsupported or invalid font");
                }
                faces = Collections.singletonList(createFace(typeface, -1, file, 0));
            }
            if (faces.isEmpty()) {
                throw new IOException("Unsupported or invalid font");
            }
            if (isCancelled(generation)) {
                return;
            }
            List<FontFace> loadedFaces = faces;
            mMainHandler.post(() -> {
                if (!isCancelled(generation)) {
                    mListener.onFontsLoaded(loadedFaces);
                }
            });
        } catch (Throwable throwable) {
            if (isCancelled(generation) || Thread.currentThread().isInterrupted()) {
                return;
            }
            mMainHandler.post(() -> {
                if (!isCancelled(generation)) {
                    mListener.onError(throwable);
                }
            });
        } finally {
            if (deleteFile) {
                // The Typeface keeps the mapped font data alive after creation.
                // The temporary provider cache file is no longer needed here.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    @NonNull
    private static List<FontFace> loadFaces(@NonNull File file) throws IOException {
        List<FontFace> faces = new ArrayList<>();
        for (int index = 0; index < 256; ++index) {
            try {
                Typeface.Builder builder = new Typeface.Builder(file);
                if (index > 0) {
                    builder.setTtcIndex(index);
                }
                Typeface typeface = builder.build();
                if (typeface == null) {
                    break;
                }
                faces.add(createFace(typeface, index, file, index));
            } catch (IllegalArgumentException e) {
                if (index == 0) {
                    throw new IOException("Unsupported or invalid font", e);
                }
                break;
            }
        }
        return faces;
    }

    @NonNull
    private static FontFace createFace(@NonNull Typeface typeface, int ttcIndex,
                                       @NonNull File file, int fontIndex) {
        int weight = -1;
        String variationSettings = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            weight = typeface.getWeight();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Font.Builder builder = new Font.Builder(file);
                if (fontIndex > 0) {
                    builder.setTtcIndex(fontIndex);
                }
                Font font = builder.build();
                weight = font.getStyle().getWeight();
                FontVariationAxis[] axes = font.getAxes();
                if (axes != null && axes.length > 0) {
                    variationSettings = FontVariationAxis.toFontVariationSettings(axes);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Typeface metadata remains available if Font cannot describe it.
            }
        }
        return new FontFace(typeface, ttcIndex, weight, typeface.getStyle(), variationSettings);
    }

    private boolean isCancelled(int generation) {
        return mClosed || generation != mGeneration || Thread.currentThread().isInterrupted();
    }

    private void cancel() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        ++mGeneration;
        cancel();
        mMainHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
    }
}
