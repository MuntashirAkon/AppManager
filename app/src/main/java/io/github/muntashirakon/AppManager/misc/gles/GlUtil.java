// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Some OpenGL utility functions.
 */
// Copyright 2014 Google Inc.
public final class GlUtil {
    public static final String TAG = GlUtil.class.getSimpleName();

    private GlUtil() {}     // do not instantiate

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }
}