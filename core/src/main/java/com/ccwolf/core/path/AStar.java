package com.ccwolf.core.path;

/**
 * 8-way A* over a {@link PathGrid}, with a binary heap open list and stamp-based visited
 * marking so the scratch arrays are reused between searches instead of cleared.
 *
 * <p>Not thread safe: give each thread (or each caller) its own instance. The simulation runs
 * one search at a time, so it keeps a single instance.
 *
 * <p>Tiles are packed into a single int as {@code x | (y << 16)}; see {@link #packX} and
 * {@link #packY}.
 */
public final class AStar {

    /** Cost of a diagonal step relative to a straight one. */
    private static final float DIAGONAL = 1.41421356f;

    /** Safety valve so a hopeless search cannot stall a tick. */
    /**
     * Node budget for a full search on the map the game shipped with.
     *
     * <p>On a larger map the budget scales with area — see {@link #findPath} — which is the
     * standard big-map answer: a search allowed to touch a fixed fraction of the world costs a
     * bounded slice of the tick whatever the world's size, and a cross-map march on a
     * two-hundred-and-fifty-six map is not a pathology to truncate, it is the game working.
     * The floor keeps every sixty-four map's behaviour bit-identical to what the goldens
     * recorded.
     */
    private static final int DEFAULT_NODE_LIMIT = 6000;

    /** The fraction of the map one search may expand: a third of the tiles. */
    private static final int AREA_DIVISOR = 3;

    private int width;
    private int height;
    private float[] gScore;
    private int[] cameFrom;
    private int[] stamp;
    private int currentStamp;

    // Binary min-heap of tile indices ordered by fScore.
    private int[] heap = new int[256];
    private float[] heapKey = new float[256];
    private int heapSize;

    private int nodesExpanded;

    public static int pack(int x, int y) {
        return (x & 0xFFFF) | (y << 16);
    }

    public static int packX(int packed) {
        return packed & 0xFFFF;
    }

    public static int packY(int packed) {
        return packed >>> 16;
    }

    /** Nodes expanded by the most recent search; handy in tests and profiling. */
    public int nodesExpanded() {
        return nodesExpanded;
    }

    /**
     * Finds a walkable route from {@code (sx, sy)} to {@code (gx, gy)}.
     *
     * <p>If the goal itself is unreachable (walled in, or occupied by the structure the caller
     * is walking towards) the search still returns the best partial route it found — the tile
     * it explored that ends up closest to the goal. That keeps units approaching a target they
     * cannot literally stand on instead of refusing to move.
     *
     * @return packed tiles from the first step to the destination, excluding the start tile;
     *     null if the start is off-grid or nothing at all could be reached
     */
    public int[] findPath(PathGrid grid, int sx, int sy, int gx, int gy) {
        int scaled = grid.width() * grid.height() / AREA_DIVISOR;
        return findPath(grid, sx, sy, gx, gy, Math.max(DEFAULT_NODE_LIMIT, scaled));
    }

    public int[] findPath(PathGrid grid, int sx, int sy, int gx, int gy, int nodeLimit) {
        ensureCapacity(grid.width(), grid.height());
        nodesExpanded = 0;

        if (sx < 0 || sy < 0 || sx >= width || sy >= height) {
            return null;
        }
        if (sx == gx && sy == gy) {
            return new int[0];
        }

        currentStamp++;
        heapSize = 0;

        int start = sy * width + sx;
        int goal = clampIndex(gx, gy);

        gScore[start] = 0f;
        cameFrom[start] = -1;
        stamp[start] = currentStamp;
        heapPush(start, heuristic(sx, sy, gx, gy));

        int bestNode = start;
        float bestH = heuristic(sx, sy, gx, gy);

        while (heapSize > 0 && nodesExpanded < nodeLimit) {
            int current = heapPop();
            nodesExpanded++;

            if (current == goal) {
                return reconstruct(current, start);
            }

            int cx = current % width;
            int cy = current / width;

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    if (grid.isBlocked(nx, ny)) {
                        continue;
                    }
                    // No cutting corners diagonally past a blocked tile.
                    if (dx != 0 && dy != 0
                            && (grid.isBlocked(cx + dx, cy) || grid.isBlocked(cx, cy + dy))) {
                        continue;
                    }

                    int neighbor = ny * width + nx;
                    float step = (dx != 0 && dy != 0) ? DIAGONAL : 1f;
                    float tentative = gScore[current] + step * grid.moveCost(nx, ny);

                    if (stamp[neighbor] == currentStamp && tentative >= gScore[neighbor]) {
                        continue;
                    }
                    stamp[neighbor] = currentStamp;
                    gScore[neighbor] = tentative;
                    cameFrom[neighbor] = current;

                    float h = heuristic(nx, ny, gx, gy);
                    if (h < bestH) {
                        bestH = h;
                        bestNode = neighbor;
                    }
                    heapPush(neighbor, tentative + h);
                }
            }
        }

        if (bestNode == start) {
            return null;
        }
        return reconstruct(bestNode, start);
    }

    private int clampIndex(int gx, int gy) {
        int cx = Math.max(0, Math.min(width - 1, gx));
        int cy = Math.max(0, Math.min(height - 1, gy));
        return cy * width + cx;
    }

    private int[] reconstruct(int node, int start) {
        int length = 0;
        for (int n = node; n != start && n != -1; n = cameFrom[n]) {
            length++;
        }
        if (length == 0) {
            return new int[0];
        }
        int[] path = new int[length];
        int i = length - 1;
        for (int n = node; n != start && n != -1; n = cameFrom[n]) {
            path[i--] = pack(n % width, n / width);
        }
        return path;
    }

    /** Octile distance: exact for 8-way movement on uniform cost, so it stays admissible. */
    private static float heuristic(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx);
        int dy = Math.abs(y - gy);
        int min = Math.min(dx, dy);
        return (dx + dy - 2 * min) + DIAGONAL * min;
    }

    private void ensureCapacity(int w, int h) {
        if (w == width && h == height && gScore != null) {
            return;
        }
        width = w;
        height = h;
        int n = w * h;
        gScore = new float[n];
        cameFrom = new int[n];
        stamp = new int[n];
        currentStamp = 0;
    }

    private void heapPush(int node, float key) {
        if (heapSize == heap.length) {
            int[] nh = new int[heapSize * 2];
            float[] nk = new float[heapSize * 2];
            System.arraycopy(heap, 0, nh, 0, heapSize);
            System.arraycopy(heapKey, 0, nk, 0, heapSize);
            heap = nh;
            heapKey = nk;
        }
        int i = heapSize++;
        heap[i] = node;
        heapKey[i] = key;
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (heapKey[parent] <= heapKey[i]) {
                break;
            }
            swap(parent, i);
            i = parent;
        }
    }

    private int heapPop() {
        int top = heap[0];
        heapSize--;
        if (heapSize > 0) {
            heap[0] = heap[heapSize];
            heapKey[0] = heapKey[heapSize];
            int i = 0;
            while (true) {
                int left = 2 * i + 1;
                int right = left + 1;
                int smallest = i;
                if (left < heapSize && heapKey[left] < heapKey[smallest]) {
                    smallest = left;
                }
                if (right < heapSize && heapKey[right] < heapKey[smallest]) {
                    smallest = right;
                }
                if (smallest == i) {
                    break;
                }
                swap(i, smallest);
                i = smallest;
            }
        }
        return top;
    }

    private void swap(int a, int b) {
        int tn = heap[a];
        heap[a] = heap[b];
        heap[b] = tn;
        float tk = heapKey[a];
        heapKey[a] = heapKey[b];
        heapKey[b] = tk;
    }
}
