package com.ccwolf.gfx.java2d;

import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The AWT backend: everything the game draws, in {@link Graphics2D}.
 *
 * <p>Nothing here is clever, and that is the point — the whole surface is six primitives, so
 * a second backend is a few hundred lines rather than a rewrite. What it does have to be is
 * *faithful*: the contact-sheet tests render the same sprites through this and through the
 * Android backend and compare, so any difference in rounding or filtering shows up as a
 * failing image rather than as a mystery on someone's phone.
 */
public final class Java2DSurface implements Surface {

    private final Graphics2D g;
    private final int width;
    private final int height;
    /**
     * Saved clip shapes, innermost last.
     *
     * <p>An ArrayList rather than a Deque because an unclipped context reports its clip as
     * null, and ArrayDeque refuses to hold one.
     */
    private final List<Shape> clips = new ArrayList<Shape>(4);

    /** The brush whose state is currently loaded into the context, and at which revision. */
    private Brush appliedBrush;
    private int appliedStamp = -1;

    private Font baseFont;

    public Java2DSurface(Graphics2D graphics, int width, int height) {
        this.g = graphics;
        this.width = width;
        this.height = height;
        // Pixel art: no interpolation anywhere by default, and no fractional text metrics.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        this.baseFont = g.getFont();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void clear(int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, width, height);
        appliedStamp = -1;
    }

    @Override
    public void fillRect(float left, float top, float right, float bottom, Brush brush) {
        apply(brush);
        int l = Math.round(left);
        int t = Math.round(top);
        g.fillRect(l, t, Math.round(right) - l, Math.round(bottom) - t);
    }

    @Override
    public void strokeRect(float left, float top, float right, float bottom, Brush brush) {
        apply(brush);
        int l = Math.round(left);
        int t = Math.round(top);
        g.drawRect(l, t, Math.round(right) - l, Math.round(bottom) - t);
    }

    @Override
    public void drawLine(float x0, float y0, float x1, float y1, Brush brush) {
        apply(brush);
        g.drawLine(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1));
    }

    @Override
    public void fillCircle(float centerX, float centerY, float radius, Brush brush) {
        apply(brush);
        int d = Math.max(1, Math.round(radius * 2f));
        g.fillOval(Math.round(centerX - radius), Math.round(centerY - radius), d, d);
    }

    @Override
    public void drawText(String text, float x, float y, Brush brush) {
        apply(brush);
        float drawX = x;
        if (brush.align() != TextAlign.LEFT) {
            float w = g.getFontMetrics().stringWidth(text);
            drawX = brush.align() == TextAlign.CENTER ? x - w * 0.5f : x - w;
        }
        g.drawString(text, drawX, y);
    }

    @Override
    public float measureText(String text, Brush brush) {
        apply(brush);
        return g.getFontMetrics().stringWidth(text);
    }

    @Override
    public void drawImage(Image image, float left, float top, float right, float bottom,
            Brush brush) {
        apply(brush);
        Image source = brush.tint() == 0 ? image : image.tinted(brush.tint());
        BufferedImage awt = ((Java2DImage) source).awt();
        int l = Math.round(left);
        int t = Math.round(top);
        g.drawImage(awt, l, t, Math.round(right) - l, Math.round(bottom) - t, null);
    }

    @Override
    public void drawImage(Image image, float srcLeft, float srcTop, float srcRight,
            float srcBottom, float left, float top, float right, float bottom, Brush brush) {
        apply(brush);
        Image source = brush.tint() == 0 ? image : image.tinted(brush.tint());
        BufferedImage awt = ((Java2DImage) source).awt();
        g.drawImage(awt,
                Math.round(left), Math.round(top), Math.round(right), Math.round(bottom),
                Math.round(srcLeft), Math.round(srcTop), Math.round(srcRight),
                Math.round(srcBottom), null);
    }

    @Override
    public void pushClip(float left, float top, float right, float bottom) {
        clips.add(g.getClip());
        g.clipRect(Math.round(left), Math.round(top),
                Math.round(right - left), Math.round(bottom - top));
    }

    @Override
    public void popClip() {
        if (!clips.isEmpty()) {
            g.setClip(clips.remove(clips.size() - 1));
        }
    }

    /**
     * Loads a brush into the graphics context, skipping the work when nothing has changed.
     *
     * <p>A frame is thousands of draw calls sharing a handful of brushes, and most consecutive
     * calls use the same one at the same revision, so the stamp check retires almost all of
     * this.
     */
    private void apply(Brush brush) {
        if (brush == appliedBrush && brush.stamp() == appliedStamp) {
            return;
        }
        appliedBrush = brush;
        appliedStamp = brush.stamp();

        int color = brush.color();
        int alpha = brush.alpha();
        if (alpha < 255) {
            // The brush's own alpha multiplies whatever the colour already carries.
            color = (((color >>> 24) * alpha / 255) << 24) | (color & 0x00FFFFFF);
        }
        g.setColor(new Color(color, true));
        g.setStroke(new BasicStroke(Math.max(1f, brush.strokeWidth())));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, brush.antiAlias()
                ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, brush.smoothScaling()
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(baseFont.deriveFont(brush.bold() ? Font.BOLD : Font.PLAIN, brush.textSize()));
    }

    /** Fills a rectangle without a brush — used by the shell to letterbox the window. */
    public static Java2DSurface over(BufferedImage target) {
        return new Java2DSurface(target.createGraphics(), target.getWidth(), target.getHeight());
    }

}
