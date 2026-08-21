package com.ccwolf.core.order;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Advance on a tile, engaging anything hostile encountered on the way. This is what the AI
 * sends attack waves out with, and what a player gets when ordering a move onto enemy ground.
 */
public final class AttackMoveOrder implements Order {

    /** How often the unit sweeps for targets, in ticks. */
    private static final int SCAN_INTERVAL = 6;

    private final int tileX;
    private final int tileY;
    private int engagedTargetId = -1;
    private final ChaseTile chase = new ChaseTile();

    public AttackMoveOrder(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public int tileX() {
        return tileX;
    }

    public int tileY() {
        return tileY;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Weapon weapon = unit.weapon();
        if (weapon == null) {
            return world.mover().moveTowards(world.grid(), unit, tileX, tileY, dt);
        }

        Entity target = engagedTargetId >= 0 ? world.entity(engagedTargetId) : null;
        if (target != null && (!target.isAlive()
                || unit.distanceTo(target) > weapon.range() + LEASH)) {
            target = null;
            engagedTargetId = -1;
            chase.reset();
        }

        if (target == null && (world.tick() + unit.id()) % SCAN_INTERVAL == 0) {
            target = world.findNearestEnemy(unit.ownerId(), unit.x(), unit.y(),
                    unit.sight(), true);
            if (target == null || target.id() != engagedTargetId) {
                chase.reset();
            }
            engagedTargetId = target == null ? -1 : target.id();
        }

        if (target != null) {
            if (world.inWeaponRange(unit, target)) {
                world.mover().stop(unit);
                unit.faceToward(target.x(), target.y());
                world.tryAttack(unit, target);
            } else {
                chase.follow(target);
                world.mover().moveTowards(world.grid(), unit, chase.tileX(), chase.tileY(), dt);
            }
            return false;
        }

        return world.mover().moveTowards(world.grid(), unit, tileX, tileY, dt);
    }

    /** How far past its range a unit will follow a target before giving up on it. */
    private static final float LEASH = 4f;

    @Override
    public String describe() {
        return "Attack-move to " + tileX + "," + tileY;
    }
}
