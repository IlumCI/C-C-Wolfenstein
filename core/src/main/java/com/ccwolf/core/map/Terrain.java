package com.ccwolf.core.map;

/**
 * A single terrain class. Every tile of a {@link TileMap} is one of these.
 *
 * <p>The {@code glyph} is what appears in a {@code .map} text file, which keeps maps
 * hand-editable and diffable.
 */
public enum Terrain {
    /** Open ground. The default. */
    GRASS('.', true, 1.0f),
    /** Cobbled road: units move noticeably faster along it. */
    ROAD('=', true, 0.65f),
    /** Bombed-out ground. Passable but slow, and infantry take cover here. */
    RUBBLE(':', true, 1.7f),
    /** Ground with a uranium seam under it. Harvestable, otherwise normal ground. */
    ORE('*', true, 1.1f),
    /** Rivers and flooded craters. Impassable to everything in this build. */
    WATER('~', false, Float.POSITIVE_INFINITY),
    /** Ruined masonry, bunker walls, dense forest. Blocks movement and sight lines. */
    WALL('#', false, Float.POSITIVE_INFINITY),

    // --- the Germania vocabulary, appended so nothing existing renumbers -------------------

    /**
     * Poured superconcrete: the Regime's monolith construction. A wall the size of a city
     * block, shutter-marked and stained, and no shell in this game brings one down.
     */
    SUPERCRETE('W', false, Float.POSITIVE_INFINITY),
    /** Monumental dressed stone: dome, arch, palace plinth. Blocks movement and sight. */
    MARBLE('M', false, Float.POSITIVE_INFINITY),
    /** Granite slab paving: parade grounds and plazas. Fast to cross, nothing to hide behind. */
    PAVEMENT('_', true, 0.8f),
    /**
     * Poured autobahn. The fastest ground in the game and the most exposed: the grand axis
     * is a kill zone with lane markings.
     */
    HIGHWAY('H', true, 0.55f);

    private static final Terrain[] BY_GLYPH = new Terrain[128];

    static {
        for (Terrain t : values()) {
            BY_GLYPH[t.glyph] = t;
        }
    }

    private final char glyph;
    private final boolean passable;
    private final float moveCost;

    Terrain(char glyph, boolean passable, float moveCost) {
        this.glyph = glyph;
        this.passable = passable;
        this.moveCost = moveCost;
    }

    public char glyph() {
        return glyph;
    }

    public boolean isPassable() {
        return passable;
    }

    /** Relative traversal cost: 1.0 is open ground, lower is faster, higher is slower. */
    public float moveCost() {
        return moveCost;
    }

    /** Whether this terrain blocks line of sight as well as movement. */
    /**
     * Protection this ground offers before anybody digs.
     *
     * <p>Rubble is the only terrain worth anything: broken masonry is what infantry get behind.
     * The javadoc on RUBBLE promised this long before anything read it.
     */
    public int baseCover() {
        switch (this) {
            case RUBBLE:
                return 2;
            case ORE:
                // Waist-high crystal: something, but not much.
                return 1;
            default:
                return 0;
        }
    }

    public boolean blocksSight() {
        return this == WALL || this == SUPERCRETE || this == MARBLE;
    }

    /**
     * @throws IllegalArgumentException if no terrain uses that glyph
     */
    public static Terrain fromGlyph(char c) {
        Terrain t = c < BY_GLYPH.length ? BY_GLYPH[c] : null;
        if (t == null) {
            throw new IllegalArgumentException("Unknown terrain glyph: '" + c + "'");
        }
        return t;
    }
}
