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
    WALL('#', false, Float.POSITIVE_INFINITY);

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
    public boolean blocksSight() {
        return this == WALL;
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
