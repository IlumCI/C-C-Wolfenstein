package com.ccwolf.core.sim;

import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;

/**
 * One side in the match: its bank, its power situation and its three production lines.
 *
 * <p>Power works the C&amp;C way — a shortfall does not switch the base off, it slows every
 * production line down, so being under-powered is a tax rather than a cliff.
 */
public final class Player {

    /** Slowest a fully browned-out base can build, as a fraction of normal speed. */
    private static final float MIN_POWER_FACTOR = 0.25f;

    private final int id;
    private final Faction faction;
    private final boolean ai;
    private final String name;

    private int credits;
    private int powerProduced;
    private int powerDrawn;
    private boolean defeated;

    private final ProductionQueue infantryQueue = new ProductionQueue();
    private final ProductionQueue vehicleQueue = new ProductionQueue();
    private final ProductionQueue structureQueue = new ProductionQueue();

    private int creditsEarned;
    private int unitsBuilt;
    private int unitsLost;
    private int buildingsLost;

    Player(int id, Faction faction, boolean ai, String name, int startingCredits) {
        this.id = id;
        this.faction = faction;
        this.ai = ai;
        this.name = name;
        this.credits = startingCredits;
    }

    public int id() {
        return id;
    }

    public Faction faction() {
        return faction;
    }

    public boolean isAi() {
        return ai;
    }

    public String name() {
        return name;
    }

    public int credits() {
        return credits;
    }

    public boolean canAfford(int amount) {
        return credits >= amount;
    }

    public boolean spend(int amount) {
        if (credits < amount) {
            return false;
        }
        credits -= amount;
        return true;
    }

    public void refund(int amount) {
        credits += amount;
    }

    /** Credits from a harvester unloading; tracked separately for the end-of-match summary. */
    public void deposit(int amount) {
        credits += amount;
        creditsEarned += amount;
    }

    public int powerProduced() {
        return powerProduced;
    }

    public int powerDrawn() {
        return powerDrawn;
    }

    void setPower(int produced, int drawn) {
        this.powerProduced = produced;
        this.powerDrawn = drawn;
    }

    public boolean isLowPower() {
        return powerDrawn > powerProduced;
    }

    /** Production speed multiplier from the power balance, between 0.25 and 1.0. */
    public float powerFactor() {
        if (powerDrawn <= powerProduced) {
            return 1f;
        }
        if (powerProduced <= 0) {
            return MIN_POWER_FACTOR;
        }
        return Math.max(MIN_POWER_FACTOR, powerProduced / (float) powerDrawn);
    }

    public ProductionQueue infantryQueue() {
        return infantryQueue;
    }

    public ProductionQueue vehicleQueue() {
        return vehicleQueue;
    }

    public ProductionQueue structureQueue() {
        return structureQueue;
    }

    /** The line a given producer structure feeds. */
    public ProductionQueue queueFor(BuildingType producer) {
        if (producer == BuildingType.WAR_WORKS) {
            return vehicleQueue;
        }
        if (producer == BuildingType.BARRACKS) {
            return infantryQueue;
        }
        return structureQueue;
    }

    public boolean isDefeated() {
        return defeated;
    }

    void setDefeated(boolean defeated) {
        this.defeated = defeated;
    }

    public int creditsEarned() {
        return creditsEarned;
    }

    public int unitsBuilt() {
        return unitsBuilt;
    }

    public int unitsLost() {
        return unitsLost;
    }

    public int buildingsLost() {
        return buildingsLost;
    }

    void noteUnitBuilt() {
        unitsBuilt++;
    }

    void noteUnitLost() {
        unitsLost++;
    }

    void noteBuildingLost() {
        buildingsLost++;
    }
}
