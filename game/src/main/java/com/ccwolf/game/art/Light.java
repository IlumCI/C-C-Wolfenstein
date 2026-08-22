package com.ccwolf.game.art;

/**
 * The sun this game is lit by, and the sky it stands under.
 *
 * <p>One instance, shared by every sprite, which is the point of having it at all: the reason the
 * old art never quite held together is that each recipe placed its own highlights, so the light
 * came from the north-west on a tank, from wherever on a rifleman, and from nowhere in particular
 * on a wall. A single light object means the whole game is lit by the same sun by construction
 * rather than by discipline.
 *
 * <p>The default is the standing art direction: an overcast valley where it is never a clear day.
 * The key is weak and comes low from the north-west, the sky fill is cold and does most of the
 * work, and the bounce off the ground is a dirty brown rather than a neutral grey — which is what
 * stops everything looking as though it is standing in a photographer's studio.
 */
public final class Light {

    /**
     * Where the light comes from, pointing from the surface toward the source.
     *
     * <p>North-west and not very high. Low sun means long shading gradients across a curved
     * surface and a cast shadow with some length to it, and both of those read as depth far
     * better than a light overhead, which flattens everything it touches.
     */
    public final float dirX;
    public final float dirY;
    public final float dirZ;

    /** The key's colour and strength. Weak, because this is an overcast valley. */
    public final int keyColor;
    public final float keyStrength;

    /** Fill from the sky above and bounce from below, mixed by which way a face turns. */
    public final int skyColor;
    public final int groundColor;
    public final float ambientStrength;

    /**
     * How pronounced the sculpted relief reads overall.
     *
     * <p>The one number to reach for when the whole game looks too flat or too embossed. Left at
     * one, every stroke is shaded as the shape it says it is; the field exists so the look can be
     * dialled without touching a single recipe.
     */
    public final float relief;

    /** How dark contact shadow gets where geometry crowds itself. */
    public final float occlusion;

    /** How dark a cast shadow gets, and how far the march looks for one. */
    public final float shadow;
    public final int shadowSteps;

    /**
     * A warm up-light from below, the ground throwing the key back at whatever stands on it.
     *
     * <p>One extra dot product, and the difference between a figure that has an underside and a
     * figure whose underside is a black hole. Undersides falling to flat black is the standing
     * tell of a renderer with a single key light.
     */
    public final int bounceColor;
    public final float bounce;

    /** How strongly flesh passes light through itself. See {@code Sculptor.flesh}. */
    public final float subsurface;

    /** A cold edge on the side facing away, which is what holds a silhouette against the ground. */
    public final int rimColor;
    public final float rim;

    private Light(float dirX, float dirY, float dirZ, int keyColor, float keyStrength,
                  int skyColor, int groundColor, float ambientStrength, float relief,
                  float occlusion, float shadow, int shadowSteps, int bounceColor, float bounce,
                  float subsurface, int rimColor, float rim) {
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        this.dirX = dirX / length;
        this.dirY = dirY / length;
        this.dirZ = dirZ / length;
        this.keyColor = keyColor;
        this.keyStrength = keyStrength;
        this.skyColor = skyColor;
        this.groundColor = groundColor;
        this.ambientStrength = ambientStrength;
        this.relief = relief;
        this.occlusion = occlusion;
        this.shadow = shadow;
        this.shadowSteps = shadowSteps;
        this.bounceColor = bounceColor;
        this.bounce = bounce;
        this.subsurface = subsurface;
        this.rimColor = rimColor;
        this.rim = rim;
    }

    /**
     * The valley's own light: overcast, from the north-west, low.
     *
     * <p>Negative Y is north on this map's sprites, so a direction of (-0.55, -0.62, 0.56) is
     * up and to the left at roughly thirty degrees above the horizon.
     */
    public static Light overcast() {
        return new Light(-0.55f, -0.62f, 0.56f,
                0xFFFFF0D8, 1.00f,
                0xFF8FA6BC, 0xFF4A3E2E, 0.62f,
                1.0f,
                0.85f,
                0.55f, 14,
                0xFF6B5A42, 0.22f,
                0.35f,
                0xFF9FB6C9, 0.30f);
    }

    /** The same sun with the relief dialled, for looking at what the height field is doing. */
    public Light withRelief(float newRelief) {
        return new Light(dirX, dirY, dirZ, keyColor, keyStrength, skyColor, groundColor,
                ambientStrength, newRelief, occlusion, shadow, shadowSteps, bounceColor, bounce,
                subsurface, rimColor, rim);
    }
}
