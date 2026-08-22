package com.ccwolf.game.art;

/**
 * The parts machines are made of.
 *
 * <p>A vocabulary rather than a set of primitives. {@link Sculptor} knows about discs, capsules,
 * boxes and grooves; it does not know what a bolt circle is, or that a louvre is nine slots at a
 * pitch, or that armour plate has a weld along the seam where two of them meet. This is where
 * that knowledge lives, so a vehicle recipe reads as a description of a vehicle instead of as
 * four hundred coordinates.
 *
 * <p>That distinction is what makes detail affordable. The old recipes topped out at fifty or so
 * primitive calls because every one of them was a hand-placed rectangle with a hand-placed
 * highlight, and past that point more parts made a sprite muddier rather than richer — highlights
 * placed by different lines of code fight each other. With the lighting computed, the opposite
 * holds: every part added is another part correctly lit, so the way to a hull that reads as a
 * hull is simply to put more of a hull on it. A tank here is several hundred calls and each one
 * pays.
 *
 * <p>Everything below is lit by the one shared {@link Light}, and nothing below picks a colour
 * except by being handed one.
 */
public final class Machine {

    private Machine() {
    }

    /**
     * A raised bolt head with the shallow washer dish it is seated in.
     *
     * <p>Two strokes rather than one dot. The dish is what makes a bolt read as fastened
     * <em>through</em> a plate instead of glued on top of it, and at any size where a bolt is
     * visible at all the dish is what the eye actually catches.
     */
    public static void bolt(Sculptor s, float x, float y, float radius, float base, int albedo) {
        s.carveBox(x - radius * 1.5f, y - radius * 1.5f, radius * 3f, radius * 3f, radius * 1.5f,
                radius * 0.35f, Form.DOME);
        // Painted over, like the plate it fastens, and only mildly glossy. Bare bright steel
        // with a full specular turns a bolt line into a string of white pearls, which is what
        // the first hull looked like from across the room.
        s.disc(x, y, radius, base, radius * 0.6f, Form.DOME, albedo, Sculptor.PAINT);
    }

