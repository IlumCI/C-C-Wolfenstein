package com.ccwolf.core.entity;

/** The two sides. Unit rosters differ; the building set is shared. */
public enum Faction {
    RESISTANCE("Kreisau Circle"),
    REGIME("Totenkopf Division");

    private final String displayName;

    Faction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Faction other() {
        return this == RESISTANCE ? REGIME : RESISTANCE;
    }
}
