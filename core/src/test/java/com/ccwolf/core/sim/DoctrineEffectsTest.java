package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.Doctrines;
import com.ccwolf.core.combat.Earthworks;
import com.ccwolf.core.combat.Suppression;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.EntrenchOrder;
import com.ccwolf.core.squad.Squad;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the Resistance's three doctrines are actually worth on the ground.
 *
 * <p>Shaped like {@code TrenchTest}, and every positive assertion is paired with the negative
 * for a side that picked nothing — because "picked nothing" has to keep meaning the game exactly
 * as it played before doctrines existed, and that is the property the whole ship-it-dark commit
 * sequence rests on. A doctrine that quietly leaked into the no-doctrine case would move the
 * determinism goldens six commits before anything is supposed to.
 *
 * <p>Each doctrine is also checked for what it does <em>not</em> do. A defensive doctrine that
 * helped everywhere would not be a choice, and the sweep would find that out much later and much
 * more expensively than a test does.
 */
public class DoctrineEffectsTest {

    private GameWorld world(long seed, Doctrine resistance, Doctrine regime) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A", resistance);
        world.addPlayer(Faction.REGIME, false, "B", regime);
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    /** Somewhere flat, empty and away from both bases, so nothing wanders into the test. */
    private int[] quietGrass(TileMap map) {
        for (int y = 24; y < 40; y++) {
            for (int x = 24; x < 40; x++) {
                if (map.terrain(x, y) == Terrain.GRASS && map.cover(x, y) == 0) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("kreisau has no open grass in the middle of it");
    }

    /** How much cover a lone digger of this doctrine has raised after a fixed stretch of work. */
    private int coverAfter(Doctrine doctrine, int ticks) {
        GameWorld world = world(11L, doctrine, null);
        int[] spot = quietGrass(world.map());
        Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
        digger.setOrder(new EntrenchOrder(spot[0], spot[1]));
        for (int i = 0; i < ticks; i++) {
            world.step();
        }
        return world.map().cover(spot[0], spot[1]);
    }

    // --- Deep Works -----------------------------------------------------------------------

    @Test
    public void deepWorksDigsALevelInFewerTicks() {
        int ticks = Doctrines.digTicks(Doctrine.TIEFBAU) + 5;
        assertTrue(ticks < Doctrines.PLAIN_DIG_TICKS,
                "this test is meaningless unless Deep Works is actually faster");

        assertEquals(1, coverAfter(Doctrine.TIEFBAU, ticks),
                "Deep Works should have a level in the ground by now");
        assertEquals(0, coverAfter(null, ticks),
                "and a man with no doctrine should still be short of one");
    }

    @Test
    public void aManWithHisHeadDownOnlyKeepsDiggingIfHisSideDugThatRuleOut() {
        // The rule EntrenchOrder states plainly: a line that arrives under fire never hardens.
        // Deep Works is the one answer to it, and deliberately so - artillery is the weapon
        // that makes a line arrive under fire in the first place.
        for (Doctrine doctrine : new Doctrine[] {null, Doctrine.TIEFBAU}) {
            GameWorld world = world(12L, doctrine, null);
            int[] spot = quietGrass(world.map());
            Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
            digger.setOrder(new EntrenchOrder(spot[0], spot[1]));

            for (int i = 0; i < Doctrines.PLAIN_DIG_TICKS * 2; i++) {
                // Held down every tick, so he is never off the floor long enough to recover.
                digger.addSuppression(Suppression.PRONE);
                world.step();
            }
            assertTrue(digger.isProne(), "the test failed to keep him down");

            int dug = world.map().cover(spot[0], spot[1]);
            if (doctrine == Doctrine.TIEFBAU) {
                assertTrue(dug > 0, "Deep Works should dig with its head down");
            } else {
                assertEquals(0, dug, "everyone else should not");
            }
        }
    }

    // --- Hardened Line --------------------------------------------------------------------

    /** What one rifle shot takes off a man standing in the given cover. */
    private int shotDamage(Doctrine doctrine, int cover, Weapon weapon) {
        GameWorld world = world(13L, doctrine, null);
        int[] spot = quietGrass(world.map());
        world.map().setCover(spot[0], spot[1], cover);

        Unit victim = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit shooter = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 1.5f, spot[1] + 0.5f);
        int before = victim.hp();
        assertTrue(world.tryAttack(shooter, victim, weapon), "the shot should have happened");
        return before - victim.hp();
    }

    @Test
    public void aHardenedLineTakesLessFromTheSameShotInTheSameTrench() {
        int plain = shotDamage(null, TileMap.MAX_COVER, Weapon.RIFLE);
        int hardened = shotDamage(Doctrine.STAHLBETON, TileMap.MAX_COVER, Weapon.RIFLE);
        assertTrue(hardened < plain,
                "the same trench should be worth more to a Hardened Line: "
                        + hardened + " vs " + plain);
    }

    @Test
    public void aHardenedLineIsWorthNothingToAManInTheOpen() {
        assertEquals(shotDamage(null, 0, Weapon.RIFLE),
                shotDamage(Doctrine.STAHLBETON, 0, Weapon.RIFLE),
                "it improves cover; it does not invent it");
    }

    @Test
    public void aHardenedLineDoesNotHelpAgainstWhatCoverNeverStopped() {
        // HOUND_JAWS is MELEE, whose cover effect is zero: something in the trench with you.
        // A defensive doctrine that answered everything would not be a choice.
        assertEquals(shotDamage(null, TileMap.MAX_COVER, Weapon.HOUND_JAWS),
                shotDamage(Doctrine.STAHLBETON, TileMap.MAX_COVER, Weapon.HOUND_JAWS));
    }

    // --- the fifth level ------------------------------------------------------------------

    @Test
    public void onlyDeepWorksReachesTheFifthLevel() {
        assertEquals(Earthworks.ROOFED, Doctrines.maxDepth(Doctrine.TIEFBAU));
        assertEquals(TileMap.FULL_COVER, Doctrines.maxDepth(null));
        assertTrue(Earthworks.ROOFED > TileMap.FULL_COVER, "a fifth level that is not deeper");

        for (Doctrine doctrine : new Doctrine[] {null, Doctrine.TIEFBAU}) {
            GameWorld world = world(15L, doctrine, null);
            int[] spot = quietGrass(world.map());
            Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
            digger.setOrder(new EntrenchOrder(spot[0], spot[1]));
            for (int i = 0; i < Doctrines.PLAIN_DIG_TICKS * 8; i++) {
                world.step();
            }
            int dug = world.map().cover(spot[0], spot[1]);
            assertEquals(Doctrines.maxDepth(doctrine), dug,
                    "left alone forever, a man digs exactly as deep as his side digs");
        }
    }

    @Test
    public void aRoofStopsWhatComesDownAndNothingThatComesFlat() {
        // The whole justification for a fifth level being a different kind of protection rather
        // than more of the same. If this ever fails as an equality, the scale change has started
        // rebalancing weapons it was never supposed to touch.
        assertEquals(shotDamage(null, TileMap.FULL_COVER, Weapon.RIFLE),
                shotDamage(null, Earthworks.ROOFED, Weapon.RIFLE),
                "a roof is no help against a man shooting at you from across the field");

        // A grenade bundle rather than a field gun: same plunging class of problem, and no
        // minimum range to trip over at the one-tile spacing this harness fires at.
        int open = shotDamage(null, TileMap.FULL_COVER, Weapon.GRENADE_BUNDLE);
        int roofed = shotDamage(null, Earthworks.ROOFED, Weapon.GRENADE_BUNDLE);
        assertTrue(roofed < open,
                "overhead cover should stop some of a shell: " + roofed + " vs " + open);
    }

    @Test
    public void theOrdinaryCoverCurveStillTopsOutWhereItAlwaysDid() {
        // Cover is a fraction of the way to FULL_COVER, not to MAX_COVER. Divide by five here
        // and every trench and every ruin on the map quietly gets worse.
        assertEquals(1f - Suppression.coverEffectFor(com.ccwolf.core.combat.WeaponClass.SMALL_ARMS),
                Suppression.damageInCover(com.ccwolf.core.combat.WeaponClass.SMALL_ARMS,
                        TileMap.FULL_COVER, TileMap.FULL_COVER), 0.0001f,
                "four should still be full cover");
    }

    // --- Dispersal ------------------------------------------------------------------------

    /** The widest gap between any two men of a fresh squad of this doctrine. */
    private float squadFrontage(Doctrine doctrine) {
        GameWorld world = world(14L, doctrine, null);
        int[] spot = quietGrass(world.map());
        List<Unit> men = new ArrayList<Unit>();
        for (int i = 0; i < 6; i++) {
            men.add(world.spawnUnit(0, UnitType.PARTISAN,
                    spot[0] + 0.5f + i * 0.4f, spot[1] + 0.5f));
        }
        Squad squad = world.formSquad(0, men);
        assertNotNull(squad, "the squad should have formed");

        float[] a = new float[2];
        float[] b = new float[2];
        float widest = 0f;
        for (int i = 0; i < squad.strength(); i++) {
            squad.slotPosition(i, a);
            for (int j = i + 1; j < squad.strength(); j++) {
                squad.slotPosition(j, b);
                float dx = a[0] - b[0];
                float dy = a[1] - b[1];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > widest) {
                    widest = d;
                }
            }
        }
        return widest;
    }

