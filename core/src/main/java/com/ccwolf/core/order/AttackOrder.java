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
