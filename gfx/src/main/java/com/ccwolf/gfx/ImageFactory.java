package com.ccwolf.gfx;

/** Turns finished ARGB pixels into a platform image. Supplied once, at startup. */
public interface ImageFactory {

    Image create(int[] argb, int width, int height);

    /**
     * The same, but promising every pixel is fully opaque.
     *
     * <p>Worth telling a backend about: compositing a large image with alpha costs
     * substantially more than copying one without it, and the ground is the biggest thing
     * drawn every frame.
     */
    Image createOpaque(int[] argb, int width, int height);
}
