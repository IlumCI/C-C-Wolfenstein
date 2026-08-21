package com.ccwolf.core.order;

import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Walk a Saboteur onto an enemy structure or heavy walker and switch it off.
 *
 * <p>The saboteur survives — this is a charge slapped on a wall, not a suicide run — but the
 * charge has to be placed by hand, which means crossing whatever is between here and there.
 */
public final class SabotageOrder implements Order {

    /** How close the saboteur must get, measured to the target's edge. */
    private static final float REACH = 1.1f;

    private final int targetId;

    public SabotageOrder(int targetId) {
        this.targetId = targetId;
    }

    public int targetId() {
        return targetId;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Entity target = world.entity(targetId);
        if (target == null || !target.isAlive()
                || !world.areEnemies(unit.ownerId(), target.ownerId())) {
            world.mover().stop(unit);
            return true;
        }

        if (unit.distanceTo(target) - target.radius() <= REACH) {
            world.mover().stop(unit);
            world.sabotage(unit, target);
            return true;
        }

        world.mover().moveTowards(world.grid(), unit, target.tileX(), target.tileY(), dt);
        return false;
    }

    @Override
    public String describe() {
        return "Sabotage #" + targetId;
    }
}
