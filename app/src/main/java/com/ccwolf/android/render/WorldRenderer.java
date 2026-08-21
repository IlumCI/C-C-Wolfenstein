package com.ccwolf.android.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.ccwolf.android.GameSession;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import java.util.List;

/**
 * Draws the battlefield: terrain, structures, units, effects, fog and the selection overlays.
 *
 * <p>Only the tiles inside the camera's viewport are visited, so the cost of a frame depends
 * on the zoom level rather than on the size of the map.
 */
public final class WorldRenderer {

    private final Sprites sprites = new Sprites();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flat = new Paint();
    private final RectF rect = new RectF();

    /** Set while the player is dragging a selection box, in screen pixels. */
    private boolean dragging;
    private float dragX0;
    private float dragY0;
    private float dragX1;
    private float dragY1;

    public WorldRenderer() {
        flat.setStyle(Paint.Style.FILL);
        flat.setAntiAlias(false);
    }

    public void setSelectionBox(boolean active, float x0, float y0, float x1, float y1) {
        this.dragging = active;
        this.dragX0 = x0;
        this.dragY0 = y0;
        this.dragX1 = x1;
        this.dragY1 = y1;
    }

    public void draw(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        GameWorld world = session.world();

        canvas.save();
        canvas.clipRect(camera.viewLeft(), camera.viewTop(),
                camera.viewLeft() + camera.viewWidth(), camera.viewTop() + camera.viewHeight());

        drawTerrain(canvas, session);
        drawBuildings(canvas, session);
        drawUnits(canvas, session);
        drawEffects(canvas, session);
        drawPlacementGhost(canvas, session);
        drawFog(canvas, session);
        drawSelectionBox(canvas);

        canvas.restore();
        if (world.isGameOver()) {
            drawOutcome(canvas, session);
        }
    }

    // --- terrain --------------------------------------------------------------------------

    private void drawTerrain(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        TileMap map = session.world().map();
        float px = camera.tilePx();

        int x0 = camera.firstVisibleTileX();
        int y0 = camera.firstVisibleTileY();
        int x1 = camera.lastVisibleTileX();
        int y1 = camera.lastVisibleTileY();

        for (int y = y0; y <= y1; y++) {
            float sy = camera.screenY(y);
            for (int x = x0; x <= x1; x++) {
                float sx = camera.screenX(x);
                flat.setColor(Palette.terrain(map.terrain(x, y), x, y, map.ore(x, y)));
                // +1 pixel to avoid hairline seams between tiles at fractional zoom.
                canvas.drawRect(sx, sy, sx + px + 1f, sy + px + 1f, flat);
            }
        }
    }

    // --- entities -------------------------------------------------------------------------

