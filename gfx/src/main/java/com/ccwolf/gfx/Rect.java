package com.ccwolf.gfx;

/**
 * A mutable rectangle in pixels, held by edges.
 *
 * <p>Mutable and reused rather than allocated per frame: the HUD and the renderer both keep a
 * handful of these and rewrite them every frame, which is the difference between zero garbage
 * per frame and a few thousand short-lived objects.
 */
public final class Rect {

    public float left;
    public float top;
    public float right;
    public float bottom;

    public Rect() {
    }

    public Rect(float left, float top, float right, float bottom) {
        set(left, top, right, bottom);
    }

    public Rect set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        return this;
    }

    public Rect set(Rect other) {
        return set(other.left, other.top, other.right, other.bottom);
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    public boolean contains(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    @Override
    public String toString() {
        return "Rect(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }
}
