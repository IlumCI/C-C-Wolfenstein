package com.ccwolf.game;

import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.java2d.Java2DImage;
import com.ccwolf.gfx.java2d.Java2DImages;
import com.ccwolf.gfx.java2d.Java2DSurface;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * An offscreen surface the rendering tests draw into, and can then read back or write out.
 *
 * <p>These tests used to run under Robolectric with native graphics, because the renderer was
 * written against Android's {@code Canvas}. Now that drawing goes through {@link Surface}, they
 * run on a plain JVM through the AWT backend instead — faster, with no emulator or Android SDK
 * anywhere near them, exercising the same code a desktop player would.
 */
public final class Frame {

    static {
        Java2DImages.install();
    }

    private final BufferedImage image;
    private final Graphics2D graphics;
    private final Java2DSurface surface;

    public Frame(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        surface = new Java2DSurface(graphics, width, height);
    }

    /** Installs the image backend before any sprite is baked. */
    public static void useAwtBackend() {
        Java2DImages.install();
    }

    public Surface surface() {
        return surface;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public int pixel(int x, int y) {
        return image.getRGB(x, y);
    }

    /** A magnified crop, so a review can see what the pixels actually are. */
    public BufferedImage zoom(int x, int y, int w, int h, int scale) {
        BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, w * scale, h * scale, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** This frame's contents as a drawable image, for composing one frame into another. */
    public Image image() {
        return Java2DImage.of(image);
    }

    public void save(String name) throws IOException {
        write(image, name);
    }

    public static void write(BufferedImage bitmap, String name) throws IOException {
        File dir = new File("build/test-frames");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        ImageIO.write(bitmap, "png", new File(dir, name));
    }

    /** Turns finished ARGB pixels into a drawable image, for tests that bake their own. */
    public static Image image(int[] argb, int width, int height) {
        Java2DImages.install();
        return Gfx.image(argb, width, height);
    }

    public Brush brush() {
        return new Brush();
    }

    public void dispose() {
        graphics.dispose();
    }
}
