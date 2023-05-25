// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.svg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;

import androidx.annotation.NonNull;

/**
 * Describes a vector Picture object, and optionally its bounds.
 */
// Copyright 2011 Larva Labs, LLC
public class SVG {
    /**
     * The parsed Picture object.
     */
    @NonNull
    private final Picture picture;

    /**
     * These are the bounds for the SVG specified as a hidden "bounds" layer in the SVG.
     */
    private final RectF bounds;

    /**
     * These are the estimated bounds of the SVG computed from the SVG elements while parsing.
     * Note that this could be null if there was a failure to compute limits (ie. an empty SVG).
     */
    private RectF limits = null;

    /**
     * Construct a new SVG.
     *
     * @param picture the parsed picture object.
     * @param bounds  the bounds computed from the "bounds" layer in the SVG.
     */
    SVG(@NonNull Picture picture, RectF bounds) {
        this.picture = picture;
        this.bounds = bounds;
    }

    /**
     * Set the limits of the SVG, which are the estimated bounds computed by the parser.
     *
     * @param limits the bounds computed while parsing the SVG, may not be entirely accurate.
     */
    void setLimits(RectF limits) {
        this.limits = limits;
    }

    /**
     * Create a picture drawable from the SVG.
     *
     * @return the PictureDrawable.
     */
    @NonNull
    public PictureDrawable createPictureDrawable() {
        return new PictureDrawable(picture);
//        return new PictureDrawable(picture) {
//            @Override
//            public int getIntrinsicWidth() {
//                if (bounds != null) {
//                    return (int) bounds.width();
//                } else if (limits != null) {
//                    return (int) limits.width();
//                } else {
//                    return -1;
//                }
//            }
//
//            @Override
//            public int getIntrinsicHeight() {
//                if (bounds != null) {
//                    return (int) bounds.height();
//                } else if (limits != null) {
//                    return (int) limits.height();
//                } else {
//                    return -1;
//                }
//            }
//        };
    }

    @NonNull
    public Bitmap getBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        picture.draw(canvas);
        return bitmap;
    }

    /**
     * Get the parsed SVG picture data.
     *
     * @return the picture.
     */
    @NonNull
    public Picture getPicture() {
        return picture;
    }

    /**
     * Gets the bounding rectangle for the SVG, if one was specified.
     *
     * @return rectangle representing the bounds.
     */
    public RectF getBounds() {
        return bounds;
    }

    /**
     * Gets the bounding rectangle for the SVG that was computed upon parsing. It may not be entirely accurate for certain curves or transformations, but is often better than nothing.
     *
     * @return rectangle representing the computed bounds.
     */
    public RectF getLimits() {
        return limits;
    }
}