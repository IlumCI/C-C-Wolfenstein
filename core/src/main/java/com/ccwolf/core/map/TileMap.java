package com.ccwolf.core.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The battlefield grid: terrain plus the uranium left in each ore tile.
 *
 * <p>Coordinates are tile coordinates with the origin at the top-left. Anything that needs a
 * sub-tile position (units) uses floats where {@code (x + 0.5f, y + 0.5f)} is the tile centre.
 */
public final class TileMap {

    /** Uranium in a freshly seeded ore tile. A harvester load is 500 credits' worth. */
    public static final int ORE_PER_TILE = 700;

    private final int width;
    private final int height;
    private final Terrain[] tiles;
    private final int[] ore;
    private final String name;
    private final List<int[]> spawnPoints;

    TileMap(String name, int width, int height, Terrain[] tiles, List<int[]> spawnPoints) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.ore = new int[tiles.length];
        this.spawnPoints = Collections.unmodifiableList(new ArrayList<int[]>(spawnPoints));
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == Terrain.ORE) {
                ore[i] = ORE_PER_TILE;
            }
        }
    }

    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public Terrain terrain(int x, int y) {
        return inBounds(x, y) ? tiles[y * width + x] : Terrain.WALL;
    }

    public void setTerrain(int x, int y, Terrain t) {
        if (!inBounds(x, y)) {
            return;
        }
        tiles[y * width + x] = t;
        if (t != Terrain.ORE) {
            ore[y * width + x] = 0;
        }
    }

    public boolean isPassable(int x, int y) {
        return terrain(x, y).isPassable();
    }

    public float moveCost(int x, int y) {
        return terrain(x, y).moveCost();
    }

    /** Uranium remaining in this tile, 0 if it was never ore or has been mined out. */
    public int ore(int x, int y) {
        return inBounds(x, y) ? ore[y * width + x] : 0;
    }

    /**
     * Mines up to {@code amount} uranium out of a tile.
     *
     * @return how much was actually taken, which is less than requested once the seam runs dry
     */
    public int takeOre(int x, int y, int amount) {
        if (!inBounds(x, y) || amount <= 0) {
            return 0;
        }
        int i = y * width + x;
        int taken = Math.min(ore[i], amount);
        ore[i] -= taken;
        // The tile stays ORE even when empty: seams regrow (see regrowOre) so a long match
        // cannot deadlock with two broke players staring at each other.
        return taken;
    }

    /**
     * Creeps uranium back into every seam that is not already full.
     *
     * <p>Deliberately slow: it is a trickle that keeps a late game playable, not an income
     * that competes with actually holding ground.
     *
     * @param amount uranium added to each seam tile
     */
    public void regrowOre(int amount) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == Terrain.ORE && ore[i] < ORE_PER_TILE) {
                ore[i] = Math.min(ORE_PER_TILE, ore[i] + amount);
            }
        }
    }

    /** Total uranium left on the map. */
    public int totalOre() {
        int total = 0;
        for (int i = 0; i < ore.length; i++) {
            total += ore[i];
        }
        return total;
    }

    /** Player start positions in map order, each as {@code {x, y}}. */
    public List<int[]> spawnPoints() {
        return spawnPoints;
    }

    public int[] spawnPoint(int index) {
        return spawnPoints.get(index).clone();
    }
}
