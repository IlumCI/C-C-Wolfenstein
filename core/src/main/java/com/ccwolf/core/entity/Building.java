package com.ccwolf.core.entity;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.Weapon;

/**
 * A structure. Occupies a rectangle of tiles anchored at its top-left corner; its
 * {@link #x()}/{@link #y()} are the centre of that rectangle so range maths matches units.
 *
 * <p>A structure under construction is alive and shootable but not {@link #isOperational()}:
 * it produces no power, cannot fire and cannot produce units until it finishes.
 */
public final class Building extends Entity {

    private final BuildingType type;
    private final int tileX;
    private final int tileY;

    private int buildProgress;
    private boolean complete;
    private int weaponCooldown;

    /** False while the owner is in a power deficit; only ever set on defences. */
    private boolean powered = true;
    private boolean repairing;
    private int repairTicks;

    /** Where newly produced units walk to, defaults to just below the structure. */
    private int rallyX;
    private int rallyY;

    public Building(int id, int ownerId, BuildingType type, int tileX, int tileY,
                    boolean startComplete) {
        super(id, ownerId, tileX + type.tilesWide() / 2f, tileY + type.tilesHigh() / 2f,
                startComplete ? type.maxHp() : Math.max(1, type.maxHp() / 10));
        this.type = type;
        this.tileX = tileX;
        this.tileY = tileY;
        this.complete = startComplete;
        this.buildProgress = startComplete ? type.buildTicks() : 0;
        this.rallyX = tileX + type.tilesWide() / 2;
        this.rallyY = tileY + type.tilesHigh() + 1;
    }

    public BuildingType type() {
        return type;
    }

    public int tileX() {
        return tileX;
    }

    public int tileY() {
        return tileY;
    }

    public int tilesWide() {
        return type.tilesWide();
    }

    public int tilesHigh() {
        return type.tilesHigh();
    }

    /** True if the given tile is part of this structure's footprint. */
    public boolean covers(int tx, int ty) {
        return tx >= tileX && ty >= tileY
                && tx < tileX + type.tilesWide() && ty < tileY + type.tilesHigh();
    }

    @Override
    public int maxHp() {
        return type.maxHp();
    }

    @Override
    public ArmorClass armor() {
        return ArmorClass.CONCRETE;
    }

    @Override
    public int sight() {
        return type.sight();
    }

    @Override
    public float radius() {
        return Math.max(type.tilesWide(), type.tilesHigh()) / 2f;
    }

    @Override
    public String displayName() {
        return type.displayName();
    }

    /**
     * The structure's gun, or null. A defence with the power cut cannot fire — which is the
     * whole reason generators are worth bombing.
     */
    @Override
    public Weapon weapon() {
        return complete && powered && !sabotaged ? type.weapon() : null;
    }

    /**
     * Mirror of the sabotage state, kept here because {@link #isOperational()} has no access to
     * the current tick. The simulation refreshes it once per tick.
     */
    private boolean sabotaged;

    public boolean isSabotaged() {
        return sabotaged;
    }

    public void setSabotaged(boolean sabotaged) {
        this.sabotaged = sabotaged;
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    /** True while the owner is paying to patch this structure up. */
    public boolean isRepairing() {
        return repairing;
    }

    public void setRepairing(boolean repairing) {
        this.repairing = repairing;
        this.repairTicks = 0;
    }

    public int repairTicks() {
        return repairTicks;
    }

    public void setRepairTicks(int ticks) {
        this.repairTicks = ticks;
    }

    /** What selling this structure pays back. */
    public int refundValue() {
        // Half the sticker price, scaled by how intact it is: a burning wreck is worth less.
        return Math.max(0, (int) (type.cost() * SELL_REFUND_FRACTION * healthFraction()));
    }

    /** Fraction of the original cost returned when a structure is sold. */
    public static final float SELL_REFUND_FRACTION = 0.5f;

    /**
     * Visual damage tier: 0 intact, 1 scarred, 2 burning. The renderer swaps sprites on this.
     */
    public int damageState() {
        float health = healthFraction();
        if (health > 0.66f) {
            return 0;
        }
        return health > 0.33f ? 1 : 2;
    }

    @Override
    public boolean isBuilding() {
        return true;
    }

    @Override
    public boolean isOperational() {
        return isAlive() && complete && !sabotaged;
    }

    public boolean isComplete() {
        return complete;
    }

    public float constructionFraction() {
        return type.buildTicks() == 0 ? 1f
                : Math.min(1f, buildProgress / (float) type.buildTicks());
    }

    /**
     * Advances on-site construction. Hit points ramp up with progress so a half-built
     * structure is genuinely fragile.
     *
     * @return true on the tick construction completes
     */
    public boolean advanceConstruction(int ticks) {
        if (complete) {
            return false;
        }
        buildProgress += ticks;
        int target = Math.max(1, (int) (type.maxHp() * constructionFraction()));
        if (target > hp) {
            hp = target;
        }
        if (buildProgress >= type.buildTicks()) {
            buildProgress = type.buildTicks();
            complete = true;
            hp = type.maxHp();
            return true;
        }
        return false;
    }

    public boolean weaponReady() {
        return weaponCooldown <= 0;
    }

    public void startWeaponCooldown() {
        Weapon w = weapon();
        weaponCooldown = w == null ? 0 : w.cooldownTicks();
    }

    public void tickCooldown() {
        if (weaponCooldown > 0) {
            weaponCooldown--;
        }
    }

    public int rallyX() {
        return rallyX;
    }

    public int rallyY() {
        return rallyY;
    }

    public void setRally(int tx, int ty) {
        this.rallyX = tx;
        this.rallyY = ty;
    }
}
