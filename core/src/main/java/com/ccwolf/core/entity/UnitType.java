package com.ccwolf.core.entity;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.Weapon;

/**
 * Unit stat table — the single place all mobile-unit balance lives.
 *
 * <p>Speeds are tiles per second on open ground, build times are ticks at 20 per second,
 * sight is a radius in tiles. A null {@link #weapon()} means the unit cannot attack.
 */
public enum UnitType {

    // --- Kreisau Circle -------------------------------------------------------------------
    /** Rifle infantry. The line unit: cheap, quick to train, dies to anything armoured. */
    PARTISAN("Partisan", Faction.RESISTANCE, 120, 60, 90, 2.0f, 5,
            ArmorClass.FLESH, Weapon.RIFLE, false, 0, BuildingType.BARRACKS, null),

    /** Anti-armour infantry. Deletes hounds and jeeps, helpless in a knife fight. */
    ROCKETEER("Panzerschreck Team", Faction.RESISTANCE, 300, 100, 80, 1.7f, 5,
            ArmorClass.FLESH, Weapon.PANZERSCHRECK, false, 0, BuildingType.BARRACKS, null),

    /** Fast scout with a pintle gun. Reveals map, harasses harvesters. */
    SCOUT_JEEP("Scout Jeep", Faction.RESISTANCE, 400, 140, 220, 4.2f, 7,
            ArmorClass.LIGHT, Weapon.JEEP_MG, true, 0, BuildingType.WAR_WORKS, null),

    /**
     * A Regime tank the Resistance stole, repainted and pressed into service. Their only
     * heavy armour, and their answer to the Ubersoldat.
     */
    CAPTURED_PANZER("Captured Panzer", Faction.RESISTANCE, 1000, 320, 450, 1.9f, 6,
            ArmorClass.HEAVY, Weapon.PANZER_CANNON, true, 0, BuildingType.WAR_WORKS, null),

    // --- Totenkopf Division ---------------------------------------------------------------
    /** Regime line infantry: slightly cheaper and faster-firing than a Partisan, shorter reach. */
    SOLDAT("Soldat", Faction.REGIME, 110, 55, 85, 2.0f, 5,
            ArmorClass.FLESH, Weapon.MP_SMG, false, 0, BuildingType.BARRACKS, null),

    /** Armoured walker. Slow, expensive, immune to small arms. Needs the War Works standing. */
    UBERSOLDAT("Ubersoldat", Faction.REGIME, 800, 260, 400, 1.3f, 5,
            ArmorClass.HEAVY, Weapon.UBER_CANNON, false, 0, BuildingType.BARRACKS,
            BuildingType.WAR_WORKS),

    /** Mech-hound: the fastest thing on the map, murder on infantry, paper-thin. */
    PANZERHUND("Panzerhund", Faction.REGIME, 500, 160, 230, 5.0f, 6,
            ArmorClass.LIGHT, Weapon.HOUND_JAWS, true, 0, BuildingType.WAR_WORKS, null),

    // --- Shared ---------------------------------------------------------------------------
    /** Mines uranium and hauls it back to a refinery. Unarmed and always a target. */
    HARVESTER("Harvester", null, 1000, 300, 500, 2.2f, 4,
            ArmorClass.LIGHT, null, true, 500, BuildingType.WAR_WORKS, BuildingType.REFINERY);

    private final String displayName;
    private final Faction faction;
    private final int cost;
    private final int buildTicks;
    private final int maxHp;
    private final float speed;
    private final int sight;
    private final ArmorClass armor;
    private final Weapon weapon;
    private final boolean vehicle;
    private final int oreCapacity;
    private final BuildingType producedBy;
    private final BuildingType prerequisite;

    UnitType(String displayName, Faction faction, int cost, int buildTicks, int maxHp, float speed,
             int sight, ArmorClass armor, Weapon weapon, boolean vehicle, int oreCapacity,
             BuildingType producedBy, BuildingType prerequisite) {
        this.displayName = displayName;
        this.faction = faction;
        this.cost = cost;
        this.buildTicks = buildTicks;
        this.maxHp = maxHp;
        this.speed = speed;
        this.sight = sight;
        this.armor = armor;
        this.weapon = weapon;
        this.vehicle = vehicle;
        this.oreCapacity = oreCapacity;
        this.producedBy = producedBy;
        this.prerequisite = prerequisite;
    }

    public String displayName() {
        return displayName;
    }

    /** The faction that fields this unit, or null if both sides can build it. */
    public Faction faction() {
        return faction;
    }

    public boolean availableTo(Faction f) {
        return faction == null || faction == f;
    }

    public int cost() {
        return cost;
    }

    public int buildTicks() {
        return buildTicks;
    }

    public int maxHp() {
        return maxHp;
    }

    /** Tiles per second on open ground; terrain move cost scales this. */
    public float speed() {
        return speed;
    }

    public int sight() {
        return sight;
    }

    public ArmorClass armor() {
        return armor;
    }

    public Weapon weapon() {
        return weapon;
    }

    public boolean isVehicle() {
        return vehicle;
    }

    /** How much uranium this unit can carry; 0 for everything but the harvester. */
    public int oreCapacity() {
        return oreCapacity;
    }

    public boolean isHarvester() {
        return oreCapacity > 0;
    }

    public BuildingType producedBy() {
        return producedBy;
    }

    /** Extra structure that must be standing, beyond {@link #producedBy()}, or null. */
    public BuildingType prerequisite() {
        return prerequisite;
    }

    /** Collision/selection radius in tiles. Vehicles are chunkier than infantry. */
    public float radius() {
        return vehicle ? 0.42f : 0.28f;
    }
}
