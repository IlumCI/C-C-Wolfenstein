package com.ccwolf.game.art;

/**
 * The body a drawn footsoldier is made of, seen from directly above.
 *
 * <h2>Why the camera came down flat</h2>
 *
 * <p>This was written twice. The first time it was a table of eight hand-composed three-quarter
 * pictures, because at a fifty-degree camera a man facing north and the same man facing south are
 * two different drawings rather than one drawing turned. That is true, and it is also what makes
 * three-quarter infantry expensive: every facing is authored, every facing can drift from its
 * neighbours, and the whole of it rests on getting a face and a chest to read at a dozen pixels.
 *
 * <p>Flat overhead throws all of that away, and the things it throws away are the things that kept
 * going wrong:
 *
 * <ul>
 *   <li><b>The eight facings become one drawing, rotated.</b> Not an image rotated — the shapes
 *       are placed at turned coordinates and rasterised fresh on the pixel grid for each facing,
 *       so every frame has hard native pixels and none of them can drift from the others.</li>
 *   <li><b>There is no face.</b> From straight above, a head is the crown of a helmet. Three
 *       separate attempts at a face failed at this size and a fourth would have too; the honest
 *       fix is a camera that does not ask for one.</li>
 *   <li><b>What identifies a unit is what the game can actually show.</b> Helmet outline, the
 *       width of the shoulders, the pack, and the length of the weapon sticking out in front. All
 *       four survive at a dozen pixels, which faces and uniform details demonstrably do not.</li>
 * </ul>
 *
 * <h2>The frame</h2>
 *
 * <p>Recipes author in the man's own frame: <b>across</b> is his own right, <b>forward</b> is
 * where he is looking, both in figure units where his shoulders are about twenty across. The
 * figure turns them into grid coordinates. Facing zero is east and they increase clockwise,
 * matching the rest of the game.
 *
 * <p>He is drawn larger than a man really is against the ground, the way every overhead strategy
 * game draws him — at true scale he would be a smudge a fifth of a tile across. What matters is
 * that the whole roster cheats by the same amount.
 */
public final class InkBody {

    private InkBody() {
    }

    public static final int FACINGS = 8;

    /**
     * A figure being drawn: the grid, where he stands on it, how big he is, and which way he
     * looks.
     *
     * <p>The turn lives here rather than in every recipe. A part says where it goes on the man;
     * this says where the man is and which way he is pointed, and there is exactly one place that
     * arithmetic can be got wrong.
     */
    public static final class Figure {
        public final Ink ink;
        private final float centreX;
        private final float centreY;
        private final float unit;
        private final float forwardX;
        private final float forwardY;
        private final float rightX;
        private final float rightY;
        private final float angle;

        /**
         * @param scale grid pixels per figure unit — his shoulders are about twenty units across
         */
        public Figure(Ink ink, int facing, float centreX, float centreY, float scale) {
            this.ink = ink;
            this.centreX = centreX;
            this.centreY = centreY;
            this.unit = scale;
            this.angle = (float) (facing * Math.PI / 4.0);
            this.forwardX = (float) Math.cos(angle);
            this.forwardY = (float) Math.sin(angle);
            // His own right: ninety degrees clockwise of forward, in a frame where y goes down.
            this.rightX = -this.forwardY;
            this.rightY = this.forwardX;
        }

        /** Grid x of a point in the man's frame. */
        public float x(float across, float forward) {
            return centreX + (rightX * across + forwardX * forward) * unit;
        }

        /** Grid y of a point in the man's frame. */
        public float y(float across, float forward) {
            return centreY + (rightY * across + forwardY * forward) * unit;
        }

        /** A length in figure units, on the grid. */
        public float u(float length) {
            return length * unit;
        }

        /**
         * Which way the man is turned, for a body-aligned ellipse.
         *
         * <p>Every ellipse on a person is aligned to him rather than to the screen — shoulders are
         * broad across and shallow forward whichever way he faces — so the rotation is his, and
         * passing zero here is the tell that a sprite was drawn facing east and then moved.
         */
        public float angle() {
            return angle;
        }
    }

    // --- the parts -----------------------------------------------------------------------------

