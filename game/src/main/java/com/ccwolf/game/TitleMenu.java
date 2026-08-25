package com.ccwolf.game;

import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.WolfPalette;
import com.ccwolf.game.render.Palette;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;
import java.util.Random;

/**
 * The title screen: the name of the game over a war it is showing you.
 *
 * <p>The background is not painted — it is the attract-mode demo, drawn by the shell before
 * this class gets the surface. What this class owns is everything on top: the dimming, the
 * weather (rain and lightning, because it is never a clear day, not even on the menu), the
 * title lettering, and the two rows you can tap.
 *
 * <p>The lettering is a font sprite in the early-90s shooter idiom: heavy slab capitals with a
 * hard bevel and a deep drop, drawn from a five-by-seven glyph grid and baked once. It is
 * original lettering in that style — the style is a genre, the logo it evokes is a trademark,
 * and this game draws everything it owns from scratch anyway.
 */
public final class TitleMenu {

    /** What a tap on the title screen asked for. */
    public enum Action { NONE, SKIRMISH, QUIT }

    /** Glyph box height in art pixels; widths vary per letter, as blades do. */
    private static final int GLYPH_H = 26;

    /** Italic shear: how far each row leans right per pixel of height above the baseline. */
    private static final float LEAN = 0.28f;

    private final Brush paint = new Brush();
    private final Image title;
    private final Image subtitle;
    private final boolean showQuit;

    /** Weather is render-side garnish: its dice owe nothing to anyone. */
    private final Random weather = new Random(0x57A6E);

    private float width;
    private float height;
    private float scale = 1f;

    private float skirmishTop;
    private float skirmishBottom;
    private float quitTop;
    private float quitBottom;
    private float rowLeft;
    private float rowRight;

    public TitleMenu(boolean showQuit) {
        this.showQuit = showQuit;
        this.title = bakeLine("WOLFENSTEIN", true);
        this.subtitle = bakeLine("THE FIRE RISES", true);
    }

    public void layout(float screenWidth, float screenHeight, float density) {
        this.width = screenWidth;
        this.height = screenHeight;
        this.scale = density;
        float rowHeight = 26f * scale;
        rowLeft = width * 0.5f - 130f * scale;
        rowRight = width * 0.5f + 130f * scale;
        skirmishTop = height * 0.66f;
        skirmishBottom = skirmishTop + rowHeight;
        quitTop = skirmishBottom + 10f * scale;
        quitBottom = quitTop + rowHeight;
    }

    /** Everything above the demo: dim, weather, title, rows. */
    public void draw(Surface surface, long nowMs) {
        // The demo is scenery, not the subject: pull it well back into the dark.
        paint.setColor(0x99000000);
        surface.fillRect(0, 0, width, height, paint);

        drawRain(surface);
        drawLightning(surface, nowMs);

        // Title block, upper third. The bake is at art scale; draw it up to screen scale.
        float titleScale = Math.min(2f * scale,
                (width * 0.86f) / title.width());
        float tw = title.width() * titleScale;
        float th = title.height() * titleScale;
        float tx = (width - tw) / 2f;
        float ty = height * 0.16f;
        paint.setColor(0xFFFFFFFF);
        surface.drawImage(title, tx, ty, tx + tw, ty + th, paint);

        float subScale = titleScale * 0.45f;
        float sw = subtitle.width() * subScale;
        float sh = subtitle.height() * subScale;
        float sx = (width - sw) / 2f;
        float sy = ty + th + 12f * scale;
        surface.drawImage(subtitle, sx, sy, sx + sw, sy + sh, paint);

        drawRow(surface, "SKIRMISH", skirmishTop, skirmishBottom, true);
        if (showQuit) {
            drawRow(surface, "QUIT", quitTop, quitBottom, false);
        }

        paint.setColor(Palette.HUD_TEXT_DIM);
        paint.setTextSize(9f * scale);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText("a war that never happened, fought in the rain",
                width / 2f, height * 0.955f, paint);
    }

    public Action tap(float x, float y) {
        if (x >= rowLeft && x <= rowRight) {
            if (y >= skirmishTop && y <= skirmishBottom) {
                return Action.SKIRMISH;
            }
            if (showQuit && y >= quitTop && y <= quitBottom) {
                return Action.QUIT;
            }
        }
        return Action.NONE;
    }

    // --- weather ----------------------------------------------------------------------------

    private void drawRain(Surface surface) {
        // Ephemeral streaks, redrawn from fresh dice every frame: at 60 Hz the eye reads
        // motion in the flicker without any streak needing a life of its own.
        paint.setColor(0x2E9FB2C4);
        paint.setStrokeWidth(1f);
        int drops = (int) (width * height / 18000f);
        for (int i = 0; i < drops; i++) {
            float x = weather.nextFloat() * width;
            float y = weather.nextFloat() * height;
            float len = (8f + weather.nextFloat() * 14f) * scale;
            surface.drawLine(x, y, x - len * 0.25f, y + len, paint);
        }
    }

