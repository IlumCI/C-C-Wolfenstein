package com.ccwolf.core.entity;

import com.ccwolf.core.combat.ArmorClass;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.order.Order;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A mobile unit. Holds its stats, its order queue and the scratch state the movement and
 * combat systems need (current path, weapon cooldown, cargo).
 */
public final class Unit extends Entity {

    private final UnitType type;
    private final Deque<Order> orders = new ArrayDeque<Order>();

    /** Facing in radians, 0 = east, growing clockwise (screen coordinates). */
    private float facing;

    /**
     * Position at the start of the current tick.
     *
     * <p>Presentation state, kept here because only the simulation knows when a tick begins:
     * the renderer draws somewhere between this and the live position, which is what stops a
     * 20 Hz simulation from looking like 20 fps on a 60 Hz screen.
     */
    private float previousX;
    private float previousY;
    private int weaponCooldown;

    // --- movement scratch, owned by com.ccwolf.core.path.Mover -----------------------------
    private int[] path;
    private int pathIndex;
    private int pathDestX = -1;
    private int pathDestY = -1;
    private int repathCooldown;
    private int blockedTicks;
    private float lastWaypointDistance = Float.MAX_VALUE;
    private float velocityX;
    private float velocityY;

    // --- harvester scratch ----------------------------------------------------------------
    private int oreCarried;
    private int harvestTicks;

    public Unit(int id, int ownerId, UnitType type, float x, float y) {
        super(id, ownerId, x, y, type.maxHp());
        this.type = type;
        this.previousX = x;
        this.previousY = y;
    }

    public UnitType type() {
        return type;
    }

    @Override
    public int maxHp() {
        return type.maxHp();
    }

    @Override
    public ArmorClass armor() {
        return type.armor();
    }

    @Override
    public int sight() {
        return type.sight();
    }

    @Override
    public float radius() {
        return type.radius();
    }

    @Override
    public String displayName() {
        return type.displayName();
    }

    @Override
    public Weapon weapon() {
        return type.weapon();
    }

    @Override
    public boolean isBuilding() {
        return false;
    }

    public void setPosition(float nx, float ny) {
        this.x = nx;
        this.y = ny;
    }

    public float previousX() {
        return previousX;
    }

    public float previousY() {
        return previousY;
    }

    /** Called by the simulation at the top of each tick, before anything moves. */
    public void snapshotPosition() {
        this.previousX = x;
        this.previousY = y;
    }

    /** Position to draw at, blended between the last tick and this one. */
    public float renderX(float alpha) {
        return previousX + (x - previousX) * alpha;
    }

    public float renderY(float alpha) {
        return previousY + (y - previousY) * alpha;
    }

    public float facing() {
        return facing;
    }

    public void setFacing(float radians) {
        this.facing = radians;
    }

    public void faceToward(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        if (dx * dx + dy * dy > 1e-6f) {
            facing = (float) Math.atan2(dy, dx);
        }
    }

    // --- orders ---------------------------------------------------------------------------

    /** Clears the queue and starts this order now. */
    public void setOrder(Order order) {
        orders.clear();
        clearPath();
        if (order != null) {
            orders.add(order);
        }
    }

    /** Adds an order to the back of the queue (shift-click style chaining). */
    public void queueOrder(Order order) {
        if (order != null) {
            orders.add(order);
        }
    }

    public Order currentOrder() {
        return orders.peek();
    }

    public void finishCurrentOrder() {
        orders.poll();
        clearPath();
    }

    public void clearOrders() {
        orders.clear();
        clearPath();
    }

    public int orderCount() {
        return orders.size();
    }

    public boolean isIdle() {
        return orders.isEmpty();
    }

    // --- weapon ---------------------------------------------------------------------------

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
        if (repathCooldown > 0) {
            repathCooldown--;
        }
    }

    public int weaponCooldown() {
        return weaponCooldown;
    }

    // --- movement scratch -----------------------------------------------------------------

    public int[] path() {
        return path;
    }

    public void setPath(int[] packedTiles, int destX, int destY) {
        this.path = packedTiles;
        this.pathIndex = 0;
        this.pathDestX = destX;
        this.pathDestY = destY;
        this.blockedTicks = 0;
        this.lastWaypointDistance = Float.MAX_VALUE;
    }

    public void clearPath() {
        this.path = null;
        this.pathIndex = 0;
        this.pathDestX = -1;
        this.pathDestY = -1;
        this.blockedTicks = 0;
        this.lastWaypointDistance = Float.MAX_VALUE;
        this.velocityX = 0f;
        this.velocityY = 0f;
    }

    public boolean hasPathTo(int destX, int destY) {
        return path != null && pathDestX == destX && pathDestY == destY && pathIndex < path.length;
    }

    public int pathIndex() {
        return pathIndex;
    }

    public void advancePath() {
        pathIndex++;
    }

    public boolean pathComplete() {
        return path == null || pathIndex >= path.length;
    }

    public int repathCooldown() {
        return repathCooldown;
    }

    public void startRepathCooldown(int ticks) {
        this.repathCooldown = ticks;
    }

    public int blockedTicks() {
        return blockedTicks;
    }

    public void noteBlocked() {
        blockedTicks++;
    }

    public void clearBlocked() {
        blockedTicks = 0;
    }

    /** Distance to the current waypoint last tick; used to notice a unit making no headway. */
    public float lastWaypointDistance() {
        return lastWaypointDistance;
    }

    public void setLastWaypointDistance(float d) {
        this.lastWaypointDistance = d;
    }

    public float velocityX() {
        return velocityX;
    }

    public float velocityY() {
        return velocityY;
    }

    public void setVelocity(float vx, float vy) {
        this.velocityX = vx;
        this.velocityY = vy;
    }

    public boolean isMoving() {
        return velocityX != 0f || velocityY != 0f;
    }

    // --- harvesting -----------------------------------------------------------------------

    public int oreCarried() {
        return oreCarried;
    }

    public int oreCapacity() {
        return type.oreCapacity();
    }

    public boolean isFullyLoaded() {
        return oreCarried >= type.oreCapacity();
    }

    public int addOre(int amount) {
        int room = type.oreCapacity() - oreCarried;
        int taken = Math.min(room, amount);
        oreCarried += taken;
        return taken;
    }

    public int unloadOre() {
        int held = oreCarried;
        oreCarried = 0;
        return held;
    }

    public int harvestTicks() {
        return harvestTicks;
    }

    public void setHarvestTicks(int ticks) {
        this.harvestTicks = ticks;
    }
}
