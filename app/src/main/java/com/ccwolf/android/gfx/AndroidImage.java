package com.ccwolf.android.gfx;

import android.graphics.Bitmap;
import com.ccwolf.gfx.Image;
import java.util.HashMap;
import java.util.Map;

/** A baked sprite living in an Android {@link Bitmap}. */
public final class AndroidImage implements Image {

    private final Bitmap bitmap;

    /**
     * Tinted variants, made once and kept.
     *
     * <p>Android can tint at draw time with a colour filter, but caching keeps both backends
     * behaving the same way — and the renderer only ever asks for one or two tints.
     */
    private Map<Integer, AndroidImage> tints;

    private AndroidImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public static AndroidImage fromPixels(int[] argb, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(argb, 0, width, 0, 0, width, height);
        return new AndroidImage(bitmap);
    }

    public Bitmap bitmap() {
        return bitmap;
    }

    @Override
    public int width() {
        return bitmap.getWidth();
    }

    @Override
    public int height() {
        return bitmap.getHeight();
    }

    @Override
    public int pixel(int x, int y) {
        return bitmap.getPixel(x, y);
    }

    @Override
    public Image tinted(int argb) {
        if (argb == 0) {
            return this;
        }
        if (tints == null) {
            tints = new HashMap<Integer, AndroidImage>(4);
        }
        AndroidImage cached = tints.get(Integer.valueOf(argb));
        if (cached != null) {
            return cached;
        }

        // Source-atop by hand rather than through a colour filter, so this backend and the AWT
        // one produce the same pixels — the contact sheets are compared across both.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int strength = argb >>> 24;
        int tintR = (argb >> 16) & 0xFF;
        int tintG = (argb >> 8) & 0xFF;
        int tintB = argb & 0xFF;
        for (int i = 0; i < pixels.length; i++) {
            int src = pixels[i];
            int srcA = src >>> 24;
            if (srcA == 0) {
                continue;
            }
            int blend = strength * srcA / 255;
            int r = mix((src >> 16) & 0xFF, tintR, blend);
            int g = mix((src >> 8) & 0xFF, tintG, blend);
            int b = mix(src & 0xFF, tintB, blend);
            pixels[i] = (srcA << 24) | (r << 16) | (g << 8) | b;
        }

        AndroidImage tintedImage = fromPixels(pixels, width, height);
        tints.put(Integer.valueOf(argb), tintedImage);
        return tintedImage;
    }

    private static int mix(int base, int over, int weight) {
        return (base * (255 - weight) + over * weight) / 255;
    }
}
