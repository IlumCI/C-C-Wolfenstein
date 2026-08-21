package com.ccwolf.core.fog;

/**
 * Per-player fog of war. Three states: never seen, seen before (terrain remembered, units
 * hidden), and currently in someone's sight radius.
 *
 * <p>The visible layer is rebuilt from scratch on each refresh; the explored layer is sticky.
 */
public final class FogGrid {

    public static final byte UNEXPLORED = 0;
    public static final byte EXPLORED = 1;
    public static final byte VISIBLE = 2;

    private final int width;
    private final int height;
    private final byte[] state;

    public FogGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.state = new byte[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public byte state(int x, int y) {
        return inBounds(x, y) ? state[y * width + x] : UNEXPLORED;
    }

    public boolean isVisible(int x, int y) {
        return state(x, y) == VISIBLE;
    }

    public boolean isExplored(int x, int y) {
        return state(x, y) != UNEXPLORED;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Demotes everything currently visible to explored, ready for a fresh reveal pass. */
    public void beginRefresh() {
        for (int i = 0; i < state.length; i++) {
            if (state[i] == VISIBLE) {
                state[i] = EXPLORED;
            }
        }
    }

    /** Marks a filled circle of tiles visible. */
    public void reveal(float cx, float cy, float radius) {
        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minY = (int) Math.floor(cy - radius);
        int maxY = (int) Math.ceil(cy + radius);
        float r2 = radius * radius;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!inBounds(x, y)) {
                    continue;
                }
                float dx = x + 0.5f - cx;
                float dy = y + 0.5f - cy;
                if (dx * dx + dy * dy <= r2) {
                    state[y * width + x] = VISIBLE;
                }
            }
        }
    }

    /** Reveals the whole map — used by the headless harness and by "no fog" skirmishes. */
    public void revealAll() {
        java.util.Arrays.fill(state, VISIBLE);
    }
}
