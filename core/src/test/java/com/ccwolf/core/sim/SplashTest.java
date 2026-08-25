package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.DamageTable;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Blast weapons, and the two new weapon classes they arrived with. */
class SplashTest {

    private GameWorld world;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.openField(40, 40), 5L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 2, 2, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 34, 34, true);
    }

    @Test
    void aBlastHurtsEverythingHostileAroundTheTarget() {
        Unit gunner = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f);
        Unit aimedAt = world.spawnUnit(1, UnitType.SOLDAT, 12.0f, 10.5f);
        Unit bystander = world.spawnUnit(1, UnitType.SOLDAT, 12.8f, 10.5f);
        Unit farAway = world.spawnUnit(1, UnitType.SOLDAT, 20.0f, 10.5f);

        world.tryAttack(gunner, aimedAt, Weapon.GRENADE_BUNDLE);

        assertTrue(aimedAt.hp() < aimedAt.maxHp(), "the aimed target takes the full hit");
        assertTrue(bystander.hp() < bystander.maxHp(), "a neighbour is caught in the blast");
        assertEquals(farAway.maxHp(), farAway.hp(), "someone outside the radius is untouched");
    }

    @Test
    void blastDamageFallsOffWithDistance() {
        Unit gunner = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f);
        Unit aimedAt = world.spawnUnit(1, UnitType.SOLDAT, 12.0f, 10.5f);
        Unit near = world.spawnUnit(1, UnitType.SOLDAT, 12.5f, 10.5f);
        Unit edge = world.spawnUnit(1, UnitType.SOLDAT, 13.4f, 10.5f);

        world.tryAttack(gunner, aimedAt, Weapon.GRENADE_BUNDLE);

        int nearDamage = near.maxHp() - near.hp();
        int edgeDamage = edge.maxHp() - edge.hp();
        assertTrue(nearDamage > 0 && edgeDamage > 0);
        assertTrue(nearDamage > edgeDamage,
                "closer to the burst should hurt more: " + nearDamage + " vs " + edgeDamage);
    }

    @Test
    void aBlastNeverCatchesYourOwnTroops() {
        Unit gunner = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f);
        Unit target = world.spawnUnit(1, UnitType.SOLDAT, 12.0f, 10.5f);
        Unit comrade = world.spawnUnit(0, UnitType.PARTISAN, 12.6f, 10.5f);

        world.tryAttack(gunner, target, Weapon.GRENADE_BUNDLE);

        assertEquals(comrade.maxHp(), comrade.hp(),
                "friendly fire would make both the AI and the player miserable");
    }

    @Test
    void singleTargetWeaponsStillHitOnlyOneThing() {
        Unit rifleman = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f);
        Unit target = world.spawnUnit(1, UnitType.SOLDAT, 11.5f, 10.5f);
        Unit neighbour = world.spawnUnit(1, UnitType.SOLDAT, 12.0f, 10.5f);

        world.tryAttack(rifleman, target, Weapon.RIFLE);

        assertTrue(target.hp() < target.maxHp());
        assertEquals(neighbour.maxHp(), neighbour.hp());
    }

    @Test
    void aSniperRoundKillsMenAndBouncesOffArmour() {
        int vsFlesh = DamageTable.damage(100, WeaponClass.SNIPER, ArmorClass.FLESH);
        int vsHeavy = DamageTable.damage(100, WeaponClass.SNIPER, ArmorClass.HEAVY);

        assertTrue(vsFlesh > 100, "a scoped round should be worse than a rifle round");
        assertTrue(vsHeavy < 15, "and should be pointless against a hull");
    }

    @Test
    void fragmentationBeatsMasonryAndStrugglesAgainstPlate() {
        int vsConcrete = DamageTable.damage(100, WeaponClass.GRENADE, ArmorClass.CONCRETE);
        int vsHeavy = DamageTable.damage(100, WeaponClass.GRENADE, ArmorClass.HEAVY);

        assertTrue(vsConcrete > vsHeavy * 2);
    }
}
