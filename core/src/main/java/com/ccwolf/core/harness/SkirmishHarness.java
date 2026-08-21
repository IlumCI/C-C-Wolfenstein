package com.ccwolf.core.harness;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Building;
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
        boolean quiet = false;

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
            }
        }
        for (int i = 0; i < args.length; i++) {
            if ("--quiet".equals(args[i])) {
                quiet = true;
            }
        }

        TileMap map = MapCatalog.load(mapName);
        Skirmish skirmish = Skirmish.createAiVersusAi(map, difficulty, seed);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);

        System.out.println("Map: " + map.name() + " (" + map.width() + "x" + map.height() + ")");
        System.out.println("Difficulty: " + difficulty + "   seed: " + seed);
        printHeader();

        long start = System.nanoTime();
        int reportEvery = GameWorld.TICKS_PER_SECOND * 60;
        while (world.tick() < maxTicks && !world.isGameOver()) {
            skirmish.step();
            world.clearEvents();
            if (!quiet && world.tick() % reportEvery == 0) {
                printRow(world);
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
