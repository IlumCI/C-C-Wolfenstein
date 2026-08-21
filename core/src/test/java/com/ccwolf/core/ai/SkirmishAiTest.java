package com.ccwolf.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import com.ccwolf.core.sim.Skirmish;
import org.junit.jupiter.api.Test;

/**
 * End-to-end checks on a real match. These are slower than the unit tests but they are the
 * ones that catch a simulation that quietly stops making progress.
 */
class SkirmishAiTest {

    private static final int FIVE_MINUTES = 5 * 60 * GameWorld.TICKS_PER_SECOND;

    @Test
    void theAiBuildsABaseAndAnArmy() {
        Skirmish skirmish = Skirmish.createAiVersusAi(MapCatalog.load(MapCatalog.KREISAU_VALLEY),
                Difficulty.VETERAN, 12L);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);

        // Recorded as the match runs, for the same reason the unit assertion below counts what
        // was produced rather than what is alive. A side that is losing can have had its
        // barracks shelled flat by the five-minute mark, and asking at the end whether one is
        // standing turns a test of "can the AI build" into a test of who happened to win.
        boolean[] everHadBarracks = new boolean[2];
        for (int i = 0; i < FIVE_MINUTES && !world.isGameOver(); i++) {
            skirmish.step();
            world.clearEvents();
            for (int playerId = 0; playerId < 2; playerId++) {
                everHadBarracks[playerId] |=
                        world.hasCompletedBuilding(playerId, BuildingType.BARRACKS);
            }
        }

        for (int playerId = 0; playerId < 2; playerId++) {
            assertTrue(everHadBarracks[playerId],
                    "player " + playerId + " should have built a barracks");
            // Asks what the AI produced, not what it still has standing. A side that is losing
            // badly can legitimately be at zero units five minutes in, and asserting on the
            // living count made this a test of who won rather than of whether both sides can
            // build.
            assertTrue(world.player(playerId).unitsBuilt() >= 3,
                    "player " + playerId + " should have trained an army");
            assertTrue(world.player(playerId).creditsEarned() > 0,
                    "player " + playerId + " should be harvesting");
        }
    }

    @Test
    void armiesActuallyLeaveHomeAndFight() {
        Skirmish skirmish = Skirmish.createAiVersusAi(MapCatalog.load(MapCatalog.KREISAU_VALLEY),
                Difficulty.VETERAN, 12L);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);

        for (int i = 0; i < FIVE_MINUTES && !world.isGameOver(); i++) {
            skirmish.step();
            world.clearEvents();
        }

        int losses = world.player(0).unitsLost() + world.player(1).unitsLost();
        assertTrue(losses > 0, "five minutes in, someone should have lost a unit");
    }

    @Test
    void aMatchReachesAConclusion() {
        Skirmish skirmish = Skirmish.createAiVersusAi(MapCatalog.load(MapCatalog.KREISAU_VALLEY),
                Difficulty.OBERST, 4L);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);

        int limit = 45 * 60 * GameWorld.TICKS_PER_SECOND;
        while (world.tick() < limit && !world.isGameOver()) {
            skirmish.step();
            world.clearEvents();
        }

        assertTrue(world.isGameOver(), "the match should not run forever");
        assertTrue(world.winnerId() >= 0);
        Player loser = world.player(1 - world.winnerId());
        assertTrue(loser.isDefeated());
    }

    @Test
    void theSimulationIsDeterministicForAGivenSeed() {
        assertEquals(runDigest(77L), runDigest(77L),
                "same seed must replay identically, or nothing is reproducible");
    }

    private String runDigest(long seed) {
        TileMap map = MapCatalog.load(MapCatalog.KREISAU_VALLEY);
        Skirmish skirmish = Skirmish.createAiVersusAi(map, Difficulty.VETERAN, seed);
        GameWorld world = skirmish.world();
        world.setFogEnabled(false);
        for (int i = 0; i < 3000; i++) {
            skirmish.step();
            world.clearEvents();
        }
        StringBuilder sb = new StringBuilder();
        for (Unit u : world.units()) {
            sb.append(u.type()).append(':').append(Math.round(u.x() * 100))
                    .append(',').append(Math.round(u.y() * 100)).append(';');
        }
        for (Building b : world.buildings()) {
            sb.append(b.type()).append('@').append(b.tileX()).append(',').append(b.tileY())
                    .append(';');
        }
        sb.append(world.player(0).credits()).append('/').append(world.player(1).credits());
        return sb.toString();
    }

    private int countFighters(GameWorld world, int playerId) {
        int n = 0;
        for (Unit u : world.units()) {
            if (u.ownerId() == playerId && !u.type().isHarvester()) {
                n++;
            }
        }
        return n;
    }
}
