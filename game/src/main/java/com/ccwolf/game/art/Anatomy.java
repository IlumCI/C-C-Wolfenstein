package com.ccwolf.game.art;

/**
 * The parts people are made of.
 *
 * <p>The counterpart to {@link Machine}, and a harder problem. A tank is a stack of boxes and
 * cylinders, which is exactly what this engine draws natively; a person is none of those, is seen
 * from almost directly above, and is about twenty pixels across at the size that matters.
 *
 * <h2>Faces are sculpted, not painted</h2>
 *
 * <p>A head here is a couple of dozen pixels wide and a face perhaps fourteen. Nothing legible
 * can be <em>drawn</em> at that size — an eye painted as two dark pixels is two dark pixels, and
 * reads as damage. But an eye <em>socket</em> is a hollow, and a hollow catches a shadow, and a
 * shadow is visible at any size at all. So every feature below is geometry: the brow is a ridge,
 * the sockets are carves, the nose is a ridge, the mouth is a groove. The lighting finds them.
 *
 * <p>This is the thing the old pipeline could not do at any resolution. It had to paint a face,
 * so it painted two dark dots, and a rifleman looked like a rifleman with two dark dots.
 *
 * <h2>Features arrive as a man turns toward you</h2>
 *
 * <p>Seen from above a soldier is mostly a helmet and a pair of shoulders; you see a face only
 * when he is coming toward the camera. So facial geometry is scaled by how much of the facing
 * points south, and fades to nothing as he turns away — which is both correct and free, since a
 * man walking north needs no face and no longer has one.
 *
 * <h2>Skin</h2>
 *
 * <p>Flesh is marked with {@code Sculptor.flesh} so the lighting passes warm light through it.
 * Without that a face shades exactly like a painted helmet, and the result is a mannequin: right
 * value, wrong material, unmistakably not alive.
 */
public final class Anatomy {

    private Anatomy() {
    }

    /** Skin is soft and slightly damp: a broad, weak sheen, nothing like plate. */
    public static final float SKIN_SHEEN = 0.12f;

    /**
     * A head: skull, and whatever of a face is turned toward the viewer.
     *
     * @param toward how much the man faces the camera, 0 turned away through 1 straight at it
     */
    public static void head(Sculptor s, float cx, float cy, float radius, float base,
                            int skin, float toward) {
        s.disc(cx, cy, radius, base, radius * 0.92f, Form.DOME, skin, SKIN_SHEEN);
        s.flesh(cx, cy, radius * 1.05f, 1f);

        if (toward <= 0.02f) {
            // The back of a head. A slight flattening at the crown is all there is to say.
            s.carveCapsule(cx - radius * 0.28f, cy - radius * 0.22f, cx + radius * 0.28f,
                    cy - radius * 0.22f, radius * 0.16f, radius * 0.012f, Form.DOME);
            return;
        }

        // The face sits low on the skull seen from above, because a forehead is what you see
        // first. Everything below is a fraction of the head radius and every cut is shallow -
        // the first version used depths around a third of the radius, which does not carve a
        // face, it opens the skull. A socket needs to be deep enough to hold a shadow and no
        // deeper, and at these sizes that is a few per cent.
        float faceY = cy + radius * 0.20f * toward;
        float t = toward;

        // Brow: a low ridge, not a bar. It exists to put the sockets underneath something.
        s.capsule(cx - radius * 0.42f, faceY - radius * 0.30f, cx + radius * 0.42f,
                faceY - radius * 0.30f, radius * 0.11f, base + radius * 0.72f,
                radius * 0.05f * t, Form.RIDGE, skin, SKIN_SHEEN);

        // The sockets. These are the face: two hollows under the brow, far enough apart to
        // read as two and shallow enough to stay a shadow rather than a hole.
        for (int side = 0; side < 2; side++) {
            float ex = cx + (side == 0 ? -1f : 1f) * radius * 0.26f;
            // A capsule rather than a box: a rounded slot reads as a socket, a rectangular one
            // reads as a letterbox, and at this size that is the whole difference.
            s.carveCapsule(ex - radius * 0.10f, faceY - radius * 0.09f,
                    ex + radius * 0.10f, faceY - radius * 0.11f, radius * 0.085f,
                    radius * 0.05f * t, Form.DOME);
        }

        // Nose: a short ridge between them, standing proudest at the tip.
        s.taper(cx, faceY - radius * 0.13f, radius * 0.045f, cx, faceY + radius * 0.14f,
                radius * 0.085f, base + radius * 0.80f, radius * 0.16f * t, Form.DOME,
                skin, SKIN_SHEEN);

        // Mouth, and the line under a bottom lip. Both barely there.
        s.carveCapsule(cx - radius * 0.13f, faceY + radius * 0.34f, cx + radius * 0.13f,
                faceY + radius * 0.34f, radius * 0.045f, radius * 0.03f * t, Form.ROUND);
    }

