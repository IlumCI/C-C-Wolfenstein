package com.ccwolf.game.render;

/**
 * Where a frame goes.
 *
 * <p>The counterpart to the simulation's tick profiler, and needed for the same reason: the
 * renderer turned out to cost six times what simulating a thousand units does, and "the
 * renderer is slow" is not something you can act on. This says which pass.
 *
 * <p>Off unless switched on. The cost when off is one predictable branch per pass.
 */
public final class RenderProfiler {

    public enum Pass {
        TERRAIN,
        DECALS,
        GROUND_FX,
        ENTITIES,
        OVERLAY_FX,
        AIR_FX,
        GHOST,
        FOG,
        SELECTION,
        HUD
    }

    private static final Pass[] PASSES = Pass.values();

    private final long[] nanos = new long[PASSES.length];
    private final long[] draws = new long[PASSES.length];

    private boolean enabled;
    private int frames;
    private long passStarted;
    private Pass openPass;

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void beginFrame() {
        if (enabled) {
            frames++;
        }
    }

    public void begin(Pass pass) {
        if (!enabled) {
            return;
        }
        openPass = pass;
        passStarted = System.nanoTime();
    }

    public void end(Pass pass) {
        if (!enabled || openPass != pass) {
            return;
        }
        nanos[pass.ordinal()] += System.nanoTime() - passStarted;
        openPass = null;
    }

    /** Counts draw calls issued by a pass — the number that usually explains the time. */
    public void countDraws(Pass pass, int count) {
        if (enabled) {
            draws[pass.ordinal()] += count;
        }
    }

    public void reset() {
        for (int i = 0; i < PASSES.length; i++) {
            nanos[i] = 0L;
            draws[i] = 0L;
        }
        frames = 0;
    }

    /** A per-pass table, sized against the 16.7 ms a frame has at 60 Hz. */
    public String report() {
        StringBuilder out = new StringBuilder();
        long total = 0L;
        for (int i = 0; i < PASSES.length; i++) {
            total += nanos[i];
        }
        int sampled = Math.max(1, frames);

        out.append("pass          ms/frame    share   draws/frame\n");
        out.append("---------------------------------------------\n");
        for (int i = 0; i < PASSES.length; i++) {
            out.append(String.format("%-12s %9.3f  %6.1f%%  %10.1f%n",
                    PASSES[i],
                    Double.valueOf(nanos[i] / (double) sampled / 1_000_000.0),
                    Double.valueOf(total == 0L ? 0.0 : 100.0 * nanos[i] / total),
                    Double.valueOf(draws[i] / (double) sampled)));
        }
        out.append("---------------------------------------------\n");
        out.append(String.format("%-12s %9.3f  (budget 16.667 at 60 Hz)%n", "TOTAL",
                Double.valueOf(total / (double) sampled / 1_000_000.0)));
        return out.toString();
    }
}
