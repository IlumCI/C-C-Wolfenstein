package com.ccwolf.core.order;

import com.ccwolf.core.entity.Entity;

/**
 * The tile a chasing unit walks towards, held still until the quarry has really moved.
 *
 * <p>Chasing used to pass the target's current tile straight to the mover, and a path is keyed
 * to an exact destination tile — so every time the quarry crossed a tile boundary, its pursuer
 * threw its route away and ran a fresh search. Two units running across a map generated a full
 * pathfind every few ticks each, for a destination that had moved by one tile.
 *
 * <p>Letting the aim point lag by a few tiles costs nothing: the pursuer is walking in the right
 * direction either way, and the weapon range check is what actually stops it, not arrival.
 */
final class ChaseTile {

    /**
     * How far the quarry may drift before the aim point is moved, in tiles.
     *
     * <p>Three is comfortably inside every weapon's range, so a unit still closes properly, and
     * it is wide enough that a target jinking along a wall does not drag its pursuer through a
     * search on every step.
     */
    private static final int SLACK = 3;

    private int tileX = Integer.MIN_VALUE;
    private int tileY = Integer.MIN_VALUE;

    /** Updates the aim point only if the target has left the slack around it. */
    void follow(Entity target) {
        int targetX = target.tileX();
        int targetY = target.tileY();
        if (tileX == Integer.MIN_VALUE
                // Chebyshev, because movement is eight-way: a diagonal step is one step.
                || Math.max(Math.abs(targetX - tileX), Math.abs(targetY - tileY)) > SLACK) {
            tileX = targetX;
            tileY = targetY;
        }
    }

    /** Forgets the aim point, so the next follow snaps to wherever the target is. */
    void reset() {
        tileX = Integer.MIN_VALUE;
        tileY = Integer.MIN_VALUE;
    }

    int tileX() {
        return tileX;
    }

    int tileY() {
        return tileY;
    }
}
