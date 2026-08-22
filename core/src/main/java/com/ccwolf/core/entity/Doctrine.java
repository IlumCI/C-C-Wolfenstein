package com.ccwolf.core.entity;

/**
 * A way of fighting, chosen once before the match and never changed.
 *
 * <p>Four twenty-seed artillery sweeps found the same thing four times: the more evenly matched
 * the two sides are, the more matches stalemate. Symmetric artillery is mutual suppression —
 * both lines dig, both get shelled, neither moves. Fronts helped, but the finding stood: what
 * breaks a deadlock is an asymmetry of method rather than better aim. Doctrines are that
 * asymmetry, written down.
 *
 * <p>The two sets are answers to each other rather than a list of bonuses. The Resistance
 * improvises with ground — it digs deeper, holds harder, spreads thinner. The Regime builds
 * monsters and reaches into the trench — gas that pools where men shelter, flame, and something
 * that walks in and clears it. A doctrine that beat every reply would not be a choice, and the
 * balance sweep says so in a table rather than in an opinion.
 *
 * <h2>A doctrine belongs to a faction, and that is a rule here</h2>
 *
 * <p>Not a rule in whatever is drawing the picker. {@link #availableTo} is the one place that
 * decides, and {@code GameWorld.addPlayer} refuses a mismatch outright, so no screen, harness
 * flag or test can put the Kreisau Circle in a gas mask by passing the wrong constant.
 *
 * <h2>Having none is a real option</h2>
 *
 * <p>{@code Player.doctrine()} returns null for a player who never picked one, and every effect
 * site checks for it. That is not a defensive habit — a match with no doctrines on either side
 * is the game exactly as it played before this feature existed, which is what lets the whole
 * thing be built and shipped dark, and lets one small commit at the end turn it on with an
 * auditable diff against the determinism goldens.
 */
public enum Doctrine {

    // --- Resistance: hold the ground ------------------------------------------------------

    /** Digs half again as fast, digs deeper than anyone else can, and digs under fire. */
    TIEFBAU("Deep Works", Faction.RESISTANCE,
            "Digs faster, digs deeper, and keeps digging under fire."),

    /** The same trench is worth more to the men standing in it. */
    STAHLBETON("Hardened Line", Faction.RESISTANCE,
            "Cover protects more. Nothing at all on the advance."),

    /** Squads stand wider, so one shell takes fewer of them. */
    ZERSTREUUNG("Dispersal", Faction.RESISTANCE,
            "Squads stand wider. Slower to gather, harder to catch."),

    // --- Regime: reach into it ------------------------------------------------------------

    /** Gas that is heavier than air, and therefore worst exactly where men shelter. */
    GASKRIEG("Gas War", Faction.REGIME,
            "Gas that sinks into a trench and stays. Cover is no help."),

    /** Flame teams: fire goes where the man is, not where the wall is. */
    BRANDSTURM("Firestorm", Faction.REGIME,
            "Flame assault teams. Fire goes where the man is."),

    /** Something large enough to walk into a position and empty it. */
    AUSMERZUNG("Extermination", Faction.REGIME,
            "A supersoldier that walks in and clears the position.");

    private final String displayName;
    private final Faction faction;
    private final String description;

    Doctrine(String displayName, Faction faction, String description) {
        this.displayName = displayName;
        this.faction = faction;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Faction faction() {
        return faction;
    }

    /** One line, for the picker. Short enough to sit under a plate without wrapping twice. */
    public String description() {
        return description;
    }

    public boolean availableTo(Faction f) {
        return faction == f;
    }

    /**
     * The doctrines one side may choose from, in declaration order.
     *
     * <p>Built once per faction into a private array rather than filtered on each call: the AI
     * asks this while choosing, and a method that allocates inside the simulation is a method
     * somebody will eventually call every tick.
     */
    public static Doctrine[] forFaction(Faction f) {
        Doctrine[] source = f == Faction.REGIME ? REGIME_SET : RESISTANCE_SET;
        Doctrine[] copy = new Doctrine[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    /** How many a side has to choose between. Both sides have the same number, deliberately. */
    public static int countFor(Faction f) {
        return (f == Faction.REGIME ? REGIME_SET : RESISTANCE_SET).length;
    }

    /**
     * The nth doctrine of a faction, wrapping.
     *
     * <p>For choosing one without a copy and without touching the random number generator — a
     * draw taken at construction would shift the whole stream and make every later golden diff
     * unreadable.
     */
    public static Doctrine forFaction(Faction f, int index) {
        Doctrine[] set = f == Faction.REGIME ? REGIME_SET : RESISTANCE_SET;
        int i = index % set.length;
        return set[i < 0 ? i + set.length : i];
    }

    private static final Doctrine[] RESISTANCE_SET = {TIEFBAU, STAHLBETON, ZERSTREUUNG};
    private static final Doctrine[] REGIME_SET = {GASKRIEG, BRANDSTURM, AUSMERZUNG};

    static {
        // Every constant belongs to exactly one set, checked at class load rather than trusted.
        // The tables in Earthworks, Suppression and DamageTable all earned this the hard way.
        for (Doctrine d : values()) {
            int found = 0;
            for (Doctrine r : RESISTANCE_SET) {
                if (r == d) {
                    found++;
                }
            }
            for (Doctrine r : REGIME_SET) {
                if (r == d) {
                    found++;
                }
            }
            if (found != 1) {
                throw new IllegalStateException(
                        "Doctrine " + d + " appears in " + found + " faction sets, not one");
            }
        }
    }
}
