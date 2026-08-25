package com.ccwolf.core.sim;

import com.ccwolf.core.map.TileMap;

/**
 * How much of the map one side holds, and where.
 *
 * <p>The substrate a front is made of. Every location decision the AI makes today —
 * where to attack, where to dig, where to put a turret — is "the nearest enemy thing, plus an
 * offset from a loop counter". An army with no notion of where the line is cannot mass against a
 * weak point, because it has no idea one exists. This is that notion.
 *
 * <h2>Scatter, then blur</h2>
 *
 * <p>Each thing that holds ground drops its weight into the cell it stands in, and the whole
 * grid is then blurred with a separable kernel. That ordering is the important part: the scatter
 * is O(units) and the blur is O(cells), so the cost of <em>reach</em> is paid once for the map
 * rather than once per unit. Five hundred men a side cost no more to spread than five do.
 *
 * <p>The obvious alternative — stamping a small neighbourhood per unit, the way
 * {@link SightMemory#see} does — was rejected on the numbers. That kernel is radius one cell,
 * and the two spawn points on the only map in the game are fourteen cells apart: each base's
 * field would be three cells across with eleven cells of exact zero between them, so
 * {@code mine - theirs} would be zero everywhere in the middle and there would be no sign change
 * to find. The front would not exist until the armies physically touched, which is exactly when
 * nobody needs to be told where it is. {@code see()} stamps small because it is written by five
 * hundred units a pass and only has to answer "recently?"; this has to make a field.
 *
 * <h2>It sees everything</h2>
 *
 * <p>Deliberately omniscient: it counts units the owner has never laid eyes on. That matches
 * {@code findNearestEnemyAnywhere}, which every AI location decision already uses, so it is not
 * a step backwards — but {@link SightMemory} exists precisely because somebody cared about this,
 * so it is said out loud rather than left to be discovered. Masking it through
 * {@code canObserve} is a later change and a deliberate one.
 *
 * <h2>Determinism</h2>
 *
 * <p>Float addition and multiplication are exact under IEEE and Java has been {@code strictfp}
 * by default since 17, so the arithmetic here is reproducible. What would break that is a
 * transcendental: the blur weights are therefore integer literals divided by their own sum, and
 * <b>nothing in this class may call {@code exp}, {@code pow} or any trigonometric function</b>,
 * including in a one-off table builder — a table built once still reaches play through its
 * output. Summation order is fixed by the caller iterating the world's lists in order, which
 * makes list order part of the answer.
 */
public final class InfluenceGrid {

    /** Tiles per cell edge, matching SightMemory and the spatial index. */
    public static final int CELL_TILES = 4;

    /**
     * How many times the field is blurred. Each pass adds the kernel's radius to the reach.
     *
     * <p>The one number to reach for if fronts misbehave, and it fails in both directions.
     *
     * <p>Too narrow and <b>no front forms at all</b> — this is not hypothetical, it is what one
     * pass does. The two bases on the only map in the game sit fourteen cells apart; a single
     * nine-tap pass reaches four cells, so one side's ground stopped at column five, the other's
     * began at column eight, and the cells between were <em>exactly</em> zero. Control never
     * changed sign, so no cell was ever adjacent to enemy ground, so the front did not exist.
     * Two passes reach eight cells each and the fields overlap across the middle of the map.
     *
     * <p>Too wide is the opposite failure and quieter: control becomes a smooth ramp whose
     * zero-crossing sits on the perpendicular bisector between the two bases regardless of what
     * the armies actually do — a decorative line that never moves.
     */
    public static final int SPREAD_PASSES = 2;

    /**
     * A nine-tap kernel, as integers over their own sum.
     *
     * <p>Integers rather than decimals so that the weights can be read and checked, and so that
     * the normalisation is one division rather than nine transcribed constants.
     */
    private static final int[] KERNEL = {1, 4, 10, 16, 20, 16, 10, 4, 1};

    private static final float KERNEL_NORM = 1f / 82f;

    /** The most a trench can multiply the presence standing in it. */
    private static final float MAX_GROUND_BONUS = 2f;

    /** How much one full level of dug ground, averaged over a cell, is worth. */
    private static final float GROUND_BONUS_PER_LEVEL = 0.25f;

    private final int cellsAcross;
    private final int cellsDown;
    private final float[] field;
    private final float[] scratch;

    public InfluenceGrid(int mapWidth, int mapHeight) {
        this.cellsAcross = (mapWidth + CELL_TILES - 1) / CELL_TILES;
        this.cellsDown = (mapHeight + CELL_TILES - 1) / CELL_TILES;
        this.field = new float[cellsAcross * cellsDown];
        this.scratch = new float[cellsAcross * cellsDown];
    }

    public int cellsAcross() {
        return cellsAcross;
    }

    public int cellsDown() {
        return cellsDown;
    }

