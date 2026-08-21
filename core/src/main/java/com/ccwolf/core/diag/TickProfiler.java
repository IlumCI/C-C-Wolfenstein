package com.ccwolf.core.diag;

/**
 * Where the tick goes, and how much work it did getting there.
 *
 * <p>Two kinds of measurement, and the difference between them matters.
 *
 * <p><b>Times</b> are advisory. They say which phase to look at, but they move with the
 * machine, the JIT and whatever else is running, so nothing should ever assert on them.
 *
 * <p><b>Counters</b> are the real regression guard. How many A* searches ran, how many nodes
 * they expanded, how many separation pairs were tested — all of that is a pure function of the
 * seed, so it is reproducible and a test can hold it to a bound. "This change made pathfinding
 * cheaper" then becomes a number that either went down or did not, rather than a stopwatch
 * reading someone took once on a laptop.
 */
public final class TickProfiler {

    /** Phases of a tick, in the order {@code GameWorld.step} runs them. */
    public enum Phase {
        SNAPSHOT,
        INDEX,
        POWER,
        PRODUCTION,
        SABOTAGE,
        STEALTH,
        UNITS,
        BUILDINGS,
        REPAIRS,
        SEPARATION,
        REMOVE_DEAD,
        ORE,
        FOG,
        VICTORY
    }

    private static final Phase[] PHASES = Phase.values();

    private final long[] nanos = new long[PHASES.length];
    private final long[] calls = new long[PHASES.length];

    private boolean enabled;
    private int ticks;
    private long phaseStarted;
    private Phase openPhase;

    // --- deterministic counters -------------------------------------------------------------

    private long astarSearches;
    private long astarNodes;
    private long separationPairs;
    private long stuckRepaths;
    private long detours;
    private long indexResults;
    private long pathQueueDepth;

    /** Off by default: an enabled check is one predictable branch, timing every phase is not. */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void beginTick() {
        if (enabled) {
            ticks++;
        }
    }

    public void begin(Phase phase) {
        if (!enabled) {
            return;
        }
        openPhase = phase;
        phaseStarted = System.nanoTime();
    }

    public void end(Phase phase) {
        if (!enabled || openPhase != phase) {
            return;
        }
        nanos[phase.ordinal()] += System.nanoTime() - phaseStarted;
        calls[phase.ordinal()]++;
        openPhase = null;
    }

    // Counters are recorded whether or not timing is on: they cost an add, and a test that
    // asserts on them should not have to remember to switch profiling on first.

    /** A full search: no bound but the pathfinder's own, and the expensive kind. */
    public void countAstarSearch(int nodesExpanded) {
        astarSearches++;
        astarNodes += nodesExpanded;
    }

    /**
     * A bounded detour around an obstruction.
     *
     * <p>Counted apart from full searches because trading one for several is the whole point:
     * the search count going up while the node count goes down is success, and one number
     * covering both would hide that.
     */
    public void countDetourSearch(int nodesExpanded) {
        detours++;
        astarNodes += nodesExpanded;
    }

    public void countSeparationPairs(long pairs) {
        separationPairs += pairs;
    }

    public void countStuckRepath() {
        stuckRepaths++;
    }

    public long detours() {
        return detours;
    }

    public void countIndexResults(long results) {
        indexResults += results;
    }

    public void notePathQueueDepth(long depth) {
        pathQueueDepth = Math.max(pathQueueDepth, depth);
    }

    public long astarSearches() {
        return astarSearches;
    }

    public long astarNodes() {
        return astarNodes;
    }

    public long separationPairs() {
        return separationPairs;
    }

    public long stuckRepaths() {
        return stuckRepaths;
    }

    public long indexResults() {
        return indexResults;
    }

    public long peakPathQueueDepth() {
        return pathQueueDepth;
    }

    public int ticks() {
        return ticks;
    }

    public void reset() {
        for (int i = 0; i < PHASES.length; i++) {
            nanos[i] = 0L;
            calls[i] = 0L;
        }
        ticks = 0;
        astarSearches = 0L;
        astarNodes = 0L;
        separationPairs = 0L;
        stuckRepaths = 0L;
        detours = 0L;
        indexResults = 0L;
        pathQueueDepth = 0L;
    }

    /** A per-phase table, plus the counters, sized against the 50 ms a 20 Hz tick has. */
    public String report() {
        StringBuilder out = new StringBuilder();
        long total = 0L;
        for (int i = 0; i < PHASES.length; i++) {
            total += nanos[i];
        }
        int sampled = Math.max(1, ticks);

        out.append("phase          ms/tick    share\n");
        out.append("--------------------------------\n");
        for (int i = 0; i < PHASES.length; i++) {
            double msPerTick = nanos[i] / (double) sampled / 1_000_000.0;
            double share = total == 0L ? 0.0 : 100.0 * nanos[i] / total;
            out.append(String.format("%-12s %8.3f  %6.1f%%%n", PHASES[i], msPerTick, share));
        }
        out.append("--------------------------------\n");
        out.append(String.format("%-12s %8.3f  (budget 50.000 at 20 Hz)%n", "TOTAL",
                total / (double) sampled / 1_000_000.0));
        out.append('\n');
        out.append("counters (deterministic - safe to assert on)\n");
        out.append(String.format("  A* full searches %10d  (%.2f per tick)%n",
                Long.valueOf(astarSearches), Double.valueOf(astarSearches / (double) sampled)));
        out.append(String.format("  A* detours       %10d  (%.2f per tick)%n",
                Long.valueOf(detours), Double.valueOf(detours / (double) sampled)));
        out.append(String.format("  A* nodes         %10d  (%.1f per tick)%n",
                Long.valueOf(astarNodes), Double.valueOf(astarNodes / (double) sampled)));
        out.append(String.format("  separation pairs %10d  (%.1f per tick)%n",
                Long.valueOf(separationPairs),
                Double.valueOf(separationPairs / (double) sampled)));
        out.append(String.format("  index results    %10d  (%.1f per tick)%n",
                Long.valueOf(indexResults), Double.valueOf(indexResults / (double) sampled)));
        out.append(String.format("  stuck repaths    %10d%n", Long.valueOf(stuckRepaths)));
        out.append(String.format("  peak path queue  %10d%n", Long.valueOf(pathQueueDepth)));
        return out.toString();
    }
}
