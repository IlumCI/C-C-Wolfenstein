package com.ccwolf.core.path;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.map.TileMap;

/**
 * Static blocking: impassable terrain plus the footprints of standing structures.
 *
 * <p>Units are deliberately <em>not</em> in here. Making units block tiles turns every traffic
 * jam into a pathfinding failure; instead units path through each other and are pushed apart by
 * separation steering in the simulation step. Structures block, so bases remain real obstacles.
 */
public final class OccupancyGrid implements PathGrid {

    private final TileMap map;
    private final int[] structureId;

    public OccupancyGrid(TileMap map) {
        this.map = map;
        this.structureId = new int[map.width() * map.height()];
        java.util.Arrays.fill(structureId, -1);
    }

    @Override
    public int width() {
        return map.width();
    }

    @Override
    public int height() {
        return map.height();
    }

    @Override
    public boolean isBlocked(int x, int y) {
        if (!map.inBounds(x, y)) {
            return true;
        }
        return !map.isPassable(x, y) || structureId[y * map.width() + x] >= 0;
    }

    @Override
    public float moveCost(int x, int y) {
        return map.moveCost(x, y);
    }

    /** Id of the structure occupying a tile, or -1. */
    public int structureAt(int x, int y) {
        return map.inBounds(x, y) ? structureId[y * map.width() + x] : -1;
    }

    public void addBuilding(Building b) {
        stamp(b, b.id());
    }

    public void removeBuilding(Building b) {
        stamp(b, -1);
    }

    private void stamp(Building b, int value) {
        for (int ty = b.tileY(); ty < b.tileY() + b.tilesHigh(); ty++) {
            for (int tx = b.tileX(); tx < b.tileX() + b.tilesWide(); tx++) {
                if (map.inBounds(tx, ty)) {
                    structureId[ty * map.width() + tx] = value;
                }
            }
        }
    }

    /** True if a structure of this type would fit at this top-left corner. */
    public boolean canPlace(BuildingType type, int tileX, int tileY) {
        for (int ty = tileY; ty < tileY + type.tilesHigh(); ty++) {
            for (int tx = tileX; tx < tileX + type.tilesWide(); tx++) {
                if (!map.inBounds(tx, ty) || !map.isPassable(tx, ty)
                        || structureAt(tx, ty) >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Nearest unblocked tile to {@code (x, y)} within {@code maxRadius} rings, or -1 packed if
     * none. Used to nudge spawn points and move orders out of walls.
     */
    public int nearestFreeTile(int x, int y, int maxRadius) {
        if (!isBlocked(x, y)) {
            return AStar.pack(x, y);
        }
        for (int r = 1; r <= maxRadius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (!isBlocked(nx, ny)) {
                        return AStar.pack(nx, ny);
                    }
                }
            }
        }
        return -1;
    }
}
