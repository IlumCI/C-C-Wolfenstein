package com.ccwolf.game.art;

/**
 * The parts people are made of, in centimetres.
 *
 * <p>The counterpart to {@link Machine}, and a harder problem. A tank is a stack of boxes and
 * cylinders, which this engine draws natively. A person is ovoids all the way down, and the first
 * attempt at one — built out of circles, positioned in screen coordinates, with a sphere for a
 * head — came out as a skittle.
 *
 * <h2>Everything is authored in the body's own frame</h2>
 *
 * <p>Across, forward and up, in centimetres, through {@link Pose}. A rifleman is a hundred and
 * seventy-five tall, his head is nineteen across and twenty-three deep, and his helmet clears his
 * skull by two. Those are numbers with meanings that can be checked against a photograph, where
 * fractions of a canvas are numbers that can only be checked by looking at the result — and every
 * layering fault in the first soldier came from a hand-tuned fraction being wrong.
 *
 * <h2>A skull is not a sphere</h2>
 *
 * <p>It is an assembly, and drawing it as one is the difference between a soldier and a bollard:
 * a cranium wider behind than in front, temples pinched in above the cheekbones, a brow ridge
 * standing out over the eyes, and a jaw slung under it tapering to a chin. Six parts, welded, so
 * they read as one skull rather than as six.
 *
 * <p>Then the face is <em>carved</em> into that, because at this size nothing legible can be
 * drawn: an eye painted as two dark pixels is two dark pixels and reads as damage, where an eye
 * socket is a hollow, and a hollow holds a shadow, and a shadow is legible at any size at all.
 *
 * <h2>Skin</h2>
 *
 * <p>Flesh is marked with {@code Sculptor.flesh} so light passes through it and comes back warm.
 * Shade a face with the same model as a painted helmet and the value is right and the man is
 * dead.
 */
public final class Anatomy {

    private Anatomy() {
    }

    /** Skin is soft and slightly damp: a broad, weak sheen, nothing like plate. */
    public static final float SKIN_SHEEN = 0.12f;

