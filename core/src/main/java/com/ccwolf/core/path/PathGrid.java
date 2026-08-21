package com.ccwolf.core.path;

/** The static blocking view the pathfinder walks: terrain plus standing structures. */
public interface PathGrid {

    int width();

    int height();

    /** True if no ground unit can enter this tile. Out-of-bounds counts as blocked. */
    boolean isBlocked(int x, int y);

    /** Relative traversal cost, 1.0 being open ground. Only called for unblocked tiles. */
    float moveCost(int x, int y);
}
