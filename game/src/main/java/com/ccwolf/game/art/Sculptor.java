package com.ccwolf.game.art;

/**
 * Draws form rather than colour, and then lights it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link PixelCanvas} draws coloured pixels: every highlight, shadow and rounded edge in the
 * old art is a line somebody placed by hand, in a recipe with no idea what shape it is drawing.
 * {@code panzer()} renders a turret as three concentric ellipses at successively lighter ramp
 * indices — a person doing, badly and once, what a lighting model does correctly and everywhere.
 *
 * <p>Raising the resolution did not fix that and could not: the limit was never how many pixels
 * were available, it was that you cannot draw a shoulder with {@code rect}. So this keeps several
 * registers per sprite instead of one, and every primitive writes all of them.
 *
 * <h2>The registers</h2>
 *
 * <ul>
 *   <li><b>albedo</b> and <b>coverage</b> — the colour, and how much of the pixel it took.</li>
 *   <li><b>depth</b> — how far this surface stands out of the sprite plane, layer offset
 *       included. Depth-tested, not accumulated; see below.</li>
 *   <li><b>normal</b> — written analytically by each stroke, because a stroke knows which way its
 *       own surface faces and differencing the depth field afterwards does not.</li>
 *   <li><b>material</b> — matte cloth through polished steel, driving the specular.</li>
 *   <li><b>emissive</b> — light the surface makes rather than receives, which must survive
 *       shading, occlusion and shadow untouched or every glow in the game dies.</li>
 *   <li><b>step</b> — where one surface cut in front of another. This is what makes a sprite read
 *       as objects stacked on objects rather than as one melted casting.</li>
 * </ul>
 *
 * <h2>How a stroke works</h2>
 *
 * <p>Every primitive is float-coordinate and defined by a signed distance to its own edge:
 * negative inside, zero on it, positive outside. That one function does two jobs — coverage falls
 * out of the distance across the final half-pixel, so edges are anti-aliased without
 * supersampling, and the same distance drives the {@link Form} profile, so a {@link Form#ROUND}
 * capsule knows how far across the tube each pixel sits and stands up as a cylinder.
 *
 * <h2>How strokes combine, and the mistake not to make</h2>
 *
 * <p>Strokes are <b>depth-tested</b>: a fragment wins where it stands in front of what is already
 * there, at {@code base + form × stand}, and {@code base} is the layer the recipe put it on.
 *
 * <p>The obvious alternative — compositing height by maximum into one shared field — was
 * written first and is wrong in a way worth recording. It leaves no boundaries
 * <em>inside</em> a sprite: a rifle laid across a coat becomes a bump <em>on</em> the coat,
 * because the surface ramps up and
 * down continuously and the shading has no reason to think otherwise. Every unit in this game is
 * a stack of small objects — pack over coat, helmet over shoulders, barrel over hull, sandbag
 * over parapet — and a maximum dissolves all of it into one lumpy blob.
 *
 * <p>When a fragment wins by more than a hair, that is recorded in the step register, and the
 * lighting darkens the contact. It is the difference between a tank with things bolted to it and
 * a tank that looks poured.
 */
public final class Sculptor {

    /** How far a pixel's distance spans the transition from covered to not. */
    private static final float EDGE = 0.5f;

    /** A depth win larger than this is a cliff between two surfaces rather than a curve. */
    private static final float STEP = 0.6f;

    private final int width;
    private final int height;

    private final float[] cover;
    private final float[] red;
    private final float[] green;
    private final float[] blue;
    private final float[] depth;
    private final float[] normalX;
    private final float[] normalY;
    private final float[] material;
    private final float[] emissive;
    private final float[] translucent;
    private final float[] step;

    /**
     * The frame recipes are drawn in, as a rotation about the canvas centre.
     *
     * <p>How a vehicle gets eight facings. The old pipeline drew a hull facing east and rotated
     * the finished bitmap, which rotates the sun with it — a tank pointing north-west carried
     * highlights belonging to a tank pointing east, and nobody noticed only because the shading
     * was too faint to read. Re-sculpting each facing in its own frame and lighting afterwards
     * keeps the sun fixed where it belongs, and costs less than the supersampled rotation it
     * replaces because there are no temporary buffers at all.
     *
     * <p>Applied by inverse-transforming each pixel into the recipe's frame rather than by
     * transforming the shape into the canvas — which is what lets a box stay a box. Its distance
     * function assumes axis alignment, and a rotated axis-aligned box is neither.
     */
    private float frameCos = 1f;
    private float frameSin;
    private boolean framed;

    /**
     * Whether strokes merge into what is under them instead of standing in front of it.
     *
     * <p>The depth test is right for a rifle lying on a coat and wrong for a jaw meeting a cheek.
     * A face is one surface: z-testing its parts against each other puts a step down the middle
     * of it, and the step register then draws a contact shadow along that step, so a head
     * assembled from parts comes out looking assembled.
     *
     * <p>The rule, stated so it does not drift: <b>things that were bolted together stack;
     * things that grew together weld.</b>
     */
    private boolean welding;

    /** What has actually been touched, so the lighting can skip the empty majority of a sprite. */
    private int dirtyX0;
    private int dirtyY0;
    private int dirtyX1 = -1;
    private int dirtyY1 = -1;