    /** Scratch for the ellipse projections, so a figure does not allocate per part. */
    private static final ThreadLocal<float[]> SHAPE = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[3];
        }
    };

    /** Scratch for the camera-space axes of a mass, for the same reason. */
    private static final ThreadLocal<float[]> AXES = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[9];
        }
    };

    /**
     * A rounded body part: an ellipsoid, projected and lit as one.
     *
     * <p>Every organic mass goes through here — skull, jaw, chest, shoulder, hip. The caller says
     * how wide, how deep and how tall the thing is and where its centre sits, and the pose works
     * out what that looks like from the camera at this facing.
     */
    public static void mass(Sculptor s, Pose p, float across, float forward, float up,
                            float wide, float deep, float tall, int albedo, float material) {
        float[] shape = SHAPE.get();
        float[] axes = AXES.get();
        p.solidEllipse(wide, deep, tall, shape);
        p.axes(wide, deep, tall, axes);
        // Ray-cast, not filled in. Handing this outline a dome profile instead - which is what
        // the first two skulls did - gives every part a correct silhouette full of fictional
        // geometry, and a head made of six of those is a bag of blobs.
        s.ellipsoid(p.x(across, forward, up), p.y(across, forward, up),
                p.depth(across, forward, up), shape[0], shape[1], shape[2], axes,
                albedo, material);
    }

    /** A limb, a barrel, a strap: a cylinder between two points in the body's frame. */
    public static void tube(Sculptor s, Pose p, float x0, float y0, float z0,
                            float x1, float y1, float z1, float radius, int albedo,
                            float material) {
        s.capsule(p.x(x0, y0, z0), p.y(x0, y0, z0), p.x(x1, y1, z1), p.y(x1, y1, z1),
                p.size(radius), p.depth((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f),
                p.size(radius), Form.ROUND, albedo, material);
    }

    // --- the head ---------------------------------------------------------------------------

    /**
     * A skull, assembled and welded, with a face carved into whatever of it faces the camera.
     *
     * <p>Sizes are a real head: nineteen centimetres across, twenty-three front to back,
     * twenty-four tall including the jaw. The cranium sits back on that footprint and the face
     * hangs forward and below it, which is what gives a head a front at all.
     *
     * @param up height of the centre of the skull off the ground
     */
    public static void skull(Sculptor s, Pose p, float across, float forward, float up,
                             int skin) {
        float toward = p.toward();
        s.weld(true);

        // Proportions off a real head rather than off a cartoon skull. The second attempt put
        // the chin further forward than the brow, which is a snout, and hung the ears off the
        // widest point of the cranium, which is a pair of handles.
        //
        // Cranium: the volume is behind, and it is taller than it is wide.
        mass(s, p, across, forward - 1f, up + 0.5f, 9.3f, 10.5f, 10.5f, skin, SKIN_SHEEN);
        // The face mass, hanging forward and below it.
        mass(s, p, across, forward + 2.5f, up - 1.5f, 8.2f, 8f, 9f, skin, SKIN_SHEEN);
        // Brow, the furthest-forward thing on a head.
        mass(s, p, across, forward + 5.5f, up + 2.5f, 7.6f, 3f, 1.9f, skin, SKIN_SHEEN);
        // Jaw and chin, tucked under and stopping short of the brow.
        mass(s, p, across, forward + 3f, up - 7f, 6.6f, 6.5f, 4.4f, skin, SKIN_SHEEN);
        // Ears, flat against the skull rather than standing off it.
        for (int side = 0; side < 2; side++) {
            float ex = across + (side == 0 ? -1f : 1f) * 8.4f;
            mass(s, p, ex, forward - 0.5f, up - 1f, 0.9f, 2.6f, 3.4f, skin, SKIN_SHEEN);
        }

        s.weld(false);
        s.flesh(p.x(across, forward, up), p.y(across, forward, up), p.size(16f), 1f);

        if (toward > 0.05f) {
            face(s, p, across, forward, up, skin, toward);
        }
    }

    /**
     * The features, cut into a skull that already exists.
     *
     * <p>Scaled by how much of the man is turned toward the camera, so a face arrives as he comes
     * on and is gone when he walks away — which is correct, free, and stops a man walking north
     * carrying a face on the back of his head.
     *
     * <p>Every cut here is shallow. The first version used depths around a third of the head
     * radius, which does not carve a face, it opens a skull.
     */
    private static void face(Sculptor s, Pose p, float across, float forward, float up,
                             int skin, float toward) {
        // Eye sockets: two shallow hollows under the brow, and nothing else. Every version of
        // this that tried for more - a carved mouth, a defined lip - read as damage rather than
        // as a feature, because at the size a head is actually seen there is no room for it.
        float[] shape = SHAPE.get();
        for (int side = 0; side < 2; side++) {
            float ex = across + (side == 0 ? -1f : 1f) * 3.1f;
            p.solidEllipse(1.7f, 1.0f, 1.1f, shape);
            s.carveEllipse(p.x(ex, forward + 5.4f, up + 0.3f),
                    p.y(ex, forward + 5.4f, up + 0.3f),
                    shape[0], shape[1], shape[2], p.size(0.5f * toward), Form.DOME);
        }

        // Nose: a short ridge off the brow. The one feature with enough relief to survive being
        // shrunk, because it catches the key light rather than holding a shadow.
        s.taper(p.x(across, forward + 5.8f, up + 1.2f), p.y(across, forward + 5.8f, up + 1.2f),
                p.size(0.6f),
                p.x(across, forward + 7f, up - 2.6f), p.y(across, forward + 7f, up - 2.6f),
                p.size(1.2f),
                p.depth(across, forward + 6.5f, up - 0.7f), p.size(1.3f * toward), Form.DOME,
                skin, SKIN_SHEEN);
    }

    // --- headgear ---------------------------------------------------------------------------

    /**
     * The Regime's coal-scuttle: a deep shell with a flare that runs out over the neck.
     *
     * <p>The silhouette is the point. This and {@link #potHelmet} have to be tellable apart in
     * black, at forty pixels, from across a table — the flare here runs longest at the back,
     * where an American pot helmet's brim runs evenly all the way round.
     */
    public static void stahlhelm(Sculptor s, Pose p, float across, float forward, float up,
                                 int albedo) {
        mass(s, p, across, forward - 0.5f, up + 3.5f, 11f, 12.5f, 8f, albedo, Sculptor.PAINT);
        // The skirt: longest astern, which is the neck guard, and shortest over the brow.
        mass(s, p, across, forward - 2.5f, up - 1f, 12f, 14.5f, 2.2f, albedo, Sculptor.PAINT);
        seamRound(s, p, across, forward - 1f, up + 0.5f, 11.5f);
        Machine.bolt(s, p.x(across - 10.5f, forward - 1f, up + 1f),
                p.y(across - 10.5f, forward - 1f, up + 1f), p.size(0.8f),
                p.depth(across - 10.5f, forward - 1f, up + 3f), albedo);
        Machine.bolt(s, p.x(across + 10.5f, forward - 1f, up + 1f),
                p.y(across + 10.5f, forward - 1f, up + 1f), p.size(0.8f),
                p.depth(across + 10.5f, forward - 1f, up + 3f), albedo);
    }

    /**
     * An American pot helmet, salvaged: a shallow dome with an even brim all the way round.
     *
     * <p>The Kreisau Circle is wearing a dead army's kit. This is the shape that says so without
     * a word of text, and it is nothing like the shell above it.
     */
    public static void potHelmet(Sculptor s, Pose p, float across, float forward, float up,
                                 int albedo) {
        mass(s, p, across, forward, up + 2f, 10.2f, 11.5f, 6f, albedo, Sculptor.PAINT);
        // The brim rides at the temples, not at mid-skull. Lower, and the head disappears under
        // a mushroom cap - which is what the first fitting did at every zoom.
        mass(s, p, across, forward + 0.5f, up + 0.5f, 11.6f, 13f, 1.5f, albedo, Sculptor.PAINT);
        seamRound(s, p, across, forward, up + 1.4f, 11f);
    }

    /**
     * A British dish helmet, salvaged: a shallow bowl on a wide flat brim.
     *
     * <p>The third silhouette, and the most distinct of the three from above — which is exactly
     * why it earns a place, since above is where this game is seen from.
     */
    public static void dishHelmet(Sculptor s, Pose p, float across, float forward, float up,
                                  int albedo) {
        mass(s, p, across, forward, up + 1.5f, 9f, 9.5f, 4.5f, albedo, Sculptor.PAINT);
        mass(s, p, across, forward, up - 1f, 15f, 15f, 1.4f, albedo, Sculptor.PAINT);
        seamRound(s, p, across, forward, up - 0.4f, 14f);
    }

    /** A groove running round a helmet where its skirt meets its shell. */
    private static void seamRound(Sculptor s, Pose p, float across, float forward, float up,
                                  float radius) {
        float[] shape = SHAPE.get();
        p.groundEllipse(radius, radius, shape);
        s.carveEllipse(p.x(across, forward, up), p.y(across, forward, up), shape[0], shape[1],
                shape[2], p.size(0.5f), Form.ROUND);
    }

    /**
     * A gas mask: filter, lenses, and the straps holding it on.
     *
     * <p>What the Regime wears instead of a face. The lenses are faintly emissive — not because
     * they glow, but because glass at this size reads as glass only when it is brighter than
     * everything around it.
     */
    public static void gasMask(Sculptor s, Pose p, float across, float forward, float up,
                               int rubber, int lens, int metal) {
        s.weld(true);
        mass(s, p, across, forward + 4f, up - 1f, 8.5f, 6f, 8f, rubber, Sculptor.LEATHER);
        mass(s, p, across, forward + 7f, up - 5f, 5f, 5f, 4f, rubber, Sculptor.LEATHER);
        s.weld(false);

        float[] shape = SHAPE.get();
        for (int side = 0; side < 2; side++) {
            float lx = across + (side == 0 ? -1f : 1f) * 3.4f;
            float ly = forward + 7.5f;
            float lz = up + 1f;
            p.solidEllipse(2.6f, 1.4f, 2.4f, shape);
            s.carveEllipse(p.x(lx, ly, lz), p.y(lx, ly, lz), shape[0] * 1.35f, shape[1] * 1.35f,
                    shape[2], p.size(0.9f), Form.DOME);
            mass(s, p, lx, ly + 0.6f, lz, 2.2f, 1.1f, 2.0f, lens, Sculptor.STEEL);
            s.glow(p.x(lx, ly, lz), p.y(lx, ly, lz), p.size(3.2f), 0.32f);
        }

        // The filter drum, hung below, and the corrugated hose running to it.
        mass(s, p, across + 2f, forward + 6f, up - 11f, 3.2f, 3.2f, 4f, metal, Sculptor.STEEL);
        for (int i = 0; i < 3; i++) {
            s.carveCapsule(p.x(across - 1.5f, forward + 7f, up - 8f + i * 1.4f),
                    p.y(across - 1.5f, forward + 7f, up - 8f + i * 1.4f),
                    p.x(across + 3.5f, forward + 7f, up - 8f + i * 1.4f),
                    p.y(across + 3.5f, forward + 7f, up - 8f + i * 1.4f),
                    p.size(0.5f), p.size(0.4f), Form.ROUND);
        }
        tube(s, p, across - 9f, forward + 1f, up + 2f, across + 9f, forward + 1f, up + 2f,
                0.9f, rubber, Sculptor.LEATHER);
    }

    // --- the body ---------------------------------------------------------------------------

    /**
     * A torso: chest broad at the shoulders, tapering to a waist.
     *
     * <p>Two masses welded rather than one box. A chest is not a waist, and the change of section
     * between them is most of what makes a clothed figure read as a body under clothes.
     */
    public static void torso(Sculptor s, Pose p, float across, float forward, int cloth,
                             int seed) {
        s.weld(true);
        mass(s, p, across, forward, 128f, 21f, 12f, 15f, cloth, Sculptor.CLOTH);
        mass(s, p, across, forward, 108f, 17f, 10.5f, 13f, cloth, Sculptor.CLOTH);
        mass(s, p, across, forward, 94f, 16f, 10f, 8f, cloth, Sculptor.CLOTH);
        s.weld(false);
        s.roughen(p.x(across - 22f, forward, 145f), p.y(across - 22f, forward, 145f),
                p.size(44f), p.size(60f), 0.20f, 4f, seed);
    }

    /**
     * A neck.
     *
     * <p>Small, and the figure does not work without it. With the skull sitting straight on the
     * yoke the two weld into one mass and a soldier reads as hunched — which is what the first
     * full figure looked like, and it took a while to see because the fault is the absence of
     * something rather than the presence of anything.
     */
    public static void neck(Sculptor s, Pose p, float across, float forward, int skin,
                            int collar) {
        s.weld(true);
        mass(s, p, across, forward - 1f, 146f, 5.2f, 5.2f, 6f, skin, SKIN_SHEEN);
        s.weld(false);
        mass(s, p, across, forward - 1f, 141f, 7.6f, 7f, 2.6f, collar, Sculptor.CLOTH);
    }

    /** Shoulders, as a yoke across the top of the chest rather than two balls stuck on it. */
    public static void shoulders(Sculptor s, Pose p, float across, float forward, int cloth) {
        s.weld(true);
        mass(s, p, across, forward, 137f, 25f, 11.5f, 6.5f, cloth, Sculptor.CLOTH);
        mass(s, p, across - 21f, forward, 134f, 7.5f, 8.5f, 8f, cloth, Sculptor.CLOTH);
        mass(s, p, across + 21f, forward, 134f, 7.5f, 8.5f, 8f, cloth, Sculptor.CLOTH);
        s.weld(false);
    }

    /** A greatcoat's skirt: the flare below the belt that makes the Regime silhouette. */
    public static void skirt(Sculptor s, Pose p, float across, float forward, int cloth,
                             int seed) {
        s.weld(true);
        mass(s, p, across, forward, 84f, 17f, 11f, 8f, cloth, Sculptor.CLOTH);
        mass(s, p, across, forward, 68f, 19f, 13f, 10f, cloth, Sculptor.CLOTH);
        mass(s, p, across, forward, 52f, 20f, 14f, 10f, cloth, Sculptor.CLOTH);
        s.weld(false);
        s.roughen(p.x(across - 22f, forward, 92f), p.y(across - 22f, forward, 92f),
                p.size(44f), p.size(50f), 0.26f, 5f, seed);
        // The split up the back, which is what stops a skirt reading as a barrel.
        s.carveCapsule(p.x(across, forward - 9f, 84f), p.y(across, forward - 9f, 84f),
                p.x(across, forward - 10f, 46f), p.y(across, forward - 10f, 46f),
                p.size(0.9f), p.size(1.2f), Form.ROUND);
    }

    /** A leg: thigh, shin and a boot, from the hip down. */
    public static void leg(Sculptor s, Pose p, float across, float stride, int cloth,
                           int leather) {
        tube(s, p, across, stride * 0.3f, 88f, across, stride, 48f, 5.5f, cloth,
                Sculptor.CLOTH);
        tube(s, p, across, stride, 48f, across, stride * 1.2f, 14f, 4.4f, leather,
                Sculptor.LEATHER);
        // The boot: a foot, and a sole standing proud of it.
        mass(s, p, across, stride * 1.2f + 3f, 7f, 4.6f, 8.5f, 6f, leather, Sculptor.LEATHER);
        mass(s, p, across, stride * 1.2f + 3f, 2.5f, 5.0f, 9.5f, 2f, leather, Sculptor.LEATHER);
    }

    /**
     * Arms holding a weapon: one hand forward on the grip, one back on the stock.
     *
     * <p>Asymmetry is most of what separates a soldier from a skittle, and every figure in the
     * reference art stands this way. The forward arm crosses the chest, which also gives the
     * silhouette the diagonal it needs to read as a man carrying something.
     */
    public static void armsAtTheReady(Sculptor s, Pose p, float across, int cloth, int skin) {
        // Right arm back, hand on the small of the stock.
        tube(s, p, across + 12f, -2f, 133f, across + 11f, 6f, 116f, 4.2f, cloth,
                Sculptor.CLOTH);
        tube(s, p, across + 11f, 6f, 116f, across + 4f, 13f, 112f, 3.6f, cloth,
                Sculptor.CLOTH);
        mass(s, p, across + 3f, 14f, 111f, 2.6f, 3.4f, 3f, skin, SKIN_SHEEN);

        // Left arm forward, hand under the fore-end.
        tube(s, p, across - 12f, -1f, 133f, across - 9f, 10f, 120f, 4.2f, cloth,
                Sculptor.CLOTH);
        tube(s, p, across - 9f, 10f, 120f, across - 2f, 20f, 118f, 3.6f, cloth,
                Sculptor.CLOTH);
        mass(s, p, across - 1f, 21f, 118f, 2.6f, 3.4f, 3f, skin, SKIN_SHEEN);
    }

    /** Webbing: a belt round the waist and pouches hung on the front of it. */
    public static void webbing(Sculptor s, Pose p, float across, int strap, int pouch) {
        tube(s, p, across - 10f, 0f, 102f, across + 10f, 0f, 102f, 1.4f, strap,
                Sculptor.LEATHER);
        tube(s, p, across - 9f, 6f, 102f, across + 9f, 6f, 102f, 1.4f, strap,
                Sculptor.LEATHER);
        for (int i = -1; i <= 1; i += 2) {
            mass(s, p, across + i * 6f, 7f, 100f, 3.4f, 2.4f, 3.6f, pouch, Sculptor.LEATHER);
        }
        // Braces over the shoulders, which is what stops a belt looking painted on.
        tube(s, p, across - 7f, 6f, 104f, across - 8f, -2f, 137f, 1.2f, strap,
                Sculptor.LEATHER);
        tube(s, p, across + 7f, 6f, 104f, across + 8f, -2f, 137f, 1.2f, strap,
                Sculptor.LEATHER);
    }
}
