package com.ccwolf.game.render;

import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Rect;
import com.ccwolf.gfx.Surface;

/**
 * The cloud, washed over the ground.
 *
 * <p>Same machinery as {@link ControlOverlay} and for the same reasons: one pixel per tile,
 * baked into one image, stretched with smoothing on so coarse cells read as a drifting haze
 * rather than as tinted squares. The colour is a jaundiced yellow-green out of the gas ramp's
 * family — unmistakably not smoke, not fog and not the control wash, because a player's first
 * question about a cloud is "is that the thing that kills my men" and the answer has to be
 * legible from across the map.
 *
 * <p>Drawn <em>over</em> the entities and under the fog: gas is the one ground layer that
 * stands man-high, and a figure walking into it should visibly wade in rather than stand on it.
 */
public final class GasOverlay {

    /** Alpha per level of concentration. Six levels lands just under opaque. */
    private static final int ALPHA_PER_LEVEL = 38;

    private final Brush paint = new Brush();

    private Image image;
    private int[] pixels;
    private int bakedWidth;
    private int bakedHeight;
    private int bakedVersion = -1;

    public GasOverlay() {
        paint.setSmoothScaling(true);
        paint.setAntiAlias(false);
    }

    /**
     * Draws the cloud over a world rectangle. Free when the map is clean.
     *
     * @param version anything that changes when the layer settles — the settle counter's tick
     *     bucket is enough, since the layer only moves on its own cadence
     */
    public void draw(Surface surface, WorldView view, Rect worldRect, int version) {
        if (!view.gasAnywhere() && bakedVersion == -1) {
            return;
        }
        bake(view, view.map(), version);
        surface.drawImage(image, worldRect, paint);
    }

    private void bake(WorldView view, TileMap map, int version) {
        if (image != null && version == bakedVersion
                && map.width() == bakedWidth && map.height() == bakedHeight) {
            return;
        }
        if (pixels == null || map.width() != bakedWidth || map.height() != bakedHeight) {
            pixels = new int[map.width() * map.height()];
            bakedWidth = map.width();
            bakedHeight = map.height();
            image = null;
        }
        for (int y = 0; y < bakedHeight; y++) {
            for (int x = 0; x < bakedWidth; x++) {
                int dose = view.gasAt(x, y);
                if (dose <= 0) {
                    pixels[y * bakedWidth + x] = 0;
                    continue;
                }
                int alpha = Math.min(230, dose * ALPHA_PER_LEVEL);
                // Denser gas shifts from yellow toward green: the centre of a fresh cloud is
                // the widest part of the tell.
                int green = 0x9A + Math.min(0x30, dose * 8);
                pixels[y * bakedWidth + x] = (alpha << 24) | (0x8F << 16) | (green << 8) | 0x3A;
            }
        }
        image = Gfx.image(pixels, bakedWidth, bakedHeight);
        bakedVersion = version;
    }
}
