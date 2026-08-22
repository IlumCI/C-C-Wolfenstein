package com.ccwolf.game.art;

/**
 * Where a point on a unit lands on the screen, seen from a camera up and to the south.
 *
 * <h2>Why recipes stop using screen coordinates</h2>
 *
 * <p>The game is drawn from a high three-quarter view, roughly fifty degrees down, so that the
 * chest of a man, the fall of a greatcoat and the glacis of a tank are all visible — the angle
 * Red Alert 3 uses, and the reason its units read as things rather than as plan views.
 *
 * <p>At that angle a unit's eight facings are genuinely eight different pictures. A man facing
 * north shows his back and his helmet; the same man facing south shows his face, his chest and
 * the weapon across it. That is not a rotation of one image, and hand-placing every part for
 * every facing across eighteen unit types is the thing that makes three-quarter art expensive.
 *
 * <p>So recipes work in the unit's own frame instead — <b>x</b> across, <b>y</b> forward,
 * <b>z</b> up off the ground — and this projects. One recipe, run eight times with eight facings,
 * and the foreshortening, the layering and the eight pictures all come out of the arithmetic.
 *
 * <h2>What it gives back</h2>
 *
 * <p>Three things, and the third is the one that was quietly broken before:
 *
 * <ul>
 *   <li>the screen position, foreshortened correctly along the ground;</li>
 *   <li>the on-screen size of something that many units across;</li>
 *   <li>a <b>depth</b> that is genuinely how near the viewer a point is, rather than a number the
 *       recipe made up. Every {@code base} in the old soldier was hand-tuned, and half the
 *       layering faults in it came from that — a helmet worn as a collar because two invented
 *       numbers were the wrong way round.</li>
 * </ul>
 *
 * <h2>The frame</h2>
 *
 * <p>Facing zero is east, matching the rest of the game, and increases clockwise on screen.
 * Forward is where the unit is looking; right is the unit's own right, which for a man facing
 * east is toward the bottom of the screen. Up is up.
 *
 * <p>Author in whatever unit suits the thing being drawn — centimetres for a man, so a rifleman
 * is a hundred and seventy-five tall and a helmet is fifteen across, which are numbers with
 * meanings rather than fractions of a canvas.
 */
public final class Pose {

    /**
     * How far above the horizon the camera sits.
     *
     * <p>Fifty degrees. Ninety would be plan view, where a greatcoat is a lozenge and a face is
     * invisible; forty and below starts to hide the ground a unit is standing on and makes
     * overlapping units hard to tell apart. Fifty keeps a figure's front visible while a tank
     * still reads as sitting on the map rather than parked against a wall.
     */
    public static final float ELEVATION = (float) Math.toRadians(50.0);

    private static final float SIN_E = (float) Math.sin(ELEVATION);
    private static final float COS_E = (float) Math.cos(ELEVATION);

    private final float forwardX;
    private final float forwardY;
    private final float rightX;
    private final float rightY;
    private final float originX;
    private final float originY;
    private final float scale;

    /**
     * @param facing radians, zero pointing east, increasing clockwise
     * @param originX where the unit's own origin sits on the canvas
     * @param scale canvas pixels per unit of the frame the recipe authors in
     */
    public Pose(float facing, float originX, float originY, float scale) {
        this.forwardX = (float) Math.cos(facing);
        this.forwardY = (float) Math.sin(facing);
        // The unit's right hand, ninety degrees clockwise of forward in a screen-down frame.
        this.rightX = -this.forwardY;
        this.rightY = this.forwardX;
        this.originX = originX;
        this.originY = originY;
        this.scale = scale;
    }

    /** Canvas x of a point in the unit's frame. */
    public float x(float across, float forward, float up) {
        return originX + (rightX * across + forwardX * forward) * scale;
    }

    /**
     * Canvas y of a point in the unit's frame.
     *
     * <p>Ground distance is compressed by the sine of the elevation and height is lifted by its
     * cosine, which is what makes walking away and standing taller both move a point up the
     * screen — and by different amounts, which is the whole of the three-quarter look.
     */
    public float y(float across, float forward, float up) {
        float ground = rightY * across + forwardY * forward;
        return originY + (ground * SIN_E - up * COS_E) * scale;
    }

    /**
     * How near the camera a point is, for the depth test.
     *
     * <p>Nearer means further south on the ground or higher off it, in the proportion the camera
     * angle implies. At ninety degrees this reduces to plain height, which is what a plan view
     * should use, and the same recipes keep working.
     */
    public float depth(float across, float forward, float up) {
        float ground = rightY * across + forwardY * forward;
        return (ground * COS_E + up * SIN_E) * scale;
    }

    /** Canvas pixels for a length in the frame — a radius, a thickness. */
    public float size(float length) {
        return length * scale;
    }

    /**
     * How much a length lying flat on the ground is squashed on screen.
     *
     * <p>For anything genuinely flat: a shadow, a painted marking, the top face of a hatch. A
     * circle drawn on the ground is an ellipse this much shorter than it is wide, and drawing it
     * as a circle is the tell that a sprite was authored in plan and tilted afterwards.
     */
    public float flatten() {
        return SIN_E;
    }

