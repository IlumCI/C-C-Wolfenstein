package com.ccwolf.core.order;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Chase a specific entity and shoot it until it dies.
 *
 * <p>Unarmed units (harvesters) treat this as a no-op rather than suicidally driving at the
 * target.
 */
public final class AttackOrder implements Order {

    private final int targetId;
    private final ChaseTile chase = new ChaseTile();

    /** Reused so that backing off allocates nothing. */
    private final int[] standOff = new int[2];

    public AttackOrder(int targetId) {
        this.targetId = targetId;
    }

    public int targetId() {
        return targetId;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Weapon weapon = unit.weapon();
        if (weapon == null) {
            return true;
        }
        Entity target = world.entity(targetId);
        if (target == null || !target.isAlive()) {
            world.mover().stop(unit);
            return true;
        }

        if (world.inWeaponRange(unit, target)) {
            world.mover().stop(unit);
            unit.faceToward(target.x(), target.y());
            world.tryAttack(unit, target);
            return false;
        }

        // Out of range means walk closer for every weapon in the game except one kind. A gun
        // with a dead zone that closed on something already inside it would walk further in
        // forever, never firing, which is how a drawback becomes a bug.
        if (weapon.hasMinRange()
                && unit.distanceTo(target) - target.radius() < weapon.minRange()) {
            if (world.standOffTile(unit, target.x(), target.y(), weapon.minRange(), standOff)) {
                world.mover().moveTowards(world.grid(), unit, standOff[0], standOff[1], dt);
            } else {
                world.mover().stop(unit);
            }
            return false;
        }

        // Walk towards the tile the target is on, but do not re-aim every time it crosses a
        // boundary: the range check above is what stops us, not arrival.
        chase.follow(target);
        world.mover().moveTowards(world.grid(), unit, chase.tileX(), chase.tileY(), dt);
        return false;
    }

    @Override
    public String describe() {
        return "Attack #" + targetId;
    }
}
