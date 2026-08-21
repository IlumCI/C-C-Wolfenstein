package com.ccwolf.game.render;

import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Rect;
import com.ccwolf.game.GameSession;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import java.util.List;

/**
 * The corner map. Terrain is baked into a one-pixel-per-tile bitmap once and then scaled, so
 * a frame only costs the entity dots, the fog and the viewport box.
 */
public final class Minimap {

    private final Brush paint = new Brush();
    private final Rect bounds = new Rect();

    private Image terrain;
    private int[] pixels;
    private int bakedOreVersion = -1;

    public Minimap() {
        paint.setAntiAlias(false);
        paint.setSmoothScaling(false);
    }

    public void setBounds(float left, float top, float size) {
        bounds.set(left, top, left + size, top + size);
    }

    public Rect bounds() {
        return bounds;
    }

    public boolean contains(float x, float y) {
        return bounds.contains(x, y);
    }

    /** Converts a touch inside the minimap to a tile, for jumping the camera. */
    public float tileXFor(float screenX, TileMap map) {
        float t = (screenX - bounds.left) / bounds.width();
        return Math.max(0f, Math.min(1f, t)) * map.width();
    }

    public float tileYFor(float screenY, TileMap map) {
        float t = (screenY - bounds.top) / bounds.height();
        return Math.max(0f, Math.min(1f, t)) * map.height();
    }

    public void draw(Surface surface, GameSession session) {
        GameWorld world = session.world();
        TileMap map = world.map();
        bake(map);

        surface.drawImage(terrain, bounds, paint);

        float sx = bounds.width() / map.width();
        float sy = bounds.height() / map.height();
        FogGrid fog = world.fogFor(session.playerId());

        // Fog: unexplored ground is blacked out entirely.
        if (world.isFogEnabled()) {
            paint.setColor(Palette.FOG_UNEXPLORED);
            for (int y = 0; y < map.height(); y++) {
                for (int x = 0; x < map.width(); x++) {
                    if (!fog.isExplored(x, y)) {
                        surface.fillRect(bounds.left + x * sx, bounds.top + y * sy,
                                bounds.left + (x + 1) * sx, bounds.top + (y + 1) * sy, paint);
                    }
                }
            }
        }

        List<Building> buildings = world.buildings();
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (world.isFogEnabled() && !fog.isExplored(b.tileX(), b.tileY())) {
                continue;
            }
            paint.setColor(Palette.faction(world.player(b.ownerId()).faction()));
            surface.fillRect(bounds.left + b.tileX() * sx, bounds.top + b.tileY() * sy,
                    bounds.left + (b.tileX() + b.tilesWide()) * sx,
                    bounds.top + (b.tileY() + b.tilesHigh()) * sy, paint);
        }

        List<Unit> units = world.units();
        float dot = Math.max(1.5f, sx * 0.9f);
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            boolean mine = u.ownerId() == session.playerId();
            if (!mine && world.isFogEnabled() && !fog.isVisible(u.tileX(), u.tileY())) {
                continue;
            }
            paint.setColor(mine ? Palette.faction(session.view().faction())
                    : Palette.faction(world.player(u.ownerId()).faction()));
            float px = bounds.left + u.x() * sx;
            float py = bounds.top + u.y() * sy;
            surface.fillRect(px - dot / 2, py - dot / 2, px + dot / 2, py + dot / 2, paint);
        }

        // Viewport box.
        Camera camera = session.camera();
        paint.setStrokeWidth(1.5f);
        paint.setColor(Palette.HUD_TEXT);
        surface.strokeRect(
                bounds.left + camera.worldX(camera.viewLeft()) * sx,
                bounds.top + camera.worldY(camera.viewTop()) * sy,
                bounds.left + camera.worldX(camera.viewLeft() + camera.viewWidth()) * sx,
                bounds.top + camera.worldY(camera.viewTop() + camera.viewHeight()) * sy, paint);

        paint.setColor(Palette.HUD_BORDER);
        surface.strokeRect(bounds, paint);
    }

    /** Rebuilds the terrain bitmap; also refreshed as uranium seams are worked out. */
    private void bake(TileMap map) {
        int oreVersion = map.totalOre() / 4000;
        if (terrain != null && oreVersion == bakedOreVersion) {
            return;
        }
        int width = map.width();
        int height = map.height();
        if (pixels == null || pixels.length != width * height) {
            pixels = new int[width * height];
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] =
                        Palette.terrain(map.terrain(x, y), x, y, map.ore(x, y));
            }
        }
        // Baked afresh rather than written into in place: an Image is opaque to the game, and
        // this only happens when a seam has visibly worked out, not every frame.
        terrain = Gfx.image(pixels, width, height);
        bakedOreVersion = oreVersion;
    }
}
