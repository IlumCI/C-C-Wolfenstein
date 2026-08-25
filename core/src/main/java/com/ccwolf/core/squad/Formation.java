package com.ccwolf.core.squad;

/**
 * Where each member of a squad stands relative to its anchor.
 *
 * <p>Offsets are in tiles, measured with the squad facing east, and rotated into place by
 * whoever is drawing or walking them. They are computed once per shape and read thereafter, so
 * moving a squad allocates nothing.
 *
 * <h2>The spacing constraint</h2>
 *
 * <p>Slots must be further apart than two unit radii. Any closer and members are permanently
 * inside one another, separation steering fights the formation every tick, and the squad turns
 * into a jitter generator that never settles. Infantry have a radius of 0.28, so the tightest
 * usable spacing is a little over 0.56 — everything here is built on 0.8, which leaves room
 * without the squad sprawling. There is a test that checks this rather than trusting it.
 */
public enum Formation {

    /** Shoulder to shoulder across the direction of travel. Good for holding ground. */
    LINE,

    /** Single file. What a squad marches in, and what fits down a road. */
    COLUMN,

    /** An arrowhead, leader forward. Reads as advancing, and puts fewest men in front. */
    WEDGE,

    /** Loosely spread. Fewer men inside one blast radius, at the cost of a wider frontage. */
    SKIRMISH;

    /** Distance between neighbouring slots, comfortably clear of twice an infantry radius. */
    public static final float SPACING = 0.8f;

    /** Largest squad any formation is laid out for. */
    public static final int MAX_SLOTS = 12;

    private final float[] offsetsX = new float[MAX_SLOTS];
    private final float[] offsetsY = new float[MAX_SLOTS];

    Formation() {
        // Enum constructors cannot see the enum's own values yet, so the tables are filled
        // lazily on first use instead.
    }

    private boolean built;

    private void build() {
        if (built) {
            return;
        }
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            switch (this) {
                case LINE: {
                    // Across the front, alternating out from the centre so the middle fills
                    // first and a half-strength squad is still centred on its anchor.
                    int rank = (slot + 1) / 2;
                    float side = (slot % 2 == 0) ? -1f : 1f;
                    offsetsX[slot] = 0f;
                    offsetsY[slot] = slot == 0 ? 0f : side * rank * SPACING;
                    break;
                }
                case COLUMN: {
                    offsetsX[slot] = -slot * SPACING;
                    offsetsY[slot] = 0f;
                    break;
                }
                case WEDGE: {
                    int rank = (slot + 1) / 2;
                    float side = (slot % 2 == 0) ? -1f : 1f;
                    offsetsX[slot] = -rank * SPACING;
                    offsetsY[slot] = slot == 0 ? 0f : side * rank * SPACING;
                    break;
                }
                case SKIRMISH:
                default: {
                    // Two loose ranks at one and a half spacing: wide enough that one shell
                    // does not take the whole squad.
                    int column = slot / 2;
                    float side = (slot % 2 == 0) ? -1f : 1f;
                    offsetsX[slot] = -column * SPACING * 1.5f;
                    offsetsY[slot] = side * SPACING * 0.75f;
                    break;
                }
            }
        }
        built = true;
    }

    /** Offset along the direction the squad faces, in tiles. */
    public float offsetX(int slot) {
        build();
        return offsetsX[Math.max(0, Math.min(MAX_SLOTS - 1, slot))];
    }

    /** Offset across the direction the squad faces, in tiles. */
    public float offsetY(int slot) {
        build();
        return offsetsY[Math.max(0, Math.min(MAX_SLOTS - 1, slot))];
    }

    /** The next shape in the cycle, for a HUD button that toggles through them. */
    public Formation next() {
        Formation[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
