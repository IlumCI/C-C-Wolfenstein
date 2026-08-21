package com.ccwolf.core.order;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Run for home, and do not stop to be told otherwise.
 *
 * <p>What a squad does when its nerve goes. The important part is that it cannot be countermanded:
 * a player whose line has broken does not get to simply order it to stand, any more than a real
 * one would. That is what makes morale a cost rather than a status effect — losing a position is
 * losing it, and getting the men back into the line takes time you do not control.
 *
 * <p>Routing men still get shot at, which is why breaking a formation is worth more than killing
 * the same number of men would be.
 */
public final class RoutOrder implements Order {

    /** Close enough to home to stop running. */
    private static final float SAFE_DISTANCE = 3f;

    private final int rallyTileX;
    private final int rallyTileY;
    private final int recoverAtTick;

    public RoutOrder(int rallyTileX, int rallyTileY, int recoverAtTick) {
        this.rallyTileX = rallyTileX;
        this.rallyTileY = rallyTileY;
        this.recoverAtTick = recoverAtTick;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        // Men who are running are not shooting, and are not choosing where to go.
        if (world.tick() >= recoverAtTick
                && unit.distanceTo(rallyTileX + 0.5f, rallyTileY + 0.5f) <= SAFE_DISTANCE) {
            world.mover().stop(unit);
            return true;
        }
        world.mover().moveTowards(world.grid(), unit, rallyTileX, rallyTileY, dt);
        return false;
    }

    /** Somewhere to run to: the owner's command post, or failing that any structure. */
    public static RoutOrder towardsHome(GameWorld world, Unit unit, int recoverAtTick) {
        Building home = world.findAnyBuilding(unit.ownerId());
        int tileX = home != null ? home.tileX() : unit.tileX();
        int tileY = home != null ? home.tileY() : unit.tileY();
        return new RoutOrder(tileX, tileY, recoverAtTick);
    }

    @Override
    public String describe() {
        return "Broken - falling back";
    }
}