    /**
     * Two boots, poking out from under him.
     *
     * <p>From above, most of a leg is behind the shoulders. What shows is the toe of each boot,
     * and how far apart along his line of march they are is the whole of the walk cycle.
     */
    public static void boots(Figure f, float stride, byte boot) {
        // Far enough fore and aft that a toe clears the shoulders. Tucked under them they were
        // drawn, shaded, outlined and then completely covered - work that cost time and produced
        // no pixels anybody would ever see.
        f.ink.ellipse(f.x(-3.5f, 6.5f + stride), f.y(-3.5f, 6.5f + stride), f.u(2.6f), f.u(4f),
                f.angle(), boot);
        f.ink.ellipse(f.x(3.5f, -6.5f - stride), f.y(3.5f, -6.5f - stride), f.u(2.6f), f.u(4f),
                f.angle(), boot);
    }

    /**
     * The shoulders and the back: one broad, shallow ellipse, and it is most of the sprite.
     *
     * <p>Its proportion is the single strongest faction read from above. A man in a blouse and
     * webbing is a narrow oval; a man in a Regime greatcoat is half again as broad and squarer at
     * the shoulder, and that difference survives all the way down to a black silhouette.
     */
    public static void shoulders(Figure f, float across, float deep, byte cloth) {
        f.ink.ellipse(f.x(0f, 0f), f.y(0f, 0f), f.u(across), f.u(deep), f.angle(), cloth);
    }

    /**
     * An arm, from the shoulder out to a hand held forward.
     *
     * @param side his right is positive
     */
    public static void arm(Figure f, float side, float handAcross, float handForward, byte cloth,
                           byte skin) {
        float shoulderAcross = side * 8f;
        // Thin, and starting inside the shoulder line. Drawn thick they were two lobes hanging
        // off the body and the whole silhouette went from a man to an amoeba - at this size an
        // arm is a two-pixel stroke and anything more is a second torso.
        f.ink.taper(f.x(shoulderAcross, -1f), f.y(shoulderAcross, -1f), f.u(2.6f),
                f.x(handAcross, handForward), f.y(handAcross, handForward), f.u(1.8f), cloth);
        f.ink.ellipse(f.x(handAcross, handForward), f.y(handAcross, handForward), f.u(1.9f),
                f.u(1.9f), f.angle(), skin);
    }

    /**
     * The crown of a plain steel helmet, and the sliver of neck behind it.
     *
     * <p>Sized off a real head rather than off what looks bold: a skull is about two-fifths the
     * width of a pair of shoulders. Drawn at two-thirds it covered the body almost entirely and
     * the man read as a dark disc with an olive rim, which is what the first overhead sheet
     * showed.
     */
    public static void helmet(Figure f, byte shell, byte shellDark, byte glint) {
        f.ink.ellipse(f.x(0f, -1f), f.y(0f, -1f), f.u(4.4f), f.u(4.8f), f.angle(), shellDark);
        f.ink.ellipse(f.x(0f, 0.6f), f.y(0f, 0.6f), f.u(4.2f), f.u(4.2f), f.angle(), shell);
        crown(f, glint);
    }

    /**
     * The glint on the top of a lid, up and to the left where this game's light always comes from.
     *
     * <p>Placed in <em>grid</em> coordinates rather than the man's, which is the one place in this
     * class that is deliberately not body-aligned: the sun does not turn when he does. Getting
     * that wrong is the fault the sculpted path had a whole test for — the sun staying put as a
     * hull turns.
     */
    private static void crown(Figure f, byte glint) {
        f.ink.ellipse(f.x(0f, 0.6f) - f.u(1.4f), f.y(0f, 0.6f) - f.u(1.4f), f.u(1.9f), f.u(1.5f), 0f, glint);
    }

    /**
     * A dish helmet: a wide flat brim with a small dome in the middle of it.
     *
     * <p>From above this is the most distinctive lid of the three by a distance, because overhead
     * is precisely the view a wide brim is widest in. The three helmets were nearly untellable at
     * the old camera; here the difference is the outline itself.
     */
    public static void dishHelmet(Figure f, byte shell, byte shellDark, byte glint) {
        f.ink.ellipse(f.x(0f, 0.3f), f.y(0f, 0.3f), f.u(6.4f), f.u(6.2f), f.angle(), shellDark);
        f.ink.ellipse(f.x(0f, 0.3f), f.y(0f, 0.3f), f.u(3.6f), f.u(3.6f), f.angle(), shell);
        crown(f, glint);
    }

