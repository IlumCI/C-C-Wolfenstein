package com.ccwolf.game.render;

import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Colors;
import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Rect;
import com.ccwolf.gfx.Surface;

/**
 * Who holds what, washed over the ground.
 *
 * <p>Baked into one image and drawn once, rather than filled a tile at a time. The tile-at-a-time
 * version is not a small inefficiency here: the camera's minimum zoom puts the whole
 * sixty-four-tile map on screen, so it would be four thousand fill calls every frame — which is
 * precisely the per-tile drawing the terrain cache exists to have removed, reintroduced for a
 * decoration.
 *
 * <p>Baking also gets the look for free. One pixel per tile, stretched over the world with
 * smoothing on, and the platform scaler interpolates between cells — so a field computed on
 * sixteen-by-sixteen blocks reads as a smooth gradient rather than as stair-steps, with no
 * interpolation code here to get wrong and no float arithmetic anywhere near the renderer.
 *
 * <p>And there are no adjacent rectangles, so there is no seam to open between them. {@code
 * tileRect}'s rounding exists to stop exactly that, and this sidesteps the whole problem.
 *
 * <h2>It must be drawn under the fog</h2>
 *
 * <p>The field behind this counts every unit on the map, seen or not. Drawn on top of fog it
 * would broadcast enemy positions through unexplored ground — so both the world pass and the
 * minimap draw it <em>before</em> their fog, and that is a correctness requirement rather than a
 * question of taste.
 */
public final class ControlOverlay {

    /**
     * Where the wash reaches full strength.
     *
     * <p>Measured, not guessed. Two minutes into a match on kreisau the field runs 13 at the
     * median tile, 113 at the ninetieth and 218 at its strongest — the blur normalises, so a
     * fourteen-hundred-point command post does not put fourteen hundred anywhere. Saturating at
     * the top of that range is what stops the map being two flat slabs with a hairline between
     * them.
     */
    private static final float FULL_STRENGTH = 200f;

    /** How solid the wash gets at its strongest. Deliberately faint: this sits under everything. */
    private static final int MAX_ALPHA = 110;

    /**
     * What the wash is worth when someone has asked to see the field itself.
     *
     * <p>Faint enough to play under is far too faint to read a gradient off, so the development
     * toggle raises it rather than adding a second drawing path that could disagree with the one
     * the game uses.
     */
    private static final int DEBUG_ALPHA = 200;

    private final Brush paint = new Brush();

    private boolean raw;

    private Image image;
    private int[] pixels;
    private int bakedWidth;
    private int bakedHeight;
    private int bakedVersion = -1;

    public ControlOverlay() {
        // Smoothing on, which is the whole point: it is what turns coarse cells into a gradient.
        paint.setSmoothScaling(true);
        paint.setAntiAlias(false);
    }

    /**
     * Draws the wash over a world rectangle.
     *
     * @param version changes whenever the field has been rebuilt, so a still field costs one
     *     {@code drawImage} and nothing else
     */
    public void draw(Surface surface, WorldView view, Rect worldRect, int version) {
        TileMap map = view.map();
        bake(view, map, version);
        surface.drawImage(image, worldRect, paint);
    }

    /**
     * Shows the field at full strength instead of as a hint, for looking at it.
     *
     * <p>Forces a rebake: the cached image is keyed on the field's version, and this changes how
     * the same field is drawn rather than the field.
     */
    public void setRaw(boolean raw) {
        if (raw != this.raw) {
            this.raw = raw;
            bakedVersion = -1;
        }
    }

    public boolean isRaw() {
        return raw;
    }

    /** The image itself, for the minimap, which draws it over a different rectangle. */
    public Image image(WorldView view, int version) {
        bake(view, view.map(), version);
        return image;
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
        }

        int mine = Palette.faction(view.faction());
        int theirs = Palette.faction(
                view.faction() == com.ccwolf.core.entity.Faction.REGIME
                        ? com.ccwolf.core.entity.Faction.RESISTANCE
                        : com.ccwolf.core.entity.Faction.REGIME);

        for (int y = 0; y < bakedHeight; y++) {
            for (int x = 0; x < bakedWidth; x++) {
                float control = view.control(x, y);
                float strength = Math.abs(control) / FULL_STRENGTH;
                if (strength > 1f) {
                    strength = 1f;
                }
                // Square root, because the field is not spread evenly: with the median tile at
                // a twentieth of the strongest, a straight mapping leaves half the map at an
                // alpha of two and the overlay only ever shows the two bases. The root lifts
                // held-but-quiet ground into view without flattening the difference at the top.
                // Exact in binary floating point, unlike anything else that would curve this.
                strength = (float) Math.sqrt(strength);
                int alpha = (int) (strength * (raw ? DEBUG_ALPHA : MAX_ALPHA));
                int rgb = control >= 0f ? mine : theirs;
                pixels[y * bakedWidth + x] = Colors.argb(alpha,
                        Colors.red(rgb), Colors.green(rgb), Colors.blue(rgb));
            }
        }
        image = Gfx.image(pixels, bakedWidth, bakedHeight);
        bakedVersion = version;
    }
}
