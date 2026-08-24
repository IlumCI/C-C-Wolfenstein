package com.ccwolf.core.order;

import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Walk an Infiltrator onto an enemy vehicle and take it.
 *
 * <p>The Infiltrator is spent doing it — they are inside the thing now — which is what keeps a
 * six-hundred-credit unit from being strictly better than building your own armour.
 */
public final class HijackOrder implements Order {

    /** How close the infiltrator must get to climb aboard. */
    private static final float REACH = 0.9f;

    private final int targetId;

    public HijackOrder(int targetId) {
        this.targetId = targetId;
    }

    public int targetId() {
        return targetId;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Entity target = world.entity(targetId);
        if (target == null || !target.isAlive() || target.isBuilding()
                || !world.areEnemies(unit.ownerId(), target.ownerId())
                // Nobody boards a machine at altitude. Without this the infiltrator chases a
                // gyrocopter across the map forever, always one reach short.
                || ((Unit) target).type().isAir()) {
            world.mover().stop(unit);
            return true;
        }

        if (unit.distanceTo(target) <= REACH + target.radius()) {
            world.mover().stop(unit);
            world.hijack(unit, (Unit) target);
            return true;
        }

        // Chase: the target is very likely driving away.
        world.mover().moveTowards(world.grid(), unit, target.tileX(), target.tileY(), dt);
        return false;
    }

    @Override
    public String describe() {
        return "Hijack #" + targetId;
    }
}
