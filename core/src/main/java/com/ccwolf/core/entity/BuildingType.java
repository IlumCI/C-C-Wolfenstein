package com.ccwolf.core.entity;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.Weapon;

/**
 * Structure stat table. Both factions build the same set; only the flavour text differs.
 *
 * <p>Build times are in simulation ticks at 20 ticks per second. Power is the classic
 * C&amp;C treatment: a shortfall slows production rather than shutting the base down.
 */
public enum BuildingType {

    /** Pre-placed. Everything else is placed from here; losing it ends your build options. */
    COMMAND_POST("Command Post", 0, 0, 1400, 3, 3, 100, 0, 6, null, null),

    /** Burns coal, makes amps. */
    GENERATOR("Generator", 300, 240, 500, 2, 2, 100, 0, 4, null, null),

    /** Uranium goes in, credits come out. Comes with one free harvester. */
    REFINERY("Uranium Refinery", 1200, 500, 900, 3, 2, 0, 40, 5, null, COMMAND_POST),

    /** Trains infantry. */
    BARRACKS("Barracks", 500, 300, 700, 2, 2, 0, 20, 4, null, COMMAND_POST),

    /** Builds vehicles, walkers and harvesters. */
    WAR_WORKS("War Works", 1500, 600, 1000, 3, 2, 0, 60, 4, null, REFINERY),

    /** Static defence. Outranges every mobile unit in the game. */
    FLAK_TURRET("Flak Turret", 600, 250, 600, 1, 1, 0, 30, 7, Weapon.TURRET_GUN, BARRACKS),

    /** Cheap, quick, and only a threat to men on foot. The first thing anyone builds. */
    MG_NEST("MG Nest", 350, 160, 420, 1, 1, 0, 15, 6, Weapon.NEST_MG, BARRACKS),

    /** Anti-tank gun: it will stop armour and it will miss a running man all day. */
    PAK_GUN("Pak Gun", 700, 300, 520, 1, 1, 0, 35, 7, Weapon.PAK_GUN, WAR_WORKS);

    private final String displayName;
    private final int cost;
    private final int buildTicks;
    private final int maxHp;
    private final int tilesWide;
    private final int tilesHigh;
    private final int powerProduced;
    private final int powerDrawn;
    private final int sight;
    private final Weapon weapon;
    private final BuildingType prerequisite;

    BuildingType(String displayName, int cost, int buildTicks, int maxHp, int tilesWide,
                 int tilesHigh, int powerProduced, int powerDrawn, int sight, Weapon weapon,
                 BuildingType prerequisite) {
        this.displayName = displayName;
        this.cost = cost;
        this.buildTicks = buildTicks;
        this.maxHp = maxHp;
        this.tilesWide = tilesWide;
        this.tilesHigh = tilesHigh;
        this.powerProduced = powerProduced;
        this.powerDrawn = powerDrawn;
        this.sight = sight;
        this.weapon = weapon;
        this.prerequisite = prerequisite;
    }

    public String displayName() {
        return displayName;
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

    public int tilesWide() {
        return tilesWide;
    }

    public int tilesHigh() {
        return tilesHigh;
    }

    public int powerProduced() {
        return powerProduced;
    }

    public int powerDrawn() {
        return powerDrawn;
    }

    public int sight() {
        return sight;
    }

    /** The turret's gun, or null for unarmed structures. */
    public Weapon weapon() {
        return weapon;
    }

    /** Structure that must already be standing before this one can be placed, or null. */
    public BuildingType prerequisite() {
        return prerequisite;
    }

    public ArmorClass armor() {
        return ArmorClass.CONCRETE;
    }

    /** Whether this structure can train or assemble units. */
    public boolean isProducer() {
        return this == BARRACKS || this == WAR_WORKS;
    }
}
