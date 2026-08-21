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

    /** How close to a formation slot counts as standing in it. */
    private static final float ARRIVAL_EPSILON = 0.12f;

    /** Within this distance of a slot, a member slows down rather than overshooting it. */
    private static final float SLOT_EASE_DISTANCE = 0.6f;

    /**
     * Ticks of no progress before a unit is treated as wedged.
     *
     * <p>Judging this over a fixed window instead was tried and is worse. It lowers the bar
     * from fourteen consecutive bad ticks to one bad window, and the two are not equivalent
     * across unit speeds: a fast unit reaches its next waypoint before a window can complete,
     * while a slow one sits through two or three of them per waypoint and keeps declaring
     * itself stuck. In a balance sweep that churned the heavier, slower side's routes badly
     * enough to take it from four wins in ten to none.
     */
    private static final int STUCK_TICKS = 14;

    /** Minimum ticks between repaths for one unit, so a jam cannot melt the CPU. */
    private static final int REPATH_COOLDOWN = 10;

    /** How far ahead on the existing route a detour tries to rejoin it, in waypoints. */
    private static final int DETOUR_LOOKAHEAD = 8;

    /**
     * Node budget for a detour.
     *
     * <p>A fifteenth of a full search. A detour is meant to get round whatever is immediately
     * in the way, so if it cannot be found close by it is not a detour and the unit should have
     * a proper route instead.
     */
    private static final int DETOUR_NODE_LIMIT = 400;

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
            // Something was built across the route. Try to step round it first: a wall going up
            // mid-journey is the textbook case for a detour, and discarding the whole route
            // means a full cross-map search to get past one building.
            if (!tryLocalDetour(grid, unit)) {
                unit.clearPath();
                if (profiler != null) {
                    profiler.countStuckRepath();
                }
            }
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

            // Separation from neighbours can shove a unit sideways. If the gap to the waypoint
            // stops shrinking for long enough, we are wedged - try to step round whatever it is
            // before giving up a route that is otherwise still good.
            if (dist >= unit.lastWaypointDistance() - step * 0.25f) {
                unit.noteBlocked();
                if (unit.blockedTicks() > STUCK_TICKS && !tryLocalDetour(grid, unit)) {
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

    /**
     * Tries to get round an obstruction without throwing the whole route away.
     *
     * <p>A wedged unit used to drop its path, which meant a full search across the map on the
     * next tick — six thousand nodes to solve a problem that is usually one building wide. This
     * asks a much smaller question instead: can we reach a point a little further along the
     * route we already have? If so, that stretch is replaced and the rest of the journey stands.
     *
     * @return true if a detour was found and spliced in
     */
    private boolean tryLocalDetour(PathGrid grid, Unit unit) {
        int[] path = unit.path();
        if (path == null || unit.repathCooldown() > 0) {
            return false;
        }
        int rejoinIndex = Math.min(unit.pathIndex() + DETOUR_LOOKAHEAD, path.length - 1);
        if (rejoinIndex <= unit.pathIndex()) {
            // Nearly there anyway; a detour cannot help.
            return false;
        }

        int rejoinX = AStar.packX(path[rejoinIndex]);
        int rejoinY = AStar.packY(path[rejoinIndex]);

        int[] detour = aStar.findPath(grid, unit.tileX(), unit.tileY(), rejoinX, rejoinY,
                DETOUR_NODE_LIMIT);
        if (profiler != null) {
            profiler.countDetourSearch(aStar.nodesExpanded());
        }
        if (detour == null || detour.length == 0) {
            return false;
        }
        // A* returns its best partial attempt when it cannot reach the goal. A detour that does
        // not actually rejoin the route is not a detour.
        int last = detour[detour.length - 1];
        if (AStar.packX(last) != rejoinX || AStar.packY(last) != rejoinY) {
            return false;
        }

        int tail = path.length - rejoinIndex - 1;
        int[] spliced = new int[detour.length + tail];
        System.arraycopy(detour, 0, spliced, 0, detour.length);
        if (tail > 0) {
            System.arraycopy(path, rejoinIndex + 1, spliced, detour.length, tail);
        }
        unit.setPath(spliced, unit.pathDestX(), unit.pathDestY());
        // Only a detour that worked costs a cooldown. Charging for a failed one left the unit
        // standing still for half a second before it could search properly, which is worse than
        // never having tried - and cost the slower, heavier side enough matches to show up in a
        // balance sweep as an eleven-to-one rout.
        unit.startRepathCooldown(REPATH_COOLDOWN);
        return true;
    }

    /**
     * Walks a unit straight at a point, with no pathfinding at all.
     *
     * <p>What squad members move by. Their slot is a few tiles from an anchor that is itself
     * already following a real route, so there is nothing to solve: steering at it is both
     * correct and free. This is what makes a squad affordable — one search for eight men
     * instead of eight.
     *
     * <p>It will walk into a wall rather than round one, which is the caller's problem to
     * notice. A member that falls far enough behind its slot is expected to ask for a proper
     * route instead.
     *
     * @return true once the unit is on the point
     */
    public boolean steerTowards(PathGrid grid, Unit unit, float targetX, float targetY,
            float dt) {
        float dx = targetX - unit.x();
        float dy = targetY - unit.y();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= ARRIVAL_EPSILON) {
            unit.setVelocity(0f, 0f);
            return true;
        }

        float terrainCost = Math.max(0.1f, grid.moveCost(unit.tileX(), unit.tileY()));
        float speed = unit.type().speed() / terrainCost;
        // Ease off close in, so a member settling into its slot does not jitter across it.
        if (dist < SLOT_EASE_DISTANCE) {
            speed *= Math.max(0.25f, dist / SLOT_EASE_DISTANCE);
        }
        float step = speed * dt;

        if (step >= dist) {
            moveIfClear(grid, unit, targetX, targetY);
            unit.setVelocity(0f, 0f);
            return true;
        }

        float vx = dx / dist * speed;
        float vy = dy / dist * speed;
        moveIfClear(grid, unit, unit.x() + vx * dt, unit.y() + vy * dt);
        unit.setVelocity(vx, vy);
        unit.faceToward(targetX, targetY);
        return false;
    }

    /** Moves onto a point, sliding along whichever axis is clear if the target tile is not. */
    private void moveIfClear(PathGrid grid, Unit unit, float x, float y) {
        if (!grid.isBlocked((int) x, (int) y)) {
            unit.setPosition(x, y);
        } else if (!grid.isBlocked((int) x, unit.tileY())) {
            unit.setPosition(x, unit.y());
        } else if (!grid.isBlocked(unit.tileX(), (int) y)) {
            unit.setPosition(unit.x(), y);
        }
    }

    /** Abandons the current route; the next call will path afresh. */
    public void stop(Unit unit) {
        unit.clearPath();
        unit.setVelocity(0f, 0f);
    }
}