    private void drawBuildings(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        GameWorld world = session.world();
        FogGrid fog = world.fogFor(session.playerId());
        float px = camera.tilePx();

        List<Building> buildings = world.buildings();
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (!isTileKnown(world, fog, b.tileX(), b.tileY())) {
                continue;
            }
            float left = camera.screenX(b.tileX());
            float top = camera.screenY(b.tileY());
            if (offScreen(camera, left, top, b.tilesWide() * px, b.tilesHigh() * px)) {
                continue;
            }
            Faction faction = world.player(b.ownerId()).faction();
            sprites.drawBuilding(canvas, b, faction, left, top, px);

            if (isSelected(session, b)) {
                drawSelectionRect(canvas, left, top, b.tilesWide() * px, b.tilesHigh() * px);
                drawRallyFlag(canvas, session, b);
            }
            if (b.healthFraction() < 1f) {
                drawHealthBar(canvas, left, top - px * 0.18f, b.tilesWide() * px,
                        b.healthFraction());
            }
        }
    }

    private void drawUnits(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        GameWorld world = session.world();
        FogGrid fog = world.fogFor(session.playerId());
        float px = camera.tilePx();

        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            // Enemy units are only drawn where we can actually see them right now.
            boolean mine = u.ownerId() == session.playerId();
            if (!mine && world.isFogEnabled() && !fog.isVisible(u.tileX(), u.tileY())) {
                continue;
            }
            float cx = camera.screenX(u.x());
            float cy = camera.screenY(u.y());
            if (offScreen(camera, cx - px, cy - px, px * 2, px * 2)) {
                continue;
            }

            sprites.drawUnit(canvas, u, world.player(u.ownerId()).faction(), cx, cy, px);

            // The ring goes on top of the sprite and outside it: a vehicle is nearly a tile
            // wide, so a ring drawn underneath simply disappears behind it.
            if (isSelected(session, u)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.8f, px * 0.055f));
                paint.setColor(Palette.SELECTION);
                canvas.drawCircle(cx, cy, px * 0.6f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            if (u.healthFraction() < 1f) {
                drawHealthBar(canvas, cx - px * 0.35f, cy - px * 0.42f, px * 0.7f,
                        u.healthFraction());
            }
        }
    }

    private void drawEffects(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        List<GameSession.Effect> effects = session.effects();
        for (int i = 0; i < effects.size(); i++) {
            GameSession.Effect fx = effects.get(i);
            float x = camera.screenX(fx.x);
            float y = camera.screenY(fx.y);
            if (fx.tracer) {
                paint.setColor(Palette.TRACER);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.2f, camera.tilePx() * 0.05f));
                canvas.drawLine(x, y, camera.screenX(fx.toX), camera.screenY(fx.toY), paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                float t = fx.progress();
                float radius = camera.tilePx() * (0.2f + 0.5f * t);
                int alpha = (int) (220 * (1f - t));
                paint.setColor((alpha << 24) | (Palette.EXPLOSION & 0x00FFFFFF));
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }

    private void drawPlacementGhost(Canvas canvas, GameSession session) {
        BuildingType type = session.placing();
        if (type == null) {
            return;
        }
        Camera camera = session.camera();
        int tileX = session.placeTileX();
        int tileY = session.placeTileY();
        if (tileX < 0 || tileY < 0) {
            // Nothing touched yet: park the ghost in the middle of the view.
            tileX = (int) camera.worldX(camera.viewLeft() + camera.viewWidth() / 2f);
            tileY = (int) camera.worldY(camera.viewTop() + camera.viewHeight() / 2f);
        }
        drawGhostAt(canvas, session, type, tileX, tileY);
    }

    /** Draws the translucent build ghost at a tile, coloured by whether the site is legal. */
    public void drawGhostAt(Canvas canvas, GameSession session, BuildingType type, int tileX,
                            int tileY) {
        Camera camera = session.camera();
        float px = camera.tilePx();
        float left = camera.screenX(tileX - type.tilesWide() / 2);
        float top = camera.screenY(tileY - type.tilesHigh() / 2);
        float w = type.tilesWide() * px;
        float h = type.tilesHigh() * px;

        boolean ok = session.isPlacementValid(type, tileX, tileY);
        paint.setColor(ok ? Palette.PLACE_OK : Palette.PLACE_BAD);
        canvas.drawRect(left, top, left + w, top + h, paint);

        canvas.saveLayerAlpha(left - px, top - px, left + w + px, top + h + px, 150);
        sprites.drawStructureShell(canvas, type, left, top, w, h, px,
                Palette.faction(session.player().faction()),
                Palette.factionDark(session.player().faction()));
        canvas.restore();
    }

    // --- overlays -------------------------------------------------------------------------

    private void drawFog(Canvas canvas, GameSession session) {
        GameWorld world = session.world();
        if (!world.isFogEnabled()) {
            return;
        }
        Camera camera = session.camera();
        FogGrid fog = world.fogFor(session.playerId());
        float px = camera.tilePx();

        int x0 = camera.firstVisibleTileX();
        int y0 = camera.firstVisibleTileY();
        int x1 = camera.lastVisibleTileX();
        int y1 = camera.lastVisibleTileY();

        for (int y = y0; y <= y1; y++) {
            float sy = camera.screenY(y);
            for (int x = x0; x <= x1; x++) {
                byte state = fog.state(x, y);
                if (state == FogGrid.VISIBLE) {
                    continue;
                }
                flat.setColor(state == FogGrid.UNEXPLORED
                        ? Palette.FOG_UNEXPLORED : Palette.FOG_EXPLORED);
                float sx = camera.screenX(x);
                canvas.drawRect(sx, sy, sx + px + 1f, sy + px + 1f, flat);
            }
        }
    }

    private void drawSelectionBox(Canvas canvas) {
        if (!dragging) {
            return;
        }
        paint.setColor(0x33A0E67A);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(Math.min(dragX0, dragX1), Math.min(dragY0, dragY1),
                Math.max(dragX0, dragX1), Math.max(dragY0, dragY1), paint);
        paint.setColor(Palette.SELECTION);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRect(Math.min(dragX0, dragX1), Math.min(dragY0, dragY1),
                Math.max(dragX0, dragX1), Math.max(dragY0, dragY1), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRallyFlag(Canvas canvas, GameSession session, Building b) {
        if (!b.type().isProducer()) {
            return;
        }
        Camera camera = session.camera();
        float x = camera.screenX(b.rallyX() + 0.5f);
        float y = camera.screenY(b.rallyY() + 0.5f);
        paint.setColor(Palette.SELECTION);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawLine(x, y, x, y - camera.tilePx() * 0.6f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y - camera.tilePx() * 0.6f, x + camera.tilePx() * 0.3f,
                y - camera.tilePx() * 0.4f, paint);
    }

    private void drawSelectionRect(Canvas canvas, float left, float top, float w, float h) {
        paint.setColor(Palette.SELECTION);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        canvas.drawRect(left - 2, top - 2, left + w + 2, top + h + 2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHealthBar(Canvas canvas, float left, float top, float width,
                               float fraction) {
        float height = Math.max(2.5f, width * 0.09f);
        flat.setColor(0xCC101010);
        canvas.drawRect(left, top, left + width, top + height, flat);
        flat.setColor(Palette.health(fraction));
        canvas.drawRect(left, top, left + width * fraction, top + height, flat);
    }

    private void drawOutcome(Canvas canvas, GameSession session) {
        GameWorld world = session.world();
        boolean won = world.winnerId() == session.playerId();
        paint.setColor(0xB0000000);
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);

        paint.setColor(won ? Palette.GOLD : Palette.REGIME);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(canvas.getHeight() * 0.13f);
        canvas.drawText(won ? "VALLEY HELD" : "OVERRUN", canvas.getWidth() / 2f,
                canvas.getHeight() * 0.45f, paint);

        paint.setFakeBoldText(false);
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(canvas.getHeight() * 0.05f);
        canvas.drawText("Tap to start a new skirmish", canvas.getWidth() / 2f,
                canvas.getHeight() * 0.58f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // --- helpers --------------------------------------------------------------------------

    private boolean isSelected(GameSession session, Entity e) {
        List<Integer> selection = session.selection();
        for (int i = 0; i < selection.size(); i++) {
            if (selection.get(i).intValue() == e.id()) {
                return true;
            }
        }
        return false;
    }

    /** A structure is drawn if we have ever seen its ground; units need live vision. */
    private boolean isTileKnown(GameWorld world, FogGrid fog, int tileX, int tileY) {
        return !world.isFogEnabled() || fog.isExplored(tileX, tileY);
    }

    private boolean offScreen(Camera camera, float left, float top, float w, float h) {
        return left + w < camera.viewLeft() || top + h < camera.viewTop()
                || left > camera.viewLeft() + camera.viewWidth()
                || top > camera.viewTop() + camera.viewHeight();
    }
}
