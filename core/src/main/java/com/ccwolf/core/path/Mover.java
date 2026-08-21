package com.ccwolf.core.path;

import com.ccwolf.core.diag.TickProfiler;
import com.ccwolf.core.entity.Unit;

/**
 * Drives a unit along an A* path: requests a route when one is missing, walks the waypoints,
 * and repaths when the world changes under it (a structure goes up across the route, or the
 * unit stops making progress because of a crowd).
 */
public final class Mover {

    /** How close to a waypoint's centre counts as reaching it. */
    private static final float WAYPOINT_EPSILON = 0.08f;

    /** Ticks of no progress before we assume we are stuck and ask for a new route. */
    private static final int STUCK_TICKS = 14;

    /** Minimum ticks between repaths for one unit, so a jam cannot melt the CPU. */
    private static final int REPATH_COOLDOWN = 10;

    private final AStar aStar = new AStar();

    /**
     * Where searches get counted.
     *
     * <p>The count matters more than the clock: how many full searches a tick runs is a
     * function of the seed alone, so it is a number a test can hold to a bound, where a
     * millisecond reading is not.
     */
    private TickProfiler profiler;

    public AStar pathfinder() {
        return aStar;
    }

    public void setProfiler(TickProfiler profiler) {
        this.profiler = profiler;
    }

    /**
     * Advances {@code unit} towards a destination tile for one step.
     *
     * @param dt seconds of simulated time this step
     * @return true once the unit has arrived, or has established that it cannot get any closer
     */
    public boolean moveTowards(PathGrid grid, Unit unit, int destX, int destY, float dt) {
        if (unit.tileX() == destX && unit.tileY() == destY && unit.pathComplete()) {
            unit.setVelocity(0f, 0f);
            return true;
        }

        if (!unit.hasPathTo(destX, destY)) {
            if (unit.repathCooldown() > 0) {
                unit.setVelocity(0f, 0f);
                return false;
            }
            unit.startRepathCooldown(REPATH_COOLDOWN);
            int[] path = aStar.findPath(grid, unit.tileX(), unit.tileY(), destX, destY);
            if (profiler != null) {
                profiler.countAstarSearch(aStar.nodesExpanded());
            }
            if (path == null || path.length == 0) {
                // Nowhere to go, or already standing on the best tile available.
                unit.clearPath();
                unit.setVelocity(0f, 0f);
                return true;
            }
            unit.setPath(path, destX, destY);
        }

        int[] path = unit.path();
        if (unit.pathComplete()) {
            unit.clearPath();
            unit.setVelocity(0f, 0f);
            return true;
        }

        int packed = path[unit.pathIndex()];
        int wx = AStar.packX(packed);
        int wy = AStar.packY(packed);

        if (grid.isBlocked(wx, wy)) {
            // Something was built across the route; drop the path and try again next tick.
            unit.clearPath();
            return false;
        }

        float targetX = wx + 0.5f;
        float targetY = wy + 0.5f;
        float dx = targetX - unit.x();
        float dy = targetY - unit.y();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist <= WAYPOINT_EPSILON) {
            unit.advancePath();
            unit.clearBlocked();
            unit.setLastWaypointDistance(Float.MAX_VALUE);
            if (unit.pathComplete()) {
                unit.clearPath();
                unit.setVelocity(0f, 0f);
                return true;
            }
            return false;
        }

        float terrainCost = Math.max(0.1f, grid.moveCost(wx, wy));
        float speed = unit.type().speed() / terrainCost;
        float step = speed * dt;

        if (step >= dist) {
            unit.setPosition(targetX, targetY);
            unit.setVelocity(dx / dt, dy / dt);
            unit.advancePath();
            unit.clearBlocked();
            unit.setLastWaypointDistance(Float.MAX_VALUE);
            if (unit.pathComplete()) {
                unit.clearPath();
                unit.setVelocity(0f, 0f);
                return true;
            }
        } else {
            float vx = dx / dist * speed;
            float vy = dy / dist * speed;
            unit.setPosition(unit.x() + vx * dt, unit.y() + vy * dt);
            unit.setVelocity(vx, vy);
            unit.faceToward(targetX, targetY);

            // Separation from neighbours can shove a unit sideways or backwards. If the gap to
            // the waypoint stops shrinking we are wedged in a crowd, so throw the route away and
            // let the next tick path around whatever is in the way.
            if (dist >= unit.lastWaypointDistance() - step * 0.25f) {
                unit.noteBlocked();
                if (unit.blockedTicks() > STUCK_TICKS) {
                    unit.clearPath();
                    if (profiler != null) {
                        profiler.countStuckRepath();
                    }
                }
            } else {
                unit.clearBlocked();
            }
            unit.setLastWaypointDistance(dist);
        }
        return false;
    }

    /** Abandons the current route; the next call will path afresh. */
    public void stop(Unit unit) {
        unit.clearPath();
        unit.setVelocity(0f, 0f);
    }
}
