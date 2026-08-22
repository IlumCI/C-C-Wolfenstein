package com.ccwolf.game.art;

import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;

/**
 * A small indexed-free ARGB pixel buffer with the handful of operations that make pixel art
 * cheap to author in code: rectangles, bevels, dithered ramps, seeded noise, outlines and
 * ASCII stamps.
 *
 * <p>Everything here is deterministic — no time, no {@code Math.random} — so a given sprite
 * recipe always bakes to exactly the same pixels, which is what lets the contact-sheet test
 * be a meaningful check rather than a lottery.
 *
 * <p>Drawing is clipped, so a recipe can happily draw off the edge of its own sprite.
 */
public final class PixelCanvas {

    private final int width;
    private final int height;
    private final int[] pixels;

    public PixelCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public int get(int x, int y) {
        return inBounds(x, y) ? pixels[y * width + x] : WolfPalette.CLEAR;
    }

    public boolean isOpaque(int x, int y) {
        return (get(x, y) >>> 24) != 0;
    }

    /** Plots a pixel. Fully transparent colours are ignored rather than punching holes. */
    public PixelCanvas px(int x, int y, int color) {
        if (inBounds(x, y) && (color >>> 24) != 0) {
            pixels[y * width + x] = color;
        }
        return this;
    }

    /** Plots a pixel, transparency included — this one does punch holes. */
    public PixelCanvas set(int x, int y, int color) {
        if (inBounds(x, y)) {
            pixels[y * width + x] = color;
        }
        return this;
    }

    public PixelCanvas clear() {
        java.util.Arrays.fill(pixels, WolfPalette.CLEAR);
        return this;
    }

    public PixelCanvas fill(int color) {
        java.util.Arrays.fill(pixels, color);
        return this;
    }

