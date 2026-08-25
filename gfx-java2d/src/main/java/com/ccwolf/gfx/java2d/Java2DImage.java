package com.ccwolf.gfx.java2d;

import com.ccwolf.gfx.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** A baked sprite living in an AWT {@link BufferedImage}. */
public final class Java2DImage implements Image {

    private final BufferedImage image;

    /**
     * Tinted variants, made once and kept.
     *
     * <p>The renderer asks for the same one or two tints on the same sprites every frame — a
     * disabled generator is disabled for four hundred ticks — so recolouring per frame would be
     * thousands of wasted pixel loops a second.
     */
    private Map<Integer, Java2DImage> tints;

    Java2DImage(BufferedImage image) {
        this.image = image;
    }

    /** Wraps an existing buffer, sharing it rather than copying. */
    public static Java2DImage of(BufferedImage image) {
        return new Java2DImage(image);
    }

    public static Java2DImage fromPixels(int[] argb, int width, int height) {
        return fromPixels(argb, width, height, false);
    }

    /**
     * @param opaque true when no pixel has alpha, which lets AWT copy rather than composite
     */
    public static Java2DImage fromPixels(int[] argb, int width, int height, boolean opaque) {
        BufferedImage image = new BufferedImage(width, height,
                opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return new Java2DImage(image);
    }

    BufferedImage awt() {
        return image;
    }

    @Override
    public int width() {
        return image.getWidth();
    }

    @Override
    public int height() {
        return image.getHeight();
    }

    @Override
    public int pixel(int x, int y) {
        return image.getRGB(x, y);
    }

    @Override
    public Image tinted(int argb) {
        if (argb == 0) {
            return this;
        }
        if (tints == null) {
            tints = new HashMap<Integer, Java2DImage>(4);
        }
        Java2DImage cached = tints.get(Integer.valueOf(argb));
        if (cached != null) {
            return cached;
        }

        // Source-atop: the tint lands only where the sprite is already opaque, so the artwork
        // keeps its silhouette instead of picking up a coloured box.
        int width = image.getWidth();
        int height = image.getHeight();
        int strength = argb >>> 24;
        int tintR = (argb >> 16) & 0xFF;
        int tintG = (argb >> 8) & 0xFF;
        int tintB = argb & 0xFF;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = image.getRGB(x, y);
                int srcA = src >>> 24;
                if (srcA == 0) {
                    continue;
                }
                int blend = strength * srcA / 255;
                int r = mix((src >> 16) & 0xFF, tintR, blend);
                int g = mix((src >> 8) & 0xFF, tintG, blend);
                int b = mix(src & 0xFF, tintB, blend);
                out.setRGB(x, y, (srcA << 24) | (r << 16) | (g << 8) | b);
            }
        }

        Java2DImage tintedImage = new Java2DImage(out);
        tints.put(Integer.valueOf(argb), tintedImage);
        return tintedImage;
    }

    private static int mix(int base, int over, int weight) {
        return (base * (255 - weight) + over * weight) / 255;
    }
}
