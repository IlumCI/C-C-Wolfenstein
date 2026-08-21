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

        // Walk to the tile the target is standing on; range checks stop us short of it.
        world.mover().moveTowards(world.grid(), unit, target.tileX(), target.tileY(), dt);
        return false;
    }

    @Override
    public String describe() {
        return "Attack #" + targetId;
    }
}