    /**
     * The screen ellipse that a flat ellipse on the ground turns into.
     *
     * <p>Positioning a shape through this class is only half of drawing it: a hull forty-six long
     * and twenty-six wide is a broad ellipse when it faces east and a short one when it faces
     * north, and passing the frame's own radii straight through gives the same upright ellipse in
     * all eight facings — a hull that pivots on the spot without ever turning. That was the first
     * fault the facing sheet showed.
     *
     * <p>The ground-to-screen map is a rotation by the facing followed by a squash in y, so the
     * image of an ellipse is another ellipse and its axes are the singular values of that two by
     * two. Closed form, no iteration.
     *
     * @param out filled with the screen radius across, the screen radius along, and the rotation
     */
    public void groundEllipse(float across, float forward, float[] out) {
        // The ground ellipse as a matrix: columns are its two semi-axes in the unit's frame.
        float m00 = rightX * across;
        float m10 = rightY * across;
        float m01 = forwardX * forward;
        float m11 = forwardY * forward;
        // Then the camera squashes y.
        m10 *= SIN_E;
        m11 *= SIN_E;

        float e = (m00 + m11) * 0.5f;
        float f = (m00 - m11) * 0.5f;
        float g = (m10 + m01) * 0.5f;
        float h = (m10 - m01) * 0.5f;
        float q = (float) Math.sqrt(e * e + h * h);
        float r = (float) Math.sqrt(f * f + g * g);

        // Both singular values are magnitudes: q - r is negative whenever the long axis is the
        // one across rather than the one along, and without the absolute value the minor radius
        // clamps to nothing and the shape disappears entirely - which is what the facing sheet
        // showed the first time, a hull that was simply not there.
        out[0] = Math.abs(q + r) * scale;
        out[1] = Math.max(0.001f, Math.abs(q - r) * scale);
        out[2] = (float) (Math.atan2(h, e) + Math.atan2(g, f)) * 0.5f;
    }

    /**
     * The screen ellipse that a solid ellipsoid turns into.
     *
     * <p>{@link #groundEllipse} handles things lying flat. This handles things with height, which
     * is nearly everything on a person: a skull is wider than it is deep and deeper than it is
     * tall, a chest is broad and shallow, a shoulder is a squashed ball. Each is an ellipsoid,
     * and the outline of an ellipsoid under any linear projection is an ellipse.
     *
     * <p>The projection is two rows by three columns; scaled by the ellipsoid's own radii it
     * becomes a matrix whose image of the unit sphere is the ellipse wanted, and the shape of
     * that ellipse is carried by {@code A·Aᵀ} — a symmetric two by two whose eigenvalues are
     * the squared semi-axes and whose eigenvectors give the tilt. Closed form, no iteration, and it
     * turns every rounded body part into one call that stays correct at all eight facings.
     *
     * @param out filled with the major screen radius, the minor screen radius, and the rotation
     */
    public void solidEllipse(float across, float forward, float up, float[] out) {
        float a00 = rightX * across;
        float a01 = forwardX * forward;
        float a10 = rightY * SIN_E * across;
        float a11 = forwardY * SIN_E * forward;
        float a12 = -COS_E * up;

        float p = a00 * a00 + a01 * a01;
        float q = a00 * a10 + a01 * a11;
        float r = a10 * a10 + a11 * a11 + a12 * a12;

        float mean = (p + r) * 0.5f;
        float diff = (p - r) * 0.5f;
        float spread = (float) Math.sqrt(diff * diff + q * q);
        float major = (float) Math.sqrt(Math.max(0f, mean + spread));
        float minor = (float) Math.sqrt(Math.max(0f, mean - spread));

        out[0] = Math.max(0.001f, major * scale);
        out[1] = Math.max(0.001f, minor * scale);
        out[2] = 0.5f * (float) Math.atan2(2f * q, p - r);
    }

    /**
     * The three semi-axes of a body-frame ellipsoid, expressed in camera space.
     *
     * <p>Column major, each column the image of one body axis as (screen x, screen y, nearness).
     * Handed to {@code Sculptor.ellipsoid}, which uses it to solve the camera ray against the
     * real surface rather than filling the outline with an invented profile.
     */
    public void axes(float wide, float deep, float tall, float[] out9) {
        out9[0] = rightX * wide * scale;
        out9[1] = rightY * SIN_E * wide * scale;
        out9[2] = rightY * COS_E * wide * scale;

        out9[3] = forwardX * deep * scale;
        out9[4] = forwardY * SIN_E * deep * scale;
        out9[5] = forwardY * COS_E * deep * scale;

        out9[6] = 0f;
        out9[7] = -COS_E * tall * scale;
        out9[8] = SIN_E * tall * scale;
    }

    /** How much this unit is facing the camera: 0 turned away, 1 straight at it. */
    public float toward() {
        return Math.max(0f, forwardY);
    }

    /** How much of the unit's own right side is showing, negative when the left side is. */
    public float profile() {
        return forwardX;
    }
}
