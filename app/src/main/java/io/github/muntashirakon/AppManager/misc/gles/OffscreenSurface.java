// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc.gles;

/**
 * Off-screen EGL surface (pbuffer).
 * <p>
 * It's good practice to explicitly release() the surface, preferably from a "finally" block.
 */
// Copyright 2014 Google Inc.
public class OffscreenSurface extends EglSurfaceBase {
    /**
     * Creates an off-screen surface with the specified width and height.
     */
    public OffscreenSurface(EglCore eglCore, int width, int height) {
        super(eglCore);
        createOffscreenSurface(width, height);
    }

    /**
     * Releases any resources associated with the surface.
     */
    public void release() {
        releaseEglSurface();
    }
}