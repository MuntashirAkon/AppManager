// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static android.media.MediaMetadataRetriever.METADATA_KEY_DURATION;
import static android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Objects;

public class ThumbnailUtilsCompat {
    /**
     * Create a thumbnail for given audio file.
     * <p>
     * This method should only be used for files that you have direct access to;
     * if you'd like to work with media hosted outside your app, consider using
     * {@link ContentResolver#loadThumbnail(Uri, Size, CancellationSignal)}
     * which enables remote providers to efficiently cache and invalidate
     * thumbnails.
     *
     * @param context The Context to use when resolving the audio Uri.
     * @param uri     The audio Uri.
     * @param size    The desired thumbnail size.
     * @throws IOException If any trouble was encountered while generating or loading the thumbnail, or if
     *                     {@link CancellationSignal#cancel()} was invoked.
     */
    public static @NonNull Bitmap createAudioThumbnail(@NonNull Context context, @NonNull Uri uri, @NonNull Size size,
                                                       @Nullable CancellationSignal signal) throws IOException {
        // Checkpoint before going deeper
        if (signal != null) signal.throwIfCanceled();

        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            final byte[] raw = retriever.getEmbeddedPicture();
            if (raw != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                return getThumbnail(bitmap, size, true);
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to create thumbnail", e);
        }
        throw new IOException("No album art found");
    }

    /**
     * Create a thumbnail for given video file.
     * <p>
     * This method should only be used for files that you have direct access to;
     * if you'd like to work with media hosted outside your app, consider using
     * {@link ContentResolver#loadThumbnail(Uri, Size, CancellationSignal)}
     * which enables remote providers to efficiently cache and invalidate
     * thumbnails.
     *
     * @param context The Context to use when resolving the video Uri.
     * @param uri     The video file.
     * @param size    The desired thumbnail size.
     * @throws IOException If any trouble was encountered while generating or
     *                     loading the thumbnail, or if
     *                     {@link CancellationSignal#cancel()} was invoked.
     */
    public static @NonNull Bitmap createVideoThumbnail(@NonNull Context context, @NonNull Uri uri, @NonNull Size size,
                                                       @Nullable CancellationSignal signal) throws IOException {
        // Checkpoint before going deeper
        if (signal != null) signal.throwIfCanceled();

        try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(context, uri);

            // Try to retrieve thumbnail from metadata
            final byte[] raw = mmr.getEmbeddedPicture();
            if (raw != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                return getThumbnail(bitmap, size, true);
            }

            final MediaMetadataRetriever.BitmapParams params;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params = new MediaMetadataRetriever.BitmapParams();
                params.setPreferredConfig(Bitmap.Config.ARGB_8888);
            } else params = null;

            // Fall back to middle of video
            // Note: METADATA_KEY_DURATION unit is in ms, not us.
            final long thumbnailTimeUs = Long.parseLong(mmr.extractMetadata(METADATA_KEY_DURATION)) * 1000 / 2;

            // If we're okay with something larger than native format, just
            // return a frame without up-scaling it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return getThumbnail(mmr.getFrameAtTime(thumbnailTimeUs, OPTION_CLOSEST_SYNC, params), size, false);
            } else {
                return getThumbnail(mmr.getFrameAtTime(thumbnailTimeUs, OPTION_CLOSEST_SYNC), size, false);
            }
        } catch (RuntimeException e) {
            throw new IOException("Failed to create thumbnail", e);
        }
    }

    private static Bitmap getThumbnail(@NonNull Bitmap bitmap, @NonNull Size size, boolean recycle) {
        return ThumbnailUtils.extractThumbnail(Objects.requireNonNull(bitmap), size.getWidth(), size.getHeight(),
                recycle ? ThumbnailUtils.OPTIONS_RECYCLE_INPUT : 0);
    }
}
