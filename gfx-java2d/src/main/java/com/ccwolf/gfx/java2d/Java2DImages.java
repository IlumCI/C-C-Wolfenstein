package com.ccwolf.gfx.java2d;

import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.ImageFactory;

/** Installs the AWT image backend. Call once, before any sprite is baked. */
public final class Java2DImages implements ImageFactory {

    private Java2DImages() {
    }

    public static void install() {
        if (!Gfx.isInstalled()) {
            Gfx.install(new Java2DImages());
        }
    }

    @Override
    public Image create(int[] argb, int width, int height) {
        return Java2DImage.fromPixels(argb, width, height);
    }

    @Override
    public Image createOpaque(int[] argb, int width, int height) {
        return Java2DImage.fromPixels(argb, width, height, true);
    }
}
