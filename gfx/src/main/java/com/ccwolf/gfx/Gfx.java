package com.ccwolf.gfx;

/**
 * The one place the platform gets named.
 *
 * <p>Sprites are baked from recipes into {@code int[]} pixels long before anything is drawn, so
 * the art pipeline needs exactly one platform capability: turning finished pixels into
 * something drawable. A static install point is blunt, but the alternative — threading a
 * factory through every sprite recipe in the game — buys nothing, because there is only ever
 * one backend alive in a process.
 */
public final class Gfx {

    private static ImageFactory factory;

    private Gfx() {
    }

    /** Called once by the platform shell before any sprite is baked. */
    public static void install(ImageFactory imageFactory) {
        factory = imageFactory;
    }

    public static boolean isInstalled() {
        return factory != null;
    }

    public static Image image(int[] argb, int width, int height) {
        return factory().create(argb, width, height);
    }

    /**
     * An image every pixel of which is opaque.
     *
     * <p>Only for artwork that genuinely has no transparency — the ground. Passing pixels with
     * alpha through here does not make them blend, it makes them wrong.
     */
    public static Image opaqueImage(int[] argb, int width, int height) {
        return factory().createOpaque(argb, width, height);
    }

    private static ImageFactory factory() {
        if (factory == null) {
            throw new IllegalStateException(
                    "No graphics backend installed - call Gfx.install(...) during startup");
        }
        return factory;
    }
}
