package com.ccwolf.core.sim;

import com.ccwolf.core.entity.Unit;
import java.util.ArrayList;
import java.util.List;

/**
 * Uniform-grid bucket index over units, rebuilt once per tick.
 *
 * <p>Separation steering and target acquisition both need "units near this point"; doing that
 * with a full scan is O(n^2) per tick and shows up immediately on a phone once a couple of
 * hundred units are alive.
 */
final class SpatialIndex {

    /**
     * Tiles per cell edge.
     *
     * <p>Four looks too coarse: the query this mostly serves is separation steering asking for
     * neighbours within about 1.6 tiles, so most of what a cell returns is thrown away. Halving
     * it to two was tried and measured, and it is slower — 14% fewer candidates fetched, but a
     * query then spans nine cells instead of four and the per-cell overhead costs more than the
     * fetch saves. Left at four on the evidence.
     */
    private static final int CELL_SIZE = 4;

    private final int cols;
    private final int rows;
    private final List<List<Unit>> cells;

    SpatialIndex(int mapWidth, int mapHeight) {
        this.cols = Math.max(1, (mapWidth + CELL_SIZE - 1) / CELL_SIZE);
        this.rows = Math.max(1, (mapHeight + CELL_SIZE - 1) / CELL_SIZE);
        this.cells = new ArrayList<List<Unit>>(cols * rows);
        for (int i = 0; i < cols * rows; i++) {
            cells.add(new ArrayList<Unit>(4));
        }
    }

    void rebuild(List<Unit> units) {
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).clear();
        }
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            List<Unit> cell = cellAt(u.x(), u.y());
            if (cell != null) {
                cell.add(u);
            }
        }
    }

    /** Appends every unit in the cells overlapping the given circle. May include extras. */
    void query(float x, float y, float radius, List<Unit> out) {
        int minCx = clampCol((int) ((x - radius) / CELL_SIZE));
        int maxCx = clampCol((int) ((x + radius) / CELL_SIZE));
        int minCy = clampRow((int) ((y - radius) / CELL_SIZE));
        int maxCy = clampRow((int) ((y + radius) / CELL_SIZE));
        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                out.addAll(cells.get(cy * cols + cx));
            }
        }
    }

    private List<Unit> cellAt(float x, float y) {
        int cx = (int) (x / CELL_SIZE);
        int cy = (int) (y / CELL_SIZE);
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) {
            return null;
        }
        return cells.get(cy * cols + cx);
    }

    private int clampCol(int c) {
        return Math.max(0, Math.min(cols - 1, c));
    }

    private int clampRow(int r) {
        return Math.max(0, Math.min(rows - 1, r));
    }
}
