package com.ccwolf.game.render;

import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.game.art.MonumentSprites;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;

/**
 * The monuments, anchored to the capital.
 *
 * <p>Terrain is the truth the simulation walks against; these sprites are what that truth
 * looks like. Each entry names the tile its sprite's top-left corner sits on, matching the
 * footprints {@code GermaniaPlan} laid — the map and this list are two views of one drawing.
 *
 * <p>Two layers: ground monuments draw under the entities (nothing can stand on their
 * footprints anyway), and the Arch draws over them, because its roof spans the avenue and a
 * convoy on the axis should genuinely disappear beneath it for a moment.
 */
public final class GermaniaDecor {

    /** One placed monument: sprite, anchor tile, size in tiles. */
    public static final class Placed {
        final Image image;
        final int tileX;
        final int tileY;
        final int tilesW;
        final int tilesH;

        Placed(Image image, int tileX, int tileY, int tilesW, int tilesH) {
            this.image = image;
            this.tileX = tileX;
            this.tileY = tileY;
            this.tilesW = tilesW;
            this.tilesH = tilesH;
        }
    }

    private static Placed[] ground;
    private static Placed[] overhead;

    private GermaniaDecor() {
    }

    private static synchronized void bake() {
        if (ground != null) {
            return;
        }
        ground = new Placed[] {
            new Placed(MonumentSprites.hall(), 112, 14,
                    MonumentSprites.HALL_TILES, MonumentSprites.HALL_TILES),
            new Placed(MonumentSprites.palaceWest(), 88, 55,
                    MonumentSprites.PALACE_TILES_W, MonumentSprites.PALACE_TILES_H),
            new Placed(MonumentSprites.palaceEast(), 157, 55,
                    MonumentSprites.PALACE_TILES_W, MonumentSprites.PALACE_TILES_H),
            new Placed(MonumentSprites.station(), 111, 236,
                    MonumentSprites.STATION_TILES_W, MonumentSprites.STATION_TILES_H),
        };
        overhead = new Placed[] {
            new Placed(MonumentSprites.arch(), 119, 172,
                    MonumentSprites.ARCH_TILES_W, MonumentSprites.ARCH_TILES_H),
        };
    }

    /** Draws one layer, clamped to the viewport. A no-op on every map but the capital. */
    public static void draw(Surface surface, String mapName, Camera camera, Brush brush,
            boolean overheadLayer) {
        if (!MapCatalog.GERMANIA.equals(mapName)) {
            return;
        }
        bake();
        Placed[] layer = overheadLayer ? overhead : ground;
        for (Placed p : layer) {
            float left = camera.screenX(p.tileX);
            float top = camera.screenY(p.tileY);
            float right = camera.screenX(p.tileX + p.tilesW);
            float bottom = camera.screenY(p.tileY + p.tilesH);
            if (right < camera.viewLeft() || bottom < camera.viewTop()
                    || left > camera.viewLeft() + camera.viewWidth()
                    || top > camera.viewTop() + camera.viewHeight()) {
                continue;
            }
            surface.drawImage(p.image, left, top, right, bottom, brush);
        }
    }

    /** For the poster and the tests: the placed monuments of one layer. */
    public static Placed[] placed(boolean overheadLayer) {
        bake();
        return overheadLayer ? overhead : ground;
    }

    public static Image imageOf(Placed p) {
        return p.image;
    }

    public static int[] frameOf(Placed p) {
        return new int[] {p.tileX, p.tileY, p.tilesW, p.tilesH};
    }
}