    @Test
    public void aDispersalSquadStandsWiderThanOneThatPickedNothing() {
        float plain = squadFrontage(null);
        float spread = squadFrontage(Doctrine.ZERSTREUUNG);
        assertTrue(plain > 0f, "a squad of six should occupy some frontage at all");
        assertTrue(spread > plain,
                "Dispersal should widen the squad: " + spread + " vs " + plain);
        assertEquals(plain * Doctrines.spread(Doctrine.ZERSTREUUNG), spread, 0.001f,
                "and widen it by exactly what the table says, not by some emergent amount");
    }

    // --- the negative that the whole commit sequence rests on ------------------------------

    @Test
    public void aSideThatPickedNothingIsUntouchedByAllOfIt() {
        assertEquals(Doctrines.PLAIN_DIG_TICKS, Doctrines.digTicks(null));
        assertTrue(!Doctrines.digsUnderFire(null));
        assertEquals(1f, Doctrines.coverScale(null), 0f);
        assertEquals(1f, Doctrines.spread(null), 0f);

        // And the Regime's three touch none of this either - they buy a unit, not a number.
        for (Doctrine d : Doctrine.forFaction(Faction.REGIME)) {
            assertEquals(Doctrines.PLAIN_DIG_TICKS, Doctrines.digTicks(d), d.toString());
            assertTrue(!Doctrines.digsUnderFire(d), d.toString());
            assertEquals(1f, Doctrines.coverScale(d), 0f, d.toString());
            assertEquals(1f, Doctrines.spread(d), 0f, d.toString());
        }
    }
}
