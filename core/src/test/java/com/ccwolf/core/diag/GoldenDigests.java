package com.ccwolf.core.diag;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Skirmish;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The scenarios the determinism gate replays, and the file it compares them against.
 *
 * <p>Kept apart from the test itself because the same list has to drive two things: asserting
 * the digests, and regenerating them when a change is meant to alter behaviour. Those must not
 * drift apart, or the golden file stops describing what the test actually runs.
 */
public final class GoldenDigests {

    /** Seeds replayed. Twenty is enough that a change touching any code path shows up. */
    public static final int SEEDS = 20;

    /**
     * Where each run is sampled.
     *
     * <p>Early, mid and late: 500 ticks is opening moves, 2000 is first contact, 6000 is a
     * developed match with an economy and casualties. A change that only breaks late-game
     * behaviour would sail past a digest taken at tick 500.
     */
    public static final int[] SAMPLE_TICKS = {500, 2000, 6000};

    public static final String RESOURCE = "/determinism/golden-digests.txt";

    private GoldenDigests() {
    }

    /** One digest line per (seed, tick) sample, in a fixed order. */
    public static List<String> run() {
        List<String> lines = new ArrayList<String>();
        for (int seed = 1; seed <= SEEDS; seed++) {
            Skirmish skirmish = Skirmish.createAiVersusAi(
                    MapCatalog.load("kreisau"), Difficulty.VETERAN, seed);
            GameWorld world = skirmish.world();
            // Fog off, matching the harness: it is presentation only, and leaving it on just
            // adds work without adding anything the digest can see.
            world.setFogEnabled(false);

            int sample = 0;
            int maxTick = SAMPLE_TICKS[SAMPLE_TICKS.length - 1];
            for (int tick = 1; tick <= maxTick; tick++) {
                skirmish.step();
                world.clearEvents();
                if (sample < SAMPLE_TICKS.length && tick == SAMPLE_TICKS[sample]) {
                    lines.add(line(seed, tick, world));
                    sample++;
                }
            }
        }
        return lines;
    }

    private static String line(int seed, int tick, GameWorld world) {
        return "seed=" + seed + " tick=" + tick
                + " stable=" + StateDigest.format(StateDigest.stable(world))
                + " exact=" + StateDigest.format(StateDigest.exact(world));
    }

    /** The committed digests, or null when the file has not been generated yet. */
    public static List<String> golden() throws IOException {
        InputStream in = GoldenDigests.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        } finally {
            reader.close();
        }
        return lines;
    }

    /** Regenerates the golden file. Run deliberately, when a change is meant to alter play. */
    public static void main(String[] args) {
        System.out.println("# Golden state digests for the deterministic simulation.");
        System.out.println("#");
        System.out.println("# A change to these is a change to how the game plays. A commit");
        System.out.println("# that claimed to be a pure optimisation must not move this file.");
        System.out.println("#");
        System.out.println("# Regenerate deliberately, when a change is MEANT to alter play:");
        System.out.println("#   gradle :core:testClasses");
        System.out.println("#   java -cp core/build/classes/java/main:core/build/classes/"
                + "java/test:core/build/resources/main \\");
        System.out.println("#     com.ccwolf.core.diag.GoldenDigests \\");
        System.out.println("#     > core/src/test/resources/determinism/golden-digests.txt");
        for (String line : run()) {
            System.out.println(line);
        }
    }
}
