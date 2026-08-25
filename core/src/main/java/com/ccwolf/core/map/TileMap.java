package com.ccwolf.core.map;

import com.ccwolf.core.combat.Earthworks;
import com.ccwolf.core.entity.Faction;
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

    /**
     * How much protection each tile offers infantry standing in it, 0 to {@link #MAX_COVER}.
     *
     * <p>A third parallel array alongside terrain and ore, for the same reason those are flat
     * arrays: there is no Tile object to hang a field on, and nothing outside this class
     * indexes into them.
     *
     * <p>Seeded from terrain and then mutable, because it is not only a property of the ground.
     * Men dig in and raise it; shellfire flattens it back down.
     */
    private final byte[] cover;

    /**
     * Which side cut the works on each tile, or {@link #UNBUILT} where nobody has.
     *
     * <p>Who <em>built</em> it, deliberately, and never who holds it. A trench outlives the men
     * who dug it — that is stated in {@code InfluenceGrid}'s javadoc and is why earthworks are
     * not a source of control — so this is a record of construction, which cannot change, rather
     * than of occupation, which changes constantly. A Regime bunker taken by partisans is still
     * a concrete bunker; keying the look to whoever is standing in it would make a line flicker
     * between two styles as men walked past.
     *
     * <p>It exists for the picture and only for the picture. Nothing in the simulation reads it,
     * and in particular the influence field must not: control is worked out from units and
     * buildings, and letting held ground vouch for itself is a feedback loop, not a front.
     */
    private final byte[] builder;
    private final String name;
    private final List<int[]> spawnPoints;

    TileMap(String name, int width, int height, Terrain[] tiles, List<int[]> spawnPoints) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.ore = new int[tiles.length];
        this.spawnPoints = Collections.unmodifiableList(new ArrayList<int[]>(spawnPoints));
        this.cover = new byte[tiles.length];
        this.builder = new byte[tiles.length];
        java.util.Arrays.fill(this.builder, UNBUILT);
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == Terrain.ORE) {
                ore[i] = ORE_PER_TILE;
            }
            cover[i] = (byte) tiles[i].baseCover();
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

    /**
     * What it costs to cross a tile: the ground, plus anything dug into it.
     *
     * <p>The single funnel every path cost goes through, which is why trenches hook in here and
     * nowhere else — one expression decides both what the pathfinder routes around and how fast
     * a man actually walks, so the two can never disagree.
     */
    public float moveCost(int x, int y) {
        return terrain(x, y).moveCost() + Earthworks.moveCostFor(entrenchment(x, y));
    }

    /** Uranium remaining in this tile, 0 if it was never ore or has been mined out. */
    /** The most protection a tile can offer: a proper trench. */
    public static final int MAX_COVER = 5;

    /** Nobody has broken ground here. Not a faction ordinal, so it cannot be mistaken for one. */
    private static final byte UNBUILT = -1;

    /**
     * The depth at which ordinary cover stops adding anything.
     *
     * <p>Deliberately not {@link #MAX_COVER}, and this is the whole reason a fifth level could
     * be added without rebalancing the game. Damage in cover is a fraction of the way to the
     * maximum, so raising the maximum to five would have turned today's deepest trench from
     * four-quarters into four-fifths and quietly weakened rubble along with it. Instead the
     * ordinary reduction still divides by four with the level clamped to four, so every depth
     * that existed yesterday protects exactly as it did — and the fifth level is a different
     * kind of protection rather than more of the same: a roof, which stops what comes down.
     */
    public static final int FULL_COVER = 4;

    /**
     * Protection for anything standing on a tile, 0 to {@link #MAX_COVER}.
     *
     * <p>Off the map reads as no cover, matching how {@code terrain} reports a wall — a query
     * about somewhere that does not exist should answer harmlessly rather than throw.
     */
    public int cover(int x, int y) {
        return contains(x, y) ? cover[y * width + x] : 0;
    }

    public void setCover(int x, int y, int value) {
        if (contains(x, y)) {
            cover[y * width + x] = (byte) Math.max(0, Math.min(MAX_COVER, value));
            if (entrenchment(x, y) <= 0) {
                // Flattened back to what the ground itself offers: whatever was built here is
                // gone, and the next side to break ground gets to claim it.
                builder[y * width + x] = UNBUILT;
            }
        }
    }

    /**
     * Records which side cut the works on a tile, if nobody has yet.
     *
     * <p>First to break ground keeps it. Deepening someone else's trench does not repaint it —
     * a line changing hands and being improved is still the line that was built there, and a
     * style that flipped halfway through a fight would read as a rendering bug.
     */
    public void setBuilder(int x, int y, Faction faction) {
        if (!contains(x, y) || faction == null) {
            return;
        }
        int i = y * width + x;
        if (builder[i] == UNBUILT) {
            builder[i] = (byte) faction.ordinal();
        }
    }

    /** Which side cut the works here, or null if the ground is as it was found. */
    public Faction builderOf(int x, int y) {
        if (!contains(x, y)) {
            return null;
        }
        byte b = builder[y * width + x];
        return b == UNBUILT ? null : Faction.values()[b];
    }

    /** Raises or lowers a tile's cover, clamped. Digging in adds; shellfire takes away. */
    public void addCover(int x, int y, int delta) {
        if (contains(x, y)) {
            setCover(x, y, cover(x, y) + delta);
        }
    }

    /**
     * How much of a tile's cover was dug rather than found — its entrenchment.
     *
     * <p>Derived rather than stored, so there is still exactly one number per tile and no way
     * for the two to drift apart. It is what earthworks cost to cross: rubble is slow because
     * it is rubble, and charging for that twice would make ruins nearly impassable for a reason
     * nobody chose.
     *
     * <p>Shellfire can strip a tile below what the ground itself offered, which reads as no
     * entrenchment rather than as negative earthworks — a flattened ruin is not a shortcut.
     */
    public int entrenchment(int x, int y) {
        return Math.max(0, cover(x, y) - terrain(x, y).baseCover());
    }

    private boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

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
