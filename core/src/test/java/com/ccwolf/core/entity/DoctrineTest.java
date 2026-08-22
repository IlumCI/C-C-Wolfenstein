package com.ccwolf.core.entity;

import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The choice itself, before anything acts on it.
 *
 * <p>Nothing reads a doctrine yet, so none of this can be caught by the determinism gate or by
 * playing a match — a doctrine that silently belonged to nobody, or that a player could be given
 * from the wrong faction, would pass every other test in the suite. This is the only coverage
 * there is until the effects land.
 */
class DoctrineTest {

    private GameWorld newWorld() {
        return new GameWorld(MapCatalog.load(MapCatalog.KREISAU_VALLEY), 1L);
    }

    @Test
    void everyDoctrineBelongsToExactlyOneSide() {
        for (Doctrine d : Doctrine.values()) {
            assertNotNull(d.faction(), d + " belongs to nobody");
            assertTrue(d.availableTo(d.faction()));
            assertTrue(!d.availableTo(d.faction().other()),
                    d + " is offered to both sides, which makes it not a doctrine");
        }
    }

    @Test
    void eachSideChoosesFromItsOwnAndOnlyItsOwn() {
        for (Faction f : Faction.values()) {
            Doctrine[] set = Doctrine.forFaction(f);
            assertTrue(set.length >= 2, f + " has nothing to choose between");
            assertEquals(set.length, Doctrine.countFor(f));
            for (Doctrine d : set) {
                assertSame(f, d.faction(), d + " turned up in the wrong side's list");
            }
        }
    }

    @Test
    void theSetsTogetherAccountForEveryDoctrine() {
        int total = 0;
        for (Faction f : Faction.values()) {
            total += Doctrine.countFor(f);
        }
        assertEquals(Doctrine.values().length, total,
                "a doctrine exists that no side can pick, or one that two sides can");
    }

    @Test
    void pickingByIndexWrapsAndNeverCrossesSides() {
        for (Faction f : Faction.values()) {
            // Including the negative, because an index derived from a seed can be one.
            for (int i = -7; i < 7; i++) {
                Doctrine d = Doctrine.forFaction(f, i);
                assertSame(f, d.faction(), "index " + i + " crossed sides");
            }
            assertSame(Doctrine.forFaction(f, 0), Doctrine.forFaction(f, Doctrine.countFor(f)));
        }
    }

    @Test
    void theListHandedOutIsACopy() {
        Doctrine[] first = Doctrine.forFaction(Faction.REGIME);
        first[0] = null;
        assertNotNull(Doctrine.forFaction(Faction.REGIME)[0],
                "the caller scribbled on the master list");
    }

    @Test
    void aPlayerRemembersWhatItPicked() {
        GameWorld world = newWorld();
        Player p = world.addPlayer(Faction.RESISTANCE, false, "us", Doctrine.TIEFBAU);
        assertSame(Doctrine.TIEFBAU, p.doctrine());
        assertTrue(p.follows(Doctrine.TIEFBAU));
        assertTrue(!p.follows(Doctrine.STAHLBETON));
    }

    @Test
    void pickingNothingIsAllowedAndIsTheOldGame() {
        GameWorld world = newWorld();
        Player p = world.addPlayer(Faction.RESISTANCE, false, "us");
        assertNull(p.doctrine(), "no doctrine must stay no doctrine, not a default one");
        assertTrue(!p.follows(Doctrine.TIEFBAU));
    }

    @Test
    void aSideCannotBeGivenTheOtherSidesDoctrine() {
        GameWorld world = newWorld();
        assertThrows(IllegalArgumentException.class,
                () -> world.addPlayer(Faction.RESISTANCE, false, "us", Doctrine.GASKRIEG),
                "the Kreisau Circle was issued gas masks and nobody objected");
        assertThrows(IllegalArgumentException.class,
                () -> world.addPlayer(Faction.REGIME, true, "them", Doctrine.TIEFBAU));
    }
}
