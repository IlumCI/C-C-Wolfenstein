package com.ccwolf.game.art;

/**
 * A pixel grid for sprites that are <em>drawn</em> rather than sculpted.
 *
 * <h2>Why this is a grid of slots and not a canvas of colours</h2>
 *
 * <p>The sculpting engine next door is finished and it is the right tool for a machine, but the
 * figures it produced were soft, smooth and shaded, and that is the wrong medium for this game.
 * This game is pixel art. The distinction is not decoration: pixel art means every edge lands on a
 * whole pixel, every tone is one of a handful chosen in advance, and the person drawing decides
 * what each pixel is. Anti-aliased shapes blown up to sprite size are a vector illustration
 * wearing a sprite's clothes.
 *
 * <p>So there is no coverage here, no blending and no colour. There is a byte per pixel naming a
 * <b>tone slot</b>, and a shape either claims a pixel or does not. Colours are attached at the
 * very end by {@link #toCanvas}, which is what lets one grid serve both factions and what makes a
 * dumped grid legible enough to correct by hand.
 *
 * <h2>The grid</h2>
 *
 * <p>A footsoldier is authored at sixty-four by sixty-four and shown at two screen pixels per art
 * pixel, which is the same hundred and twenty-eight pixels on screen the old thirty-two grid gave
 * at four. Four times the art pixels at no cost anywhere else — the atlas, the renderer and the
 * memory budget only ever see the finished buffer.
 *
 * <p>What those pixels buy is the answer to a problem three attempts failed at: a head at the old
 * grid was six pixels across, which is a helmet-coloured blob, and every attempt to render a face
 * into it failed because there was nothing to render into. At sixty-four the head is about twelve
 * by fourteen, which is a brim, a brow shadow, a jaw and two eyes — <em>placed</em>, by hand, for
 * how they read, rather than derived from geometry.
 *
 * <h2>How a sprite gets made</h2>
 *
 * <p>Shapes block the figure in, {@link #toGrid} dumps it as text, the pixels that read badly get
 * corrected by hand, and the corrected grid is checked into the source and is from then on the
 * art. The shape primitives are scaffolding for the blocking-in stage and are deliberately the
 * same signed-distance formulas the sculptor uses, because they were already proven — what
 * changed is that a pixel is claimed when its centre falls inside, with no partial coverage
 * anywhere.
 */
public final class Ink {

    // --- the slot alphabet ---------------------------------------------------------------------

    /** No pixel. Distinct from every tone, and the only slot {@link #toCanvas} leaves clear. */
    public static final byte EMPTY = 0;
    /** The dark edge that holds a figure against the ground. */
    public static final byte OUTLINE = 1;

    public static final byte COAT_LIGHT = 2;
    public static final byte COAT = 3;
    public static final byte COAT_DARK = 4;
    public static final byte TROUSER = 5;
    public static final byte TROUSER_DARK = 6;
    public static final byte BOOT = 7;
    public static final byte BOOT_DARK = 8;
    public static final byte SKIN = 9;
    public static final byte SKIN_SHADE = 10;
    public static final byte KIT = 11;
    public static final byte KIT_DARK = 12;
    public static final byte METAL = 13;
    public static final byte METAL_DARK = 14;
    public static final byte WOOD = 15;
    /** A faction flash: an armband, a painted number, a unit's one spot of colour. */
    public static final byte ACCENT = 16;
    /** An eye, a lens, a visor slit. One pixel, and it is what makes a helmet a head. */
    public static final byte EYE = 17;

    public static final int SLOTS = 18;

    /**
     * One character per slot, for dumping and reloading a grid as text.
     *
     * <p>Chosen so a grid reads as a picture in a source file: upper case is the lit tone of a
     * thing and lower case its shadow, and the dot is nothing at all. Correcting art by hand is
     * only bearable if the thing being corrected looks like the thing it draws.
     */
    private static final char[] KEYS = {
        '.', 'o',
        'L', 'C', 'd',
        'T', 't',
        'B', 'b',
        'S', 's',
        'K', 'k',
        'M', 'm',
        'W',
        'A', 'E',
    };

    private final int width;
    private final int height;
    private final byte[] slot;

