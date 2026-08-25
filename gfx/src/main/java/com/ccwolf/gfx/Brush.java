package com.ccwolf.gfx;

/**
 * Drawing state — colour, stroke, text and filtering — passed to each draw call.
 *
 * <p>This mirrors how the renderer already works: a handful of long-lived brushes, each
 * configured once for a job (sprites, flat fills, outlines, HUD text) and reused every frame.
 * A single piece of "current state" on the surface would have meant setting four fields before
 * every call instead.
 *
 * <p>Backends have to translate this into their own paint object, and doing that per draw call
 * is wasteful when the same brush is used hundreds of times in a row. So every mutation bumps
 * {@link #stamp()}, and a backend can skip the translation when it sees the same brush at the
 * same stamp it applied last.
 */
public final class Brush {

    private int color = 0xFF000000;
    private int alpha = 255;
    private float strokeWidth = 1f;
    private float textSize = 12f;
    private TextAlign align = TextAlign.LEFT;
    private boolean bold;
    private boolean antiAlias;
    private boolean smoothScaling;

    /** ARGB tint composited over the drawn artwork, keeping its shape. 0 means no tint. */
    private int tint;

    private int stamp = 1;

    /** Changes with every mutation, so a backend can cache its translated paint state. */
    public int stamp() {
        return stamp;
    }

    public int color() {
        return color;
    }

    public Brush setColor(int argb) {
        if (argb != color) {
            color = argb;
            stamp++;
        }
        return this;
    }

    public int alpha() {
        return alpha;
    }

    public Brush setAlpha(int value) {
        int clamped = value < 0 ? 0 : (value > 255 ? 255 : value);
        if (clamped != alpha) {
            alpha = clamped;
            stamp++;
        }
        return this;
    }

    public float strokeWidth() {
        return strokeWidth;
    }

    public Brush setStrokeWidth(float width) {
        if (width != strokeWidth) {
            strokeWidth = width;
            stamp++;
        }
        return this;
    }

    public float textSize() {
        return textSize;
    }

    public Brush setTextSize(float size) {
        if (size != textSize) {
            textSize = size;
            stamp++;
        }
        return this;
    }

    public TextAlign align() {
        return align;
    }

    public Brush setAlign(TextAlign value) {
        if (value != align) {
            align = value;
            stamp++;
        }
        return this;
    }

    public boolean bold() {
        return bold;
    }

    public Brush setBold(boolean value) {
        if (value != bold) {
            bold = value;
            stamp++;
        }
        return this;
    }

    public boolean antiAlias() {
        return antiAlias;
    }

    public Brush setAntiAlias(boolean value) {
        if (value != antiAlias) {
            antiAlias = value;
            stamp++;
        }
        return this;
    }

    /** Off by default: smoothed pixel art stops being pixel art. */
    public boolean smoothScaling() {
        return smoothScaling;
    }

    public Brush setSmoothScaling(boolean value) {
        if (value != smoothScaling) {
            smoothScaling = value;
            stamp++;
        }
        return this;
    }

    public int tint() {
        return tint;
    }

    public Brush setTint(int argb) {
        if (argb != tint) {
            tint = argb;
            stamp++;
        }
        return this;
    }
}
