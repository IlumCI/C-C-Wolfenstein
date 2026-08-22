package com.ccwolf.game.art;

/**
 * A canvas for sprites that are <em>drawn</em> rather than sculpted.
 *
 * <h2>Why there are two pipelines</h2>
 *
 * <p>{@link Sculptor} builds form and lights it, and it is the right tool for anything with
 * enough pixels to show geometry: a tank, a gun, an Ubersoldat. Two contact sheets settled that
 * it is the wrong tool for a footsoldier. A head rendered at three hundred, ninety-six, forty and
 * twenty pixels has no face at the last two — and twenty is roughly what the game shows. Three
 * distinct helmet silhouettes, stripped to pure black, are clearly three at eighty pixels and
 * three identical blobs at twenty.
 *
 * <p>Sculpted detail needs pixels to be legible and a footsoldier never gets them. So infantry
 * are drawn the way sprite artists have always solved "must read at twenty pixels": a bold
 * silhouette, a few deliberate tones, and a dark contour holding the figure against the ground.
 *
 * <h2>What this is, and what it deliberately is not</h2>
 *
 * <p>Shapes, colours, an order to paint them in, and a contour. No depth, no normals, no
 * material, no lighting. Bending the sculptor into flat output would have meant six float buffers
 * doing the work of two and a lighting model carefully defeated; this is two hundred lines that
 * does the job directly.
 *
 * <p>The anti-aliased shapes are the same signed-distance formulas {@code Sculptor} uses, because
 * they were already written and already proven. What changes is everything behind them:
 * compositing is a plain painter's over in draw order, so the artist controls exactly which tone
 * lands where, which is the entire point of drawing rather than rendering.
 *
 * <h2>Tones come from {@code WolfPalette.shade}</h2>
 *
 * <p>The ramps in this game are pre-shaded — index 0 is documented as the highlight — which is
 * why the sculpted path has to use {@code albedo} and let the light generate the rest. A drawn
 * sprite is unlit by construction, so the ramps are used as authored. The rule across the two
 * pipelines is now simply <b>albedo for lit, shade for drawn</b>.
 */
public final class Ink {

    /** How far a pixel's distance spans the transition from covered to not. */
    private static final float EDGE = 0.5f;

    private final int width;
    private final int height;
    private final float[] cover;
    private final float[] red;
    private final float[] green;
    private final float[] blue;

