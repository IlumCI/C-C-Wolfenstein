package com.ccwolf.core.harness;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.diag.StateDigest;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import com.ccwolf.core.sim.Skirmish;

/**
 * Runs a whole match with no Android device in sight: two AIs fight, and a scoreboard is
 * printed every simulated minute.
 *
 * <p>This is how the simulation gets exercised on a build machine — it surfaces stalls,
 * runaway economies and units that never reach a target long before anything is on screen.
 *
 * <pre>
 * gradle :core:run --args="--ticks 24000 --seed 7 --difficulty VETERAN"
 * </pre>
 */
public final class SkirmishHarness {

    private SkirmishHarness() {
    }

    public static void main(String[] args) {
        int maxTicks = 24000;
        long seed = 7L;
        Difficulty difficulty = Difficulty.VETERAN;
        String mapName = MapCatalog.KREISAU_VALLEY;
        Doctrine resistanceDoctrine = null;
        Doctrine regimeDoctrine = null;
        boolean quiet = false;
        boolean plain = false;
        boolean profile = false;
        boolean digest = false;
        int benchUnits = 0;
        boolean benchSquads = false;

        for (int i = 0; i < args.length - 1; i++) {
            String key = args[i];
            String value = args[i + 1];
            if ("--ticks".equals(key)) {
                maxTicks = Integer.parseInt(value);
            } else if ("--seed".equals(key)) {
                seed = Long.parseLong(value);
            } else if ("--difficulty".equals(key)) {
                difficulty = Difficulty.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } else if ("--map".equals(key)) {
                mapName = value;
            } else if ("--bench".equals(key)) {
                benchUnits = Integer.parseInt(value);
            } else if ("--doctrine".equals(key)) {
                // One flag, either side: a doctrine already knows which faction it belongs to,
                // and GameWorld.addPlayer refuses a mismatch, so there is nothing to get wrong.
                Doctrine d = Doctrine.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                if (d.faction() == com.ccwolf.core.entity.Faction.REGIME) {
                    regimeDoctrine = d;
                } else {
                    resistanceDoctrine = d;
                }
            }
        }
        for (int i = 0; i < args.length; i++) {
            if ("--quiet".equals(args[i])) {
                quiet = true;
            } else if ("--plain".equals(args[i])) {
                plain = true;
            } else if ("--profile".equals(args[i])) {
                profile = true;
            } else if ("--digest".equals(args[i])) {
                digest = true;
            } else if ("--squads".equals(args[i])) {
                benchSquads = true;
            }
        }

        if (benchUnits > 0) {
            StressBench.run(mapName, benchUnits, maxTicks == 24000 ? 2000 : maxTicks, seed,
                    benchSquads);
            return;
        }

        // The default match is the goldens' match: both AIs declare the doctrine the seed
        // picks for them. --doctrine overrides one side; --plain strips both, for comparing
        // against how the game played before doctrines existed.
        if (!plain) {
            if (resistanceDoctrine == null) {
                resistanceDoctrine = Doctrine.pickFor(
                        com.ccwolf.core.entity.Faction.RESISTANCE, seed);
            }
            if (regimeDoctrine == null) {
                regimeDoctrine = Doctrine.pickFor(com.ccwolf.core.entity.Faction.REGIME, seed);
            }
        } else {
            resistanceDoctrine = null;
            regimeDoctrine = null;
        }

        TileMap map = MapCatalog.load(mapName);
        Skirmish skirmish = Skirmish.createAiVersusAi(map, difficulty, seed,
                resistanceDoctrine, regimeDoctrine);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);
        world.profiler().setEnabled(profile);

        System.out.println("Map: " + map.name() + " (" + map.width() + "x" + map.height() + ")");
        System.out.println("Difficulty: " + difficulty + "   seed: " + seed);
        System.out.println("Doctrines: "
                + (resistanceDoctrine == null ? "none" : resistanceDoctrine.displayName())
                + " vs "
                + (regimeDoctrine == null ? "none" : regimeDoctrine.displayName()));
        printHeader();

        long start = System.nanoTime();
        int reportEvery = GameWorld.TICKS_PER_SECOND * 60;
        while (world.tick() < maxTicks && !world.isGameOver()) {
            skirmish.step();
            world.clearEvents();
            if (!quiet && world.tick() % reportEvery == 0) {
                printRow(world);
            }
            if (digest && world.tick() % reportEvery == 0) {
                System.out.println("  digest tick=" + world.tick() + " stable="
                        + StateDigest.format(StateDigest.stable(world)));
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        printRow(world);
        System.out.println();
        if (world.isGameOver()) {
            int winner = world.winnerId();
            System.out.println("Result: " + (winner < 0 ? "mutual destruction"
                    : world.player(winner).name() + " wins")
                    + " after " + formatClock(world.tick()));
        } else {
            System.out.println("Result: no winner within " + formatClock(maxTicks)
                    + " of game time");
        }
        System.out.println("Simulated " + world.tick() + " ticks in " + elapsedMs + " ms ("
                + (elapsedMs == 0 ? "-" : String.valueOf(world.tick() * 1000L / elapsedMs))
                + " ticks/sec, real time is " + GameWorld.TICKS_PER_SECOND + ")");
        if (digest) {
            System.out.println("Final digest: stable="
                    + StateDigest.format(StateDigest.stable(world))
                    + " exact=" + StateDigest.format(StateDigest.exact(world)));
        }
        if (profile) {
            System.out.println();
            System.out.println(world.profiler().report());
        }
    }

    private static void printHeader() {
        System.out.println();
        System.out.printf("%-7s | %-20s | %8s %6s %5s %5s %5s%n",
                "clock", "player", "credits", "power", "units", "bldgs", "lost");
        System.out.println("--------+----------------------+------------------------------------");
    }

    private static void printRow(GameWorld world) {
        String clock = formatClock(world.tick());
        for (int i = 0; i < world.players().size(); i++) {
            Player p = world.player(i);
            int units = 0;
            int harvesters = 0;
            for (Unit u : world.units()) {
                if (u.ownerId() == i) {
                    units++;
                    if (u.type().isHarvester()) {
                        harvesters++;
                    }
                }
            }
            int bldgs = 0;
            for (Building b : world.buildings()) {
                if (b.ownerId() == i) {
                    bldgs++;
                }
            }
            System.out.printf("%-7s | %-20s | %8d %3d/%-3d %3d+%-1d %5d %5d%n",
                    i == 0 ? clock : "", p.name(), p.credits(), p.powerProduced(), p.powerDrawn(),
                    units - harvesters, harvesters, bldgs, p.unitsLost());
        }
    }

    private static String formatClock(int ticks) {
        int seconds = ticks / GameWorld.TICKS_PER_SECOND;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
