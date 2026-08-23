package com.ccwolf.core.sim;

import com.ccwolf.core.map.TileMap;

/**
 * Gas on the ground: the Gas War doctrine's whole idea, as a tile layer.
 *
 * <p>The doctrine's one line is "gas that sinks into a trench and stays", and everything in this
 * class is that sentence made mechanical. The agent is heavier than air, so it drains toward dug
 * ground; it disperses quickly in the open and slowly in a hole; and cover does not reduce what
 * it does to a man, because the trench is precisely where it pools. It is the counter-doctrine to
 * everything the Resistance's three do — the deeper and harder a line digs, the better a target
 * it is for this.
 *
 * <h2>Determinism</h2>
 *
 * <p>No randomness anywhere: settling walks the tiles row-major, offers each tile's surplus to
 * its four neighbours in a fixed order, and decays on a fixed cadence. The layer therefore draws
 * nothing from the world's random stream — which keeps the standing invariant that the number of
 * draws per tick never depends on what happened — and two runs of the same match gas the same
 * tiles on the same ticks.
 *
 * <p>The layer is not digested, and that follows the precedent of cover: trench depth is not in
 * {@code StateDigest} either. Both act on the simulation only through damage and movement, so
 * any divergence they cause surfaces in unit hp and positions within a few ticks, which the
 * digest does see.
 */
public final class GasLayer {

    /** The most gas one tile holds. Six is three lethal doses; enough to outlast a retreat. */
    public static final int MAX_LEVEL = 6;

    /** Ticks between settling passes. Half a second: gas moves like weather, not like a shot. */
    public static final int SETTLE_EVERY = 10;

    /** Open ground sheds a level every settle; dug ground only every third. It stays. */
    public static final int TRENCH_LINGER = 3;

    /** How much one shell releases at its impact tile. */
    public static final int PER_SHELL = MAX_LEVEL;

    private final int width;
    private final int height;
    private final byte[] level;
    private int settles;
    private int active;

    public GasLayer(int width, int height) {
        this.width = width;
        this.height = height;
        this.level = new byte[width * height];
    }

    public int at(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return level[y * width + x];
    }

    /** True while any tile holds gas. The cheap guard that keeps clean matches free. */
    public boolean any() {
        return active > 0;
    }

    /** Vents a cloud at a tile: full strength there, half at the four neighbours. */
    public void release(int x, int y, int amount) {
        raise(x, y, amount);
        raise(x + 1, y, amount / 2);
        raise(x - 1, y, amount / 2);
        raise(x, y + 1, amount / 2);
        raise(x, y - 1, amount / 2);
    }

    /** Raises one tile only, for tests that need two clouds that do not touch. */
    public void raiseForTest(int x, int y, int amount) {
        raise(x, y, amount);
    }

    private void raise(int x, int y, int amount) {
        if (amount <= 0 || x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int i = y * width + x;
        if (level[i] == 0) {
            active++;
        }
        level[i] = (byte) Math.min(MAX_LEVEL, level[i] + amount);
    }

    /**
     * One tick of weather. Cheap no-op while the map is clean.
     *
     * <p>Settling and decay run together every {@link #SETTLE_EVERY} ticks. Settling first:
     * every tile offers one unit of its load to the first of its four neighbours (east, south,
     * west, north — fixed, because the order is part of the behaviour) that is <em>lower</em>,
     * where lower means deeper dug, or level ground already carrying at least two fewer. That
     * single rule is both behaviours the doctrine wants: a cloud on flat ground spreads into a
     * shallow disc, and a cloud beside a trench line drains along it.
     */
    public void step(int tick, TileMap map) {
        if (active == 0 || tick % SETTLE_EVERY != 0) {
            return;
        }
        settles++;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (level[i] <= 0) {
                    continue;
                }
                int here = map.cover(x, y);
                if (flow(i, x + 1, y, here, map) || flow(i, x, y + 1, here, map)
                        || flow(i, x - 1, y, here, map) || flow(i, x, y - 1, here, map)) {
                    continue;
                }
            }
        }

        boolean trenchesDecayToo = settles % TRENCH_LINGER == 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (level[i] <= 0) {
                    continue;
                }
                if (map.cover(x, y) == 0 || trenchesDecayToo) {
                    level[i]--;
                    if (level[i] == 0) {
                        active--;
                    }
                }
            }
        }
    }

    private boolean flow(int from, int nx, int ny, int hereCover, TileMap map) {
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
            return false;
        }
        int to = ny * width + nx;
        boolean downhill = map.cover(nx, ny) > hereCover && level[to] < level[from];
        boolean spreading = map.cover(nx, ny) == hereCover && level[to] < level[from] - 1;
        if (!downhill && !spreading) {
            return false;
        }
        if (level[to] == 0) {
            active++;
        }
        level[to]++;
        level[from]--;
        if (level[from] == 0) {
            active--;
        }
        return true;
    }
}