    /** A row of bolts along a line, at a pitch. Seams, hatch rims, plate edges. */
    public static void boltLine(Sculptor s, float x0, float y0, float x1, float y1, float pitch,
                                float radius, float base, int albedo) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        int count = Math.max(1, (int) (length / Math.max(1f, pitch)));
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            bolt(s, x0 + dx * t, y0 + dy * t, radius, base, albedo);
        }
    }

    /** Bolts round the rim of a hatch or a turret ring. */
    public static void boltRing(Sculptor s, float cx, float cy, float radius, int count,
                                float boltRadius, float base, int albedo) {
        for (int i = 0; i < count; i++) {
            double a = i * 2.0 * Math.PI / count;
            bolt(s, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius,
                    boltRadius, base, albedo);
        }
    }

    /**
     * A panel line: the seam where two plates meet.
     *
     * <p>Cut, never painted. The line is the same steel as the plates on either side of it and
     * reads as a line only because the light cannot get down into it — which is exactly why the
     * old hand-drawn version, a dark stroke, always looked like a stripe on a tank rather than a
     * join in one.
     */
    public static void seam(Sculptor s, float x0, float y0, float x1, float y1, float width,
                            float depth) {
        s.carveCapsule(x0, y0, x1, y1, width, depth, Form.ROUND);
    }

    /**
     * A weld bead running along a seam: the proud, lumpy line of metal a torch leaves.
     *
     * <p>Drawn as a chain of small overlapping domes rather than one capsule, because a weld is
     * not smooth and the unevenness is the entire reason it reads as a weld.
     */
    public static void weld(Sculptor s, float x0, float y0, float x1, float y1, float radius,
                            float base, int albedo, int seed) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        int beads = Math.max(2, (int) (length / Math.max(1f, radius * 1.1f)));
        for (int i = 0; i <= beads; i++) {
            float t = i / (float) beads;
            float wobble = ((i * 2654435761L ^ seed) & 7) / 7f - 0.5f;
            s.disc(x0 + dx * t - dy / length * wobble, y0 + dy * t + dx / length * wobble,
                    radius * (0.8f + 0.4f * (((i * 40503) >> 3) & 1)), base, radius * 0.5f,
                    Form.DOME, albedo, Sculptor.STEEL);
        }
    }

    /** A run of cooling louvres: an engine deck, a radiator, an armoured vent. */
    public static void grille(Sculptor s, float x, float y, float w, float h, int slots,
                              float depth) {
        float pitch = h / slots;
        for (int i = 0; i < slots; i++) {
            s.carveBox(x, y + i * pitch, w, pitch * 0.55f, pitch * 0.2f, depth, Form.BEVEL);
        }
    }

    /**
     * An armoured plate with a chamfered edge, a recessed border and a weathered face.
     *
     * <p>The unit of construction for anything armoured. The border cut is what separates one
     * plate from the next when several are laid side by side, and the roughening is what stops a
     * large flat area reading as plastic — a plate this size with a perfectly even surface is the
     * single most obvious tell of a computer-generated sprite.
     */
    public static void plate(Sculptor s, float x, float y, float w, float h, float corner,
                             float base, float thickness, int albedo, float material, int seed) {
        s.box(x, y, w, h, corner, base, thickness, Form.BEVEL, albedo, material);
        s.roughen(x, y, w, h, 0.12f, 6f, seed);
        s.carveCapsule(x + corner, y + corner, x + w - corner, y + corner, 0.9f, 0.8f, Form.ROUND);
        s.carveCapsule(x + corner, y + h - corner, x + w - corner, y + h - corner, 0.9f, 0.8f,
                Form.ROUND);
    }

    /** A hatch: a recessed rim, a raised lid, a handle and its bolts. */
    public static void hatch(Sculptor s, float cx, float cy, float radius, float base,
                             int albedo) {
        s.carveBox(cx - radius - 2f, cy - radius - 2f, radius * 2f + 4f, radius * 2f + 4f,
                radius, 1.6f, Form.BEVEL);
        s.disc(cx, cy, radius, base, radius * 0.35f, Form.BEVEL, albedo, Sculptor.STEEL);
        boltRing(s, cx, cy, radius * 0.78f, 8, radius * 0.11f, base + radius * 0.35f, albedo);
        s.capsule(cx - radius * 0.4f, cy, cx + radius * 0.4f, cy, radius * 0.12f,
                base + radius * 0.35f, radius * 0.16f, Form.ROUND, albedo, Sculptor.STEEL);
    }

    /**
     * A gun barrel: tube, reinforcing collar, muzzle brake and a bored-out muzzle.
     *
     * <p>The bore is the detail worth having. A barrel that ends in a flat disc reads as a pipe;
     * a barrel with a hole down the end of it reads as a gun, and it costs one carve.
     */
    public static void barrel(Sculptor s, float x0, float y0, float x1, float y1, float radius,
                              float base, int albedo) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / length;
        float uy = dy / length;

        s.capsule(x0, y0, x1, y1, radius, base, radius, Form.ROUND, albedo, Sculptor.STEEL);
        // Collar where the barrel leaves the mantlet, and a second a third of the way out.
        s.capsule(x0 + ux * radius, y0 + uy * radius, x0 + ux * radius * 3f,
                y0 + uy * radius * 3f, radius * 1.35f, base, radius * 1.1f, Form.ROUND,
                albedo, Sculptor.STEEL);
        s.capsule(x0 + ux * length * 0.42f, y0 + uy * length * 0.42f,
                x0 + ux * length * 0.5f, y0 + uy * length * 0.5f, radius * 1.15f, base,
                radius * 1.0f, Form.ROUND, albedo, Sculptor.STEEL);
        // Muzzle brake, then the bore cut into the end of it.
        s.capsule(x1 - ux * radius * 2.6f, y1 - uy * radius * 2.6f, x1, y1, radius * 1.45f,
                base, radius * 1.2f, Form.ROUND, albedo, Sculptor.STEEL);
        s.carveCapsule(x1 - ux * radius * 2.1f, y1 - uy * radius * 2.1f,
                x1 - ux * radius * 1.4f, y1 - uy * radius * 1.4f, radius * 1.45f, 2.2f,
                Form.BEVEL);
        s.carveCapsule(x1 - ux * radius * 0.4f, y1 - uy * radius * 0.4f, x1, y1,
                radius * 0.55f, radius * 1.6f, Form.ROUND);
    }

    /**
     * A run of track: the plate under it, then a link at a time with a guide horn and a pin.
     *
     * <p>Drawn link by link because a track is the most recognisable thing on a tracked vehicle
     * and a repeating hatched band is the most obvious way to get it wrong. The phase shifts the
     * whole run along by a fraction of a link, which is what animates it.
     */
    public static void trackRun(Sculptor s, float x0, float y0, float x1, float y1, float width,
                                float base, int albedo, float phase) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / length;
        float uy = dy / length;
        float px = -uy;
        float py = ux;

        s.capsule(x0, y0, x1, y1, width, base, width * 0.55f, Form.BEVEL, albedo,
                Sculptor.LEATHER);

        float pitch = width * 0.72f;
        int links = (int) (length / pitch);
        for (int i = -1; i <= links + 1; i++) {
            float t = (i + phase) * pitch;
            if (t < -pitch || t > length + pitch) {
                continue;
            }
            float lx = x0 + ux * t;
            float ly = y0 + uy * t;
            s.capsule(lx - px * width * 0.86f, ly - py * width * 0.86f,
                    lx + px * width * 0.86f, ly + py * width * 0.86f, pitch * 0.34f,
                    base + width * 0.55f, pitch * 0.3f, Form.ROUND, albedo, Sculptor.STEEL);
            // Guide horn down the centre line, and the pin joining this link to the next.
            s.disc(lx, ly, pitch * 0.26f, base + width * 0.85f, pitch * 0.24f, Form.DOME,
                    albedo, Sculptor.STEEL);
            s.carveCapsule(lx + ux * pitch * 0.5f - px * width * 0.8f,
                    ly + uy * pitch * 0.5f - py * width * 0.8f,
                    lx + ux * pitch * 0.5f + px * width * 0.8f,
                    ly + uy * pitch * 0.5f + py * width * 0.8f, pitch * 0.1f, 1.4f, Form.ROUND);
        }
    }

    /** A road wheel: tyre, hub, and the bolts holding the hub on. */
    public static void roadWheel(Sculptor s, float cx, float cy, float radius, float base,
                                 int tyre, int hub) {
        s.disc(cx, cy, radius, base, radius * 0.5f, Form.ROUND, tyre, Sculptor.LEATHER);
        s.carveBox(cx - radius, cy - radius * 0.28f, radius * 2f, radius * 0.56f, radius * 0.28f,
                1.2f, Form.ROUND);
        s.disc(cx, cy, radius * 0.52f, base + radius * 0.5f, radius * 0.3f, Form.BEVEL, hub,
                Sculptor.STEEL);
        boltRing(s, cx, cy, radius * 0.33f, 6, radius * 0.09f, base + radius * 0.8f, hub);
    }

    /**
     * Chipped paint along an edge, showing the metal under it.
     *
     * <p>Colour rather than form, and one of the few places colour is still the right answer:
     * wear is where the paint has gone, not where the steel has. Deterministic from the seed.
     */
    public static void chip(Sculptor s, float x0, float y0, float x1, float y1, int bare,
                            int count, int seed) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        for (int i = 0; i < count; i++) {
            int h = (seed * 0x9E3779B1) + i * 0x85EBCA6B;
            h ^= h >>> 15;
            float t = ((h >>> 8) & 0xFFFF) / 65536f;
            float size = 1.2f + ((h >>> 3) & 3);
            s.stain(x0 + dx * t - size, y0 + dy * t - size, size * 2f, size * 2f, bare, 0.7f);
        }
    }
}
