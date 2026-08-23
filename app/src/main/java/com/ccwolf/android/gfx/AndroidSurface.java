package com.ccwolf.android.gfx;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;

/**
 * The Android backend: everything the game draws, in a {@link Canvas}.
 *
 * <p>One reusable {@link Paint} rather than one per brush. A frame is thousands of draw calls
 * sharing a handful of brushes, and consecutive calls almost always use the same one, so the
 * brush's revision stamp retires nearly all of the state translation.
 */
public final class AndroidSurface implements Surface {

    private final Paint paint = new Paint();
    private final Rect dst = new Rect();

    private final Rect src = new Rect();

    private Canvas canvas;
    private Brush appliedBrush;
    private int appliedStamp = -1;

    /** Points the surface at the frame's canvas. Reused across frames; nothing is allocated. */
    public AndroidSurface bind(Canvas canvas) {
        this.canvas = canvas;
        this.appliedBrush = null;
        this.appliedStamp = -1;
        return this;
    }

    @Override
    public int width() {
        return canvas.getWidth();
    }

    @Override
    public int height() {
        return canvas.getHeight();
    }

    @Override
    public void clear(int argb) {
        canvas.drawColor(argb);
    }

    @Override
    public void fillRect(float left, float top, float right, float bottom, Brush brush) {
        apply(brush, Paint.Style.FILL);
        canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void strokeRect(float left, float top, float right, float bottom, Brush brush) {
        apply(brush, Paint.Style.STROKE);
        canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void drawLine(float x0, float y0, float x1, float y1, Brush brush) {
        apply(brush, Paint.Style.STROKE);
        canvas.drawLine(x0, y0, x1, y1, paint);
    }

    @Override
    public void fillCircle(float centerX, float centerY, float radius, Brush brush) {
        apply(brush, Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    @Override
    public void drawText(String text, float x, float y, Brush brush) {
        apply(brush, Paint.Style.FILL);
        canvas.drawText(text, x, y, paint);
    }

    @Override
    public float measureText(String text, Brush brush) {
        apply(brush, Paint.Style.FILL);
        return paint.measureText(text);
    }

    @Override
    public void drawImage(Image image, float left, float top, float right, float bottom,
            Brush brush) {
        apply(brush, Paint.Style.FILL);
        Image source = brush.tint() == 0 ? image : image.tinted(brush.tint());
        dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        canvas.drawBitmap(((AndroidImage) source).bitmap(), null, dst, paint);
    }

    @Override
    public void drawImage(Image image, float srcLeft, float srcTop, float srcRight,
            float srcBottom, float left, float top, float right, float bottom, Brush brush) {
        apply(brush, Paint.Style.FILL);
        Image source = brush.tint() == 0 ? image : image.tinted(brush.tint());
        src.set(Math.round(srcLeft), Math.round(srcTop), Math.round(srcRight),
                Math.round(srcBottom));
        dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        canvas.drawBitmap(((AndroidImage) source).bitmap(), src, dst, paint);
    }

    @Override
    public void pushClip(float left, float top, float right, float bottom) {
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
    }

    @Override
    public void popClip() {
        canvas.restore();
    }

    private void apply(Brush brush, Paint.Style style) {
        if (brush != appliedBrush || brush.stamp() != appliedStamp) {
            appliedBrush = brush;
            appliedStamp = brush.stamp();

            paint.setColor(brush.color());
            if (brush.alpha() < 255) {
                // The brush's own alpha multiplies whatever the colour already carries.
                paint.setAlpha((brush.color() >>> 24) * brush.alpha() / 255);
            }
            paint.setStrokeWidth(brush.strokeWidth());
            paint.setTextSize(brush.textSize());
            paint.setFakeBoldText(brush.bold());
            paint.setAntiAlias(brush.antiAlias());
            paint.setFilterBitmap(brush.smoothScaling());
            paint.setDither(false);
            paint.setTextAlign(alignOf(brush.align()));
        }
        paint.setStyle(style);
    }

    private static Paint.Align alignOf(TextAlign align) {
        switch (align) {
            case CENTER:
                return Paint.Align.CENTER;
            case RIGHT:
                return Paint.Align.RIGHT;
            case LEFT:
            default:
                return Paint.Align.LEFT;
        }
    }
}