    public Ink(int width, int height) {
        this.width = width;
        this.height = height;
        this.slot = new byte[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public byte at(int x, int y) {
        return inBounds(x, y) ? slot[y * width + x] : EMPTY;
    }

    /** Sets one pixel. The whole point of the exercise, and the only primitive that must exist. */
    public Ink pixel(int x, int y, byte tone) {
        if (inBounds(x, y)) {
            slot[y * width + x] = tone;
        }
        return this;
    }

    // --- shapes, for blocking in ---------------------------------------------------------------

    /**
     * An ellipse, at an angle. Heads, shoulders, packs, wheels.
     *
     * <p>Signed distance rather than a scan conversion, so the same call handles the rotation and
     * so the maths is shared with {@code Sculptor} where it was already proven. The pixel is
     * claimed when its <em>centre</em> is inside, which is the standard rule and is what puts an
     * edge on a whole pixel.
     */
    public Ink ellipse(float cx, float cy, float rx, float ry, float rotation, byte tone) {
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
                if (distance <= 0f) {
                    slot[y * width + x] = tone;
                }
            }
        }
        return this;
    }

    /** A round-ended thick line: limbs, straps, barrels, a slung rifle. */
    public Ink capsule(float x0, float y0, float x1, float y1, float radius, byte tone) {
        return taper(x0, y0, radius, x1, y1, radius, tone);
    }