    /** A coal-scuttle: a long skirt over the neck, so it reads as a teardrop pointing back. */
    public static void deepHelmet(Figure f, byte shell, byte shellDark, byte glint) {
        f.ink.ellipse(f.x(0f, -1.6f), f.y(0f, -1.6f), f.u(4.8f), f.u(5.8f), f.angle(), shellDark);
        f.ink.ellipse(f.x(0f, 0.8f), f.y(0f, 0.8f), f.u(4.2f), f.u(4f), f.angle(), shell);
        crown(f, glint);
    }

    /** A soft field cap: small, with a peak in front. For the men who found no helmet. */
    public static void cap(Figure f, byte cloth, byte clothDark) {
        f.ink.ellipse(f.x(0f, 0f), f.y(0f, 0f), f.u(4f), f.u(4f), f.angle(), cloth);
        f.ink.ellipse(f.x(0f, 3.2f), f.y(0f, 3.2f), f.u(3f), f.u(1.6f), f.angle(), clothDark);
    }

    /** A pack square on the back, and the strap over each shoulder that holds it there. */
    public static void pack(Figure f, float across, byte kit, byte kitDark) {
        f.ink.plate(f.x(0f, -4f), f.y(0f, -4f), f.u(across * 2f), f.u(5.5f), f.u(1.5f),
                f.angle(), kit);
        f.ink.line(Math.round(f.x(-across * 0.6f, -6f)), Math.round(f.y(-across * 0.6f, -6f)),
                Math.round(f.x(-across * 0.6f, 5f)), Math.round(f.y(-across * 0.6f, 5f)), kitDark);
        f.ink.line(Math.round(f.x(across * 0.6f, -6f)), Math.round(f.y(across * 0.6f, -6f)),
                Math.round(f.x(across * 0.6f, 5f)), Math.round(f.y(across * 0.6f, 5f)), kitDark);
    }

    /**
     * A belt across the small of the back, and one pouch on it.
     *
     * <p>Two marks, and they earn their pixels the same way the strap does: they break up the flat
     * oval the back otherwise is, and they sit off centre, which quietly says which way is up.
     */
    public static void webbing(Figure f, float across, byte kit, byte kitDark) {
        f.ink.line(Math.round(f.x(-across, -3f)), Math.round(f.y(-across, -3f)),
                Math.round(f.x(across, -3f)), Math.round(f.y(across, -3f)), kit);
        f.ink.plate(f.x(2f, -6f), f.y(2f, -6f), f.u(4f), f.u(3f), f.u(1f), f.angle(), kitDark);
    }

    // --- what he is carrying ---------------------------------------------------------------

    /**
     * A weapon held forward, along his line of sight.
     *
     * <p>Overhead, how far it juts out in front is the clearest thing about a man after the shape
     * of his lid, so the length is what separates a rifle from a submachine gun rather than any
     * detail on either.
     *
     * @param length how far the muzzle reaches in front of him
     * @param bulk how thick it reads
     */
    public static void weapon(Figure f, float length, float bulk, byte metal, byte wood) {
        f.ink.capsule(f.x(1.5f, -3f), f.y(1.5f, -3f), f.x(1.5f, length), f.y(1.5f, length),
                f.u(bulk), metal);
        f.ink.capsule(f.x(1.5f, -3f), f.y(1.5f, -3f), f.x(1.5f, length * 0.3f),
                f.y(1.5f, length * 0.3f), f.u(bulk + 0.6f), wood);
    }

    /** The box magazine of a submachine gun, hanging out to one side. Four pixels, and it names it. */
    public static void magazine(Figure f, byte metal) {
        f.ink.plate(f.x(-2f, 2f), f.y(-2f, 2f), f.u(2.5f), f.u(4f), f.u(0.8f), f.angle(), metal);
    }

    /** A launcher tube on the shoulder: short, fat, and pointing where he is. */
    public static void tube(Figure f, float length, byte metal, byte metalDark) {
        f.ink.capsule(f.x(3f, -length * 0.45f), f.y(3f, -length * 0.45f),
                f.x(3f, length * 0.55f), f.y(3f, length * 0.55f), f.u(2.6f), metal);
        f.ink.ellipse(f.x(3f, length * 0.55f), f.y(3f, length * 0.55f), f.u(2.6f), f.u(2f),
                f.angle(), metalDark);
    }
}