    /**
     * A steel helmet: the shell, its flare, and the band round the rim.
     *
     * <p>Sits over the head rather than replacing it, so a face still shows under the brim as a
     * man turns toward you — which is the difference between a soldier and a bollard.
     */
    public static void helmet(Sculptor s, float cx, float cy, float radius, float base,
                              int albedo, float toward) {
        // The shell sits above the skull, and the flare sits just below the shell rather than
        // below the head - the first version put the skirt under the head's own height, so the
        // skull came up through the brim and the man wore his helmet as a collar.
        float shell = base;
        s.disc(cx, cy - radius * 0.08f, radius, shell, radius * 0.72f, Form.DOME, albedo,
                Sculptor.PAINT);
        s.disc(cx, cy - radius * 0.02f, radius * 1.16f, shell - radius * 0.06f,
                radius * 0.16f, Form.BEVEL, albedo, Sculptor.PAINT);
        // The join between skirt and shell, cut rather than drawn.
        s.carveCapsule(cx - radius * 0.98f, cy - radius * 0.02f, cx + radius * 0.98f,
                cy - radius * 0.02f, radius * 0.05f, radius * 0.012f, Form.ROUND);
        Machine.bolt(s, cx - radius * 0.9f, cy - radius * 0.06f, radius * 0.08f,
                shell + radius * 0.1f, albedo);
        Machine.bolt(s, cx + radius * 0.9f, cy - radius * 0.06f, radius * 0.08f,
                shell + radius * 0.1f, albedo);
        if (toward > 0.3f) {
            // A shallow shadow line where the brim overhangs the brow.
            s.carveCapsule(cx - radius * 0.55f, cy + radius * 0.42f, cx + radius * 0.55f,
                    cy + radius * 0.42f, radius * 0.09f, radius * 0.018f * toward, Form.DOME);
        }
    }

    /**
     * A gas mask: filter, lenses, and the straps holding it on.
     *
     * <p>What the Regime wears instead of a face. The lenses are emissive at a low level — not
     * because they glow, but because glass at this size reads as glass only if it is brighter
     * than everything around it, and a faint self-lit disc is the cheapest way to say so.
     */
    public static void gasMask(Sculptor s, float cx, float cy, float radius, float base,
                               int rubber, int lens, int metal) {
        s.disc(cx, cy + radius * 0.1f, radius * 0.98f, base, radius * 0.8f, Form.DOME, rubber,
                Sculptor.LEATHER);
        // Lenses: recessed rims with glass sitting down in them.
        for (int side = 0; side < 2; side++) {
            float lx = cx + (side == 0 ? -1f : 1f) * radius * 0.42f;
            float ly = cy + radius * 0.06f;
            s.carveBox(lx - radius * 0.30f, ly - radius * 0.30f, radius * 0.6f, radius * 0.6f,
                    radius * 0.3f, radius * 0.22f, Form.DOME);
            s.disc(lx, ly, radius * 0.26f, base + radius * 0.55f, radius * 0.12f, Form.DOME,
                    lens, Sculptor.STEEL);
            s.glow(lx, ly, radius * 0.30f, 0.22f);
        }
        // The filter drum, hung below and to one side, and the corrugated hose to it.
        s.disc(cx + radius * 0.1f, cy + radius * 0.95f, radius * 0.36f, base - radius * 0.2f,
                radius * 0.36f, Form.ROUND, metal, Sculptor.STEEL);
        for (int i = 0; i < 3; i++) {
            s.carveCapsule(cx - radius * 0.22f, cy + radius * (0.75f + i * 0.14f),
                    cx + radius * 0.42f, cy + radius * (0.75f + i * 0.14f), radius * 0.06f,
                    radius * 0.08f, Form.ROUND);
        }
        // Straps across the crown.
        s.capsule(cx - radius * 0.9f, cy - radius * 0.35f, cx + radius * 0.9f, cy - radius * 0.35f,
                radius * 0.11f, base + radius * 0.4f, radius * 0.1f, Form.ROUND, rubber,
                Sculptor.LEATHER);
    }

