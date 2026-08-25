package com.ccwolf.android.gfx;

import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.ImageFactory;

/** Installs the Android image backend. Call once, before any sprite is baked. */
public final class AndroidImages implements ImageFactory {

    private AndroidImages() {
    }

    public static void install() {
        if (!Gfx.isInstalled()) {
            Gfx.install(new AndroidImages());
        }
    }

    @Override
    public Image create(int[] argb, int width, int height) {
        return AndroidImage.fromPixels(argb, width, height);
    }

    @Override
    public Image createOpaque(int[] argb, int width, int height) {
        return AndroidImage.fromPixels(argb, width, height, true);
    }
}
