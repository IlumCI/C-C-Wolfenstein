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
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.InfluenceGrid;
import com.ccwolf.core.sim.GameWorld;
import java.util.List;

/**
 * The corner map. Terrain is baked into a one-pixel-per-tile bitmap once and then scaled, so
 * a frame only costs the entity dots, the fog and the viewport box.
 */
public final class Minimap {

    private final Brush paint = new Brush();

    /**
     * Its own brush for the front line, and it needs one.
     *
     * <p>{@code paint} has antialiasing off — right for blocks of terrain, and a staircase for a
     * diagonal contour — and the viewport box leaves {@code strokeWidth} at 1.5f behind for the
     * border to inherit. Changing either in place would fix the line and break something else.
     */
    private final Brush line = new Brush();

    private final ControlOverlay control = new ControlOverlay();
    private final Rect controlRect = new Rect();
    private final Rect bounds = new Rect();

    private Image terrain;
    private int[] pixels;
    private int bakedOreVersion = -1;

    public Minimap() {
        paint.setAntiAlias(false);
        paint.setSmoothScaling(false);
        line.setAntiAlias(true);
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

        // Held ground, before the fog blacks anything out - the field counts every unit on the
        // map, so painted over the fog it would give away where the enemy is.
        controlRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        control.draw(surface, session.view(), controlRect, session.view().controlVersion());

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

        drawFront(surface, session, sx, sy);

        paint.setColor(Palette.HUD_BORDER);
        surface.strokeRect(bounds, paint);
    }

    /** Rebuilds the terrain bitmap; also refreshed as uranium seams are worked out. */
    /**
     * The line where the two sides meet, traced across the control field.
     *
     * <p>Marching squares rather than a polyline through cell centres. The field is sixteen
     * cells across, so cell centres on a box this size are about eleven pixels apart: joining
     * them gives a chunky zigzag that jumps a whole cell whenever one flips. Interpolating the
     * crossing along each cell edge instead puts the segment where the sign actually changes,
     * which moves smoothly as the fighting does.
     *
     * <p>Only the four cases that matter are handled — a straight cut through the square, in
     * whichever of the four orientations. The two saddle cases, where opposite corners agree, are
     * left alone: they are rare on a field this smooth, and the honest failure is a one-cell gap
     * in the line rather than a segment drawn across the wrong diagonal.
     */
    private void drawFront(Surface surface, GameSession session, float sx, float sy) {
        WorldView view = session.view();
        int across = view.controlCellsAcross();
        int down = view.controlCellsDown();
        float cell = InfluenceGrid.CELL_TILES;

        line.setColor(Palette.HUD_TEXT);
        line.setStrokeWidth(Math.max(1.2f, sx * 0.9f));

        for (int y = 0; y < down - 1; y++) {
            for (int x = 0; x < across - 1; x++) {
                float topLeft = view.controlAtCell(x, y);
                float topRight = view.controlAtCell(x + 1, y);
                float bottomLeft = view.controlAtCell(x, y + 1);
                float bottomRight = view.controlAtCell(x + 1, y + 1);

                // An exact zero is ground neither side has reached - the far corners of the map
                // early on. Treating it as one side or the other would run the line along the
                // edge of a dead zone and call it a front; the honest picture is the line
                // stopping where the armies do.
                if (topLeft == 0f || topRight == 0f || bottomLeft == 0f || bottomRight == 0f) {
                    continue;
                }

                boolean a = topLeft > 0f;
                boolean b = topRight > 0f;
                boolean c = bottomRight > 0f;
                boolean d = bottomLeft > 0f;
                if (a == b && b == c && c == d) {
                    continue;
                }

                // Where along each edge the sign flips, in cells from the corner.
                float top = crossing(topLeft, topRight);
                float bottom = crossing(bottomLeft, bottomRight);
                float left = crossing(topLeft, bottomLeft);
                float right = crossing(topRight, bottomRight);

                float cx = (x + 0.5f) * cell;
                float cy = (y + 0.5f) * cell;

                if (a != b && c != d) {
                    // Cut across, entering on the top edge and leaving on the bottom.
                    segment(surface, cx + top * cell, cy, cx + bottom * cell, cy + cell, sx, sy);
                } else if (a != d && b != c) {
                    // Cut down, entering on the left edge and leaving on the right.
                    segment(surface, cx, cy + left * cell, cx + cell, cy + right * cell, sx, sy);
                } else if (a != b && a != d) {
                    segment(surface, cx + top * cell, cy, cx, cy + left * cell, sx, sy);
                } else if (a != b && b != c) {
                    segment(surface, cx + top * cell, cy, cx + cell, cy + right * cell, sx, sy);
                } else if (c != d && a != d) {
                    segment(surface, cx, cy + left * cell, cx + bottom * cell, cy + cell, sx, sy);
                } else if (c != d && b != c) {
                    segment(surface, cx + cell, cy + right * cell,
                            cx + bottom * cell, cy + cell, sx, sy);
                }
            }
        }
        // Left as the border stroke expects to find it.
        line.setStrokeWidth(1.5f);
    }

    /** How far between two cells the sign changes, as a fraction. Half if they somehow match. */
    private static float crossing(float from, float to) {
        float span = from - to;
        if (span == 0f) {
            return 0.5f;
        }
        float t = from / span;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    private void segment(Surface surface, float x0, float y0, float x1, float y1,
                         float sx, float sy) {
        surface.drawLine(bounds.left + x0 * sx, bounds.top + y0 * sy,
                bounds.left + x1 * sx, bounds.top + y1 * sy, line);
    }

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