    public PixelCanvas rect(int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                px(xx, yy, color);
            }
        }
        return this;
    }

    public PixelCanvas rectOutline(int x, int y, int w, int h, int color) {
        hLine(x, x + w - 1, y, color);
        hLine(x, x + w - 1, y + h - 1, color);
        vLine(x, y, y + h - 1, color);
        vLine(x + w - 1, y, y + h - 1, color);
        return this;
    }

    public PixelCanvas hLine(int x0, int x1, int y, int color) {
        int from = Math.min(x0, x1);
        int to = Math.max(x0, x1);
        for (int x = from; x <= to; x++) {
            px(x, y, color);
        }
        return this;
    }

    public PixelCanvas vLine(int x, int y0, int y1, int color) {
        int from = Math.min(y0, y1);
        int to = Math.max(y0, y1);
        for (int y = from; y <= to; y++) {
            px(x, y, color);
        }
        return this;
    }

    /**
     * A line with mass: parallel runs offset along the perpendicular.
     *
     * <p>Needed for limbs that point in an arbitrary direction. A single-pixel line reads as a
     * stick whichever way it points, and stacking axis-aligned rectangles only works for the
     * four cardinal facings.
     */
    public PixelCanvas thickLine(int x0, int y0, int x1, int y1, int halfWidth, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return this;
        }
        float px = -dy / len;
        float py = dx / len;
        for (int i = -halfWidth; i <= halfWidth; i++) {
            line(Math.round(x0 + px * i), Math.round(y0 + py * i),
                    Math.round(x1 + px * i), Math.round(y1 + py * i), color);
        }
        return this;
    }

    /** Bresenham line, for barrels, aerials and cracks. */
    public PixelCanvas line(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            px(x0, y0, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
        return this;
    }

    public PixelCanvas ellipse(int cx, int cy, int rx, int ry, int color) {
        for (int y = -ry; y <= ry; y++) {
            for (int x = -rx; x <= rx; x++) {
                float nx = rx == 0 ? 0 : x / (float) rx;
                float ny = ry == 0 ? 0 : y / (float) ry;
                if (nx * nx + ny * ny <= 1.02f) {
                    px(cx + x, cy + y, color);
                }
            }
        }
        return this;
    }

    /**
     * Lights a rectangle from the top-left: highlight along the top and left edges, shadow
     * along the bottom and right. This one operation is most of what makes flat shapes read
     * as solid objects.
     */
    public PixelCanvas bevel(int x, int y, int w, int h, int light, int dark) {
        hLine(x, x + w - 1, y, light);
        vLine(x, y, y + h - 1, light);
        hLine(x, x + w - 1, y + h - 1, dark);
        vLine(x + w - 1, y, y + h - 1, dark);
        return this;
    }

    /** Checkerboard dither between two shades — the period way to fake a gradient. */
    public PixelCanvas dither(int x, int y, int w, int h, int colorA, int colorB) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                px(xx, yy, ((xx + yy) & 1) == 0 ? colorA : colorB);
            }
        }
        return this;
    }

    /**
     * Fills a rectangle with a ramp running top to bottom, dithering across each boundary so
     * the bands do not look like stripes.
     */
    public PixelCanvas rampVertical(int x, int y, int w, int h, int[] ramp, int from, int to) {
        int steps = Math.max(1, to - from + 1);
        for (int yy = 0; yy < h; yy++) {
            float t = h <= 1 ? 0f : yy / (float) (h - 1);
            int index = from + (int) (t * steps);
            int color = WolfPalette.shade(ramp, index);
            int next = WolfPalette.shade(ramp, index + 1);
            float local = (t * steps) - (int) (t * steps);
            for (int xx = 0; xx < w; xx++) {
                // Dither only in the last quarter of each band. Spreading it across half the
                // band, as this first did, turns a roof into a crosshatch pattern that reads
                // as a rendering fault rather than as shading.
                boolean checker = ((x + xx + y + yy) & 1) == 0;
                px(x + xx, y + yy, (local > 0.78f && checker) ? next : color);
            }
        }
        return this;
    }

    /**
     * Speckles a region with a shade, deterministically from a seed. Used for gravel, rust,
     * grass tufts and scorch — the texture that stops flat fills looking like flat fills.
     */
    public PixelCanvas speckle(int x, int y, int w, int h, int color, int seed, int oneIn) {
        int state = seed * 1103515245 + 12345;
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                state = state * 1103515245 + 12345;
                int roll = (state >>> 16) & 0x7FFF;
                if (oneIn > 0 && roll % oneIn == 0) {
                    px(xx, yy, color);
                }
            }
        }
        return this;
    }

    /** Draws a 1px outline around every opaque pixel, the classic sprite-readability trick. */
    public PixelCanvas outline(int color) {
        int[] copy = pixels.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((copy[y * width + x] >>> 24) != 0) {
                    continue;
                }
                if (opaqueAt(copy, x - 1, y) || opaqueAt(copy, x + 1, y)
                        || opaqueAt(copy, x, y - 1) || opaqueAt(copy, x, y + 1)) {
                    set(x, y, color);
                }
            }
        }
        return this;
    }

    private boolean opaqueAt(int[] buffer, int x, int y) {
        return inBounds(x, y) && (buffer[y * width + x] >>> 24) != 0;
    }

    /**
     * Stamps hand-authored pixel art. Each string is a row; each character is looked up in
     * {@code key}, and any character not in the key (space, by convention) leaves the pixel
     * alone. This is how the details that carry a sprite's identity get placed exactly.
     */
    public PixelCanvas stamp(int ox, int oy, String[] rows, char[] keys, int[] colors) {
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                for (int k = 0; k < keys.length; k++) {
                    if (keys[k] == c) {
                        px(ox + x, oy + y, colors[k]);
                        break;
                    }
                }
            }
        }
        return this;
    }

    /** Copies another canvas on top, skipping its transparent pixels. */
    /**
     * A smaller copy, averaging each {@code factor}x{@code factor} block down to one pixel.
     *
     * <p>A box filter rather than nearest-neighbour, and the difference matters at these ratios:
     * dropping three pixels in four throws away most of the detail that was the reason for
     * authoring at a high resolution in the first place, and does it unevenly, so a cobbled road
     * sampled by nearest neighbour turns into a moire pattern that crawls as the camera moves.
     * Averaging is what makes a high-resolution sprite still look right when it is shown small.
     *
     * <p>Alpha is averaged with the colour and the colour is weighted by it, so a sprite with
     * transparent edges does not pick up a dark halo from averaging in the zeroes.
     */
    public PixelCanvas downscaled(int factor) {
        if (factor <= 1) {
            return this;
        }
        int w = Math.max(1, width / factor);
        int h = Math.max(1, height / factor);
        PixelCanvas out = new PixelCanvas(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                int n = 0;
                for (int sy = y * factor; sy < (y + 1) * factor && sy < height; sy++) {
                    for (int sx = x * factor; sx < (x + 1) * factor && sx < width; sx++) {
                        int p = pixels[sy * width + sx];
                        int pa = (p >>> 24) & 0xFF;
                        a += pa;
                        r += ((p >> 16) & 0xFF) * pa;
                        g += ((p >> 8) & 0xFF) * pa;
                        b += (p & 0xFF) * pa;
                        n++;
                    }
                }
                if (n == 0 || a == 0) {
                    out.pixels[y * w + x] = 0;
                    continue;
                }
                out.pixels[y * w + x] = ((a / n) << 24) | ((r / a) << 16) | ((g / a) << 8)
                        | (b / a);
            }
        }
        return out;
    }

    public PixelCanvas blit(PixelCanvas source, int ox, int oy) {
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                px(ox + x, oy + y, source.get(x, y));
            }
        }
        return this;
    }

    /** Mirrors the buffer left-to-right in place. */
    public PixelCanvas mirrorX() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                int left = y * width + x;
                int right = y * width + (width - 1 - x);
                int tmp = pixels[left];
                pixels[left] = pixels[right];
                pixels[right] = tmp;
            }
        }
        return this;
    }

    /**
     * Nearest-neighbour rotation about the centre into a new canvas of the same size.
     *
     * <p>Rotating pixel art is a compromise, but for top-down vehicle hulls it is the right
     * one: eight hand-drawn facings per vehicle would be eight times the art for a silhouette
     * the player sees at 40 pixels across.
     */
    public PixelCanvas rotated(float radians) {
        PixelCanvas out = new PixelCanvas(width, height);
        float cx = (width - 1) / 2f;
        float cy = (height - 1) / 2f;
        float cos = (float) Math.cos(-radians);
        float sin = (float) Math.sin(-radians);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = x - cx;
                float dy = y - cy;
                int sx = Math.round(cx + dx * cos - dy * sin);
                int sy = Math.round(cy + dx * sin + dy * cos);
                if (inBounds(sx, sy)) {
                    out.set(x, y, pixels[sy * width + sx]);
                }
            }
        }
        return out;
    }

    /**
     * Rotation via supersampling: blow the sprite up, rotate at the higher resolution, then
     * collapse each block back down to its most common colour.
     *
     * <p>Rotating a small sprite directly leaves diagonals as a torn mess of stray pixels —
     * that is exactly what the first pass of vehicle art looked like. Doing the rotation with
     * three times the pixels and then picking the dominant colour per block keeps the hull
     * edges solid while staying honestly blocky.
     */
    public PixelCanvas rotatedSmooth(float radians, int factor) {
        if (factor <= 1) {
            return rotated(radians);
        }
        PixelCanvas big = new PixelCanvas(width * factor, height * factor);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = pixels[y * width + x];
                for (int by = 0; by < factor; by++) {
                    for (int bx = 0; bx < factor; bx++) {
                        big.set(x * factor + bx, y * factor + by, color);
                    }
                }
            }
        }

        PixelCanvas rotated = big.rotated(radians);
        PixelCanvas out = new PixelCanvas(width, height);
        int[] colors = new int[factor * factor];
        int[] counts = new int[factor * factor];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int distinct = 0;
                for (int by = 0; by < factor; by++) {
                    for (int bx = 0; bx < factor; bx++) {
                        int color = rotated.get(x * factor + bx, y * factor + by);
                        int slot = -1;
                        for (int i = 0; i < distinct; i++) {
                            if (colors[i] == color) {
                                slot = i;
                                break;
                            }
                        }
                        if (slot < 0) {
                            slot = distinct++;
                            colors[slot] = color;
                            counts[slot] = 0;
                        }
                        counts[slot]++;
                    }
                }
                int best = 0;
                for (int i = 1; i < distinct; i++) {
                    if (counts[i] > counts[best]) {
                        best = i;
                    }
                }
                out.set(x, y, distinct == 0 ? WolfPalette.CLEAR : colors[best]);
            }
        }
        return out;
    }

    /**
     * A lit panel: mid-tone fill, highlight along the top and left, shadow along the bottom
     * and right. The workhorse for armour plate, hull sides, crates and shutters.
     */
    public PixelCanvas panel(int x, int y, int w, int h, int[] ramp, int base) {
        rect(x, y, w, h, WolfPalette.shade(ramp, base));
        hLine(x, x + w - 1, y, WolfPalette.shade(ramp, base - 1));
        vLine(x, y, y + h - 1, WolfPalette.shade(ramp, base - 1));
        hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(ramp, base + 2));
        vLine(x + w - 1, y, y + h - 1, WolfPalette.shade(ramp, base + 2));
        return this;
    }

    /** Rivet heads across a plate: a bright pixel with a dark one under it. */
    public PixelCanvas rivets(int x, int y, int w, int h, int spacing, int light, int dark) {
        for (int yy = y; yy < y + h; yy += spacing) {
            for (int xx = x; xx < x + w; xx += spacing) {
                px(xx, yy, light);
                px(xx, yy + 1, dark);
            }
        }
        return this;
    }

    /** Diagonal hazard stripes, for loading bays and warning panels. */
    public PixelCanvas hazard(int x, int y, int w, int h, int colorA, int colorB) {
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                px(x + xx, y + yy, (((xx + yy) / 3) & 1) == 0 ? colorA : colorB);
            }
        }
        return this;
    }

    /** A soft ground shadow: an ellipse that fades at its edge rather than stopping dead. */
    public PixelCanvas groundShadow(int cx, int cy, int rx, int ry) {
        ellipse(cx, cy, rx, ry, 0x55000000);
        ellipse(cx, cy, rx - 1, Math.max(1, ry - 1), 0x77000000);
        return this;
    }

    /** Multiplies every opaque pixel towards a colour — scorching, team tinting, fading. */
    public PixelCanvas tint(int color, float amount) {
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) != 0) {
                pixels[i] = WolfPalette.mix(pixels[i], color, amount);
            }
        }
        return this;
    }

    public Image toImage() {
        return Gfx.image(pixels, width, height);
    }

    /** For artwork with no transparency in it, which a backend can draw more cheaply. */
    public Image toOpaqueImage() {
        return Gfx.opaqueImage(pixels, width, height);
    }
}
