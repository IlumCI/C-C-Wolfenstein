package com.ccwolf.core.entity;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.Weapon;

/**
 * Anything on the battlefield that belongs to a player, has hit points and can be shot at.
 *
 * <p>Position is in tile units with the origin at the map's top-left corner; {@code (0.5, 0.5)}
 * is the centre of tile {@code (0, 0)}.
 */
public abstract class Entity {

    private final int id;
    private int ownerId;
    protected float x;
    protected float y;
    protected int hp;
    private boolean alive = true;
    /** Tick this entity last took damage, or -1 if it never has. */
    private int lastDamagedTick = -1;
    /** Tick at which sabotage wears off; anything at or before "now" means working again. */
    private int disabledUntilTick = -1;
    private int lastAttackerId = -1;

    protected Entity(int id, int ownerId, float x, float y, int hp) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.hp = hp;
    }

    public int id() {
        return id;
    }

    public int ownerId() {
        return ownerId;
    }

    /**
     * Changes sides. Go through {@code GameWorld.transferOwnership} rather than calling this
     * directly — there is bookkeeping either side of it.
     */
    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public int tileX() {
        return (int) x;
    }

    public int tileY() {
        return (int) y;
    }

    public int hp() {
        return hp;
    }

    public boolean isAlive() {
        return alive;
    }

    public float healthFraction() {
        return maxHp() <= 0 ? 0f : Math.max(0f, Math.min(1f, hp / (float) maxHp()));
    }

    public int lastDamagedTick() {
        return lastDamagedTick;
    }

    /**
     * Whether this entity took damage within the last {@code window} ticks.
     *
     * <p>Always ask through this rather than subtracting {@link #lastDamagedTick()} yourself:
     * the "never damaged" sentinel makes naive subtraction read as "hit a moment ago".
     */
    public boolean wasDamagedWithin(int currentTick, int window) {
        return lastDamagedTick >= 0 && currentTick - lastDamagedTick <= window;
    }

    public int lastAttackerId() {
        return lastAttackerId;
    }

    /**
     * Applies already-multiplied damage.
     *
     * @return true if this hit destroyed the entity
     */
    public boolean applyDamage(int amount, int attackerId, int tick) {
        if (!alive) {
            return false;
        }
        hp -= amount;
        lastDamagedTick = tick;
        lastAttackerId = attackerId;
        if (hp <= 0) {
            hp = 0;
            alive = false;
            return true;
        }
        return false;
    }

    public void heal(int amount) {
        if (alive) {
            hp = Math.min(maxHp(), hp + amount);
        }
    }

    /** Removes the entity without attributing a kill (sold, replaced, cleaned up). */
    public void kill() {
        hp = 0;
        alive = false;
    }

    public float distanceTo(Entity other) {
        float dx = other.x - x;
        float dy = other.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float distanceTo(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public abstract int maxHp();

    public abstract ArmorClass armor();

    public abstract int sight();

    /** Radius in tiles, used for selection, separation and range checks. */
    public abstract float radius();

    public abstract String displayName();

    /** The entity's gun, or null if it is unarmed. */
    public abstract Weapon weapon();

    /**
     * Whether sabotage currently has this thing switched off. A disabled structure makes no
     * power, builds nothing and cannot fire; a disabled unit cannot move or shoot.
     */
    public boolean isDisabled(int currentTick) {
        return currentTick < disabledUntilTick;
    }

    /** Ticks of sabotage remaining, for the interface to draw a countdown. */
    public int disabledTicksLeft(int currentTick) {
        return Math.max(0, disabledUntilTick - currentTick);
    }

    /** Applies (or extends) sabotage. Never shortens an existing outage. */
    public void disableUntil(int tick) {
        if (tick > disabledUntilTick) {
            disabledUntilTick = tick;
        }
    }

    public void clearDisable() {
        disabledUntilTick = -1;
    }

    public abstract boolean isBuilding();

    /** True once the entity can act: units always, buildings only when construction finishes. */
    public boolean isOperational() {
        return alive;
    }
}
