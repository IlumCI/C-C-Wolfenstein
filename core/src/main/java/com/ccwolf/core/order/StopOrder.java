package com.ccwolf.core.order;

import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/** Halt where you are. Completes immediately, which leaves the unit idle and on guard. */
public final class StopOrder implements Order {

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        world.mover().stop(unit);
        return true;
    }

    @Override
    public String describe() {
        return "Stop";
    }
}
