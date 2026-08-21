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
     * Marksman: reaches further than anything else on foot and kills a man with one round,
     * but cannot scratch a hull. Lies up when it holds still.
     */
    MARKSMAN("Marksman", Faction.RESISTANCE, 350, 130, 70, 1.6f, 8,
            ArmorClass.FLESH, Weapon.HUNTING_RIFLE, false, 0, BuildingType.BARRACKS,
            BuildingType.WAR_WORKS),

    /** Grenadier: bundled charges, and the only Resistance answer to massed Soldaten. */
    GRENADIER("Grenadier", Faction.RESISTANCE, 280, 110, 85, 1.8f, 5,
            ArmorClass.FLESH, Weapon.GRENADE_BUNDLE, false, 0, BuildingType.BARRACKS, null),

    /**
     * Saboteur: carries charges, not a gun. Shuts a structure or a walker down long enough
     * for the rest of the cell to do something about it.
     */
    SABOTEUR("Saboteur", Faction.RESISTANCE, 450, 150, 75, 2.1f, 5,
            ArmorClass.FLESH, null, false, 0, BuildingType.BARRACKS, BuildingType.REFINERY),

    /**
     * Infiltrator: unarmed, hidden, and the reason the Resistance can field Regime armour at
     * all. Boards an enemy vehicle and drives it home.
     */
    INFILTRATOR("Infiltrator", Faction.RESISTANCE, 600, 200, 65, 2.0f, 6,
            ArmorClass.FLESH, null, false, 0, BuildingType.BARRACKS, BuildingType.WAR_WORKS),

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

    /** Regime counter-sniper: everything the Marksman is, for rather more money. */
    SCHARFSCHUTZE("Scharfschutze", Faction.REGIME, 420, 150, 75, 1.6f, 8,
            ArmorClass.FLESH, Weapon.SCHARFSCHUTZE_RIFLE, false, 0, BuildingType.BARRACKS,
            BuildingType.WAR_WORKS),

    /**
     * Sturmpionier: a flame projector on legs. Clears a trench or a building in seconds and
     * dies to anything that can shoot back from range.
     */
    STURMPIONIER("Sturmpionier", Faction.REGIME, 500, 180, 140, 1.7f, 4,
            ArmorClass.FLESH, Weapon.FLAMMENWERFER, false, 0, BuildingType.BARRACKS,
            BuildingType.REFINERY),

    /** Mech-hound: the fastest thing on the map, murder on infantry, paper-thin. */
    PANZERHUND("Panzerhund", Faction.REGIME, 500, 160, 230, 5.0f, 6,
            ArmorClass.LIGHT, Weapon.HOUND_JAWS, true, 0, BuildingType.WAR_WORKS, null),

    /**
     * Sturmpanzer: the heaviest thing in the game and the Resistance's favourite thing to
     * steal. They have no equivalent and cannot build one.
     */
    STURMPANZER("Sturmpanzer", Faction.REGIME, 900, 340, 620, 1.7f, 6,
            ArmorClass.HEAVY, Weapon.STURM_CANNON, true, 0, BuildingType.WAR_WORKS, null),

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

    /**
     * Whether this unit lies up rather than standing about: stealthy units are invisible to
     * the enemy while they hold still and are not shooting.
     */
    /**
     * How many of this type are trained and fight as one body.
     *
     * <p>One switch decides the whole feature. Vehicles and harvesters are individuals because
     * they are; Saboteurs and Infiltrators are individuals because their whole job is to slip
     * away from everybody else, and putting them in a formation would defeat the point.
     */
    public int squadSize() {
        switch (this) {
            case PARTISAN:
            case SOLDAT:
                return 8;
            case ROCKETEER:
            case GRENADIER:
            case STURMPIONIER:
                return 4;
            case MARKSMAN:
            case SCHARFSCHUTZE:
                return 2;
            case UBERSOLDAT:
                return 2;
            default:
                // Vehicles, harvesters, Saboteurs and Infiltrators fight alone.
                return 1;
        }
    }

    /** True for types that are trained and commanded as a squad rather than one at a time. */
    public boolean formsSquads() {
        return squadSize() > 1;
    }

    public boolean isStealthy() {
        return this == MARKSMAN || this == INFILTRATOR || this == SCHARFSCHUTZE;
    }

    /** Unarmed specialists that act on a target instead of shooting it. */
    public boolean isInfiltrator() {
        return this == INFILTRATOR || this == SABOTEUR;
    }

    /** Collision/selection radius in tiles. Vehicles are chunkier than infantry. */
    public float radius() {
        return vehicle ? 0.42f : 0.28f;
    }
}
