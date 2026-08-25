package com.ccwolf.gfx;

/**
 * A baked bitmap the platform can draw.
 *
 * <p>Deliberately opaque. The art pipeline works entirely in {@code int[]} ARGB and only needs
 * a handle back once a sprite is finished, so nothing above this interface has to know whether
 * the pixels ended up in an Android {@code Bitmap} or an AWT {@code BufferedImage}.
 */
public interface Image {

    int width();

    int height();

    /**
     * The ARGB value of one pixel.
     *
     * <p>Here so a test can ask whether a sprite recipe actually drew anything — a recipe that
     * bakes a fully transparent image compiles perfectly and looks like nothing at all.
     */
    int pixel(int x, int y);

    /**
     * A copy of this image with an ARGB colour composited over its opaque pixels, keeping its
     * shape. Backends are expected to cache the result: the renderer asks for the same few
     * tints every frame.
     */
    Image tinted(int argb);
}
