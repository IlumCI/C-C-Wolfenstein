package com.ccwolf.game.art;

/**
 * The game's entire colour vocabulary, in the VGA idiom of early-90s id shooters: few hues,
 * hard shading ramps, everything a little dirty.
 *
 * <p>Colours are only ever used through the named ramps below. That constraint is what makes a
 * pile of separately authored sprites look like one game rather than a sprite jumble: a
 * Regime bunker and a Regime helmet are lit from the same four greys.
 *
 * <p>Each ramp runs light to dark, index 0 being the highlight.
 */
public final class WolfPalette {

    private WolfPalette() {
    }

    public static final int CLEAR = 0x00000000;

    /** Regime masonry: the ochre-grey stone of a bunker wall. */
    public static final int[] STONE = {
        0xFF8B8168, 0xFF6E6552, 0xFF554E3E, 0xFF3D382C, 0xFF2A261E,
    };

    /**
     * Regime field uniform: black leather and lacquered plate with a cold highlight.
     *
     * <p>The reference the Regime is drawn from is unrelieved black with exactly one saturated
     * colour on it. An earlier pass had them in steel blue, which read as "generic sci-fi
     * soldier" — the black is most of what makes the silhouette theirs.
     */
    public static final int[] NIGHT = {
        0xFF3E4148, 0xFF2C2E34, 0xFF1E2025, 0xFF141519, 0xFF0A0B0D,
    };

    /** Heavy armour plate: pale gunmetal, the Ubersoldat's steel skin. */
    public static final int[] STEEL = {
        0xFFB8BCC4, 0xFF9096A0, 0xFF6C727C, 0xFF4A4F58, 0xFF2E3238,
    };

    /** Poured concrete: brutalist mass, deliberately joyless. */
    public static final int[] CONCRETE = {
        0xFF7E8087, 0xFF63656B, 0xFF4B4D52, 0xFF35373B, 0xFF232427,
    };

    /** Bare gunmetal: weapons, treads, barrels. */
    public static final int[] GUNMETAL = {
        0xFF5E5E64, 0xFF47474D, 0xFF343439, 0xFF232327, 0xFF141417,
    };

    /**
     * The one saturated colour the Regime allows itself: armbands, banners, lenses, stripes.
     * Kept hot on purpose — against the black it is the whole identity.
     */
    public static final int[] BLOOD = {
        0xFFFF4A3C, 0xFFD8231F, 0xFFA31414, 0xFF6E0D0F, 0xFF3D0709,
    };

    /** Brass fittings, buckles, shell casings, gold trim. */
    public static final int[] BRASS = {
        0xFFE8C860, 0xFFC8A02A, 0xFF9A7A1E, 0xFF6E5614, 0xFF48380D,
    };

    /** Bone: skull emblems, teeth, plaster. */
    public static final int[] BONE = {
        0xFFF2ECD6, 0xFFE0D8BC, 0xFFBFB595, 0xFF938A6E, 0xFF635C48,
    };

    /** Resistance olive drab: canvas, field jackets, improvised armour. */
    public static final int[] OLIVE = {
        0xFF9A9A5C, 0xFF7A7B45, 0xFF5E5F34, 0xFF444526, 0xFF2C2D18,
    };

    /** Resistance leather and timber. */
    public static final int[] LEATHER = {
        0xFFA07A4C, 0xFF7E5D38, 0xFF5E4529, 0xFF43301C, 0xFF2A1E12,
    };

    /** Skin, for the sliver of face under a helmet. */
    public static final int[] FLESH = {
        0xFFE8B98C, 0xFFD09A68, 0xFFB07E58, 0xFF8A5E3E, 0xFF5E3F2A,
    };

    /**
     * Reverse-engineered resonance: neon red bleeding into violet.
     *
     * <p>The Regime's wonder-weapons do not glow the same colour as the ground does. This ramp
     * exists so that the one thing on the battlefield nobody built from first principles is the
     * one thing that is not a colour anything else in this game is — it should look like it is
     * lit from a source the rest of the world does not have.
     *
     * <p>Used only where the containment lets it out: chamber throats, a bore, an impact. Never
     * as a surface anything is painted.
     */
    public static final int[] RESONANCE = {
        0xFFFF5FA8, 0xFFE02B7A, 0xFFA81C6E, 0xFF6E1257, 0xFF35082C,
    };


    /** Occult tech glow: the uranium seams and whatever the Regime is doing with them. */
    public static final int[] OCCULT = {
        0xFFCFFFA8, 0xFF8FE86A, 0xFF5CBE3E, 0xFF357A28, 0xFF1D4418,
    };

    /**
     * Weathered copper: the dome and the state roofs. Planned bright, installed once,
     * never polished again — so it is the patina the city actually wears.
     */
    public static final int[] PATINA = {
        0xFF9CBCA4, 0xFF7AA087, 0xFF5F8570, 0xFF476856, 0xFF304A3C,
    };

    /** Fire, muzzle flash, explosion core. */
    public static final int[] FIRE = {
        0xFFFFF0B0, 0xFFFFD24A, 0xFFF09428, 0xFFC4451E, 0xFF7A2410,
    };

    /** Smoke and scorch. */
    public static final int[] SMOKE = {
        0xFF7E7E78, 0xFF62625C, 0xFF474742, 0xFF32322E, 0xFF1E1E1B,
    };

    // --- terrain --------------------------------------------------------------------------

    /** Valley grass. */
    public static final int[] GRASS = {
        0xFF63713F, 0xFF4E5A34, 0xFF43502C, 0xFF374323, 0xFF29331A,
    };

    /** Churned earth and cart tracks. */
    public static final int[] DIRT = {
        0xFF8A7550, 0xFF6B5C3E, 0xFF57492F, 0xFF443923, 0xFF2F2718,
    };

    /** Cobbled road. */
    public static final int[] COBBLE = {
        0xFF8A8478, 0xFF6E695E, 0xFF565248, 0xFF413E36, 0xFF2C2A25,
    };

    /** River water. */
    public static final int[] WATER = {
        0xFF3F6A96, 0xFF2A4A6E, 0xFF1F3A58, 0xFF16293F, 0xFF0E1B2A,
    };

    private static final int RAMP_LENGTH = 5;

    /**
     * Clamped ramp lookup, so a recipe can walk off the end without exploding.
     *
     * <p>Every ramp in this file is <em>pre-shaded</em>: index 0 is the highlight and index 4
     * the deepest shadow, and a recipe places its own lighting by choosing indices. Two whole
     * rendering pipelines were built and torn out re-learning that this is the design rather
     * than a limitation — a lit renderer has to fight the ramps, and the ramps win.
     */
    public static int shade(int[] ramp, int index) {
        if (index < 0) {
            index = 0;
        }
        if (index >= ramp.length) {
            index = ramp.length - 1;
        }
        return ramp[index];
    }

    /** Blends two colours; used for scorching and fading, not for new palette entries. */
    public static int mix(int a, int b, float t) {
        float u = 1f - t;
        int aa = (int) (((a >>> 24) & 0xFF) * u + ((b >>> 24) & 0xFF) * t);
        int rr = (int) (((a >>> 16) & 0xFF) * u + ((b >>> 16) & 0xFF) * t);
        int gg = (int) (((a >>> 8) & 0xFF) * u + ((b >>> 8) & 0xFF) * t);
        int bb = (int) ((a & 0xFF) * u + (b & 0xFF) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Darkens a colour towards black, for shadowed sides and damage scorch. */
    public static int darken(int color, float amount) {
        return mix(color, 0xFF000000, amount);
    }

    public static int rampLength() {
        return RAMP_LENGTH;
    }
}
