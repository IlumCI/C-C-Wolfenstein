package com.ccwolf.game.input;

/**
 * One pointer event, in whatever form the platform delivers it.
 *
 * <p>Android hands over a {@code MotionEvent}; a desktop mouse produces one pointer and
 * synthesises pinch from the scroll wheel. Both fill one of these, so the gesture logic in
 * {@link InputController} never learns which it is talking to.
 *
 * <p>Mutable and reused by the shell that owns it: pointer events arrive at whatever rate the
 * screen samples at, and allocating one per event is pure garbage.
 */
public final class PointerEvent {

    /** More fingers than this and the extras are ignored; the game only reads two. */
    public static final int MAX_POINTERS = 4;

    public enum Action {
        /** The first pointer went down. */
        DOWN,
        /** An additional pointer went down while others were already held. */
        POINTER_DOWN,
        MOVE,
        /** One of several pointers lifted, others remain. */
        POINTER_UP,
        /** The last pointer lifted. */
        UP,
        /** The gesture was taken away — a call arrived, the window lost focus. */
        CANCEL
    }

    private Action action = Action.CANCEL;
    private int count;
    private final float[] xs = new float[MAX_POINTERS];
    private final float[] ys = new float[MAX_POINTERS];

    public PointerEvent set(Action action, int count) {
        this.action = action;
        this.count = Math.max(0, Math.min(MAX_POINTERS, count));
        return this;
    }

    public PointerEvent setPointer(int index, float x, float y) {
        if (index >= 0 && index < MAX_POINTERS) {
            xs[index] = x;
            ys[index] = y;
        }
        return this;
    }

    /** Convenience for the single-pointer case, which is every desktop event. */
    public PointerEvent single(Action action, float x, float y) {
        return set(action, 1).setPointer(0, x, y);
    }

    public Action action() {
        return action;
    }

    public int count() {
        return count;
    }

    public float x() {
        return x(0);
    }

    public float y() {
        return y(0);
    }

    public float x(int index) {
        return index >= 0 && index < count ? xs[index] : 0f;
    }

    public float y(int index) {
        return index >= 0 && index < count ? ys[index] : 0f;
    }

    /** Distance between the first two pointers — the pinch measure. */
    public float span() {
        if (count < 2) {
            return 0f;
        }
        float dx = xs[0] - xs[1];
        float dy = ys[0] - ys[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float focusX() {
        return count < 2 ? x(0) : (xs[0] + xs[1]) * 0.5f;
    }

    public float focusY() {
        return count < 2 ? y(0) : (ys[0] + ys[1]) * 0.5f;
    }
}
