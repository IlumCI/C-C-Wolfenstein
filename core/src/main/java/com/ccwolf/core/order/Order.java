package com.ccwolf.core.order;

import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * A standing instruction for one unit. Orders are ticked by the simulation and removed from
 * the unit's queue when they report completion, so a unit with an empty queue is idle.
 */
public interface Order {

    /**
     * Advances the order by one simulation step.
     *
     * @param dt seconds of simulated time
     * @return true when the order is finished and should be dropped
     */
    boolean update(GameWorld world, Unit unit, float dt);

    /** Short label for the HUD and for debugging. */
    String describe();
}