    /**
     * A torso in a greatcoat: chest, the fall of the skirt, and the seam down the front.
     *
     * <p>Cloth is the one thing here that is genuinely soft, and the way to say so is to keep
     * every edge rounded and the surface slightly uneven. A coat drawn with the crisp chamfers
     * that suit armour plate reads as armour plate.
     */
    public static void torso(Sculptor s, float cx, float cy, float width, float height,
                             float base, int cloth, int seed) {
        s.box(cx - width / 2f, cy - height / 2f, width, height, width * 0.42f, base,
                width * 0.34f, Form.DOME, cloth, Sculptor.CLOTH);
        s.roughen(cx - width / 2f, cy - height / 2f, width, height, 0.22f, 4f, seed);
        // Chest, standing a little proud of the skirt below it.
        s.disc(cx, cy - height * 0.22f, width * 0.44f, base + width * 0.1f, width * 0.2f,
                Form.DOME, cloth, Sculptor.CLOTH);
        // The button seam, cut down the middle.
        s.carveCapsule(cx, cy - height * 0.34f, cx, cy + height * 0.42f, width * 0.05f,
                width * 0.1f, Form.ROUND);
    }

    /** Shoulders, as two caps rather than a bar: a bar reads as a yoke. */
    public static void shoulders(Sculptor s, float cx, float cy, float span, float base,
                                 int cloth) {
        for (int side = 0; side < 2; side++) {
            float x = cx + (side == 0 ? -1f : 1f) * span / 2f;
            s.disc(x, cy, span * 0.28f, base, span * 0.24f, Form.DOME, cloth, Sculptor.CLOTH);
        }
        s.capsule(cx - span / 2f, cy, cx + span / 2f, cy, span * 0.22f, base - span * 0.04f,
                span * 0.18f, Form.ROUND, cloth, Sculptor.CLOTH);
    }

    /** An arm or a leg: a cylinder, tapering slightly, because limbs do. */
    public static void limb(Sculptor s, float x0, float y0, float x1, float y1, float radius,
                            float base, int cloth) {
        s.taper(x0, y0, radius, x1, y1, radius * 0.82f, base, radius * 0.9f, Form.ROUND, cloth,
                Sculptor.CLOTH);
    }

    /** A boot: sole, upper, and the toe standing proud of both. */
    public static void boot(Sculptor s, float cx, float cy, float length, float base,
                            int leather) {
        s.capsule(cx, cy - length * 0.3f, cx, cy + length * 0.3f, length * 0.3f, base,
                length * 0.28f, Form.ROUND, leather, Sculptor.LEATHER);
        s.disc(cx, cy + length * 0.34f, length * 0.3f, base + length * 0.04f, length * 0.24f,
                Form.DOME, leather, Sculptor.LEATHER);
        s.carveCapsule(cx - length * 0.3f, cy + length * 0.14f, cx + length * 0.3f,
                cy + length * 0.14f, length * 0.08f, length * 0.1f, Form.ROUND);
    }

    /** Webbing: a belt, and pouches hung off it. */
    public static void webbing(Sculptor s, float cx, float cy, float span, float base,
                               int strap, int pouch) {
        s.capsule(cx - span / 2f, cy, cx + span / 2f, cy, span * 0.09f, base, span * 0.07f,
                Form.ROUND, strap, Sculptor.LEATHER);
        for (int i = -1; i <= 1; i += 2) {
            s.box(cx + i * span * 0.26f - span * 0.11f, cy - span * 0.06f, span * 0.22f,
                    span * 0.2f, span * 0.05f, base + span * 0.05f, span * 0.09f, Form.BEVEL,
                    pouch, Sculptor.LEATHER);
        }
        Machine.bolt(s, cx, cy, span * 0.055f, base + span * 0.08f,
                WolfPalette.albedo(WolfPalette.BRASS));
    }
}
