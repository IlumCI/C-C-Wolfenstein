package com.ccwolf.core.order;

import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/** Walk to a tile and stop. Completes on arrival, or when the route runs out. */
public final class MoveOrder implements Order {

    private final int tileX;
    private final int tileY;

    public MoveOrder(int tileX, int tileY) {
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
        return world.mover().moveTowards(world.grid(), unit, tileX, tileY, dt);
    }

    @Override
    public String describe() {
        return "Move to " + tileX + "," + tileY;
    }
}