    /** Wipes the field. Called at the start of every pass; nothing here accumulates. */
    public void clear() {
        java.util.Arrays.fill(field, 0f);
    }

    /** Drops a thing's holding weight into the cell it stands in. */
    public void add(float x, float y, float weight) {
        if (weight <= 0f) {
            return;
        }
        int cellX = (int) x / CELL_TILES;
        int cellY = (int) y / CELL_TILES;
        if (cellX < 0 || cellY < 0 || cellX >= cellsAcross || cellY >= cellsDown) {
            return;
        }
        field[cellY * cellsAcross + cellX] += weight;
    }

    /**
     * Multiplies what is present by the ground it is standing on.
     *
     * <p>Applied to the raw scatter, before the blur, so that a dug-in position spreads its
     * amplified weight rather than amplifying whatever happened to drift in.
     */
    public void applyGroundBonus(float[] bonusPerCell) {
        for (int i = 0; i < field.length; i++) {
            field[i] *= bonusPerCell[i];
        }
    }

    /**
     * Spreads the scattered weight, across then down.
     *
     * <p>Separable, so the cost is two one-dimensional passes rather than one
     * nine-by-nine — eighteen taps a cell instead of eighty-one. Edges clamp rather than fade,
     * which means a base in a corner still holds its corner instead of leaking half its
     * influence off the side of the world.
     */
    public void spread() {
        for (int pass = 0; pass < SPREAD_PASSES; pass++) {
            spreadOnce();
        }
    }

    private void spreadOnce() {
        int radius = KERNEL.length / 2;
        for (int y = 0; y < cellsDown; y++) {
            for (int x = 0; x < cellsAcross; x++) {
                float sum = 0f;
                for (int k = 0; k < KERNEL.length; k++) {
                    int sx = clamp(x + k - radius, cellsAcross);
                    sum += field[y * cellsAcross + sx] * KERNEL[k];
                }
                scratch[y * cellsAcross + x] = sum * KERNEL_NORM;
            }
        }
        for (int y = 0; y < cellsDown; y++) {
            for (int x = 0; x < cellsAcross; x++) {
                float sum = 0f;
                for (int k = 0; k < KERNEL.length; k++) {
                    int sy = clamp(y + k - radius, cellsDown);
                    sum += scratch[sy * cellsAcross + x] * KERNEL[k];
                }
                field[y * cellsAcross + x] = sum * KERNEL_NORM;
            }
        }
    }

    private static int clamp(int v, int limit) {
        return v < 0 ? 0 : (v >= limit ? limit - 1 : v);
    }

    /** Influence in a cell. Out of bounds reads as nothing, matching TileMap's accessors. */
    public float atCell(int cellX, int cellY) {
        if (cellX < 0 || cellY < 0 || cellX >= cellsAcross || cellY >= cellsDown) {
            return 0f;
        }
        return field[cellY * cellsAcross + cellX];
    }

    /** Influence over a tile. */
    public float atTile(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) {
            return 0f;
        }
        return atCell(tileX / CELL_TILES, tileY / CELL_TILES);
    }

    /**
     * How much every cell's ground is worth to whoever holds it, from the earthworks on it.
     *
     * <p>Shared across players rather than computed per side, because {@code TileMap} records
     * how deep a tile is dug but <em>not who dug it</em> — a trench survives its diggers
     * leaving. So earthworks cannot be a source of control without inventing ownership; they are
     * a multiplier on whoever is actually standing there. An abandoned trench holds nothing,
     * which is both cheaper and truer.
     *
     * <p>Averaged over the cell's sixteen tiles rather than read from the tile underfoot. The
     * tile-underfoot version has a real failure: one man standing on one deeply dug tile reads
     * maximum entrenchment and multiplies his whole weight, so a lone scout projects as much as
     * a platoon. The mean makes the bonus proportional to the frontage actually dug — one tile
     * of sixteen is worth almost nothing, half a cell is worth a great deal.
     *
     * <p>Capped, because uncapped this is the knob that freezes the front, which is the failure
     * {@code Earthworks} is already watching for.
     */
    public static void groundBonus(TileMap map, float[] out, int cellsAcross, int cellsDown) {
        float perTile = GROUND_BONUS_PER_LEVEL / (CELL_TILES * CELL_TILES);
        for (int cellY = 0; cellY < cellsDown; cellY++) {
            for (int cellX = 0; cellX < cellsAcross; cellX++) {
                int dug = 0;
                for (int ty = 0; ty < CELL_TILES; ty++) {
                    for (int tx = 0; tx < CELL_TILES; tx++) {
                        dug += map.entrenchment(cellX * CELL_TILES + tx, cellY * CELL_TILES + ty);
                    }
                }
                float bonus = 1f + dug * perTile;
                out[cellY * cellsAcross + cellX] =
                        bonus > MAX_GROUND_BONUS ? MAX_GROUND_BONUS : bonus;
            }
        }
    }
}
