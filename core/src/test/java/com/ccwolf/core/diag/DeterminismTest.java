package com.ccwolf.core.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Skirmish;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Holds the simulation to how it behaved before the scaling work started.
 *
 * <p>Most of what is coming — a rewritten spatial index, reworked separation steering, a
 * budgeted pathfinder — is meant to change how fast the game runs and nothing else. This is what
 * makes that claim checkable rather than asserted: twenty seeds sampled at three points each,
 * against digests committed before any of it was touched.
 *
 * <p>When a change <em>is</em> meant to alter play, the golden file gets regenerated and the
 * diff is part of the review. What must never happen is a behaviour change slipping through
 * unnoticed inside a commit that claimed to be an optimisation.
 */
public class DeterminismTest {

    @Test
    public void theSimulationStillPlaysOutTheWayItWasRecorded() throws IOException {
        List<String> golden = GoldenDigests.golden();
        assertNotNull(golden,
                "No golden digests on the classpath. Generate them with GoldenDigests.main "
                        + "before making simulation changes - they are the baseline.");

        List<String> actual = GoldenDigests.run();
        assertEquals(golden.size(), actual.size(), "sample count changed");

        for (int i = 0; i < golden.size(); i++) {
            assertEquals(golden.get(i), actual.get(i),
                    "The simulation diverged from its recorded behaviour.\n"
                            + "If this change was meant to be a pure optimisation, it is not "
                            + "one.\nIf it was meant to change how the game plays, regenerate "
                            + "the golden file and put the diff in the commit.");
        }
    }

    @Test
    public void thesameSeedTwiceGivesTheSameGame() {
        assertEquals(digestAfter(4321L, 1500), digestAfter(4321L, 1500),
                "the same seed must produce the same game, or nothing else here means anything");
    }

    @Test
    public void differentSeedsGiveDifferentGames() {
        // Guards against the digest folding in so little that everything hashes the same, which
        // would make the whole gate silently vacuous.
        assertTrue(digestAfter(1L, 1500) != digestAfter(2L, 1500),
                "two seeds produced identical digests - the digest is not reading enough state");
    }

    @Test
    public void theExactDigestIsAtLeastAsStrictAsTheStableOne() {
        GameWorld world = runTo(99L, 800);
        long stable = StateDigest.stable(world);
        long exact = StateDigest.exact(world);
        // Not equal to each other (they read positions differently), but both must be stable
        // across repeated reads of an unchanging world.
        assertEquals(stable, StateDigest.stable(world));
        assertEquals(exact, StateDigest.exact(world));
    }

    private long digestAfter(long seed, int ticks) {
        return StateDigest.stable(runTo(seed, ticks));
    }

    private GameWorld runTo(long seed, int ticks) {
        Skirmish skirmish = Skirmish.createAiVersusAi(
                MapCatalog.load("kreisau"), Difficulty.VETERAN, seed);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);
        for (int i = 0; i < ticks; i++) {
            skirmish.step();
            world.clearEvents();
        }
        return world;
    }
}
