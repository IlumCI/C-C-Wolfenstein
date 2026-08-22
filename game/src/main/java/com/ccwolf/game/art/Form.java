package com.ccwolf.game.art;

/**
 * What shape a stroke stands up in.
 *
 * <p>The whole point of the sculpting engine in one enum. A primitive does not just cover pixels
 * with a colour — it deposits <em>form</em>, and this decides the profile of that form across the
 * stroke. So a capsule drawn {@link #ROUND} is a cylinder, which is what a limb and a gun barrel
 * actually are, and the lighting shades it as one without anybody hand-placing a highlight down
 * its length.
 *
 * <p>Both methods take {@code d}, the distance from the middle of the stroke to its edge as a
 * fraction — 0 at the axis or centre, 1 at the rim.
 *
 * <h2>Why there are two of them</h2>
 *
 * <p>{@link #profile} gives height, which is used for occlusion, contact and cast shadow —
 * things that genuinely want to know how tall the surface is. {@link #slope} gives the
 * <em>surface normal</em> directly, and that is what the lighting actually shades with.
 *
 * <p>They are separate because deriving the normal from the height field by differencing
 * neighbouring pixels — the obvious approach, and the first written here — is wrong at every
 * edge. A silhouette is a cliff, so its gradient is enormous, so its normal lies flat, so every
 * shape in the game gets a blazing white or pitch-black one-pixel rim. Worse, the same operator
 * produces both the good dome shading and the bad edges, so there is no way to keep one without
 * the other. A stroke knows its own normal analytically; asking it is both cheaper and correct.
 */
public enum Form {

    /** A flat plate. Constant height, no turn: the lighting only sees its edges. */
    FLAT {
        @Override
        public float profile(float d) {
            return 1f;
        }

        @Override
        public float slope(float d) {
            return 0f;
        }
    },

    /**
     * A hemisphere. A head, a turret, a rivet, a sandbag.
     *
     * <p>The circular profile is what makes the falloff read as a curved surface rather than as a
     * gradient: the normal turns fastest near the rim, which is exactly where a real sphere's
     * does.
     */
    DOME {
        @Override
        public float profile(float d) {
            return (float) Math.sqrt(Math.max(0f, 1f - d * d));
        }

        @Override
        public float slope(float d) {
            return domeSlope(d);
        }
    },

    /**
     * A cylinder. The same maths as {@link #DOME} and a different word on purpose — on a capsule
     * the distance is measured across the axis rather than from a point, so this is a tube and
     * that is a ball, and a recipe reads better for saying which it meant.
     */
    ROUND {
        @Override
        public float profile(float d) {
            return (float) Math.sqrt(Math.max(0f, 1f - d * d));
        }

        @Override
        public float slope(float d) {
            return domeSlope(d);
        }
    },

    /** A pitched roof or a folded plate: straight sides meeting in a crest. */
    RIDGE {
        @Override
        public float profile(float d) {
            return 1f - d;
        }

        @Override
        public float slope(float d) {
            return 1f;
        }
    },

    /**
     * A flat top with a chamfer round it. Machined steel, poured concrete, a stowage box.
     *
     * <p>Most man-made things are this rather than {@link #FLAT}: the chamfer is a quarter of the
     * way in, which gives the lighting a narrow band of turned surface to catch, and that band is
     * the difference between a box that looks solid and a box that looks like a sticker.
     */
    BEVEL {
        @Override
        public float profile(float d) {
            return Math.min(1f, (1f - d) * 4f);
        }

        @Override
        public float slope(float d) {
            return d < 0.75f ? 0f : 4f;
        }
    },

    /**
     * A hollow — the same curve as {@link #DOME}, inverted.
     *
     * <p>For ground that has been dug rather than built. A trench cut with this gets its near
     * wall dark and its far wall lit from the same pass as everything else, without either being
     * drawn: about a hundred and fifty lines of hand-painted depth cues in the terrain recipes
     * exist only because this did not.
     */
    CONCAVE {
        @Override
        public float profile(float d) {
            return -(float) Math.sqrt(Math.max(0f, 1f - d * d));
        }

        @Override
        public float slope(float d) {
            return -domeSlope(d);
        }
    };

    /**
     * How much of the stroke's height stands at this fraction of the way to its edge.
     *
     * <p>Every profile is continuous and reaches zero at {@code d == 1}, which is what lets two
     * shapes meet without a step where they touch.
     */
    public abstract float profile(float d);

    /**
     * How steeply the surface is turned here, as rise over run, before the stroke's own
     * height-to-radius ratio is applied.
     *
     * <p>Positive means the surface falls away from the middle of the stroke — the outward
     * direction is supplied by the primitive, which is the only thing that knows it.
     */
    public abstract float slope(float d);

    /**
     * The slope of a unit hemisphere, clamped near the rim.
     *
     * <p>{@code d/sqrt(1-d²)} runs to infinity at the edge, which is true of a real sphere and
     * useless in a shading model: it would put a hard black band round every dome. Six is about
     * eighty degrees of turn, past which nothing is gained by turning further.
     */
    private static float domeSlope(float d) {
        float s = (float) Math.sqrt(Math.max(1e-4f, 1f - d * d));
        float slope = d / s;
        return slope > 6f ? 6f : slope;
    }
}
