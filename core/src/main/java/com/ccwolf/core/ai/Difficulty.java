package com.ccwolf.core.ai;

/** How hard the computer opponent plays. Tunes wave size, army cap and reaction speed. */
public enum Difficulty {

    /** Small waves, a lazy build order, keeps a big cash cushion. */
    RECRUIT(3, 12, 1500, 40),
    /** The default skirmish opponent. */
    VETERAN(5, 20, 900, 25),
    /** Bigger waves, faster decisions, spends nearly everything it earns. */
    OBERST(7, 30, 400, 15);

    private final int firstWaveSize;
    private final int armyCap;
    private final int creditReserve;
    private final int decisionIntervalTicks;

    Difficulty(int firstWaveSize, int armyCap, int creditReserve, int decisionIntervalTicks) {
        this.firstWaveSize = firstWaveSize;
        this.armyCap = armyCap;
        this.creditReserve = creditReserve;
        this.decisionIntervalTicks = decisionIntervalTicks;
    }

    public int firstWaveSize() {
        return firstWaveSize;
    }

    public int armyCap() {
        return armyCap;
    }

    /** Credits the AI tries to keep in the bank for structures before spending on units. */
    public int creditReserve() {
        return creditReserve;
    }

    public int decisionIntervalTicks() {
        return decisionIntervalTicks;
    }
}
