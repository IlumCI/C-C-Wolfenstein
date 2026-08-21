package com.ccwolf.core.order;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.path.AStar;
import com.ccwolf.core.sim.GameWorld;

/**
 * The harvester loop: drive to uranium, mine until full, drive back to the nearest refinery,
 * unload, repeat.
 *
 * <p>This order is deliberately never "finished" while there is ore and a refinery, so a
 * harvester keeps working without any supervision. It ends only when the player gives the
 * harvester something else to do, or when there is genuinely nothing left to mine.
 */
public final class HarvestOrder implements Order {

    /** Uranium pulled out of a tile per mining tick. */
    private static final int MINE_RATE = 6;

    /** Ticks spent docked before the load is credited. */
    private static final int UNLOAD_TICKS = 30;

    /** How far a harvester will wander looking for a fresh seam. */
    private static final int SEARCH_RADIUS = 24;

    private enum State { SEEKING, MINING, RETURNING, UNLOADING }

    private State state = State.SEEKING;
    private int oreTileX = -1;
    private int oreTileY = -1;
    private int dockX = -1;
    private int dockY = -1;

    /** Where this harvester was last mining, so it can go back after unloading. */
    private int lastSeamX = -1;
    private int lastSeamY = -1;

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        if (unit.oreCapacity() <= 0) {
            return true;
        }

        switch (state) {
            case SEEKING:
                return seek(world, unit, dt);
            case MINING:
                return mine(world, unit);
            case RETURNING:
                return returnToBase(world, unit, dt);
            case UNLOADING:
            default:
                return unload(world, unit);
        }
    }

    private boolean seek(GameWorld world, Unit unit, float dt) {
        if (unit.isFullyLoaded()) {
            state = State.RETURNING;
            return false;
        }
        TileMap map = world.map();
        if (oreTileX < 0 || map.ore(oreTileX, oreTileY) <= 0) {
            // Prefer the seam we were working before the last trip home.
            float fromX = lastSeamX >= 0 ? lastSeamX + 0.5f : unit.x();
            float fromY = lastSeamY >= 0 ? lastSeamY + 0.5f : unit.y();
            int packed = world.findOreTile(fromX, fromY, SEARCH_RADIUS);
            if (packed < 0 && (lastSeamX >= 0)) {
                packed = world.findOreTile(unit.x(), unit.y(), SEARCH_RADIUS);
            }
            if (packed < 0) {
                // Nothing left to mine. Deliver whatever we have, then stand down.
                if (unit.oreCarried() > 0) {
                    state = State.RETURNING;
                    return false;
                }
                world.mover().stop(unit);
                return true;
            }
            oreTileX = AStar.packX(packed);
            oreTileY = AStar.packY(packed);
        }

        boolean arrived = world.mover().moveTowards(world.grid(), unit, oreTileX, oreTileY, dt);
        if (arrived) {
            if (map.ore(unit.tileX(), unit.tileY()) > 0) {
                oreTileX = unit.tileX();
                oreTileY = unit.tileY();
                state = State.MINING;
            } else {
                oreTileX = -1;
            }
        }
        return false;
    }

    private boolean mine(GameWorld world, Unit unit) {
        int taken = world.map().takeOre(oreTileX, oreTileY, MINE_RATE);
        if (taken > 0) {
            unit.addOre(taken);
            lastSeamX = oreTileX;
            lastSeamY = oreTileY;
        }
        if (unit.isFullyLoaded()) {
            state = State.RETURNING;
            return false;
        }
        if (taken == 0) {
            oreTileX = -1;
            state = State.SEEKING;
        }
        return false;
    }

    private boolean returnToBase(GameWorld world, Unit unit, float dt) {
        Building refinery = world.findNearestRefinery(unit.ownerId(), unit.x(), unit.y());
        if (refinery == null) {
            // Refinery gone: sit on the load until one is rebuilt rather than dumping it.
            world.mover().stop(unit);
            return false;
        }
        dockX = refinery.tileX() + refinery.tilesWide() / 2;
        dockY = refinery.tileY() + refinery.tilesHigh();

        boolean arrived = world.mover().moveTowards(world.grid(), unit, dockX, dockY, dt);
        if (arrived || unit.distanceTo(refinery) <= refinery.radius() + 1.2f) {
            world.mover().stop(unit);
            unit.setHarvestTicks(0);
            state = State.UNLOADING;
        }
        return false;
    }

    private boolean unload(GameWorld world, Unit unit) {
        unit.setHarvestTicks(unit.harvestTicks() + 1);
        if (unit.harvestTicks() < UNLOAD_TICKS) {
            return false;
        }
        world.deliverOre(unit);
        unit.setHarvestTicks(0);
        oreTileX = -1;
        state = State.SEEKING;
        return false;
    }

    /** Exposed for the HUD and for tests: what the harvester is currently doing. */
    public String stateName() {
        return state.name();
    }

    @Override
    public String describe() {
        return "Harvest (" + state.name().toLowerCase(java.util.Locale.ROOT) + ")";
    }
}
