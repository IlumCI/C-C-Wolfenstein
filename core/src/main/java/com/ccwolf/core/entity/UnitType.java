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
            ArmorClass.LIGHT, null, true, 500, BuildingType.WAR_WORKS, BuildingType.REFINERY),

    /**
     * The Resistance's only gun, and it is not theirs.
     *
     * <p>A Regime field piece taken off a column and dragged behind a farm cart ever since.
     * 1945 in every respect: one shell at a time, a long wait between them, and a crew whose
     * best plan after firing is to be somewhere else. It is also the single most expensive
     * thing the Kreisau Circle can field, which is the point — their heaviest punch is
     * something they stole.
     */
    FELDKANONE("Stolen Feldkanone", Faction.RESISTANCE, 650, 240, 100, 1.3f, 5,
            ArmorClass.FLESH, Weapon.FELDKANONE, false, 0,
            BuildingType.WAR_WORKS, BuildingType.REFINERY),

    /**
     * A rack of tubes that empties itself at a piece of ground.
     *
     * <p>1970s industry, and the first weapon in the game that cares about the shape of what it
     * is shooting at rather than only the distance to it. Four rounds across a frontage is
     * wasted on one man and ruinous to a line of them.
     */
    NEBELWERFER("Nebelwerfer-71", Faction.REGIME, 950, 300, 110, 1.4f, 5,
            ArmorClass.FLESH, Weapon.NEBELWERFER, false, 0,
            BuildingType.WAR_WORKS, BuildingType.REFINERY),

    /**
     * Whatever the Regime dug up, on a carriage.
     *
     * <p>It does not throw anything, and there is no crater afterwards. Cover is no help and
     * earthworks are no help, and what it mostly leaves behind is a position full of men who
     * are alive and will not stay. Ponderous, ruinously expensive, and it outranges everything
     * else on the map — which is the whole argument for building one.
     */
    RESONANZKANONE("Resonanzkanone", Faction.REGIME, 1800, 440, 130, 0.9f, 6,
            ArmorClass.FLESH, Weapon.RESONANZKANONE, false, 0,
            BuildingType.WAR_WORKS, BuildingType.REFINERY),

    // --- the doctrine units ---------------------------------------------------------------
    // Each exists only for a player who declared for its doctrine at match start:
    // requiredDoctrine() is the gate and production checks it. None of them appears in a
    // baseline match, which is what keeps the determinism goldens still.

    /**
     * Firestorm's answer to a dug line: cheap flame teams, and a lot of them.
     *
     * <p>The Sturmpionier is an engineer who happens to carry fire; this is fire that happens
     * to have men attached. Half the cost, twice the wave.
     */
    FLAMMTRUPP("Flammtrupp", Faction.REGIME, 320, 120, 90, 2.1f, 4,
            ArmorClass.FLESH, Weapon.FLAMMENWERFER, false, 0,
            BuildingType.BARRACKS, null),

    /**
     * Gas War's one delivery system.
     *
     * <p>A crew and a pressure gun, on the Feldkanone's pattern: indirect, slow, soft. It has
     * to stand closer to the line than any other battery, because what it throws is worthless
     * against anything that can simply drive away - it exists to make trenches lethal to the
     * men holding them.
     */
    GASWERFER("Gaswerfer-40", Faction.REGIME, 800, 280, 100, 1.3f, 5,
            ArmorClass.FLESH, Weapon.GASWERFER, false, 0,
            BuildingType.WAR_WORKS, BuildingType.REFINERY),

    /**
     * Extermination made walkable: the machine that goes in and empties the position.
     *
     * <p>Slower than an Ubersoldat, twice the plate, and armed with a projector that reaches
     * barely past its own fists - the doctrine is that it closes, and everything about the
     * statline forces the question of what happens when it arrives.
     */
    AUSMERZER("Ausmerzer", Faction.REGIME, 1400, 380, 700, 1.2f, 5,
            ArmorClass.HEAVY, Weapon.VERNICHTER, true, 0,
            BuildingType.WAR_WORKS, BuildingType.REFINERY);

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
            case FLAMMTRUPP:
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
    /**
     * True for a gun that drops shells on a place rather than shooting at a thing.
     *
     * <p>Keyed on the weapon having a dead zone rather than on a list of unit names, because
     * that is the property everything actually cares about: a weapon with a hole in the middle
     * of its range needs callers that back off instead of closing, and needs keeping out of any
     * list that ends in an attack-move.
     */
    public boolean isArtillery() {
        return weapon != null && weapon.hasMinRange();
    }

    public boolean isInfiltrator() {
        return this == INFILTRATOR || this == SABOTEUR;
    }

    /** Collision/selection radius in tiles. Vehicles are chunkier than infantry. */
    /**
     * The doctrine a player must have declared for this unit to exist for them, or null.
     *
     * <p>This is the whole mechanism by which the Regime's doctrines buy roster instead of
     * numbers: production checks it in exactly one place ({@code GameWorld.canProduce}), the
     * same way faction availability works, so no menu can offer what the declaration did not.
     */
    public Doctrine requiredDoctrine() {
        switch (this) {
            case FLAMMTRUPP:
                return Doctrine.BRANDSTURM;
            case GASWERFER:
                return Doctrine.GASKRIEG;
            case AUSMERZER:
                return Doctrine.AUSMERZUNG;
            default:
                return null;
        }
    }

    public float radius() {
        switch (this) {
            // The guns take up real ground. A machine drawn three and a half tiles across that
            // other units walk straight through would look like a bug, and the footprint is
            // also what makes a battery worth flanking rather than worth walking around.
            case RESONANZKANONE:
                return 1.0f;
            case NEBELWERFER:
                return 0.7f;
            case FELDKANONE:
            case GASWERFER:
                return 0.5f;
            case AUSMERZER:
                return 0.55f;
            default:
                return vehicle ? 0.42f : 0.28f;
        }
    }
}