    /** The same with a radius that changes along its length: a forearm, a muzzle. */
    public Ink taper(float x0, float y0, float r0, float x1, float y1, float r1, byte tone) {
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
                if ((float) Math.sqrt(dx * dx + dy * dy) - (r0 + (r1 - r0) * t) <= 0f) {
                    slot[y * width + x] = tone;
                }
            }
        }
        return this;
    }

    /** A rounded box: pouches, plates, the flat of a stock, a magazine. */
    public Ink box(float x, float y, float w, float h, float corner, byte tone) {
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
                if (outside + Math.min(Math.max(dx, dy), 0f) - r <= 0f) {
                    slot[py * width + px] = tone;
                }
            }
        }
        return this;
    }

    /** A closed polygon: a coat skirt, a cape, a helmet brim seen edge-on. */
    public Ink polygon(float[] xy, byte tone) {
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
                if (inside(xy, x + 0.5f, y + 0.5f)) {
                    slot[y * width + x] = tone;
                }
            }
        }
        return this;
    }

    /** A straight run of pixels, ends included. For a strap, a barrel, a rifle sling. */
    public Ink line(int x0, int y0, int x1, int y1, byte tone) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            pixel(x0, y0, tone);
            if (x0 == x1 && y0 == y1) {
                return this;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    /** A filled rectangle in whole pixels, for anything that should have no curve at all. */
    public Ink rect(int x, int y, int w, int h, byte tone) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                pixel(px, py, tone);
            }
        }
        return this;
    }

    // --- the pixel-art operations --------------------------------------------------------------

    /**
     * A checkerboard of two tones, the classic way to get a third tone out of two.
     *
     * <p>Worth having rather than adding palette entries: a dithered band between a coat's lit and
     * shadowed tone reads as a gradient at two screen pixels per art pixel, and it keeps the
     * palette small, which is most of what makes a set of sprites look like one set.
     */
    public Ink dither(int x, int y, int w, int h, byte a, byte b) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                pixel(px, py, ((px + py) & 1) == 0 ? a : b);
            }
        }
        return this;
    }

    /**
     * Darkens the band of a tone that lies along its turned-away edge.
     *
     * <p>A pixel of {@code lit} takes {@code shadow} when the tone runs out within {@code depth}
     * steps in the given direction — so the shaded band is as wide as the depth asked for, and it
     * follows the shape's own outline rather than a rectangle.
     *
     * <p>The depth is the whole point, and the shape card is what showed it: at one pixel this
     * darkens a rim so thin it may as well be part of the outline, which is not what a pixel
     * artist means by shading a side. Three or four pixels on a torso is a lit side and a turned
     * side, and that is what makes a flat tone read as a body.
     *
     * <p>It is a rule, not a lighting model, and that is deliberate. One direction applied
     * consistently across every sprite means the whole roster is lit from the same place by
     * construction — the same reason the sculpted path has exactly one {@code Light}.
     */
    public Ink shade(byte lit, byte shadow, int dx, int dy, int depth) {
        byte[] before = slot.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (before[index] != lit) {
                    continue;
                }
                for (int step = 1; step <= depth; step++) {
                    int nx = x + dx * step;
                    int ny = y + dy * step;
                    byte neighbour = inBounds(nx, ny) ? before[ny * width + nx] : EMPTY;
                    if (neighbour != lit) {
                        slot[index] = shadow;
                        break;
                    }
                }
            }
        }
        return this;
    }

    /**
     * Lays a one-pixel dark edge around the figure. Call once, last.
     *
     * <p>One <em>art</em> pixel, which is the whole reason this does not use
     * {@code PixelCanvas.outline}: that one walks the raw buffer, so on a canvas showing two
     * screen pixels per art pixel it draws a half-pixel edge, and on the old four-times canvas a
     * quarter of one. A contour that is a fraction of a pixel wide is not a contour.
     *
     * <p>Four-connected, so corners stay sharp rather than picking up a diagonal bulge, and it
     * only ever writes into empty pixels, so nothing already drawn is eaten.
     */
    public Ink outline() {
        byte[] before = slot.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (before[index] != EMPTY) {
                    continue;
                }
                if (solid(before, x - 1, y) || solid(before, x + 1, y)
                        || solid(before, x, y - 1) || solid(before, x, y + 1)) {
                    slot[index] = OUTLINE;
                }
            }
        }
        return this;
    }

    // --- text, for the hand-fixing step --------------------------------------------------------

    /** The grid as one string per row, ready to be looked at, corrected and pasted back. */
    public String[] toGrid() {
        String[] rows = new String[height];
        StringBuilder line = new StringBuilder(width);
        for (int y = 0; y < height; y++) {
            line.setLength(0);
            for (int x = 0; x < width; x++) {
                line.append(KEYS[slot[y * width + x]]);
            }
            rows[y] = line.toString();
        }
        return rows;
    }

    /**
     * A grid read back from text — the hand-corrected art, on its way to being drawn.
     *
     * <p>Any character not in the alphabet is empty, so a grid can be annotated in the margin
     * while it is being worked on without the annotation becoming art.
     */
    public static Ink fromGrid(String[] rows) {
        int w = 0;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        Ink ink = new Ink(w, rows.length);
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                for (byte k = 0; k < KEYS.length; k++) {
                    if (KEYS[k] == c) {
                        ink.slot[y * w + x] = k;
                        break;
                    }
                }
            }
        }
        return ink;
    }

    // --- colour, attached last -----------------------------------------------------------------

    /**
     * The finished sprite, at the given screen pixels per art pixel.
     *
     * <p>Colour arrives here and nowhere earlier, which is what lets one grid be a Resistance
     * partisan and a Regime soldier depending on the row of tones handed in.
     *
     * @param tones one colour per slot, indexed by the slot constants; slot zero is ignored
     */
    public PixelCanvas toCanvas(int[] tones, int scale) {
        PixelCanvas out = new PixelCanvas(width, height, scale);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte tone = slot[y * width + x];
                if (tone != EMPTY) {
                    out.px(x, y, tones[tone]);
                }
            }
        }
        return out;
    }

    /**
     * A row of tones built from the ramps a unit wears.
     *
     * <p>Through {@code shade} rather than {@code albedo}: the ramps in this game are pre-shaded,
     * the sculpted path has to hand the top of one to a light and let it generate the rest, and a
     * drawn sprite is unlit by construction and wants them exactly as authored. The rule across
     * the two pipelines is <b>albedo for lit, shade for drawn</b>.
     *
     * <p>The outline is derived from the coat rather than being black, which is the finding from
     * the shape card that survived the change of medium: pure black on a Regime greatcoat is
     * invisible and on a bone sleeve is a sticker. Derived, it is always the same tone darker than
     * the thing it surrounds — and the floor stops a black uniform losing its edge entirely.
     */
    public static int[] tones(int[] coat, int[] trouser, int[] kit, int[] skin, int accent) {
        int[] out = new int[SLOTS];
        out[OUTLINE] = WolfPalette.mix(WolfPalette.darken(coat[coat.length - 1], 0.55f),
                0xFF1A1712, 0.35f);
        out[COAT_LIGHT] = WolfPalette.shade(coat, 0);
        out[COAT] = WolfPalette.shade(coat, 1);
        out[COAT_DARK] = WolfPalette.shade(coat, 3);
        out[TROUSER] = WolfPalette.shade(trouser, 2);
        out[TROUSER_DARK] = WolfPalette.shade(trouser, 3);
        out[BOOT] = WolfPalette.shade(WolfPalette.LEATHER, 3);
        out[BOOT_DARK] = WolfPalette.shade(WolfPalette.LEATHER, 4);
        out[SKIN] = WolfPalette.shade(skin, 1);
        out[SKIN_SHADE] = WolfPalette.shade(skin, 3);
        out[KIT] = WolfPalette.shade(kit, 1);
        out[KIT_DARK] = WolfPalette.shade(kit, 3);
        out[METAL] = WolfPalette.shade(WolfPalette.GUNMETAL, 1);
        out[METAL_DARK] = WolfPalette.shade(WolfPalette.GUNMETAL, 3);
        out[WOOD] = WolfPalette.shade(WolfPalette.LEATHER, 1);
        out[ACCENT] = accent;
        out[EYE] = 0xFF14100C;
        return out;
    }

    // --- helpers -------------------------------------------------------------------------------

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private boolean solid(byte[] buffer, int x, int y) {
        return inBounds(x, y) && buffer[y * width + x] != EMPTY;
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
