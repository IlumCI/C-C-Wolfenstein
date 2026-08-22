package com.ccwolf.core.sim;

/**
 * Where one side has been, and how long ago.
 *
 * <p>A gun that can shell further than anyone can see needs somebody to tell it what is out
 * there. This is that record: a coarse map of when each patch of ground was last under the eye
 * of one of the owner's units or buildings.
 *
 * <h2>Why not FogGrid</h2>
 *
 * <p>Fog looks like the obvious home for this and is the wrong one, for a reason that would
 * have been very hard to find later. {@code GameWorld.updateFog} returns immediately when fog
 * is switched off, and the headless harness, the golden-digest generator and every AI test all
 * call {@code setFogEnabled(false)}. A spotting rule built on fog would therefore have been
 * silently inert in exactly the runs the game's balance numbers come from — artillery would
 * have been omniscient in every measurement and blind in play.
 *
 * <p>Fog is also presentational: nothing in the simulation has ever read it. Keeping it that
 * way is worth more than the array this class costs.
 *
 * <h2>Coarse on purpose</h2>
 *
 * <p>One cell per {@link #CELL_TILES} tiles, so a cell means "somewhere around here, recently"
 * rather than "this exact tile, now". That is the loose rule this is meant to express: having
 * seen a place earns you the right to shell it for a while, and a spotter who moves on does not
 * take the whole map with him. It is cheap enough not to matter either way — a 64x64 map is 256
 * cells — so the resolution is a design choice, not a saving.
 */
public final class SightMemory {

    /** Tiles per cell edge. Matches SpatialIndex's cell size, for no reason but consistency. */
    public static final int CELL_TILES = 4;

    /**
     * Long enough ago that nobody remembers.
     *
     * <p>Not -1, and the difference matters. The natural sentinel makes {@code tick - lastSeen}
     * small for the first few hundred ticks of a match, so the entire map would read as
     * recently seen until the clock caught up — the same trap {@code Entity.wasDamagedWithin}
     * documents, and one that would have made artillery omniscient for the opening minutes and
     * nowhere else.
     */
    private static final int NEVER = Integer.MIN_VALUE / 2;

    private final int cellsAcross;
    private final int cellsDown;
    private final int[] lastSeenTick;

    public SightMemory(int mapWidth, int mapHeight) {
        this.cellsAcross = (mapWidth + CELL_TILES - 1) / CELL_TILES;
        this.cellsDown = (mapHeight + CELL_TILES - 1) / CELL_TILES;
        this.lastSeenTick = new int[cellsAcross * cellsDown];
        java.util.Arrays.fill(lastSeenTick, NEVER);
    }

    public int cellsAcross() {
        return cellsAcross;
    }

    public int cellsDown() {
        return cellsDown;
    }

    /**
     * Records that somebody of ours is standing here.
     *
     * <p>Stamps the cell underfoot and its eight neighbours rather than tracing a circle of the
     * unit's sight radius. At four tiles to a cell that covers a sight of five to eight closely
     * enough for a rule this coarse, and it is nine writes instead of twenty-five — which is
     * what keeps this free with five hundred men a side.
     */
    public void see(float x, float y, int tick) {
        int cx = (int) x / CELL_TILES;
        int cy = (int) y / CELL_TILES;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                mark(cx + dx, cy + dy, tick);
            }
        }
    }

    /** Records a single cell, for something seen rather than somewhere stood. */
    public void mark(int cellX, int cellY, int tick) {
        if (cellX < 0 || cellY < 0 || cellX >= cellsAcross || cellY >= cellsDown) {
            return;
        }
        int index = cellY * cellsAcross + cellX;
        // Only ever move forward, the same rule as every other tick stamp in the simulation.
        if (tick > lastSeenTick[index]) {
            lastSeenTick[index] = tick;
        }
    }

    /** Marks the cell a tile falls in. */
    public void markTile(int tileX, int tileY, int tick) {
        mark(tileX / CELL_TILES, tileY / CELL_TILES, tick);
    }

    /** The tick this patch of ground was last seen, or a long way in the past. */
    public int lastSeen(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) {
            return NEVER;
        }
        int cellX = tileX / CELL_TILES;
        int cellY = tileY / CELL_TILES;
        if (cellX >= cellsAcross || cellY >= cellsDown) {
            return NEVER;
        }
        return lastSeenTick[cellY * cellsAcross + cellX];
    }

    /** True if this ground was under our eye within the last {@code window} ticks. */
    public boolean seenWithin(int tileX, int tileY, int tick, int window) {
        int seen = lastSeen(tileX, tileY);
        return seen > NEVER && tick - seen <= window;
    }

    /** How many cells are still within memory. Diagnostic, and cheap enough to fold. */
    public int cellsRemembered(int tick, int window) {
        int count = 0;
        for (int i = 0; i < lastSeenTick.length; i++) {
            if (lastSeenTick[i] > NEVER && tick - lastSeenTick[i] <= window) {
                count++;
            }
        }
        return count;
    }
}
