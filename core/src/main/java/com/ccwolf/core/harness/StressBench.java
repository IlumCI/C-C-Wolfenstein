package com.ccwolf.core.harness;

import com.ccwolf.core.diag.TickProfiler;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import java.util.Arrays;
import java.util.List;

/**
 * Puts a large army on the map immediately and measures what the tick costs.
 *
 * <p>The ordinary AI-vs-AI harness cannot answer the question this part is about. It has to
 * build an economy first, and its army cap tops out around thirty a side, so it never reaches
 * the conditions that break the simulation — hundreds of units in contact, all repathing,
 * all separating against each other. Waiting for a real match to get there would take longer
 * than the match lasts, and it would never get there at all.
 *
 * <p>So this skips the economy and puts the armies straight onto the field, marching at each
 * other. It is not a game and does not pretend to be one: it is the conditions the tick has to
 * survive, reproduced on demand and in a controlled way.
 */
public final class StressBench {

    /** Warm-up ticks excluded from the timings, so the JIT is not measured instead of the code. */
    private static final int WARMUP_TICKS = 200;

    private StressBench() {
    }

    public static void run(String mapName, int unitsPerSide, int ticks, long seed) {
        TileMap map = MapCatalog.load(mapName);
        GameWorld world = new GameWorld(map, seed);
        Player resistance =
                world.addPlayer(Faction.RESISTANCE, true, Faction.RESISTANCE.displayName());
        Player regime = world.addPlayer(Faction.REGIME, true, Faction.REGIME.displayName());
        world.setFogEnabled(false);

        List<int[]> spawns = map.spawnPoints();
        int[] west = spawns.get(0);
        int[] east = spawns.get(spawns.size() - 1);

        int placedWest = deploy(world, resistance.id(), unitsPerSide, west[0], west[1], map);
        int placedEast = deploy(world, regime.id(), unitsPerSide, east[0], east[1], map);

        System.out.println("Stress bench: " + map.name()
                + " (" + map.width() + "x" + map.height() + ")   seed " + seed);
        System.out.println("Deployed " + placedWest + " vs " + placedEast
                + " (asked for " + unitsPerSide + " a side)");
        if (placedWest < unitsPerSide || placedEast < unitsPerSide) {
            System.out.println("  NOTE: the map ran out of room before the army did.");
        }

        // Send each side at the other, so they meet in the middle and stay in contact. The
        // costly state is the melee, not the march.
        order(world, resistance.id(), east[0], east[1]);
        order(world, regime.id(), west[0], west[1]);

        for (int i = 0; i < WARMUP_TICKS; i++) {
            world.step();
            world.clearEvents();
        }

        TickProfiler profiler = world.profiler();
        profiler.reset();
        profiler.setEnabled(true);

        long[] tickNanos = new long[ticks];
        long start = System.nanoTime();
        int measured = 0;
        for (int i = 0; i < ticks && !world.isGameOver(); i++) {
            long tickStart = System.nanoTime();
            world.step();
            world.clearEvents();
            tickNanos[measured++] = System.nanoTime() - tickStart;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        report(world, profiler, tickNanos, measured, elapsedMs);
    }

    /**
     * Fills a wedge of the map out from a spawn point.
     *
     * <p>Spread over a band rather than packed into a block: a thousand units standing on one
     * tile is a different, and much less interesting, problem than a thousand units with room
     * to manoeuvre.
     */
    private static int deploy(GameWorld world, int ownerId, int count, int originX, int originY,
            TileMap map) {
        // Shuffled from the world's seeded RNG, so different seeds give genuinely different
        // battles. Without this the deployment is fixed and every seed runs the same fight,
        // which makes sweeping seeds look meaningful while telling you nothing.
        UnitType[] mix = shuffled(mixFor(world.player(ownerId).faction()), world.random());
        int placed = 0;
        int radius = 1;

        while (placed < count && radius < Math.max(map.width(), map.height())) {
            for (int dy = -radius; dy <= radius && placed < count; dy++) {
                for (int dx = -radius; dx <= radius && placed < count; dx++) {
                    // Ring by ring, so the army grows outwards evenly.
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    int tx = originX + dx;
                    int ty = originY + dy;
                    if (tx < 1 || ty < 1 || tx >= map.width() - 1 || ty >= map.height() - 1) {
                        continue;
                    }
                    if (!map.terrain(tx, ty).isPassable()) {
                        continue;
                    }
                    Unit unit = world.spawnUnit(ownerId, mix[placed % mix.length],
                            tx + 0.5f, ty + 0.5f);
                    if (unit != null) {
                        placed++;
                    }
                }
            }
            radius++;
        }
        return placed;
    }

    private static UnitType[] shuffled(UnitType[] mix, java.util.Random random) {
        UnitType[] out = Arrays.copyOf(mix, mix.length);
        for (int i = out.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            UnitType swap = out[i];
            out[i] = out[j];
            out[j] = swap;
        }
        return out;
    }

    /** A believable order of battle: mostly line infantry, with support and armour among it. */
    private static UnitType[] mixFor(Faction faction) {
        if (faction == Faction.REGIME) {
            return new UnitType[] {
                UnitType.SOLDAT, UnitType.SOLDAT, UnitType.SOLDAT, UnitType.SOLDAT,
                UnitType.SCHARFSCHUTZE, UnitType.STURMPIONIER,
                UnitType.SOLDAT, UnitType.SOLDAT, UnitType.PANZERHUND, UnitType.STURMPANZER,
            };
        }
        return new UnitType[] {
            UnitType.PARTISAN, UnitType.PARTISAN, UnitType.PARTISAN, UnitType.PARTISAN,
            UnitType.ROCKETEER, UnitType.GRENADIER,
            UnitType.PARTISAN, UnitType.PARTISAN, UnitType.MARKSMAN, UnitType.CAPTURED_PANZER,
        };
    }

    private static void order(GameWorld world, int ownerId, int tileX, int tileY) {
        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.ownerId() == ownerId) {
                unit.setOrder(new AttackMoveOrder(tileX, tileY));
            }
        }
    }

    private static void report(GameWorld world, TickProfiler profiler, long[] tickNanos,
            int measured, long elapsedMs) {
        long[] sorted = Arrays.copyOf(tickNanos, measured);
        Arrays.sort(sorted);

        int alive = 0;
        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).isAlive()) {
                alive++;
            }
        }

        System.out.println();
        System.out.println("Ran " + measured + " ticks in " + elapsedMs + " ms  ("
                + (elapsedMs == 0 ? "-" : String.valueOf(measured * 1000L / elapsedMs))
                + " ticks/sec, real time is " + GameWorld.TICKS_PER_SECOND + ")");
        System.out.println("Units still alive: " + alive);
        System.out.printf("Tick time: p50 %.2f ms   p95 %.2f ms   worst %.2f ms   "
                        + "(budget 50.00)%n",
                Double.valueOf(percentile(sorted, 0.50)),
                Double.valueOf(percentile(sorted, 0.95)),
                Double.valueOf(percentile(sorted, 1.00)));
        System.out.println();
        System.out.println(profiler.report());
    }

    private static double percentile(long[] sorted, double fraction) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int index = (int) Math.round(fraction * (sorted.length - 1));
        return sorted[index] / 1_000_000.0;
    }
}
