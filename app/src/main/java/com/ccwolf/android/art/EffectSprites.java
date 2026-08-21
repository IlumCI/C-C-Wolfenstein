package com.ccwolf.android.art;

/** Explosions, muzzle flashes, wrecks and craters — the things a battle leaves behind. */
public final class EffectSprites {

    public static final int EXPLOSION_FRAMES = 5;
    public static final int EXPLOSION_SIZE = 32;
    public static final int FLASH_SIZE = 12;
    public static final int WRECK_SIZE = 24;

    private EffectSprites() {
    }

    /**
     * An explosion in five frames: a white-hot core, a fireball, then a smoke ring pulling
     * apart. Drawn with hard palette steps rather than alpha, because a soft gradient looks
     * wrong next to sprites shaded in four flat tones.
     */
    public static PixelCanvas explosion(int frame) {
        PixelCanvas c = new PixelCanvas(EXPLOSION_SIZE, EXPLOSION_SIZE);
        int cx = EXPLOSION_SIZE / 2;
        int cy = EXPLOSION_SIZE / 2;
        int seed = 101 + frame * 47;

        switch (frame) {
            case 0:
                c.ellipse(cx, cy, 5, 5, WolfPalette.shade(WolfPalette.FIRE, 1));
                c.ellipse(cx, cy, 3, 3, WolfPalette.shade(WolfPalette.FIRE, 0));
                break;
            case 1:
                c.ellipse(cx, cy, 9, 8, WolfPalette.shade(WolfPalette.FIRE, 2));
                c.ellipse(cx, cy, 6, 5, WolfPalette.shade(WolfPalette.FIRE, 1));
                c.ellipse(cx, cy, 3, 2, WolfPalette.shade(WolfPalette.FIRE, 0));
                c.speckle(cx - 12, cy - 12, 24, 24, WolfPalette.shade(WolfPalette.FIRE, 2),
                        seed, 17);
                break;
            case 2:
                c.ellipse(cx, cy, 13, 11, WolfPalette.shade(WolfPalette.FIRE, 3));
                c.ellipse(cx, cy, 9, 8, WolfPalette.shade(WolfPalette.FIRE, 2));
                c.ellipse(cx, cy - 1, 5, 4, WolfPalette.shade(WolfPalette.FIRE, 1));
                c.speckle(2, 2, EXPLOSION_SIZE - 4, EXPLOSION_SIZE - 4,
                        WolfPalette.shade(WolfPalette.FIRE, 3), seed, 9);
                break;
            case 3:
                c.ellipse(cx, cy - 1, 14, 12, WolfPalette.shade(WolfPalette.SMOKE, 2));
                c.ellipse(cx, cy - 1, 10, 8, WolfPalette.shade(WolfPalette.SMOKE, 1));
                c.ellipse(cx, cy, 5, 4, WolfPalette.shade(WolfPalette.FIRE, 3));
                c.speckle(0, 0, EXPLOSION_SIZE, EXPLOSION_SIZE,
                        WolfPalette.shade(WolfPalette.SMOKE, 0), seed, 13);
                break;
            case 4:
            default:
                c.ellipse(cx, cy - 3, 13, 10, WolfPalette.shade(WolfPalette.SMOKE, 1));
                c.ellipse(cx, cy - 3, 8, 6, WolfPalette.shade(WolfPalette.SMOKE, 0));
                c.ellipse(cx, cy - 4, 4, 3, 0x00000000);
                break;
        }
        return c;
    }

    /** Muzzle flash: a four-pointed star, because a round blob reads as a bubble. */
    public static PixelCanvas muzzleFlash(int frame) {
        PixelCanvas c = new PixelCanvas(FLASH_SIZE, FLASH_SIZE);
        int cx = FLASH_SIZE / 2;
        int cy = FLASH_SIZE / 2;
        int reach = frame == 0 ? 5 : 3;

        c.hLine(cx - reach, cx + reach, cy, WolfPalette.shade(WolfPalette.FIRE, 1));
        c.vLine(cx, cy - reach, cy + reach, WolfPalette.shade(WolfPalette.FIRE, 1));
        c.ellipse(cx, cy, 2, 2, WolfPalette.shade(WolfPalette.FIRE, 0));
        c.px(cx - reach, cy, WolfPalette.shade(WolfPalette.FIRE, 2));
        c.px(cx + reach, cy, WolfPalette.shade(WolfPalette.FIRE, 2));
        return c;
    }

    /**
     * What is left after something dies: a scorched crater with a burnt-out hulk in it.
     * Four variants so a wrecked column does not look stamped out.
     */
    public static PixelCanvas wreck(int variant) {
        PixelCanvas c = new PixelCanvas(WRECK_SIZE, WRECK_SIZE);
        int cx = WRECK_SIZE / 2;
        int cy = WRECK_SIZE / 2;
        int seed = 313 + variant * 91;

        c.ellipse(cx, cy, 9, 7, WolfPalette.shade(WolfPalette.DIRT, 4));
        c.ellipse(cx, cy, 7, 5, 0xFF1B1712);
        c.speckle(cx - 9, cy - 7, 18, 14, WolfPalette.shade(WolfPalette.SMOKE, 3), seed, 5);

        // Twisted plate sticking out of the hole.
        c.rect(cx - 4 + (variant % 3), cy - 2, 7, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        c.hLine(cx - 4 + (variant % 3), cx + 2 + (variant % 3), cy - 2,
                WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        c.line(cx + 2, cy - 2, cx + 6, cy - 5, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        c.px(cx - 2, cy + 1, WolfPalette.shade(WolfPalette.FIRE, 3));
        return c;
    }
}