    private void drawLightning(Surface surface, long nowMs) {
        // Time-driven, not state-driven: each nine-second cycle rolls its own flash moment
        // from the cycle number, so the storm needs no memory and never drifts with framerate.
        long cycle = nowMs / 9000L;
        Random bolt = new Random(cycle * 0x9E3779B97F4A7C15L);
        long offset = 1500 + bolt.nextInt(6000);
        long inCycle = nowMs - cycle * 9000L;
        long sinceFlash = inCycle - offset;
        if (sinceFlash < 0 || sinceFlash > 220) {
            return;
        }
        float strength = 1f - sinceFlash / 220f;
        paint.setColor(((int) (strength * 0x38) << 24) | 0xE8F0FF);
        surface.fillRect(0, 0, width, height, paint);

        // The bolt itself only on the first bright half.
        if (sinceFlash < 110) {
            paint.setColor(0xFFE8F0FF);
            paint.setStrokeWidth(Math.max(1f, 1.5f * scale));
            float x = width * (0.15f + bolt.nextFloat() * 0.7f);
            float y = 0;
            float targetY = height * (0.35f + bolt.nextFloat() * 0.25f);
            while (y < targetY) {
                float nx = x + (bolt.nextFloat() - 0.5f) * 40f * scale;
                float ny = y + (10f + bolt.nextFloat() * 24f) * scale;
                surface.drawLine(x, y, nx, ny, paint);
                x = nx;
                y = ny;
            }
        }
    }

    // --- chrome -----------------------------------------------------------------------------

    private void drawRow(Surface surface, String text, float top, float bottom, boolean primary) {
        paint.setColor(primary ? WolfPalette.shade(WolfPalette.BLOOD, 2) : 0xCC15161A);
        surface.fillRect(rowLeft, top, rowRight, bottom, paint);
        paint.setColor(primary ? WolfPalette.shade(WolfPalette.BLOOD, 0)
                : Palette.HUD_TEXT_DIM);
        paint.setStrokeWidth(2f * scale);
        surface.strokeRect(rowLeft, top, rowRight, bottom, paint);
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(13f * scale);
        paint.setBold(true);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText(text, (rowLeft + rowRight) / 2f, top + (bottom - top) * 0.64f, paint);
        paint.setBold(false);
    }

    // --- the lettering ----------------------------------------------------------------------

