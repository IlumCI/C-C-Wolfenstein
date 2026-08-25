package com.ccwolf.gfx;

/**
 * Everything the game draws with.
 *
 * <p>Six primitives and a clip stack. That is genuinely the whole surface: the sprites are
 * baked ahead of time by {@code PixelCanvas} working in plain {@code int[]}, so by the time
 * anything reaches a platform it is either a rectangle, a line, a circle, a string, or a
 * finished image. Keeping the interface this narrow is what makes a second backend cheap.
 *
 * <p>Coordinates are pixels with the origin top-left. Rect arguments are edges (left, top,
 * right, bottom), matching the call sites this replaced.
 */
public interface Surface {

    int width();

    int height();

    /** Fills the whole surface with an opaque colour. */
    void clear(int argb);

    void fillRect(float left, float top, float right, float bottom, Brush brush);

    /** Outlines a rectangle at the brush's stroke width, centred on the edge. */
    void strokeRect(float left, float top, float right, float bottom, Brush brush);

    void drawLine(float x0, float y0, float x1, float y1, Brush brush);

    void fillCircle(float centerX, float centerY, float radius, Brush brush);

    /** Draws text with its baseline at {@code y}, positioned by the brush's alignment. */
    void drawText(String text, float x, float y, Brush brush);

    float measureText(String text, Brush brush);

    /** Scales {@code image} into the destination rectangle. */
    void drawImage(Image image, float left, float top, float right, float bottom, Brush brush);

    /**
     * Draws a rectangle of the image into a rectangle of the surface.
     *
     * <p>The overload that makes big maps affordable. A layer stretched over the whole world -
     * the control wash, the gas cloud - covers three and a half thousand pixels of virtual
     * canvas on the large map, and drawing all of it every frame cost thirteen milliseconds of
     * a seventeen-millisecond budget for pixels the clip then threw away. Drawing only the
     * visible source rectangle is what every tile-scrolling engine has done since tile
     * scrolling was invented.
     *
     * @param srcLeft source rectangle, in the image's own pixels
     */
    void drawImage(Image image, float srcLeft, float srcTop, float srcRight, float srcBottom,
                   float left, float top, float right, float bottom, Brush brush);

    // --- Rect-shaped conveniences ---------------------------------------------------------
    //
    // Default methods rather than backend responsibilities: the renderer and HUD keep reusable
    // Rects and pass them straight through, and there is nothing a backend could do with one
    // that it cannot do with four floats.

    default void fillRect(Rect rect, Brush brush) {
        fillRect(rect.left, rect.top, rect.right, rect.bottom, brush);
    }

    default void strokeRect(Rect rect, Brush brush) {
        strokeRect(rect.left, rect.top, rect.right, rect.bottom, brush);
    }

    default void drawImage(Image image, Rect dst, Brush brush) {
        drawImage(image, dst.left, dst.top, dst.right, dst.bottom, brush);
    }

    default void pushClip(Rect rect) {
        pushClip(rect.left, rect.top, rect.right, rect.bottom);
    }

    void pushClip(float left, float top, float right, float bottom);

    void popClip();
}