    public Sculptor(int width, int height) {
        this.width = width;
        this.height = height;
        int n = width * height;
        this.cover = new float[n];
        this.red = new float[n];
        this.green = new float[n];
        this.blue = new float[n];
        this.depth = new float[n];
        this.normalX = new float[n];
        this.normalY = new float[n];
        this.material = new float[n];
        this.emissive = new float[n];
        this.translucent = new float[n];
        this.step = new float[n];
        this.dirtyX0 = width;
        this.dirtyY0 = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Draws everything after this rotated about the canvas centre.
     *
     * <p>Set once, before the recipe runs. Things that must stay upright in screen space
     * whatever the unit is doing — a ground shadow, a faction decal — are drawn with the frame
     * reset to zero.
     */
    /**
     * Turns welding on or off for the strokes that follow. See {@link #welding}.
     *
     * <p>Scoped by hand rather than by a block, because an organic assembly is usually a whole
     * method — a skull, a torso — and wrapping every one of them in a lambda would cost more
     * clarity than the discipline of turning it off again.
     */
    public Sculptor weld(boolean on) {
        this.welding = on;
        return this;
    }

    public Sculptor facing(float radians) {
        frameCos = (float) Math.cos(-radians);
        frameSin = (float) Math.sin(-radians);
        framed = radians != 0f;
        return this;
    }

    /** Where a canvas pixel lands in the recipe's own frame. */
    private float localX(float x, float y) {
        if (!framed) {
            return x;
        }
        float dx = x - width * 0.5f;
        float dy = y - height * 0.5f;
        return width * 0.5f + dx * frameCos - dy * frameSin;
    }

    private float localY(float x, float y) {
        if (!framed) {
            return y;
        }
        float dx = x - width * 0.5f;
        float dy = y - height * 0.5f;
        return height * 0.5f + dx * frameSin + dy * frameCos;
    }

    /**
     * The canvas rectangle a shape drawn in the recipe's frame can possibly touch.
     *
     * <p>The circumscribing square of the rotated box, which over-covers by up to forty per cent
     * on the diagonal. Cheaper than the alternative and the cost is only in pixels visited, not
     * in pixels written — everything outside the shape fails its distance test anyway.
     */
    private int[] frameBounds(float x0, float y0, float x1, float y1) {
        if (!framed) {
            return new int[] {(int) Math.floor(x0), (int) Math.floor(y0),
                    (int) Math.ceil(x1), (int) Math.ceil(y1)};
        }
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float mx = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        float my = Math.max(Math.abs(y0 - cy), Math.abs(y1 - cy));
        float reach = (float) Math.sqrt(mx * mx + my * my) + 2f;
        return new int[] {(int) (cx - reach), (int) (cy - reach),
                (int) Math.ceil(cx + reach), (int) Math.ceil(cy + reach)};
    }

    /** Turns a direction in the recipe's frame back into one in canvas space. */
    private float frameDirX(float ux, float uy) {
        return framed ? ux * frameCos + uy * frameSin : ux;
    }

    private float frameDirY(float ux, float uy) {
        return framed ? -ux * frameSin + uy * frameCos : uy;
    }

    // --- material presets ---------------------------------------------------------------------

    /** Cloth, earth, sandbags: no sheen at all. */
    public static final float CLOTH = 0f;

    /** Leather, timber, rubber: a dull sheen where the light lands square. */
    public static final float LEATHER = 0.18f;

    /** Painted plate, concrete kept clean: broad and soft. */
    public static final float PAINT = 0.35f;

    /** Bare gunmetal, a barrel, a track link: tight and bright. */
    public static final float STEEL = 0.75f;

    // --- primitives -------------------------------------------------------------------------

    /**
     * A disc: a ball when domed, a plate when flat, a dimple when concave.
     *
     * @param base the layer this sits on; higher wins the depth test
     * @param stand how far it stands proud of that layer at its highest
     * @param material one of the presets above
     */
    public Sculptor disc(float cx, float cy, float radius, float base, float stand, Form form,
                         int albedo, float material) {
        if (radius <= 0f) {
            return this;
        }
        float turn = stand / radius;
        int[] box = frameBounds(cx - radius - 1, cy - radius - 1, cx + radius + 1,
                cy + radius + 1);
        for (int y = Math.max(0, box[1]); y <= Math.min(height - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(width - 1, box[2]); x++) {
                float dx = localX(x + 0.5f, y + 0.5f) - cx;
                float dy = localY(x + 0.5f, y + 0.5f) - cy;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float d = distance / radius;
                float ux = distance <= 1e-5f ? 0f : dx / distance;
                float uy = distance <= 1e-5f ? 0f : dy / distance;
                deposit(x, y, distance - radius, d, frameDirX(ux, uy), frameDirY(ux, uy), turn,
                        base, stand, form, albedo, material);
            }
        }
        return this;
    }

    /**
     * An ellipse, at an angle: the shape almost everything organic actually is.
     *
     * <p>The gap that made the first soldiers look like stacked balls. A skull seen from above is
     * an ovoid — widest behind the ears, pinched at the temples, tapering to a jaw. A torso is an
     * ovoid narrowing to a waist. A helmet is an ovoid with a flare. Built out of {@link #disc},
     * every one of them comes out a sphere, and no amount of shading fixes a wrong shape.
     *
     * <p>Rotation matters as much as the two radii, because a jaw is an ovoid <em>at an angle</em>
     * to the skull above it, and so is a shoulder to a chest.
     *
     * <p>An exact signed distance to an ellipse needs iteration; this uses the standard closed
     * form, {@code k1(k1-1)/k2}, which is accurate near the boundary — which is the only place
     * coverage is decided — and degrades gracefully well inside, where the form profile is
     * driven by the normalised radius instead.
     *
     * @param rotation radians, clockwise on screen
     */
    public Sculptor ellipse(float cx, float cy, float rx, float ry, float rotation, float base,
                            float stand, Form form, int albedo, float material) {
        if (rx <= 0f || ry <= 0f) {
            return this;
        }
        float cos = (float) Math.cos(-rotation);
        float sin = (float) Math.sin(-rotation);
        float reach = Math.max(rx, ry);
        float turn = stand / Math.min(rx, ry);
        int[] bounds = frameBounds(cx - reach - 1, cy - reach - 1, cx + reach + 1, cy + reach + 1);

        for (int y = Math.max(0, bounds[1]); y <= Math.min(height - 1, bounds[3]); y++) {
            for (int x = Math.max(0, bounds[0]); x <= Math.min(width - 1, bounds[2]); x++) {
                float px = localX(x + 0.5f, y + 0.5f) - cx;
                float py = localY(x + 0.5f, y + 0.5f) - cy;
                // Into the ellipse's own frame, where it is axis-aligned.
                float ex = px * cos - py * sin;
                float ey = px * sin + py * cos;

                float ax = ex / rx;
                float ay = ey / ry;
                float k1 = (float) Math.sqrt(ax * ax + ay * ay);
                if (k1 > 2.5f) {
                    continue;
                }
                float bx = ex / (rx * rx);
                float by = ey / (ry * ry);
                float k2 = (float) Math.sqrt(bx * bx + by * by);
                float distance = k2 <= 1e-6f ? -Math.min(rx, ry) : k1 * (k1 - 1f) / k2;

                // The outward normal of an ellipse points along the gradient, not along the
                // radius - which is the whole difference between an egg and a stretched ball.
                float ux = 0f;
                float uy = 0f;
                if (k2 > 1e-6f) {
                    float gx = bx / k2;
                    float gy = by / k2;
                    ux = gx * cos + gy * sin;
                    uy = -gx * sin + gy * cos;
                }
                deposit(x, y, distance, Math.min(1f, k1), ux, uy, turn, base, stand, form,
                        albedo, material);
            }
        }
        return this;
    }

    /** An elliptical hollow: an eye socket, a dished helmet, a cheek. */
    public Sculptor carveEllipse(float cx, float cy, float rx, float ry, float rotation,
                                 float depth, Form form) {
        if (rx <= 0f || ry <= 0f) {
            return this;
        }
        float cos = (float) Math.cos(-rotation);
        float sin = (float) Math.sin(-rotation);
        float reach = Math.max(rx, ry);
        float turn = depth / Math.min(rx, ry);
        int[] bounds = frameBounds(cx - reach - 1, cy - reach - 1, cx + reach + 1, cy + reach + 1);

        for (int y = Math.max(0, bounds[1]); y <= Math.min(height - 1, bounds[3]); y++) {
            for (int x = Math.max(0, bounds[0]); x <= Math.min(width - 1, bounds[2]); x++) {
                float px = localX(x + 0.5f, y + 0.5f) - cx;
                float py = localY(x + 0.5f, y + 0.5f) - cy;
                float ex = px * cos - py * sin;
                float ey = px * sin + py * cos;
                float ax = ex / rx;
                float ay = ey / ry;
                float k1 = (float) Math.sqrt(ax * ax + ay * ay);
                if (k1 >= 1f) {
                    continue;
                }
                float bx = ex / (rx * rx);
                float by = ey / (ry * ry);
                float k2 = (float) Math.sqrt(bx * bx + by * by);
                float ux = 0f;
                float uy = 0f;
                if (k2 > 1e-6f) {
                    float gx = bx / k2;
                    float gy = by / k2;
                    ux = gx * cos + gy * sin;
                    uy = -gx * sin + gy * cos;
                }
                cut(x, y, k1, ux, uy, turn, depth, form);
            }
        }
        return this;
    }

    /**
     * A capsule: a round-ended thick line, and a cylinder when {@link Form#ROUND}.
     *
     * <p>The workhorse. Arms, legs, gun barrels, pipes, cables, roof beams — anything longer than
     * it is wide and round in section is one of these, and gets lit as one.
     */
    public Sculptor capsule(float x0, float y0, float x1, float y1, float radius, float base,
                            float stand, Form form, int albedo, float material) {
        return taper(x0, y0, radius, x1, y1, radius, base, stand, form, albedo, material);
    }

    /** A capsule whose radius changes along its length: a cone, a tapered limb, a muzzle brake. */
    public Sculptor taper(float x0, float y0, float r0, float x1, float y1, float r1,
                          float base, float stand, Form form, int albedo, float material) {
        float maxRadius = Math.max(r0, r1);
        if (maxRadius <= 0f) {
            return this;
        }
        int[] box = frameBounds(Math.min(x0, x1) - maxRadius - 1, Math.min(y0, y1) - maxRadius - 1,
                Math.max(x0, x1) + maxRadius + 1, Math.max(y0, y1) + maxRadius + 1);

        float ax = x1 - x0;
        float ay = y1 - y0;
        float lengthSquared = ax * ax + ay * ay;

        for (int y = Math.max(0, box[1]); y <= Math.min(height - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(width - 1, box[2]); x++) {
                float px = localX(x + 0.5f, y + 0.5f) - x0;
                float py = localY(x + 0.5f, y + 0.5f) - y0;
                // Where along the axis this pixel projects, clamped so the caps are round
                // rather than the line running on forever.
                float t = lengthSquared <= 0f ? 0f : (px * ax + py * ay) / lengthSquared;
                t = t < 0f ? 0f : (t > 1f ? 1f : t);
                float dx = px - ax * t;
                float dy = py - ay * t;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float radius = r0 + (r1 - r0) * t;
                if (radius <= 0f) {
                    continue;
                }
                float ux = distance <= 1e-5f ? 0f : dx / distance;
                float uy = distance <= 1e-5f ? 0f : dy / distance;
                deposit(x, y, distance - radius, distance / radius, frameDirX(ux, uy),
                        frameDirY(ux, uy), stand / radius, base, stand, form, albedo, material);
            }
        }
        return this;
    }

    /**
     * A rounded box. With {@link Form#BEVEL} this is the shape most man-made things actually are.
     *
     * @param corner corner radius; zero for a hard rectangle
     */
    public Sculptor box(float x, float y, float w, float h, float corner, float base, float stand,
                        Form form, int albedo, float material) {
        if (w <= 0f || h <= 0f) {
            return this;
        }
        float halfW = w / 2f;
        float halfH = h / 2f;
        float cx = x + halfW;
        float cy = y + halfH;
        float r = Math.min(corner, Math.min(halfW, halfH));
        float reach = Math.min(halfW, halfH);
        float turn = reach <= 0f ? 0f : stand / reach;

        int[] bounds = frameBounds(x - 1, y - 1, x + w + 1, y + h + 1);
        for (int py = Math.max(0, bounds[1]); py <= Math.min(height - 1, bounds[3]); py++) {
            for (int px = Math.max(0, bounds[0]); px <= Math.min(width - 1, bounds[2]); px++) {
                float sx = localX(px + 0.5f, py + 0.5f) - cx;
                float sy = localY(px + 0.5f, py + 0.5f) - cy;
                float dx = Math.abs(sx) - (halfW - r);
                float dy = Math.abs(sy) - (halfH - r);
                float outX = Math.max(dx, 0f);
                float outY = Math.max(dy, 0f);
                float outside = (float) Math.sqrt(outX * outX + outY * outY);
                float distance = outside + Math.min(Math.max(dx, dy), 0f) - r;
                float d = reach <= 0f ? 1f : 1f + distance / reach;
                d = d < 0f ? 0f : (d > 1f ? 1f : d);
                // The chamfer faces whichever side is nearest, which for a box is whichever of
                // dx and dy is larger — the same test that decided the distance.
                float ux;
                float uy;
                if (dx > dy) {
                    ux = sx < 0f ? -1f : 1f;
                    uy = 0f;
                } else {
                    ux = 0f;
                    uy = sy < 0f ? -1f : 1f;
                }
                deposit(px, py, distance, d, frameDirX(ux, uy), frameDirY(ux, uy), turn, base,
                        stand, form, albedo, material);
            }
        }
        return this;
    }

    /**
     * An arbitrary closed polygon: sloped plates, roof pitches, anything with a straight cut.
     *
     * <p>Coverage is sampled on a four-by-four grid rather than derived from a distance, because a
     * general polygon has no cheap signed distance. Worth reaching for only when a shape genuinely
     * is not a box or a capsule — those two do most of the work, and do it faster.
     *
     * @param xy alternating x and y, at least three points
     */
    public Sculptor polygon(float[] xy, float base, float stand, Form form, int albedo,
                            float material) {
        if (xy.length < 6) {
            return this;
        }
        float lox = xy[0];
        float hix = xy[0];
        float loy = xy[1];
        float hiy = xy[1];
        for (int i = 0; i < xy.length; i += 2) {
            lox = Math.min(lox, xy[i]);
            hix = Math.max(hix, xy[i]);
            loy = Math.min(loy, xy[i + 1]);
            hiy = Math.max(hiy, xy[i + 1]);
        }
        float reach = Math.max(1f, Math.min(hix - lox, hiy - loy) / 2f);
        float turn = stand / reach;

        int[] box = frameBounds(lox - 1, loy - 1, hix + 1, hiy + 1);
        for (int y = Math.max(0, box[1]); y <= Math.min(height - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(width - 1, box[2]); x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float px = x + (sx + 0.5f) / 4f;
                        float py = y + (sy + 0.5f) / 4f;
                        if (inside(xy, localX(px, py), localY(px, py))) {
                            hits++;
                        }
                    }
                }
                if (hits == 0) {
                    continue;
                }
                float[] near = nearestEdge(xy, localX(x + 0.5f, y + 0.5f),
                        localY(x + 0.5f, y + 0.5f));
                float d = 1f - Math.min(1f, near[0] / reach);
                place(x, y, hits / 16f, d, frameDirX(near[1], near[2]),
                        frameDirY(near[1], near[2]), turn, base, stand, form, albedo, material);
            }
        }
        return this;
    }

    /**
     * Cuts a groove into whatever is already there, without repainting it.
     *
     * <p>The counterpart to every primitive above, and the engine cannot make anything look
     * manufactured without it. A stroke wins its pixels by standing <em>in front</em> of what is
     * behind it — which is right for a rivet on a plate and useless for a panel line, a hatch
     * seam, a bolt recess or a louvre, because all of those are places where the surface goes
     * <em>away</em> from the viewer. Every one of them is the difference between a hull and a
     * lozenge.
     *
     * <p>Colour is deliberately left alone. A panel line is the same steel as the panel; it reads
     * as a line because the light cannot get into it, and painting it dark instead is the thing
     * that makes hand-drawn mechanical art look like a sticker of a tank.
     *
     * @param depth how far below the existing surface the bottom of the cut sits
     */
    public Sculptor carveCapsule(float x0, float y0, float x1, float y1, float radius,
                                 float depth, Form form) {
        return carveTaper(x0, y0, radius, x1, y1, radius, depth, form);
    }

    /** A groove whose width changes along its length. */
    public Sculptor carveTaper(float x0, float y0, float r0, float x1, float y1, float r1,
                               float depth, Form form) {
        float maxRadius = Math.max(r0, r1);
        if (maxRadius <= 0f) {
            return this;
        }
        float ax = x1 - x0;
        float ay = y1 - y0;
        float lengthSquared = ax * ax + ay * ay;

        int[] box = frameBounds(Math.min(x0, x1) - maxRadius - 1, Math.min(y0, y1) - maxRadius - 1,
                Math.max(x0, x1) + maxRadius + 1, Math.max(y0, y1) + maxRadius + 1);
        for (int y = Math.max(0, box[1]); y <= Math.min(height - 1, box[3]); y++) {
            for (int x = Math.max(0, box[0]); x <= Math.min(width - 1, box[2]); x++) {
                float px = localX(x + 0.5f, y + 0.5f) - x0;
                float py = localY(x + 0.5f, y + 0.5f) - y0;
                float t = lengthSquared <= 0f ? 0f : (px * ax + py * ay) / lengthSquared;
                t = t < 0f ? 0f : (t > 1f ? 1f : t);
                float dx = px - ax * t;
                float dy = py - ay * t;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float radius = r0 + (r1 - r0) * t;
                if (radius <= 0f || distance > radius) {
                    continue;
                }
                float d = distance / radius;
                float ux = distance <= 1e-5f ? 0f : dx / distance;
                float uy = distance <= 1e-5f ? 0f : dy / distance;
                cut(x, y, d, frameDirX(ux, uy), frameDirY(ux, uy), depth / radius, depth, form);
            }
        }
        return this;
    }

    /** A rectangular recess: a hatch seam, a louvre, a sunken plate. */
    public Sculptor carveBox(float x, float y, float w, float h, float corner, float depth,
                             Form form) {
        if (w <= 0f || h <= 0f) {
            return this;
        }
        float halfW = w / 2f;
        float halfH = h / 2f;
        float cx = x + halfW;
        float cy = y + halfH;
        float r = Math.min(corner, Math.min(halfW, halfH));
        float reach = Math.min(halfW, halfH);

        int[] bounds = frameBounds(x - 1, y - 1, x + w + 1, y + h + 1);
        for (int py = Math.max(0, bounds[1]); py <= Math.min(height - 1, bounds[3]); py++) {
            for (int px = Math.max(0, bounds[0]); px <= Math.min(width - 1, bounds[2]); px++) {
                float sx = localX(px + 0.5f, py + 0.5f) - cx;
                float sy = localY(px + 0.5f, py + 0.5f) - cy;
                float dx = Math.abs(sx) - (halfW - r);
                float dy = Math.abs(sy) - (halfH - r);
                float outX = Math.max(dx, 0f);
                float outY = Math.max(dy, 0f);
                float outside = (float) Math.sqrt(outX * outX + outY * outY);
                float distance = outside + Math.min(Math.max(dx, dy), 0f) - r;
                if (distance > 0f) {
                    continue;
                }
                float d = reach <= 0f ? 1f : 1f + distance / reach;
                d = d < 0f ? 0f : (d > 1f ? 1f : d);
                float ux;
                float uy;
                if (dx > dy) {
                    ux = sx < 0f ? -1f : 1f;
                    uy = 0f;
                } else {
                    ux = 0f;
                    uy = sy < 0f ? -1f : 1f;
                }
                cut(px, py, d, frameDirX(ux, uy), frameDirY(ux, uy),
                        reach <= 0f ? 0f : depth / reach, depth, form);
            }
        }
        return this;
    }

    /**
     * Lowers one pixel and turns its normal into the cut.
     *
     * <p>Sign is the whole subtlety. The walls of a groove face <em>inward</em>, which is the
     * opposite of a stroke standing proud, so the outward direction is negated — get this wrong
     * and every panel line lights up as a raised weld bead, which is a mistake that looks
     * plausible enough to survive a long time.
     */
    private void cut(int x, int y, float d, float ux, float uy, float turn, float sinkBy,
                     Form form) {
        int index = y * width + x;
        if (cover[index] <= 0.02f) {
            return;
        }
        // Deepest down the middle of the cut and shallowing to nothing at its lip, which is the
        // profile the right way up. Inverted, a groove is a pair of trenches with a ridge down
        // the centre of it, and it reads as a weld bead rather than a panel line.
        float sink = sinkBy * Math.abs(form.profile(d));
        depth[index] -= sink;
        float slope = form.slope(d) * turn;
        normalX[index] = -ux * slope;
        normalY[index] = -uy * slope;
        step[index] = Math.max(step[index], Math.min(1f, sink * 0.5f));
    }

    /**
     * Roughens a region's surface without touching its colour.
     *
     * <p>This is the mechanism for texture, and it is not the same thing as speckle. Cast
     * concrete, churned earth and rusted plate are not mottled paint — they are uneven surfaces,
     * and the difference is that an uneven surface catches the light differently across itself.
     * Perturbing colour looks like dirt; perturbing the surface looks like a material.
     *
     * @param grain how many pixels across one bump is
     */
    public Sculptor roughen(float x, float y, float w, float h, float amount, float grain,
                            int seed) {
        float step = Math.max(1f, grain);
        for (int py = Math.max(0, (int) y); py < Math.min(height, (int) (y + h)); py++) {
            for (int px = Math.max(0, (int) x); px < Math.min(width, (int) (x + w)); px++) {
                int index = py * width + px;
                if (cover[index] <= 0f) {
                    continue;
                }
                float gx = px / step;
                float gy = py / step;
                // Tilt the surface rather than displace it: the normal is what gets shaded, so
                // perturbing it directly is both cheaper and more controllable than bumping the
                // depth and hoping the gradient picks it up.
                //
                // Smoothly interpolated between grain cells, not stepped. Sampling the hash per
                // cell gives hard little squares, and at any strength worth having that reads as
                // television static rather than as a surface - which is exactly what the first
                // test card showed.
                normalX[index] += amount * (smoothNoise(gx, gy, seed) - 0.5f);
                normalY[index] += amount * (smoothNoise(gx, gy, seed + 977) - 0.5f);
                depth[index] += amount * 0.35f * (smoothNoise(gx, gy, seed + 31) - 0.5f);
            }
        }
        return this;
    }

    /** Paints colour over whatever is already covered, leaving the form alone. */
    public Sculptor stain(float x, float y, float w, float h, int albedo, float amount) {
        float ar = ((albedo >> 16) & 0xFF) / 255f;
        float ag = ((albedo >> 8) & 0xFF) / 255f;
        float ab = (albedo & 0xFF) / 255f;
        for (int py = Math.max(0, (int) y); py < Math.min(height, (int) (y + h)); py++) {
            for (int px = Math.max(0, (int) x); px < Math.min(width, (int) (x + w)); px++) {
                int index = py * width + px;
                if (cover[index] <= 0f) {
                    continue;
                }
                red[index] += (ar - red[index]) * amount;
                green[index] += (ag - green[index]) * amount;
                blue[index] += (ab - blue[index]) * amount;
            }
        }
        return this;
    }

    /**
     * Marks a region as flesh: light goes into it and comes back out warm.
     *
     * <p>Everything else in this engine reflects. Skin does not — a good deal of the light
     * landing on a face enters it, scatters through blood and comes back out somewhere near
     * where it went in, which is why the shaded side of a cheek is reddish and the shaded side
     * of a helmet is blue-grey. Shade skin with the same model as painted steel and you get a
     * mannequin: the value is right and it is unmistakably not alive.
     *
     * <p>So the lighting adds a warm term that is <em>strongest where the key is weakest</em>,
     * which is the opposite of every other term in the model and is the whole trick. It also
     * rides on how thin the flesh is, and thin is where light gets through: an ear, the ridge of
     * a nose, the rim of a jaw. Approximated here by the surface turning away from the viewer,
     * which at this scale is close enough and costs nothing.
     */
    public Sculptor flesh(float cx, float cy, float radius, float amount) {
        for (int py = Math.max(0, (int) (cy - radius)); py <= Math.min(height - 1,
                (int) (cy + radius)); py++) {
            for (int px = Math.max(0, (int) (cx - radius)); px <= Math.min(width - 1,
                    (int) (cx + radius)); px++) {
                int index = py * width + px;
                if (cover[index] <= 0f) {
                    continue;
                }
                float dx = px + 0.5f - cx;
                float dy = py + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / radius;
                if (d >= 1f) {
                    continue;
                }
                translucent[index] = Math.max(translucent[index], amount * (1f - d * d));
            }
        }
        return this;
    }

    /**
     * Marks a region as making its own light.
     *
     * <p>Emissive survives the lighting untouched — not shaded, not occluded, not shadowed —
     * because a uranium seam or the throat of a resonance chamber is a source rather than a
     * surface. Without this every glow in the game gets shaded like painted metal and dies.
     */
    public Sculptor glow(float cx, float cy, float radius, float strength) {
        for (int py = Math.max(0, (int) (cy - radius)); py <= Math.min(height - 1,
                (int) (cy + radius)); py++) {
            for (int px = Math.max(0, (int) (cx - radius)); px <= Math.min(width - 1,
                    (int) (cx + radius)); px++) {
                int index = py * width + px;
                if (cover[index] <= 0f) {
                    continue;
                }
                float dx = px + 0.5f - cx;
                float dy = py + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / radius;
                if (d >= 1f) {
                    continue;
                }
                float falloff = (1f - d) * (1f - d);
                emissive[index] = Math.max(emissive[index], strength * falloff);
            }
        }
        return this;
    }

    // --- deposition -------------------------------------------------------------------------

    private void deposit(int x, int y, float distance, float d, float ux, float uy, float turn,
                         float base, float stand, Form form, int albedo, float material) {
        if (distance >= EDGE) {
            return;
        }
        float coverage = distance <= -EDGE ? 1f : (EDGE - distance);
        place(x, y, coverage, d, ux, uy, turn, base, stand, form, albedo, material);
    }

    private void place(int x, int y, float coverage, float d, float ux, float uy, float turn,
                       float base, float stand, Form form, int albedo, float material) {
        if (coverage <= 0f) {
            return;
        }
        if (coverage > 1f) {
            coverage = 1f;
        }
        int index = y * width + x;

        float standing = base + stand * form.profile(d);
        float behind = depth[index];
        boolean covered = cover[index] > 0.5f;
        if (welding) {
            // Merged, not stacked: the taller of the two surfaces, and no step recorded, so
            // nothing draws a contact shadow along a join that is not a join.
            standing = covered ? Math.max(standing, behind) : standing;
        } else {
            // A stroke only takes a pixel it stands in front of. On empty ground anything wins.
            if (covered && standing < behind) {
                return;
            }
            if (covered && standing - behind > STEP) {
                step[index] = Math.max(step[index],
                        Math.min(1f, (standing - behind) / STEP - 1f));
            }
        }

        float ar = ((albedo >> 16) & 0xFF) / 255f;
        float ag = ((albedo >> 8) & 0xFF) / 255f;
        float ab = (albedo & 0xFF) / 255f;

        red[index] += (ar - red[index]) * coverage;
        green[index] += (ag - green[index]) * coverage;
        blue[index] += (ab - blue[index]) * coverage;
        this.material[index] += (material - this.material[index]) * coverage;

        depth[index] = standing;
        float slope = form.slope(d) * turn;
        normalX[index] = ux * slope;
        normalY[index] = uy * slope;

        float c = cover[index];
        cover[index] = c + (1f - c) * coverage;

        if (x < dirtyX0) {
            dirtyX0 = x;
        }
        if (y < dirtyY0) {
            dirtyY0 = y;
        }
        if (x > dirtyX1) {
            dirtyX1 = x;
        }
        if (y > dirtyY1) {
            dirtyY1 = y;
        }
    }

    // --- lighting ---------------------------------------------------------------------------

    /**
     * Turns the registers into a finished sprite.
     *
     * <p>Per covered pixel: the analytic normal, a Lambert term against the key, hemisphere
     * ambient mixed by which way the face turns, a warm bounce from below so undersides do not go
     * to flat black, occlusion from the depth around it, a specular whose tightness comes from
     * the material register, a cast shadow, a cold rim on the turned-away side, and emissive
     * added last so nothing above can darken it.
     *
     * <p>Only the touched rectangle is walked, and empty pixels inside it are skipped, which on a
     * typical unit sprite is most of them.
     */
    public PixelCanvas light(Light light) {
        PixelCanvas out = new PixelCanvas(width, height);
        int[] pixels = out.pixels();
        if (dirtyX1 < dirtyX0) {
            return out;
        }

        float keyR = ((light.keyColor >> 16) & 0xFF) / 255f * light.keyStrength;
        float keyG = ((light.keyColor >> 8) & 0xFF) / 255f * light.keyStrength;
        float keyB = (light.keyColor & 0xFF) / 255f * light.keyStrength;
        float skyR = ((light.skyColor >> 16) & 0xFF) / 255f * light.ambientStrength;
        float skyG = ((light.skyColor >> 8) & 0xFF) / 255f * light.ambientStrength;
        float skyB = (light.skyColor & 0xFF) / 255f * light.ambientStrength;
        float gndR = ((light.groundColor >> 16) & 0xFF) / 255f * light.ambientStrength;
        float gndG = ((light.groundColor >> 8) & 0xFF) / 255f * light.ambientStrength;
        float gndB = (light.groundColor & 0xFF) / 255f * light.ambientStrength;
        float bounceR = ((light.bounceColor >> 16) & 0xFF) / 255f * light.bounce;
        float bounceG = ((light.bounceColor >> 8) & 0xFF) / 255f * light.bounce;
        float bounceB = (light.bounceColor & 0xFF) / 255f * light.bounce;
        float rimR = ((light.rimColor >> 16) & 0xFF) / 255f;
        float rimG = ((light.rimColor >> 8) & 0xFF) / 255f;
        float rimB = (light.rimColor & 0xFF) / 255f;

        // Half-vector for the specular. The viewer is straight overhead: this is a top-down game.
        float hx = light.dirX;
        float hy = light.dirY;
        float hz = light.dirZ + 1f;
        float hLen = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hLen;
        hy /= hLen;
        hz /= hLen;

        for (int y = dirtyY0; y <= dirtyY1; y++) {
            for (int x = dirtyX0; x <= dirtyX1; x++) {
                int index = y * width + x;
                float coverage = cover[index];
                if (coverage <= 0.002f) {
                    continue;
                }

                float nx = normalX[index] * light.relief;
                float ny = normalY[index] * light.relief;
                // Flatten the normal toward straight-up as coverage falls. At an anti-aliased
                // silhouette the true normal is grazing, so a north-west key leaves the whole
                // south-east fringe near black and the edge reads as grime rather than as a soft
                // edge. This is the difference between the two.
                if (coverage < 1f) {
                    nx *= coverage;
                    ny *= coverage;
                }
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + 1f);
                nx /= nLen;
                ny /= nLen;
                float nz = 1f / nLen;

                float lambert = nx * light.dirX + ny * light.dirY + nz * light.dirZ;
                if (lambert < 0f) {
                    lambert = 0f;
                }

                float ao = occlusion(x, y, index, light.occlusion);
                float shade = castShadow(x, y, index, light);

                float up = nz * 0.5f + 0.5f;
                float ambR = gndR + (skyR - gndR) * up;
                float ambG = gndG + (skyG - gndG) * up;
                float ambB = gndB + (skyB - gndB) * up;

                // Bounce comes up from the south-east: the ground the figure is standing on.
                float bounce = -(nx * light.dirX + ny * light.dirY) * 0.5f + 0.35f;
                if (bounce < 0f) {
                    bounce = 0f;
                }

                float mat = material[index];
                float specular = 0f;
                if (mat > 0.01f) {
                    float nh = nx * hx + ny * hy + nz * hz;
                    if (nh > 0f) {
                        specular = sharpen(nh, mat) * mat * shade;
                    }
                }

                float turned = 1f - nz;
                float rim = turned * turned * turned * (1f - lambert) * light.rim;

                float lit = lambert * shade;
                float r = red[index] * (ambR * ao + bounceR * bounce + keyR * lit)
                        + keyR * specular + rimR * rim;
                float g = green[index] * (ambG * ao + bounceG * bounce + keyG * lit)
                        + keyG * specular + rimG * rim;
                float b = blue[index] * (ambB * ao + bounceB * bounce + keyB * lit)
                        + keyB * specular + rimB * rim;

                // Subsurface: warm light coming back out of flesh, strongest where the key is
                // weakest and where the surface has turned away. Added before emissive and
                // after everything that could darken it, because scattered light does not
                // care about the occlusion of the surface it leaves through.
                float skin = translucent[index];
                if (skin > 0f) {
                    float through = (1f - lambert) * (0.35f + 0.65f * (1f - nz));
                    float warm = skin * through * light.subsurface;
                    r += red[index] * warm * 0.85f;
                    g += green[index] * warm * 0.34f;
                    b += blue[index] * warm * 0.26f;
                }

                float glow = emissive[index];
                if (glow > 0f) {
                    r += red[index] * glow * 2.2f;
                    g += green[index] * glow * 2.2f;
                    b += blue[index] * glow * 2.2f;
                }

                pixels[index] = (clamp255(coverage) << 24) | (clamp255(r) << 16)
                        | (clamp255(g) << 8) | clamp255(b);
            }
        }
        return out;
    }

    /**
     * How boxed-in a pixel is, from how much of its surroundings stands above it.
     *
     * <p>Two rings of eight, plus whatever the step register recorded — the crease where an arm
     * meets a body is a depth cliff, and the step map already knows about it from the moment the
     * arm was drawn, so the rings do not have to find it.
     */
    private float occlusion(int x, int y, int index, float strength) {
        float here = depth[index];
        float blocked = step[index] * 4f;
        for (int ring = 1; ring <= 2; ring++) {
            int r = ring * 2;
            blocked += above(x + r, y, here) + above(x - r, y, here)
                    + above(x, y + r, here) + above(x, y - r, here)
                    + above(x + r, y + r, here) + above(x - r, y - r, here)
                    + above(x + r, y - r, here) + above(x - r, y + r, here);
        }
        float ao = 1f - strength * blocked / 16f;
        return ao < 0f ? 0f : ao;
    }

    private float above(int x, int y, float here) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0f;
        }
        int index = y * width + x;
        if (cover[index] <= 0f) {
            return 0f;
        }
        float difference = depth[index] - here;
        if (difference <= 0f) {
            return 0f;
        }
        return difference > 2f ? 1f : difference / 2f;
    }

    /**
     * Marches toward the light looking for anything standing between this pixel and it.
     *
     * <p>The ray climbs as it goes, at the light's own slope, so a surface rising no faster than
     * the light is lit. That is a shadow test rather than an is-anything-higher test, and the
     * difference is a tank that shadows its own hull correctly instead of one covered in blotches.
     */
    private float castShadow(int x, int y, int index, Light light) {
        float here = depth[index];
        float slope = light.dirZ / (float) Math.sqrt(light.dirX * light.dirX
                + light.dirY * light.dirY + 1e-6f);
        float deepest = 0f;
        for (int stepIndex = 1; stepIndex <= light.shadowSteps; stepIndex++) {
            int sx = Math.round(x + light.dirX * stepIndex);
            int sy = Math.round(y + light.dirY * stepIndex);
            if (sx < 0 || sy < 0 || sx >= width || sy >= height) {
                break;
            }
            int at = sy * width + sx;
            if (cover[at] <= 0f) {
                continue;
            }
            float over = depth[at] - (here + slope * stepIndex);
            if (over > deepest) {
                deepest = over;
            }
        }
        if (deepest <= 0f) {
            return 1f;
        }
        float amount = deepest > 3f ? 1f : deepest / 3f;
        return 1f - light.shadow * amount;
    }

    /**
     * The specular falloff, by repeated squaring rather than {@code Math.pow}.
     *
     * <p>Five multiplies against a transcendental called once per lit pixel of every sprite in
     * the atlas. The exponent is a power of two by construction, which is a constraint nobody
     * will ever notice in a highlight and is worth a third of the lighting cost.
     */
    private static float sharpen(float nh, float material) {
        float v = nh * nh;
        v *= v;
        v *= v;
        if (material > 0.3f) {
            v *= v;
        }
        if (material > 0.6f) {
            v *= v;
        }
        return v;
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /**
     * Value noise interpolated between lattice points, so a rough surface reads as one.
     *
     * <p>Smoothstep rather than linear on each axis: linear interpolation leaves visible creases
     * along the lattice, which at grazing light is precisely where the eye looks.
     */
    private static float smoothNoise(float x, float y, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float n00 = noise(x0, y0, seed);
        float n10 = noise(x0 + 1, y0, seed);
        float n01 = noise(x0, y0 + 1, seed);
        float n11 = noise(x0 + 1, y0 + 1, seed);
        float top = n00 + (n10 - n00) * fx;
        float bottom = n01 + (n11 - n01) * fx;
        return top + (bottom - top) * fy;
    }

    /** Deterministic value noise in [0,1). */
    private static float noise(int x, int y, int seed) {
        int h = seed * 0x9E3779B1 + x * 0x85EBCA6B + y * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x27D4EB2F;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536f;
    }

    // --- polygon helpers ---------------------------------------------------------------------

    private static boolean inside(float[] xy, float px, float py) {
        boolean in = false;
        int n = xy.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = xy[i * 2];
            float yi = xy[i * 2 + 1];
            float xj = xy[j * 2];
            float yj = xy[j * 2 + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** Distance to the nearest edge and the outward direction across it. */
    private static float[] nearestEdge(float[] xy, float px, float py) {
        float best = Float.MAX_VALUE;
        float bx = 0f;
        float by = 0f;
        int n = xy.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = xy[i * 2];
            float yi = xy[i * 2 + 1];
            float xj = xy[j * 2];
            float yj = xy[j * 2 + 1];
            float ax = xj - xi;
            float ay = yj - yi;
            float lengthSquared = ax * ax + ay * ay;
            float t = lengthSquared <= 0f ? 0f : ((px - xi) * ax + (py - yi) * ay) / lengthSquared;
            t = t < 0f ? 0f : (t > 1f ? 1f : t);
            float dx = px - (xi + ax * t);
            float dy = py - (yi + ay * t);
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < best) {
                best = distance;
                bx = distance <= 1e-5f ? 0f : dx / distance;
                by = distance <= 1e-5f ? 0f : dy / distance;
            }
        }
        return new float[] {best, bx, by};
    }
}
