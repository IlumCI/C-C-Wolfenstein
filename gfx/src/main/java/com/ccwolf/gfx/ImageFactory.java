package com.ccwolf.gfx;

/** Turns finished ARGB pixels into a platform image. Supplied once, at startup. */
public interface ImageFactory {

    Image create(int[] argb, int width, int height);
}
