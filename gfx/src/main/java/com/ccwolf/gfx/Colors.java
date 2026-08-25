package com.ccwolf.gfx;

/** Packing and unpacking ARGB ints, without a platform behind it. */
public final class Colors {

    private Colors() {
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** The same colour at a different opacity. */
    public static int withAlpha(int color, int a) {
        return ((a < 0 ? 0 : (a > 255 ? 255 : a)) << 24) | (color & 0x00FFFFFF);
    }
}