    /**
     * Bakes one line of the title face: angular capitals built from tapered blade strokes,
     * leaned into an italic, run through a heat gradient — near-black at the top of the
     * letters, blood in the body, fire at the feet — and rimmed in pale bone.
     *
     * <p>The first two cuts drew grid slabs and read as tube lettering and then as toy
     * blocks. The reference the user gave is a knife fight, not masonry: strokes must end in
     * points, and the letters must look heated from below. Original letterforms in that
     * genre — the genre is fair game, the logo it evokes is a trademark.
     */
    private static Image bakeLine(String text, boolean hot) {
        int gap = 3;
        int lean = (int) Math.ceil(GLYPH_H * LEAN);
        int width = lean + 2;
        for (int i = 0; i < text.length(); i++) {
            width += (text.charAt(i) == ' ' ? 8 : glyphWidth(text.charAt(i))) + gap;
        }
        PixelCanvas canvas = new PixelCanvas(width + 2, GLYPH_H + 4);

        int pen = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                pen += 8 + gap;
                continue;
            }
            for (float[] b : blades(c)) {
                blade(canvas, pen, b);
            }
            pen += glyphWidth(c) + gap;
        }

        heatAndRim(canvas, hot);
        return canvas.toImage();
    }

    /**
     * One tapered stroke: squares stamped along a line, shrinking from one half-width to the
     * other so a stroke can die in a point. The italic lean is applied here, per row, so
     * every stroke shares the same slant without any glyph knowing about it.
     */
    private static void blade(PixelCanvas canvas, int penX, float[] b) {
        float x0 = b[0];
        float y0 = b[1];
        float x1 = b[2];
        float y1 = b[3];
        float w0 = b[4];
        float w1 = b[5];
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = (int) (Math.sqrt(dx * dx + dy * dy) * 2f) + 1;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            float hw = w0 + (w1 - w0) * t;
            float shear = (GLYPH_H - 1 - y) * LEAN;
            int left = Math.round(penX + x + shear - hw);
            int top = Math.round(y - hw);
            int size = Math.max(1, Math.round(hw * 2f));
            canvas.rect(left, top, size, size, 0xFFFFFFFF);
        }
    }

    /** Replaces the white mask with the heat gradient and rims the result in pale bone. */
    private static void heatAndRim(PixelCanvas canvas, boolean hot) {
        int w = canvas.width();
        int h = canvas.height();
        for (int y = 0; y < h; y++) {
            float f = y / (float) (h - 1);
            int color;
            if (!hot) {
                // The quiet variant: bone fading to blood, for lines that must not shout.
                color = f < 0.4f ? WolfPalette.shade(WolfPalette.BONE, 2)
                        : WolfPalette.shade(WolfPalette.BLOOD, f < 0.7f ? 1 : 2);
            } else if (f < 0.14f) {
                color = WolfPalette.shade(WolfPalette.BLOOD, 4);
            } else if (f < 0.42f) {
                color = WolfPalette.shade(WolfPalette.BLOOD, 3);
            } else if (f < 0.62f) {
                color = WolfPalette.shade(WolfPalette.BLOOD, 2);
            } else if (f < 0.78f) {
                color = WolfPalette.shade(WolfPalette.BLOOD, 1);
            } else if (f < 0.9f) {
                color = WolfPalette.shade(WolfPalette.FIRE, 3);
            } else {
                color = WolfPalette.shade(WolfPalette.FIRE, 2);
            }
            for (int x = 0; x < w; x++) {
                if (canvas.isOpaque(x, y)) {
                    canvas.set(x, y, color);
                }
            }
        }
        // A one-pixel bone rim, the pale edge that lifts the word off the storm.
        canvas.outline(WolfPalette.shade(WolfPalette.BONE, 3));
    }

    private static int glyphWidth(char c) {
        switch (c) {
            case 'W': return 28;
            case 'N':
            case 'H': return 20;
            case 'R': return 17;
            case 'O': return 18;
            case 'T': return 16;
            case 'F':
            case 'E':
            case 'S': return 15;
            case 'L': return 14;
            case 'I': return 8;
            default:
                throw new IllegalArgumentException("The title font has no '" + c + "'");
        }
    }

    /**
     * The strokes of each capital, as {x0, y0, x1, y1, halfWidth0, halfWidth1} in a box
     * {@link #GLYPH_H} tall. Verticals taper downward so letters stand on points; bars taper
     * toward their free end so every terminal is a cut, not a butt.
     */
    private static float[][] blades(char c) {
        switch (c) {
            case 'W': return new float[][] {
                {3, 1, 8, 25, 3.2f, 1.1f}, {13, 1, 8, 25, 2.6f, 1.1f},
                {13, 1, 19, 25, 2.6f, 1.1f}, {25, 1, 19, 25, 3.2f, 1.1f}};
            case 'O': return new float[][] {
                {3, 3, 3, 23, 2.2f, 1.5f}, {15, 3, 15, 23, 2.2f, 1.5f},
                {4, 2, 14, 2, 1.7f, 1.7f}, {4, 24, 14, 24, 1.5f, 1.5f}};
            case 'L': return new float[][] {
                {3, 1, 3, 23, 2.8f, 2.2f}, {3, 24, 12, 24, 2.2f, 1.0f}};
            case 'F': return new float[][] {
                {3, 1, 3, 25, 2.8f, 1.1f}, {3, 2, 13, 2, 2.4f, 1.2f},
                {3, 12, 11, 12, 2.0f, 1.0f}};
            case 'E': return new float[][] {
                {3, 1, 3, 23, 2.8f, 2.2f}, {3, 2, 13, 2, 2.4f, 1.2f},
                {3, 12, 11, 12, 1.8f, 1.0f}, {3, 24, 13, 24, 2.2f, 1.2f}};
            case 'N': return new float[][] {
                {3, 1, 3, 25, 2.8f, 1.2f}, {17, 1, 17, 25, 2.8f, 1.2f},
                {3, 1, 17, 25, 2.6f, 2.2f}};
            case 'S': return new float[][] {
                {13, 2, 4, 2, 2.2f, 1.4f}, {3, 3, 3, 11, 2.2f, 1.8f},
                {3, 12, 13, 12, 1.9f, 1.9f}, {13, 13, 13, 22, 1.8f, 2.2f},
                {12, 24, 3, 24, 2.2f, 1.4f}};
            case 'T': return new float[][] {
                {2, 2, 14, 2, 2.4f, 2.4f}, {8, 3, 8, 25, 2.6f, 1.0f}};
            case 'I': return new float[][] {
                {4, 1, 4, 25, 2.6f, 1.2f}};
            case 'H': return new float[][] {
                {3, 1, 3, 25, 2.8f, 1.2f}, {17, 1, 17, 25, 2.8f, 1.2f},
                {3, 13, 17, 13, 2.2f, 2.2f}};
            case 'R': return new float[][] {
                {3, 1, 3, 25, 2.8f, 1.2f}, {3, 2, 12, 2, 2.4f, 1.8f},
                {13, 3, 13, 11, 2.2f, 1.8f}, {3, 12, 12, 12, 1.8f, 1.6f},
                {7, 13, 15, 25, 2.2f, 1.0f}};
            default:
                throw new IllegalArgumentException("The title font has no '" + c + "'");
        }
    }
}