    public Ink(int width, int height) {
        this.width = width;
        this.height = height;
        int n = width * height;
        this.cover = new float[n];
        this.red = new float[n];
        this.green = new float[n];
        this.blue = new float[n];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // --- shapes ------------------------------------------------------------------------------

    /** An ellipse, at an angle. Heads, shoulders, packs, wheels — most of a figure. */
    public Ink ellipse(float cx, float cy, float rx, float ry, float rotation, int color) {
        if (rx <= 0f || ry <= 0f) {
            return this;
        }
        float cos = (float) Math.cos(-rotation);
        float sin = (float) Math.sin(-rotation);
        float reach = Math.max(rx, ry) + 1f;
        for (int y = Math.max(0, (int) (cy - reach)); y <= Math.min(height - 1, (int) (cy + reach));
                y++) {
            for (int x = Math.max(0, (int) (cx - reach));
                    x <= Math.min(width - 1, (int) (cx + reach)); x++) {
                float px = x + 0.5f - cx;
                float py = y + 0.5f - cy;
                float ex = px * cos - py * sin;
                float ey = px * sin + py * cos;
                float ax = ex / rx;
                float ay = ey / ry;
                float k1 = (float) Math.sqrt(ax * ax + ay * ay);
                if (k1 > 2f) {
                    continue;
                }
                float bx = ex / (rx * rx);
                float by = ey / (ry * ry);
                float k2 = (float) Math.sqrt(bx * bx + by * by);
                float distance = k2 <= 1e-6f ? -Math.min(rx, ry) : k1 * (k1 - 1f) / k2;
                paint(x, y, distance, color);
            }
        }
        return this;
    }

    /** A round-ended thick line: limbs, straps, barrels, slung rifles. */
    public Ink capsule(float x0, float y0, float x1, float y1, float radius, int color) {
        return taper(x0, y0, radius, x1, y1, radius, color);
    }

    /** The same with a radius that changes along its length. */
    public Ink taper(float x0, float y0, float r0, float x1, float y1, float r1, int color) {
        float maxRadius = Math.max(r0, r1);
        if (maxRadius <= 0f) {
            return this;
        }
        float ax = x1 - x0;
        float ay = y1 - y0;
        float lengthSquared = ax * ax + ay * ay;
        int lox = (int) (Math.min(x0, x1) - maxRadius - 1);
        int hix = (int) (Math.max(x0, x1) + maxRadius + 1);
        int loy = (int) (Math.min(y0, y1) - maxRadius - 1);
        int hiy = (int) (Math.max(y0, y1) + maxRadius + 1);

        for (int y = Math.max(0, loy); y <= Math.min(height - 1, hiy); y++) {
            for (int x = Math.max(0, lox); x <= Math.min(width - 1, hix); x++) {
                float px = x + 0.5f - x0;
                float py = y + 0.5f - y0;
                float t = lengthSquared <= 0f ? 0f : (px * ax + py * ay) / lengthSquared;
                t = t < 0f ? 0f : (t > 1f ? 1f : t);
                float dx = px - ax * t;
                float dy = py - ay * t;
                float distance = (float) Math.sqrt(dx * dx + dy * dy) - (r0 + (r1 - r0) * t);
                paint(x, y, distance, color);
            }
        }
        return this;
    }

    /** A rounded box: webbing pouches, boxes, plates, the flat of a stock. */
    public Ink box(float x, float y, float w, float h, float corner, int color) {
        if (w <= 0f || h <= 0f) {
            return this;
        }
        float halfW = w / 2f;
        float halfH = h / 2f;
        float cx = x + halfW;
        float cy = y + halfH;
        float r = Math.min(corner, Math.min(halfW, halfH));
        for (int py = Math.max(0, (int) y - 1); py <= Math.min(height - 1, (int) (y + h) + 1);
                py++) {
            for (int px = Math.max(0, (int) x - 1); px <= Math.min(width - 1, (int) (x + w) + 1);
                    px++) {
                float dx = Math.abs(px + 0.5f - cx) - (halfW - r);
                float dy = Math.abs(py + 0.5f - cy) - (halfH - r);
                float outX = Math.max(dx, 0f);
                float outY = Math.max(dy, 0f);
                float outside = (float) Math.sqrt(outX * outX + outY * outY);
                paint(px, py, outside + Math.min(Math.max(dx, dy), 0f) - r, color);
            }
        }
        return this;
    }

    /**
     * A closed polygon: a coat skirt, a cape, a helmet brim seen edge-on.
     *
     * <p>Coverage is sampled four by four rather than derived from a distance, because a general
     * polygon has no cheap signed distance. Reach for a box or a capsule where either will do.
     */
    public Ink polygon(float[] xy, int color) {
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
        for (int y = Math.max(0, (int) loy - 1); y <= Math.min(height - 1, (int) hiy + 1); y++) {
            for (int x = Math.max(0, (int) lox - 1); x <= Math.min(width - 1, (int) hix + 1); x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (inside(xy, x + (sx + 0.5f) / 4f, y + (sy + 0.5f) / 4f)) {
                            hits++;
                        }
                    }
                }
                if (hits > 0) {
                    over(y * width + x, hits / 16f, color);
                }
            }
        }
        return this;
    }

    // --- the contour --------------------------------------------------------------------------

    /**
     * Lays a dark outline under the figure. Call once, last.
     *
     * <p>This is the readability mechanism, and two decisions in it matter more than the rest of
     * the class.
     *
     * <p><b>The width is in output pixels and does not scale with the sprite.</b> A contour that
     * shrinks along with the art stops holding the figure at exactly the distance where holding
     * it is the whole job.
     *
     * <p><b>It is not constant black.</b> Pure black on a Regime greatcoat is invisible and on a
     * bone-coloured sleeve is a sticker, so the contour is a darkened version of whatever it
     * surrounds, with a floor so that something very dark still gets an edge.
     *
     * @param thickness how far the outline reaches beyond the silhouette, in pixels
     * @param strength how far toward black the surrounding colour is taken
     */
    public Ink contour(float thickness, float strength) {
        int reach = Math.max(1, Math.round(thickness));
        float[] outAlpha = new float[width * height];
        int[] outColor = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (cover[index] >= 0.99f) {
                    continue;
                }
                // The nearest ring at which something solid sits, which is the distance to the
                // silhouette to within a pixel and costs a small fixed scan.
                int found = 0;
                int source = -1;
                for (int r = 1; r <= reach && found == 0; r++) {
                    for (int dy = -r; dy <= r && found == 0; dy++) {
                        for (int dx = -r; dx <= r; dx++) {
                            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                                continue;
                            }
                            int sx = x + dx;
                            int sy = y + dy;
                            if (sx < 0 || sy < 0 || sx >= width || sy >= height) {
                                continue;
                            }
                            int at = sy * width + sx;
                            if (cover[at] >= 0.5f) {
                                found = r;
                                source = at;
                                break;
                            }
                        }
                    }
                }
                if (found == 0) {
                    continue;
                }
                float fade = 1f - (found - 1) / (float) reach;
                outAlpha[index] = fade * (1f - cover[index]);
                outColor[index] = darken(source, strength);
            }
        }

        // Composited underneath, so the figure keeps its own colour and only the edge darkens.
        for (int i = 0; i < outAlpha.length; i++) {
            float a = outAlpha[i];
            if (a <= 0f) {
                continue;
            }
            under(i, a, outColor[i]);
        }
        return this;
    }

    private int darken(int index, float strength) {
        float r = red[index] * (1f - strength);
        float g = green[index] * (1f - strength);
        float b = blue[index] * (1f - strength);
        // A floor, so a black greatcoat still gets an edge that is darker than it is.
        float floor = 0.055f;
        r = Math.min(r, red[index] - floor < 0f ? 0f : red[index] - floor);
        g = Math.min(g, green[index] - floor < 0f ? 0f : green[index] - floor);
        b = Math.min(b, blue[index] - floor < 0f ? 0f : blue[index] - floor);
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    // --- compositing ---------------------------------------------------------------------------

    private void paint(int x, int y, float distance, int color) {
        if (distance >= EDGE) {
            return;
        }
        over(y * width + x, distance <= -EDGE ? 1f : (EDGE - distance), color);
    }

    /** Source over destination, straight alpha. */
    private void over(int index, float alpha, int color) {
        if (alpha <= 0f) {
            return;
        }
        if (alpha > 1f) {
            alpha = 1f;
        }
        float sr = ((color >> 16) & 0xFF) / 255f;
        float sg = ((color >> 8) & 0xFF) / 255f;
        float sb = (color & 0xFF) / 255f;
        float da = cover[index];
        float out = alpha + da * (1f - alpha);
        if (out <= 1e-6f) {
            return;
        }
        red[index] = (sr * alpha + red[index] * da * (1f - alpha)) / out;
        green[index] = (sg * alpha + green[index] * da * (1f - alpha)) / out;
        blue[index] = (sb * alpha + blue[index] * da * (1f - alpha)) / out;
        cover[index] = out;
    }

    /** Destination over source: the contour goes beneath what is already drawn. */
    private void under(int index, float alpha, int color) {
        float sr = ((color >> 16) & 0xFF) / 255f;
        float sg = ((color >> 8) & 0xFF) / 255f;
        float sb = (color & 0xFF) / 255f;
        float da = cover[index];
        float out = da + alpha * (1f - da);
        if (out <= 1e-6f) {
            return;
        }
        red[index] = (red[index] * da + sr * alpha * (1f - da)) / out;
        green[index] = (green[index] * da + sg * alpha * (1f - da)) / out;
        blue[index] = (blue[index] * da + sb * alpha * (1f - da)) / out;
        cover[index] = out;
    }

    /** The finished sprite. */
    public PixelCanvas finish() {
        PixelCanvas out = new PixelCanvas(width, height);
        int[] pixels = out.pixels();
        for (int i = 0; i < pixels.length; i++) {
            if (cover[i] <= 0.002f) {
                continue;
            }
            pixels[i] = (clamp255(cover[i]) << 24) | (clamp255(red[i]) << 16)
                    | (clamp255(green[i]) << 8) | clamp255(blue[i]);
        }
        return out;
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

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
}
