// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.util.Size;

import androidx.annotation.NonNull;

import com.j256.simplemagic.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ThumbnailUtilsCompat;
import io.github.muntashirakon.AppManager.fm.FmItem;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.PathContentInfo;
import io.github.muntashirakon.svg.SVG;
import io.github.muntashirakon.svg.SVGParser;
import io.github.muntashirakon.util.UiUtils;

public class FmIconFetcher implements ImageLoader.ImageFetcherInterface {
    @NonNull
    private final FmItem mFmItem;

    public FmIconFetcher(@NonNull FmItem fmItem) {
        mFmItem = fmItem;
    }

    @NonNull
    @Override
    public ImageLoader.ImageFetcherResult fetchImage(@NonNull String tag) {
        PathContentInfo contentInfo = mFmItem.getContentInfo();
        if (contentInfo == null) {
            contentInfo = mFmItem.path.getPathContentInfo();
            mFmItem.setContentInfo(contentInfo);
        }
        String mimeType = contentInfo.getMimeType();
        int drawableRes = FmIcons.getDrawableFromType(mimeType);
        int padding = UiUtils.dpToPx(ContextUtils.getContext(), 4);
        int length = UiUtils.dpToPx(ContextUtils.getContext(), 40);
        ImageLoader.DefaultImage defaultImage = new ImageLoader.DefaultImageDrawableRes("drawable_" + drawableRes, drawableRes, padding);
        Size size = new Size(length, length);
        if (FmIcons.isAudio(drawableRes)) {
            try {
                Bitmap bitmap = ThumbnailUtilsCompat.createAudioThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null);
                return new ImageLoader.ImageFetcherResult(tag, bitmap, false, true, defaultImage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (FmIcons.isVideo(drawableRes)) {
            try {
                Bitmap bitmap = ThumbnailUtilsCompat.createVideoThumbnail(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path), size, null);
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (FmIcons.isImage(drawableRes)) {
            if (ContentType.SVG.getMimeType().equals(mimeType)) {
                // Load SVG image
                try (InputStream is = mFmItem.path.openInputStream()) {
                    SVG svg = SVGParser.getSVGFromInputStream(is);
                    Bitmap bitmap = svg.getBitmap();
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true), false, true, defaultImage);
                } catch (Throwable th) {
                    // There can be runtime exceptions
                    th.printStackTrace();
                }
            } else {
                byte[] bytes = mFmItem.path.getContentAsBinary();
                if (bytes.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                                false, true, defaultImage);
                    }
                }
            }
        } else if (FmIcons.isEbook(drawableRes)) {
            if (ContentType.EPUB.getMimeType().equals(mimeType)) {
                Bitmap bitmap = FmIcons.generateEpubCover(mFmItem.path);
                if (bitmap != null) {
                    return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                            false, true, defaultImage);
                }
            }
        } else if (FmIcons.isFont(drawableRes)) {
            Bitmap bitmap = FmIcons.generateFontBitmap(mFmItem.path);
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, bitmap,
                        false, true, defaultImage);
            }
        } else if (FmIcons.isPdf(drawableRes)) {
            Bitmap bitmap = FmIcons.generatePdfBitmap(ContextUtils.getContext(), FmProvider.getContentUri(mFmItem.path));
            if (bitmap != null) {
                return new ImageLoader.ImageFetcherResult(tag, getThumbnail(bitmap, size, true),
                        false, true, defaultImage);
            }
        } else if (FmIcons.isGeneric(drawableRes)) {
            String extension = mFmItem.path.getExtension();
            if (extension != null) {
                // Generate icon from extension (at most 4 characters)
                int len = Math.min(extension.length(), 4);
                String shortExt = extension.substring(0, len).toUpperCase(Locale.ROOT);
                String extTag = "fm_ext_" + shortExt;
                return new ImageLoader.ImageFetcherResult(tag, null,
                        new ImageLoader.DefaultImageString(extTag, shortExt));
            }
            if (mFmItem.path.canExecute()) {
                // Generate executable string
                drawableRes = R.drawable.ic_frost_termux;
                return new ImageLoader.ImageFetcherResult(tag, null,
                        new ImageLoader.DefaultImageDrawableRes("drawable_" + drawableRes, drawableRes, padding));
            }
        }
        // TODO: 24/5/23 Check APK, XAPK, APKS, APKM icons
        return new ImageLoader.ImageFetcherResult(tag, null, defaultImage);
    }

    private Bitmap getThumbnail(@NonNull Bitmap bitmap, @NonNull Size size, boolean recycle) {
        return ThumbnailUtils.extractThumbnail(bitmap, size.getWidth(), size.getHeight(), recycle ? ThumbnailUtils.OPTIONS_RECYCLE_INPUT : 0);
    }
}