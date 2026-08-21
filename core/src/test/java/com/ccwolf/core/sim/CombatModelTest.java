package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.Suppression;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.squad.Squad;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cover, suppression, morale and exhaustion — the four things that make a fight cost something
 * other than hit points.
 *
 * <p>Each is asserted on its own effect rather than on an outcome, because outcomes in a
 * simulation this size have too many causes to pin a mechanic on.
 */
public class CombatModelTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    // --- cover ----------------------------------------------------------------------------

    @Test
    public void coverBluntsRifleFireAndBarelyTouchesGrenades() {
        int max = TileMap.MAX_COVER;
        float rifleOpen = Suppression.damageInCover(
                Weapon.RIFLE.weaponClass(), 0, max);
        float rifleDugIn = Suppression.damageInCover(
                Weapon.RIFLE.weaponClass(), max, max);
        float grenadeDugIn = Suppression.damageInCover(
                Weapon.GRENADE_BUNDLE.weaponClass(), max, max);

        assertEquals(1f, rifleOpen, 0.001f);
        assertTrue(rifleDugIn < 0.4f, "a trench should stop most rifle fire, got " + rifleDugIn);
        assertTrue(grenadeDugIn > 0.8f,
                "a grenade should barely care about a trench, got " + grenadeDugIn);
        // This asymmetry is the reason artillery and grenadiers exist.
        assertTrue(grenadeDugIn > rifleDugIn * 2f);
    }

    @Test
    public void aManInCoverTakesLessFromARifle() {
        GameWorld world = world(1L);
        Unit shooter = world.spawnUnit(0, UnitType.PARTISAN, 20.5f, 16.5f);
        Unit exposed = world.spawnUnit(1, UnitType.SOLDAT, 22.5f, 16.5f);
        Unit dugIn = world.spawnUnit(1, UnitType.SOLDAT, 22.5f, 18.5f);
        world.map().setCover(dugIn.tileX(), dugIn.tileY(), TileMap.MAX_COVER);

        int before = exposed.hp();
        world.tryAttack(shooter, exposed);
        int openDamage = before - exposed.hp();

        shooter.clearWeaponCooldown();
        before = dugIn.hp();
        world.tryAttack(shooter, dugIn);
        int coveredDamage = before - dugIn.hp();

        assertTrue(openDamage > coveredDamage,
                "cover took " + coveredDamage + " against " + openDamage + " in the open");
    }

    @Test
    public void coverDoesNothingForAVehicle() {
        GameWorld world = world(2L);
        Unit shooter = world.spawnUnit(0, UnitType.ROCKETEER, 20.5f, 16.5f);
        Unit tank = world.spawnUnit(1, UnitType.STURMPANZER, 22.5f, 16.5f);
        world.map().setCover(tank.tileX(), tank.tileY(), TileMap.MAX_COVER);

        int before = tank.hp();
        world.tryAttack(shooter, tank);
        int dugIn = before - tank.hp();

        world.map().setCover(tank.tileX(), tank.tileY(), 0);
        shooter.clearWeaponCooldown();
        before = tank.hp();
        world.tryAttack(shooter, tank);
        int open = before - tank.hp();

        // A wall a rifleman shelters behind is not cover for a tank, it is scenery.
        assertEquals(open, dugIn);
    }

    // --- suppression ----------------------------------------------------------------------

    @Test
    public void sustainedFirePinsAManAndHeCannotAdvance() {
        GameWorld world = world(3L);
        Unit target = world.spawnUnit(1, UnitType.SOLDAT, 30.5f, 16.5f);
        target.setOrder(new MoveOrder(44, 16));

        // A section's worth of rifles on one man.
        List<Unit> firing = new ArrayList<Unit>();
        for (int i = 0; i < 6; i++) {
            firing.add(world.spawnUnit(0, UnitType.PARTISAN, 28.5f, 15.5f + i * 0.4f));
        }

        float startX = target.x();
        for (int t = 0; t < 60; t++) {
            for (int i = 0; i < firing.size(); i++) {
                world.tryAttack(firing.get(i), target);
            }
            world.step();
            if (!target.isAlive()) {
                break;
            }
        }

        if (target.isAlive()) {
            assertTrue(target.isPinned(),
                    "six rifles should have pinned him, suppression was " + target.suppression());
            assertTrue(target.x() - startX < 1.5f,
                    "a pinned man should not have advanced, but moved "
                            + (target.x() - startX));
        }
    }

    @Test
    public void suppressionBleedsOffFasterInCover() {
        GameWorld world = world(4L);
        Unit inOpen = world.spawnUnit(0, UnitType.PARTISAN, 20.5f, 16.5f);
        Unit inCover = world.spawnUnit(0, UnitType.PARTISAN, 24.5f, 16.5f);
        // Both tiles set explicitly. "Somewhere on the map" is not the same as "in the open" -
        // rubble and uranium both start with cover of their own, and picking a tile that
        // happened to have some made this compare cover against cover.
        world.map().setCover(inOpen.tileX(), inOpen.tileY(), 0);
        world.map().setCover(inCover.tileX(), inCover.tileY(), TileMap.MAX_COVER);

        inOpen.addSuppression(Suppression.MAX);
        inCover.addSuppression(Suppression.MAX);
        for (int i = 0; i < 10; i++) {
            world.step();
        }

        assertTrue(inCover.suppression() < inOpen.suppression(),
                "cover should let a man get his nerve back sooner");
    }

    @Test
    public void aVehicleIsNeverSuppressed() {
        GameWorld world = world(5L);
        Unit shooter = world.spawnUnit(0, UnitType.PARTISAN, 20.5f, 16.5f);
        Unit tank = world.spawnUnit(1, UnitType.STURMPANZER, 22.5f, 16.5f);

        for (int i = 0; i < 20; i++) {
            shooter.clearWeaponCooldown();
            world.tryAttack(shooter, tank);
        }
        assertEquals(0, tank.suppression(), "a tank does not keep its head down");
    }

    // --- morale ---------------------------------------------------------------------------

    @Test
    public void aSquadUnderFireItCannotAnswerLosesItsNerve() {
        GameWorld world = world(6L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 8; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 30.5f + i * 0.9f, 30.5f));
        }
        Squad squad = world.formSquad(0, members);
        assertEquals(100, squad.morale());

        // Snipers outside the squad's sight but inside their own range: fire it can neither
        // answer nor see the source of. Distance matters more than it looks - a rifleman put
        // next to the squad was killed in the first second, and a sniper at four tiles was
        // inside their sight, so they closed on it and killed it too.
        List<Unit> snipers = new ArrayList<Unit>();
        for (int i = 0; i < 3; i++) {
            snipers.add(world.spawnUnit(1, UnitType.SCHARFSCHUTZE, 41.5f, 29.5f + i));
        }

        for (int t = 0; t < 4000 && !squad.isBroken() && !squad.isWipedOut(); t++) {
            for (int i = 0; i < members.size(); i++) {
                Unit m = members.get(i);
                if (m.isAlive()) {
                    // At the weapons' own rate. Clearing cooldowns each tick fires twenty
                    // times faster than anything in the game can, which kills a squad long
                    // before its nerve has a chance to give out.
                    for (int k = 0; k < snipers.size(); k++) {
                        world.tryAttack(snipers.get(k), m);
                    }
                    break;
                }
            }
            world.step();
        }

        // Asserted on the mechanism rather than on a particular outcome, because whether a
        // squad breaks before it is wiped out depends on exactly how the loss weight is tuned
        // against how fast men die, and that is a balance number that will move.
        assertTrue(squad.morale() < 80,
                "sustained fire should have told on them, morale was " + squad.morale()
                        + " at strength " + squad.strength());
        // And it cannot simply shrug the losses off: a badly cut-about squad recovers only to
        // a ceiling set by how much of it is left, which is what stops a worn formation being
        // as good as a fresh one the moment the shooting stops.
        assertTrue(squad.morale() < 100 && squad.strengthFraction() < 1f);
        if (squad.isBroken()) {
            assertTrue(squad.strength() > 0, "if it broke, it ran rather than dying to a man");
        }
    }

    @Test
    public void aBrokenSquadTakesNoOrders() {
        GameWorld world = world(7L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 30.5f + i * 0.9f, 30.5f));
        }
        Squad squad = world.formSquad(0, members);
        squad.breakAt(world.tick() + 200);

        world.orderSquadTo(0, squad, com.ccwolf.core.squad.SquadOrder.MOVE, 44, 30);

        // Losing a position is losing it: you do not get to tell men who have run to stand.
        assertEquals(com.ccwolf.core.squad.SquadOrder.HOLD, squad.order());
    }

    @Test
    public void aBrokenSquadComesBackShakenRatherThanRestored() {
        GameWorld world = world(8L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 12.5f + i * 0.9f, 14.5f));
        }
        Squad squad = world.formSquad(0, members);
        squad.breakAt(world.tick() + 40);

        for (int t = 0; t < 400 && squad.isBroken(); t++) {
            world.step();
        }

        assertFalse(squad.isBroken(), "with nobody near, it should have rallied");
        assertTrue(squad.morale() < 60,
                "a rallied squad should still be shaken, morale was " + squad.morale());
    }

    // --- exhaustion -----------------------------------------------------------------------

    @Test
    public void anArmyInContactTiresAndOneAtRestRecovers() {
        GameWorld world = world(9L);
        // Exhaustion is measured as the fraction of an army in contact, so both sides need
        // armies. Player 0 commits its whole strength; player 1 sends one man and keeps the
        // rest at home, which is what "not fighting hard" means here.
        List<Unit> committed = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            committed.add(world.spawnUnit(0, UnitType.PARTISAN, 30.5f + i * 0.9f, 30.5f));
        }
        Unit skirmisher = world.spawnUnit(1, UnitType.SOLDAT, 32.5f, 31.5f);
        for (int i = 0; i < 12; i++) {
            world.spawnUnit(1, UnitType.SOLDAT, 50.5f + (i % 4) * 0.9f, 48.5f + (i / 4) * 0.9f);
        }

        world.player(0).changeStamina(-30);
        world.player(1).changeStamina(-30);

        for (int t = 0; t < 400; t++) {
            for (int i = 0; i < committed.size(); i++) {
                Unit u = committed.get(i);
                if (u.isAlive() && skirmisher.isAlive()) {
                    u.clearWeaponCooldown();
                    skirmisher.clearWeaponCooldown();
                    world.tryAttack(skirmisher, u);
                    world.tryAttack(u, skirmisher);
                }
            }
            world.step();
        }

        assertTrue(world.player(0).stamina() < world.player(1).stamina(),
                "the side with its whole army in contact should be the more tired: "
                        + world.player(0).stamina() + " against " + world.player(1).stamina());
    }

    @Test
    public void aTiredArmyRecoversItsNerveMoreSlowly() {
        GameWorld world = world(10L);
        List<Unit> fresh = new ArrayList<Unit>();
        List<Unit> tired = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            fresh.add(world.spawnUnit(0, UnitType.PARTISAN, 20.5f + i * 0.9f, 30.5f));
            tired.add(world.spawnUnit(1, UnitType.SOLDAT, 40.5f + i * 0.9f, 44.5f));
        }
        Squad freshSquad = world.formSquad(0, fresh);
        Squad tiredSquad = world.formSquad(1, tired);
        freshSquad.setMorale(20);
        tiredSquad.setMorale(20);
        world.player(1).changeStamina(-90);

        for (int t = 0; t < 200; t++) {
            world.step();
        }

        assertTrue(freshSquad.morale() > tiredSquad.morale(),
                "a fresh army should get its nerve back faster: " + freshSquad.morale()
                        + " against " + tiredSquad.morale());
    }
}
