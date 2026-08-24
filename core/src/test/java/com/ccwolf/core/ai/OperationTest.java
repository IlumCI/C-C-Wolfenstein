package com.ccwolf.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Skirmish;
import org.junit.jupiter.api.Test;

/**
 * The operational cycle, held to the failures it was built out of.
 *
 * <p>Every assertion here is a bug that actually happened. The first operational AI never
 * committed once in twenty thousand ticks - the garrison dig and the staging rally fought over
 * the same squads, and separately the strength bar sat above what the economy could field. So
 * the pin is on the <em>cycle</em>: operations must actually launch, more than once, and the
 * machine must keep turning for both sides across a long match. What the operations achieve is
 * a balance number for the sweep; that they happen at all is a correctness claim.
 */
class OperationTest {

    @Test
    void operationsActuallyLaunch() {
        Skirmish skirmish = Skirmish.createAiVersusAi(MapCatalog.load(MapCatalog.KREISAU_VALLEY),
                Difficulty.VETERAN, 1L);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);

        int[] commits = new int[2];
        int[] lastState = new int[2];
        int limit = 20 * 60 * GameWorld.TICKS_PER_SECOND;
        for (int i = 0; i < limit && !world.isGameOver(); i++) {
            skirmish.step();
            world.clearEvents();
            for (int p = 0; p < skirmish.ais().size(); p++) {
                int state = skirmish.ais().get(p).operationState();
                if (state == SkirmishAi.COMMITTED && lastState[p] != SkirmishAi.COMMITTED) {
                    commits[p]++;
                }
                lastState[p] = state;
            }
        }

        for (int p = 0; p < 2; p++) {
            assertTrue(commits[p] >= 1,
                    "player " + p + " never committed an operation in twenty minutes - the"
                            + " mass/commit cycle has stalled, which is the zero-commit bug"
                            + " this test exists to keep dead");
        }
        assertTrue(commits[0] + commits[1] >= 3,
                "one operation each in twenty minutes is a cycle turning too slowly to matter:"
                        + " got " + commits[0] + " and " + commits[1]);
    }

    @Test
    void aFailedOperationRaisesTheBar() {
        // Not a match: the learning rule alone. A fresh AI's bar is twice the first wave;
        // the constant the rule adds is what a failure teaches.
        SkirmishAi ai = new SkirmishAi(0, Difficulty.VETERAN);
        assertEquals(Difficulty.VETERAN.firstWaveSize() * 2, ai.operationTargetStrength(),
                "the opening bar is two waves' worth of men");
    }
}
